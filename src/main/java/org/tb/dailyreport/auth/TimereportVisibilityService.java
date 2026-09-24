package org.tb.dailyreport.auth;

import static org.tb.auth.domain.AccessLevel.READ;
import static org.tb.auth.service.AuthService.ANY_MATCH;

import java.util.ArrayList;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.tb.auth.domain.AuthorizedUser;
import org.tb.auth.service.AuthService;
import org.tb.common.LocalDateRange;
import org.tb.employee.domain.AuthorizedEmployee;
import org.tb.employee.service.EmployeeService;
import org.tb.employee.service.EmployeecontractService;
import org.tb.order.service.CustomerorderService;
import org.tb.order.service.SuborderService;

/**
 * Builds the {@link TimereportVisibility} of the current user for a period (#1092).
 *
 * <p>Same ladder as {@link TimereportAuthorization#isAuthorized} for {@code READ}, one rung per clause: the manager
 * reads everything, everybody reads their own bookings, a people lead those of the people they supervise, whoever is
 * responsible for an order reads what is booked on it, backoffice reads what has to be invoiced — and on top of that
 * the individual grants, {@code AuthorizationRule} of category {@code TIMEREPORT}.
 *
 * <p><b>This is a second expression of a rule that already exists</b>, and that is its risk: it can drift away from
 * {@code isAuthorized} without anything failing. {@code TimereportVisibilityConsistencyTest} holds the two against
 * each other for real bookings; whoever changes one of them runs it.
 *
 * <p>Nothing here costs a statement on {@code timereport}: the roles come from the session, the rules from the
 * in-memory cache of {@link AuthService}, and the three lookups go to master data.
 */
@Component
@RequiredArgsConstructor
public class TimereportVisibilityService {

  static final String AUTH_CATEGORY_TIMEREPORT = "TIMEREPORT";
  private static final String SIGN_SEPARATOR = ":";

  private final AuthorizedUser authorizedUser;
  private final AuthorizedEmployee authorizedEmployee;
  private final AuthService authService;
  private final EmployeeService employeeService;
  private final EmployeecontractService employeecontractService;
  private final CustomerorderService customerorderService;
  private final SuborderService suborderService;

  public TimereportVisibility forPeriod(LocalDateRange period) {
    if (authorizedUser.isManager()) {
      return TimereportVisibility.all();
    }

    var clauses = new ArrayList<TimereportVisibility.Clause>();
    var employeeId = authorizedEmployee.getEmployeeId();
    if (employeeId != null) {
      clauses.add(TimereportVisibility.Clause.forEmployees(Set.of(employeeId)));

      if (authorizedUser.isPeopleLead()) {
        var supervised = employeecontractService.getSupervisedEmployeeIds(employeeId);
        if (!supervised.isEmpty()) {
          clauses.add(TimereportVisibility.Clause.forEmployees(supervised));
        }
      }

      var responsibleFor = customerorderService.getIdsByResponsibleEmployeeId(employeeId);
      if (!responsibleFor.isEmpty()) {
        clauses.add(TimereportVisibility.Clause.forOrders(Set.copyOf(responsibleFor)));
      }
    }

    if (authorizedUser.isBackoffice()) {
      clauses.add(TimereportVisibility.Clause.billable());
    }

    for (var rule : authService.getRulesForCurrentUser(AUTH_CATEGORY_TIMEREPORT, period, READ)) {
      if (ANY_MATCH.equals(rule.getObjectId())) {
        // A rule without an object grants the category as a whole — nothing left to restrict.
        return TimereportVisibility.all();
      }
      toClause(rule.getObjectId()).ifPresent(clauses::add);
    }

    return TimereportVisibility.of(clauses);
  }

  /**
   * Reads one object of a rule back into a clause. The forms are the ones {@code TimereportAuthorization#objectsOf}
   * writes, and the separator is read at its first occurrence for the same reason it is written there: an order sign
   * may contain a colon, an employee sign may not.
   *
   * <p>An object naming something that no longer exists yields no clause rather than an empty one — an empty clause
   * would restrict nothing and grant everything.
   */
  private Optional<TimereportVisibility.Clause> toClause(String objectId) {
    var separator = objectId.indexOf(SIGN_SEPARATOR);
    var employeeSign = separator < 0 ? null : objectId.substring(0, separator);
    var orderPart = separator < 0 ? objectId : objectId.substring(separator + 1);

    Set<Long> employeeIds = Set.of();
    if (employeeSign != null) {
      var employee = employeeService.getEmployeeBySign(employeeSign);
      if (employee == null) return Optional.empty();
      employeeIds = Set.of(employee.getId());
    }

    if (ANY_MATCH.equals(orderPart)) {
      // xx:* — the bookings of one person, on every order
      return employeeIds.isEmpty()
          ? Optional.empty()
          : Optional.of(new TimereportVisibility.Clause(employeeIds, Set.of(), Set.of(), false));
    }

    if (orderPart.contains("/")) {
      var suborder = suborderService.getSuborderByCompleteOrderSign(orderPart);
      if (suborder == null) return Optional.empty();
      return Optional.of(
          new TimereportVisibility.Clause(employeeIds, Set.of(), Set.of(suborder.getId()), false));
    }

    var customerorder = customerorderService.getCustomerorderBySign(orderPart);
    if (customerorder == null) return Optional.empty();
    return Optional.of(
        new TimereportVisibility.Clause(employeeIds, Set.of(customerorder.getId()), Set.of(), false));
  }

  /**
   * The scope without a period: every rule that was ever valid counts. That is what the filter lists need — a booking
   * from last month is covered by a grant that was valid last month, so a list of values that only knew today's rules
   * would leave out what the bookings behind them show.
   */
  public TimereportVisibility anyTime() {
    return forPeriod(new LocalDateRange(LocalDateRange.FINIT_FROM_BOUNDARY, LocalDateRange.FINIT_UNTIL_BOUNDARY));
  }
}
