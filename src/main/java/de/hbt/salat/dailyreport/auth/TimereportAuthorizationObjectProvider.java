package de.hbt.salat.dailyreport.auth;

import static de.hbt.salat.auth.service.AuthService.ANY_MATCH;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import de.hbt.salat.auth.domain.AuthorizationObject;
import de.hbt.salat.auth.domain.AuthorizationObjectProvider;
import de.hbt.salat.auth.domain.ObjectJudgement;
import de.hbt.salat.employee.service.EmployeeService;
import de.hbt.salat.order.service.CustomerorderService;
import de.hbt.salat.order.service.SuborderService;

/**
 * What the rule editor may offer for the category {@code TIMEREPORT} (#1074).
 *
 * <p>The only category that restricts two things at once — whose bookings, and on which order — and it carries both
 * in the one object since #1089. It cannot be enumerated: the product of every person and every order is not a list
 * anybody wants to scroll, so the field stays free text with a hint.
 *
 * <p><b>Typed and stored are two forms</b> (#1204). People type what they know, and that is the forms
 * {@link TimereportAuthorization} used to ask with:
 * <ul>
 *   <li>{@code 1453}, {@code 1453/01} — the bookings of everybody on that order</li>
 *   <li>{@code xx:1453}, {@code xx:1453/01} — only the bookings of xx there</li>
 *   <li>{@code xx:*} — the bookings of xx, on every order</li>
 * </ul>
 * The rule stores the ids instead ({@link TimereportRuleObject}), and the editor shows them in the typed form again,
 * with the sign and the order sign they have today. {@link #objectIdOf} translates, {@link #describe} translates back.
 */
@Component
@RequiredArgsConstructor
public class TimereportAuthorizationObjectProvider implements AuthorizationObjectProvider {

  private static final String SIGN_SEPARATOR = ":";

  private final EmployeeService employeeService;
  private final CustomerorderService customerorderService;
  private final SuborderService suborderService;

  @Override
  public String category() {
    return "TIMEREPORT";
  }

  @Override
  public String labelKey() {
    return "main.auth.rule.category.timereport";
  }

  @Override
  public String objectHintKey() {
    return "main.auth.rule.object.hint.timereport";
  }

  /** Not enumerable — every person times every order is not a list anybody picks from. */
  @Override
  public List<AuthorizationObject> objects() {
    return List.of();
  }

  /**
   * Reads the typed form. The separator is read at its first colon: an order sign may contain one, a sign may not. A
   * sign or an order sign that does not resolve yields nothing — an id cannot precede the record it names.
   */
  @Override
  public Optional<String> objectIdOf(String input) {
    var separator = input.indexOf(SIGN_SEPARATOR);
    var orderSign = separator < 0 ? input : input.substring(separator + SIGN_SEPARATOR.length());
    Long employeeId = null;
    if (separator >= 0) {
      var employee = employeeService.getEmployeeBySign(input.substring(0, separator));
      if (employee == null) {
        return Optional.empty();
      }
      employeeId = employee.getId();
      if (ANY_MATCH.equals(orderSign)) {
        // "that person, on every order" — a wildcard only this category knows
        return Optional.of(new TimereportRuleObject(employeeId, null, null).toString());
      }
    }
    if (orderSign.isBlank()) {
      return Optional.empty();
    }
    // a path (1453/01) names a suborder, a single sign the customer order
    if (orderSign.contains("/")) {
      var suborder = suborderService.getSuborderByCompleteOrderSign(orderSign);
      return suborder == null
          ? Optional.empty()
          : Optional.of(new TimereportRuleObject(employeeId, null, suborder.getId()).toString());
    }
    var customerorder = customerorderService.getCustomerorderBySign(orderSign);
    return customerorder == null
        ? Optional.empty()
        : Optional.of(new TimereportRuleObject(employeeId, customerorder.getId(), null).toString());
  }

  /** Shows a stored object in the typed form, with today's sign and order sign. */
  @Override
  public Map<String, AuthorizationObject> describe(Collection<String> objectIds) {
    var described = new LinkedHashMap<String, AuthorizationObject>();
    for (var objectId : objectIds) {
      typedFormOf(objectId).ifPresent(typed -> described.put(objectId, new AuthorizationObject(objectId, typed)));
    }
    return described;
  }

  @Override
  public ObjectJudgement judge(String objectId) {
    if (TimereportRuleObject.parse(objectId).isEmpty()) {
      return ObjectJudgement.MALFORMED;
    }
    return typedFormOf(objectId).isPresent() ? ObjectJudgement.VALID : ObjectJudgement.UNKNOWN;
  }

  private Optional<String> typedFormOf(String objectId) {
    var object = TimereportRuleObject.parse(objectId).orElse(null);
    if (object == null) {
      return Optional.empty();
    }
    String sign = null;
    if (object.employeeId() != null) {
      var employee = employeeService.getEmployeeById(object.employeeId());
      if (employee == null) {
        return Optional.empty();
      }
      sign = employee.getSign();
    }
    String orderSign;
    if (object.customerorderId() != null) {
      var customerorder = customerorderService.getCustomerorderById(object.customerorderId());
      if (customerorder == null) {
        return Optional.empty();
      }
      orderSign = customerorder.getSign();
    } else if (object.suborderId() != null) {
      var suborder = suborderService.getSuborderById(object.suborderId());
      if (suborder == null) {
        return Optional.empty();
      }
      orderSign = suborder.getCompleteOrderSign();
    } else {
      orderSign = ANY_MATCH;
    }
    return Optional.of(sign == null ? orderSign : sign + SIGN_SEPARATOR + orderSign);
  }

}
