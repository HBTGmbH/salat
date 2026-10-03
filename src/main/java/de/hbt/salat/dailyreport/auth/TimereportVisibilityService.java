package de.hbt.salat.dailyreport.auth;

import static de.hbt.salat.auth.domain.AccessLevel.READ;
import static de.hbt.salat.auth.service.AuthService.ANY_MATCH;

import java.util.ArrayList;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import de.hbt.salat.auth.domain.AuthorizedUser;
import de.hbt.salat.auth.service.AuthService;
import de.hbt.salat.common.LocalDateRange;
import de.hbt.salat.employee.domain.AuthorizedEmployee;
import de.hbt.salat.employee.service.EmployeecontractService;
import de.hbt.salat.order.service.CustomerorderService;

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
 * in-memory cache of {@link AuthService}, and the lookups of team and responsibility go to master data.
 */
@Component
@RequiredArgsConstructor
public class TimereportVisibilityService {

  static final String AUTH_CATEGORY_TIMEREPORT = "TIMEREPORT";

  private final AuthorizedUser authorizedUser;
  private final AuthorizedEmployee authorizedEmployee;
  private final AuthService authService;
  private final EmployeecontractService employeecontractService;
  private final CustomerorderService customerorderService;

  public TimereportVisibility forPeriod(LocalDateRange period) {
    if (authorizedUser.isManager()) {
      return TimereportVisibility.all();
    }

    var clauses = new ArrayList<TimereportVisibility.Clause>();
    var employeeId = authorizedEmployee.getEmployeeId();
    if (employeeId != null) {
      clauses.add(TimereportVisibility.Clause.forEmployees(Set.of(employeeId)));

      if (authorizedUser.isPeopleLead()) {
        var supervised = employeecontractService.getTeamEmployeeIdsIncludingExpired(employeeId);
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
   * Reads one object of a rule back into a clause, in the forms {@link TimereportRuleObject} defines.
   *
   * <p>An object that is none of the forms — a value the move to ids (#1204) could not assign — yields no clause rather
   * than an empty one: an empty clause would restrict nothing and grant everything. An id that no longer exists needs
   * no lookup: the clause it yields simply matches no booking.
   */
  private Optional<TimereportVisibility.Clause> toClause(String objectId) {
    return TimereportRuleObject.parse(objectId).map(object -> new TimereportVisibility.Clause(
        idsOf(object.employeeId()), idsOf(object.customerorderId()), idsOf(object.suborderId()), false));
  }

  private static Set<Long> idsOf(Long id) {
    return id == null ? Set.of() : Set.of(id);
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
