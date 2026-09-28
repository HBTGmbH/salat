package org.tb.common.palette;

import java.text.Normalizer;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * What was typed into the palette, prepared for the providers (#1157): up to {@link #MAX_WORDS}
 * words, each of which a hit has to contain somewhere, and a ranking of how well a hit answers.
 *
 * <p>The words reach the database as {@code lower(column) like :word escape '!'}. The escape
 * character is not the backslash on purpose: MySQL reads a backslash inside a string literal as an
 * escape of its own, and {@code escape '\'} would never reach it as one character. {@code lower}
 * on both sides, because the collation of the columns is not the same everywhere — {@code utf8_bin}
 * in the test schema compares case-sensitively.
 */
public final class PaletteQuery {

  /** From this many characters on the palette asks the server at all. */
  public static final int MIN_LENGTH = 2;

  /** More words than this are one word too many for the fixed parameters of the queries. */
  public static final int MAX_WORDS = 3;

  /**
   * How many candidates a provider fetches at most, before it ranks them and the palette keeps the
   * best of each kind. The queries order hidden and ended objects last, so the candidates are the
   * current ones as long as there are enough of those.
   */
  public static final int CANDIDATE_LIMIT = 30;

  /** How many hits of one kind the palette shows. */
  public static final int HITS_PER_KIND = 5;

  public static final char LIKE_ESCAPE = '!';

  private final String text;
  private final List<String> words;

  private PaletteQuery(String text, List<String> words) {
    this.text = text;
    this.words = words;
  }

  /**
   * Characters outside the Basic Multilingual Plane, emoji above all, are dropped: they take four
   * bytes in UTF-8, and MySQL refuses to compare them with a column in utf8mb3 — the query would end
   * in an error instead of in no hits. They stand in no sign and no name anyway.
   */
  public static PaletteQuery of(String raw) {
    var text = raw == null ? "" : raw.codePoints()
        .filter(Character::isBmpCodePoint)
        .collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append)
        .toString().trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    var words = text.isEmpty() ? List.<String>of()
        : Arrays.stream(text.split(" ")).limit(MAX_WORDS).toList();
    return new PaletteQuery(text, words);
  }

  /** The query in lower case, whitespace collapsed. */
  public String text() {
    return text;
  }

  public boolean isSearchable() {
    return text.length() >= MIN_LENGTH;
  }

  /**
   * The LIKE pattern of the n-th word ({@code %word%}, lower case, escaped with
   * {@link #LIKE_ESCAPE}), or {@code null} where the query has fewer words — the queries read a
   * {@code null} word as "no condition".
   */
  public String likeWord(int index) {
    if (index >= words.size()) {
      return null;
    }
    return "%" + escape(words.get(index)) + "%";
  }

  static String escape(String word) {
    var escaped = new StringBuilder(word.length());
    for (char c : word.toCharArray()) {
      if (c == LIKE_ESCAPE || c == '%' || c == '_') {
        escaped.append(LIKE_ESCAPE);
      }
      escaped.append(c);
    }
    return escaped.toString();
  }

  /**
   * How well a hit answers the query, the same order as in the palette's own ranking (salat.js):
   * 4 the title begins with it, 3 a word of the title or of another field begins with it, 2 every
   * word stands in one of the fields, 1 anything else the database found. Case and diacritics do
   * not count.
   */
  public int match(String title, String... others) {
    var folded = fold(title);
    var query = fold(text);
    if (!query.isEmpty() && folded.startsWith(query)) {
      return 4;
    }
    var fields = Stream.concat(Stream.of(title), Arrays.stream(others))
        .filter(Objects::nonNull).map(PaletteQuery::fold).toList();
    if (fields.stream().anyMatch(field -> startsAWord(field, query))) {
      return 3;
    }
    var foldedWords = words.stream().map(PaletteQuery::fold).toList();
    if (foldedWords.stream().allMatch(word -> fields.stream().anyMatch(field -> field.contains(word)))) {
      return 2;
    }
    return 1;
  }

  /**
   * {@link #match} with one tier above the others: 5 where the query is the whole key of the hit,
   * which is not its title — the sign of a person, who is shown by name. A short sign is the
   * beginning of many names, and without this tier all of them would rank before its owner. The
   * palette's own ranking has no keys and needs no such tier.
   */
  public int matchWithKey(String key, String title, String... others) {
    if (isKey(key)) {
      return 5;
    }
    return match(title, Stream.concat(Stream.of(key), Arrays.stream(others)).toArray(String[]::new));
  }

  /** The query is the whole key — a person's sign, an order's sign —, case and diacritics aside. */
  public boolean isKey(String key) {
    return key != null && !text.isEmpty() && fold(key).equals(fold(text));
  }

  /** Every word of the query stands in one of the fields; an empty query is contained in anything. */
  public boolean isContainedIn(String... fields) {
    var folded = Arrays.stream(fields).filter(Objects::nonNull).map(PaletteQuery::fold).toList();
    return words.stream().map(PaletteQuery::fold)
        .allMatch(word -> folded.stream().anyMatch(field -> field.contains(word)));
  }

  private static boolean startsAWord(String field, String query) {
    if (query.isEmpty()) {
      return false;
    }
    for (int at = field.indexOf(query); at >= 0; at = field.indexOf(query, at + 1)) {
      if (at == 0 || !Character.isLetterOrDigit(field.charAt(at - 1))) {
        return true;
      }
    }
    return false;
  }

  static String fold(String value) {
    if (value == null) {
      return "";
    }
    return Normalizer.normalize(value, Normalizer.Form.NFD).replaceAll("\\p{M}", "")
        .toLowerCase(Locale.ROOT);
  }
}
