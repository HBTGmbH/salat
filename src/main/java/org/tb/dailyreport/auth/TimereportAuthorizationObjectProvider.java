package org.tb.dailyreport.auth;

import static org.tb.auth.service.AuthService.ANY_MATCH;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.tb.auth.domain.AuthorizationObject;
import org.tb.auth.domain.AuthorizationObjectProvider;
import org.tb.auth.domain.ObjectJudgement;
import org.tb.employee.service.EmployeeService;
import org.tb.order.service.CustomerorderService;

/**
 * What the rule editor may offer for the category {@code TIMEREPORT} (#1074).
 *
 * <p>The only category that restricts two things at once — whose bookings, and on which order — and it carries both
 * in the one object since #1089. It cannot be enumerated: the product of every person and every order is not a list
 * anybody wants to scroll, so the field stays free text with a hint, and the judgement below is what catches the
 * typo the list would otherwise have caught.
 *
 * <p>The forms, exactly the ones {@link TimereportAuthorization} asks with:
 * <ul>
 *   <li>{@code 1453}, {@code 1453/01} — the bookings of everybody on that order</li>
 *   <li>{@code xx:1453}, {@code xx:1453/01} — only the bookings of xx there</li>
 *   <li>{@code xx:*} — the bookings of xx, on every order</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
public class TimereportAuthorizationObjectProvider implements AuthorizationObjectProvider {

  private static final String SIGN_SEPARATOR = ":";

  private final EmployeeService employeeService;
  private final CustomerorderService customerorderService;

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

  @Override
  public ObjectJudgement judge(String objectId) {
    var separator = objectId.indexOf(SIGN_SEPARATOR);
    if (separator < 0) {
      return judgeOrder(objectId);
    }
    // Read at the first colon: an order sign may contain one, a sign may not.
    var employeeSign = objectId.substring(0, separator);
    var orderSign = objectId.substring(separator + SIGN_SEPARATOR.length());
    if (employeeSign.isBlank() || orderSign.isBlank()) {
      return ObjectJudgement.MALFORMED;
    }
    if (!employeeService.getAllEmployeeSigns().contains(employeeSign)) {
      return ObjectJudgement.UNKNOWN;
    }
    // "that person, on every order" — a wildcard only this category knows, so only this class can let it pass.
    return ANY_MATCH.equals(orderSign) ? ObjectJudgement.VALID : judgeOrder(orderSign);
  }

  /**
   * An order sign, possibly the path of a suborder ({@code 1453/01/02}). Judged by its first segment: that the path
   * below it exists is not decided here — a rule may precede the suborder it is about.
   */
  private ObjectJudgement judgeOrder(String orderSign) {
    var customerOrderSign = orderSign.split("/", 2)[0];
    if (customerOrderSign.isBlank()) {
      return ObjectJudgement.MALFORMED;
    }
    return customerorderService.getCustomerorderBySign(customerOrderSign) == null
        ? ObjectJudgement.UNKNOWN
        : ObjectJudgement.VALID;
  }

}
