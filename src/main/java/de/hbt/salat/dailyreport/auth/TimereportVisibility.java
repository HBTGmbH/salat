package de.hbt.salat.dailyreport.auth;

import jakarta.annotation.Nullable;
import java.util.List;
import java.util.Set;
import de.hbt.salat.common.LocalDateRange;
import de.hbt.salat.dailyreport.domain.Timereport;

/**
 * Who may read which bookings, as a value a query can carry (#1092).
 *
 * <p>{@link TimereportAuthorization#isAuthorized} answers that question for <em>one</em> booking. A list must not ask
 * it per row: it would load everything to throw most of it away. This is the same rule, turned into a condition —
 * built once per request by {@link TimereportVisibilityService}, used twice: as the {@code where} of the booking query
 * and as the source of the values the filters offer.
 *
 * <p><b>The scope is a disjunction, not a cross product.</b> A rule {@code xx:1453} means "the bookings of xx on that
 * order", not "every visible employee on every visible order". Collecting all employees into one set and all orders
 * into another and combining them with {@code and} would grant more than any single source does — a security bug, not
 * a cosmetic one. Every source therefore contributes its own {@link Clause}, and the clauses are combined with
 * {@code or}.
 *
 * @param unrestricted a manager reads everything; no condition is needed and none is built
 * @param clauses      alternatives, each of which is sufficient on its own
 */
public record TimereportVisibility(boolean unrestricted, List<Clause> clauses) {

  /** Everything — what a manager gets; the query then carries no visibility condition at all. */
  public static TimereportVisibility all() {
    return new TimereportVisibility(true, List.of());
  }

  public static TimereportVisibility of(List<Clause> clauses) {
    return new TimereportVisibility(false, List.copyOf(clauses));
  }

  /** Nothing is visible — an authenticated user without any booking of their own and without any rule. */
  public boolean isEmpty() {
    return !unrestricted && clauses.isEmpty();
  }

  /**
   * Whether this scope covers the given booking. Only the query needs the condition; this method is what a test uses
   * to hold the scope against {@link TimereportAuthorization#isAuthorized} for real bookings, so the two cannot drift
   * apart unnoticed.
   */
  public boolean covers(Timereport timereport) {
    return unrestricted || clauses.stream().anyMatch(clause -> clause.covers(timereport));
  }

  /**
   * One sufficient reason to see a booking. An empty set means "no restriction in this dimension", so a clause of
   * four empty sets, {@code billableOnly = false} and no days would cover everything — which is why only
   * {@link TimereportVisibilityService} creates clauses and never creates that one.
   *
   * @param employeeIds      whose bookings, empty for anybody's
   * @param contractIds      on which contracts, empty for all — what a people lead gets: a person has one id but over
   *                         time several contracts, and the lead reads the bookings on the contracts they lead, not
   *                         those on a contract under somebody else (#1548)
   * @param customerOrderIds on which orders, empty for all
   * @param suborderIds      on which suborders, empty for all
   * @param billableOnly     restricted to bookings on a billable suborder — what backoffice gets
   * @param days             restricted to bookings on these days, {@code null} for any day: the validity of a rule
   *                         that does not cover the whole period asked for (#1534) — {@code isAuthorized} measures a
   *                         rule against the day of the booking, not against the period of a list
   */
  public record Clause(Set<Long> employeeIds, Set<Long> contractIds, Set<Long> customerOrderIds,
                       Set<Long> suborderIds, boolean billableOnly, @Nullable LocalDateRange days) {

    public Clause {
      employeeIds = Set.copyOf(employeeIds);
      contractIds = Set.copyOf(contractIds);
      customerOrderIds = Set.copyOf(customerOrderIds);
      suborderIds = Set.copyOf(suborderIds);
    }

    public static Clause forEmployees(Set<Long> employeeIds) {
      return new Clause(employeeIds, Set.of(), Set.of(), Set.of(), false, null);
    }

    public static Clause forContracts(Set<Long> contractIds) {
      return new Clause(Set.of(), contractIds, Set.of(), Set.of(), false, null);
    }

    public static Clause forOrders(Set<Long> customerOrderIds) {
      return new Clause(Set.of(), Set.of(), customerOrderIds, Set.of(), false, null);
    }

    public static Clause billable() {
      return new Clause(Set.of(), Set.of(), Set.of(), Set.of(), true, null);
    }

    /** Every booking on these days — what a wildcard rule grants that is valid for part of the period only. */
    public static Clause onDays(LocalDateRange days) {
      return new Clause(Set.of(), Set.of(), Set.of(), Set.of(), false, days);
    }

    public boolean covers(Timereport timereport) {
      if (days != null && !days.contains(timereport.getReferenceday().getRefdate())) {
        return false;
      }
      var suborder = timereport.getSuborder();
      if (!employeeIds.isEmpty()
          && !employeeIds.contains(timereport.getEmployeecontract().getEmployee().getId())) {
        return false;
      }
      if (!contractIds.isEmpty() && !contractIds.contains(timereport.getEmployeecontract().getId())) {
        return false;
      }
      if (!customerOrderIds.isEmpty()
          && !customerOrderIds.contains(suborder.getCustomerorder().getId())) {
        return false;
      }
      if (!suborderIds.isEmpty() && !suborderIds.contains(suborder.getId())) {
        return false;
      }
      return !billableOnly || suborder.isInvoiceable();
    }
  }
}
