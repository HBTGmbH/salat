package de.hbt.salat.dailyreport.auth;

import static de.hbt.salat.auth.service.AuthService.ANY_MATCH;

import java.util.Optional;
import java.util.regex.Pattern;

/**
 * The object of a rule of category {@code TIMEREPORT}: whose bookings, and on which order (#1089) — both by id since
 * #1204. A sign or an order sign can be changed and given to something else; the rule would then follow the name, not
 * the thing.
 *
 * <p>The forms:
 * <ul>
 *   <li>{@code C42}, {@code S77} — the bookings of everybody on customer order 42, on suborder 77</li>
 *   <li>{@code E12:C42}, {@code E12:S77} — only the bookings of employee 12 there</li>
 *   <li>{@code E12:*} — the bookings of employee 12, on every order</li>
 * </ul>
 * The letter in front says what the number is: a customer order and a suborder can carry the same id. A suborder covers
 * its own bookings, not those of the suborders below it — exactly as the order sign did before.
 *
 * <p>The format lives here and only here: {@link TimereportAuthorization} asks with every form a booking matches,
 * {@link TimereportVisibilityService} reads the forms back into a query, and the rule editor writes them.
 *
 * @param employeeId      the person, or {@code null} for everybody
 * @param customerorderId the customer order, or {@code null}
 * @param suborderId      the suborder, or {@code null}; at most one of the two order ids is set, neither for "every
 *                        order"
 */
public record TimereportRuleObject(Long employeeId, Long customerorderId, Long suborderId) {

  private static final Pattern FORM = Pattern.compile("(?:E(\\d+):)?(?:C(\\d+)|S(\\d+)|(\\*))");

  /** Every form a booking of this person on this suborder of this customer order matches. */
  public static String[] formsOf(long employeeId, long customerorderId, long suborderId) {
    return new String[] {
        new TimereportRuleObject(null, customerorderId, null).toString(),
        new TimereportRuleObject(null, null, suborderId).toString(),
        new TimereportRuleObject(employeeId, customerorderId, null).toString(),
        new TimereportRuleObject(employeeId, null, suborderId).toString(),
        new TimereportRuleObject(employeeId, null, null).toString()
    };
  }

  /**
   * Reads one object of a rule. Empty for anything that is not one of the forms — a value the migration could not
   * assign is one of those, and so is the bare wildcard, which is the auth module's business, not this category's.
   */
  public static Optional<TimereportRuleObject> parse(String objectId) {
    var matcher = FORM.matcher(objectId);
    if (!matcher.matches()) {
      return Optional.empty();
    }
    var employeeId = idOf(matcher.group(1));
    if (matcher.group(4) != null && employeeId == null) {
      return Optional.empty();
    }
    return Optional.of(new TimereportRuleObject(employeeId, idOf(matcher.group(2)), idOf(matcher.group(3))));
  }

  /** Whether the object stands for every order of its person. */
  public boolean anyOrder() {
    return customerorderId == null && suborderId == null;
  }

  @Override
  public String toString() {
    var order = customerorderId != null ? "C" + customerorderId : suborderId != null ? "S" + suborderId : ANY_MATCH;
    return employeeId == null ? order : "E" + employeeId + ":" + order;
  }

  private static Long idOf(String digits) {
    return digits == null ? null : Long.valueOf(digits);
  }

}
