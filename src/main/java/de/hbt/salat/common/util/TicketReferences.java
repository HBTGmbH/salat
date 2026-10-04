package de.hbt.salat.common.util;

import static de.hbt.salat.common.GlobalConstants.TICKET_REFERENCE_MAX_LENGTH;
import static de.hbt.salat.common.exception.ErrorCode.TR_TICKET_REFERENCE_DUPLICATE;
import static de.hbt.salat.common.exception.ErrorCode.TR_TICKET_REFERENCE_INVALID_LENGTH;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import de.hbt.salat.common.exception.InvalidDataException;

/**
 * The rule every ticket reference of a booking or a favourite is stored under (#982, #1326).
 *
 * <p>A reference is free text: the suggestions are a convenience, and a ticket that was never
 * replicated must still be bookable. Surrounding blanks go, and a reference is at most
 * {@value de.hbt.salat.common.GlobalConstants#TICKET_REFERENCE_MAX_LENGTH} characters long. What has
 * the shape of a ticket key is stored in capitals — JIRA hands its keys out that way, and {@code abc-12}
 * names the same ticket as {@code ABC-12}. Anything else, a word without a number say, stays as typed.
 *
 * <p>Here rather than next to the booking: a favourite stores the same references and has to store
 * them under the same rule, and the favourites module must not depend on the bookings.
 */
public final class TicketReferences {

  /**
   * The shape of a ticket key: a letter, at least one more letter, digit or underscore, a hyphen, a
   * number — {@code ABC-12}, {@code A1_B-7}. Upper and lower case alike; {@code A-1} is too short to be
   * one.
   */
  private static final String KEY = "[A-Za-z][A-Za-z0-9_]+-[0-9]+";

  private static final Pattern WHOLE_KEY = Pattern.compile(KEY);

  /** A key standing on its own in a text: not glued to a longer word, before or after it. */
  private static final Pattern KEY_IN_TEXT = Pattern.compile("(?<![A-Za-z0-9_-])" + KEY + "(?![A-Za-z0-9_-])");

  private TicketReferences() {
  }

  /**
   * One reference as it is stored, {@code null} for an empty or blank one.
   *
   * @throws InvalidDataException if the reference is longer than the column
   */
  public static String normalize(String reference) {
    if (reference == null || reference.isBlank()) {
      return null;
    }
    var trimmed = reference.trim();
    DataValidationUtils.lengthIsInRange(trimmed, 0, TICKET_REFERENCE_MAX_LENGTH, TR_TICKET_REFERENCE_INVALID_LENGTH);
    return WHOLE_KEY.matcher(trimmed).matches() ? trimmed.toUpperCase(Locale.ROOT) : trimmed;
  }

  /**
   * The references of one booking as they are stored, in the order given; empty and blank ones drop
   * out. A reference names a ticket once per booking (#1326): a second one that equals an earlier one
   * apart from case is refused, not silently dropped — whoever sent it meant something by it.
   *
   * @throws InvalidDataException if a reference is too long or occurs twice
   */
  public static List<String> normalize(Collection<String> references) {
    if (references == null || references.isEmpty()) {
      return List.of();
    }
    var normalized = new ArrayList<String>();
    var seen = new HashSet<String>();
    for (var reference : references) {
      var value = normalize(reference);
      if (value == null) {
        continue;
      }
      if (!seen.add(comparable(value))) {
        throw new InvalidDataException(TR_TICKET_REFERENCE_DUPLICATE, value);
      }
      normalized.add(value);
    }
    return List.copyOf(normalized);
  }

  /**
   * The ticket keys a text names, in capitals, in the order they first occur and each once — what the
   * booking form and the inline edit propose as references from a comment (#1326). Every word of the
   * shape counts, also one that is not a ticket at all ({@code ISO-9001}); the person decides.
   */
  public static List<String> keysIn(String text) {
    if (text == null || text.isBlank()) {
      return List.of();
    }
    var keys = new LinkedHashSet<String>();
    var matcher = KEY_IN_TEXT.matcher(text);
    while (matcher.find()) {
      keys.add(matcher.group().toUpperCase(Locale.ROOT));
    }
    return List.copyOf(keys);
  }

  /** The keys of {@link #keysIn} that are not among {@code references} yet. */
  public static List<String> keysNotReferenced(String text, Collection<String> references) {
    var referenced = new HashSet<String>();
    if (references != null) {
      references.stream().filter(reference -> reference != null && !reference.isBlank())
          .map(TicketReferences::comparable).forEach(referenced::add);
    }
    return keysIn(text).stream().filter(key -> !referenced.contains(comparable(key))).toList();
  }

  /**
   * What two references are compared by: without surrounding blanks and without regard to case — the
   * comparison the worklog sync matches a reference against a replicated ticket with.
   */
  public static String comparable(String reference) {
    return reference.trim().toUpperCase(Locale.ROOT);
  }
}
