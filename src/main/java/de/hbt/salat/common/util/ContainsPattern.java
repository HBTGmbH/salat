package de.hbt.salat.common.util;

import java.util.Locale;

/**
 * A typed search term as the pattern of {@code lower(column) like :pattern escape '!'}, which finds it anywhere in
 * the column, case aside (#1331) — the database counterpart of {@code value.toLowerCase().contains(term)}.
 *
 * <p>The escape character is not the backslash on purpose: MySQL reads a backslash inside a string literal as an
 * escape of its own (see {@code PaletteQuery}).
 */
public final class ContainsPattern {

  public static final char ESCAPE = '!';

  private ContainsPattern() {
  }

  /**
   * {@code %term%}: trimmed, in lower case, with {@code %}, {@code _} and the escape character taken literally.
   * {@code null} for a blank term — the queries read that as no condition.
   */
  public static String of(String term) {
    var search = term == null ? "" : term.trim().toLowerCase(Locale.ROOT);
    if (search.isEmpty()) {
      return null;
    }
    var pattern = new StringBuilder(search.length() + 2).append('%');
    for (char c : search.toCharArray()) {
      if (c == ESCAPE || c == '%' || c == '_') {
        pattern.append(ESCAPE);
      }
      pattern.append(c);
    }
    return pattern.append('%').toString();
  }

  /**
   * Whether the term can stand in a column in {@code utf8mb3} at all. A character outside the Basic Multilingual
   * Plane, an emoji above all, cannot, and MySQL refuses to compare it with such a column — the query would end in an
   * error instead of in no hits.
   */
  public static boolean canOccurInUtf8mb3(String term) {
    return term == null || term.codePoints().allMatch(Character::isBmpCodePoint);
  }
}
