package org.tb.budget.service;

import static java.util.Comparator.comparing;
import static java.util.Comparator.reverseOrder;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;

import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.function.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.tb.auth.domain.Authorized;
import org.tb.auth.domain.AuthorizedUser;
import org.tb.budget.auth.BudgetAuthorization;
import org.tb.budget.domain.AppliedRate;
import org.tb.budget.domain.AppliedRateLookup;
import org.tb.budget.domain.AppliedRates;
import org.tb.budget.domain.AssignedBooking;
import org.tb.budget.domain.AssignedEmployeeDay;
import org.tb.budget.domain.BudgetEmployee;
import org.tb.budget.domain.BudgetEmployeeMinutes;
import org.tb.budget.domain.BudgetEmployees;
import org.tb.budget.domain.CostCategoryRate;
import org.tb.budget.domain.OrderBudget;
import org.tb.budget.persistence.TimereportBudgetAssignmentRepository;
import org.tb.order.domain.Suborder;
import org.tb.order.service.SuborderService;

/**
 * Who works on a budget plan, and which rates apply to their work (#964).
 *
 * <p>Until now a plan told nobody who booked on it, and nothing at all about what that work costs or
 * earns. Both resolutions existed — {@code EmployeeCostLookup.findEffectiveCost} and
 * {@code OrderPricingLookup.findEffectiveRate} — but only to compute with, never to show. This
 * service shows them, and it changes nothing: no assignment is created, no condition, no default.
 *
 * <p>The costs are for managers only, as in controlling. The condition applies to everybody who
 * reaches the plan: whoever is responsible for the order does the budget tracking for it, and a
 * condition that is missing for one of their people is theirs to notice. Only creating and editing
 * a rate stays with managers, which is where the links of the card lead.
 *
 * <p><b>This service must stay a leaf.</b> It depends on {@code EmployeeCostService},
 * {@code OrderPricingService} and {@code SuborderService}; injecting it back into
 * {@code TimereportBudgetAssignmentService} or {@code OrderBudgetService} would close a bean cycle,
 * and the application would not start.
 */
@Slf4j
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
@Authorized
public class BudgetEmployeeService {

    private final TimereportBudgetAssignmentRepository assignmentRepository;
    private final BudgetAuthorization budgetAuthorization;
    private final SuborderService suborderService;
    private final EmployeeCostService employeeCostService;
    private final OrderPricingService orderPricingService;
    private final AuthorizedUser authorizedUser;

    /**
     * The "Mitarbeitende" card of a plan and the rates of the bookings the page renders — one pass,
     * one lookup, so the two cannot name different rates for the same work.
     *
     * <p>The card is built from <em>all</em> bookings of the period, which is why it has a query of
     * its own: {@code rendered} is capped at a couple of hundred rows, and a card derived from it
     * would report too few hours on exactly the plans that need attention. The rates of the rendered
     * rows come out of the same lookup, which answers from memory — there is no second load and no
     * query per row.
     *
     * @param rendered the bookings the page lists, of the same period; only their rates are resolved
     */
    public AppliedRates resolve(OrderBudget budget, LocalDate from, LocalDate until,
                                List<AssignedBooking> rendered) {
        budgetAuthorization.checkAuthorized(budget);
        // Costs are managers-only, as in controlling. Deliberately decided here and not by the
        // caller: EmployeeCostService requires a manager on its class, so calling lookup() for
        // anybody else throws and the whole detail page would break for the order responsible it
        // is meant for.
        var includeCosts = authorizedUser.isManager();

        var days = assignmentRepository.findAssignedEmployeeDays(budget.getId(), from, until);
        if (days.isEmpty() && rendered.isEmpty()) {
            return AppliedRates.none(includeCosts);
        }

        var lookup = AppliedRateLookup.of(budget.getCustomerorderSign(),
            subordersOf(days, rendered),
            includeCosts ? employeeCostService.lookup() : null,
            orderPricingService.lookupFor(List.of(budget.getCustomerorderSign())));

        return new AppliedRates(
            BudgetEmployees.of(rowsOf(days, lookup), includeCosts),
            rendered.stream().collect(toMap(AssignedBooking::id,
                booking -> lookup.resolve(booking.employeeSign(), booking.suborderId(), booking.day()),
                (first, second) -> first)));
    }

    /**
     * Who booked on which of these plans, most hours first — for the overview column (#964).
     *
     * <p>One statement for every row of the page, not one per row: reading the bookings of a whole
     * customer order per plan is the pattern that broke the budget dashboard
     * (→ {@code docs/performance-tips.md}).
     *
     * <p>Only plans the user may see are asked for. The caller has already filtered them, but the
     * check is repeated here rather than trusted: the query itself establishes nothing, and
     * {@code BudgetAuthorization} is request scoped, so asking it again costs nothing.
     */
    public Map<Long, List<BudgetEmployeeMinutes>> employeesOf(Collection<OrderBudget> budgets) {
        var ids = budgets.stream()
            .filter(budgetAuthorization::isAuthorized)
            .map(OrderBudget::getId)
            .toList();
        // An empty IN () is not valid SQL, and there is nothing to group anyway.
        if (ids.isEmpty()) {
            return Map.of();
        }
        return assignmentRepository.findEmployeeMinutesByBudgetIds(ids).stream()
            .collect(groupingBy(BudgetEmployeeMinutes::orderBudgetId, LinkedHashMap::new, toList()));
    }

    /**
     * The suborders the resolution needs, read once per distinct suborder.
     *
     * <p>Read one by one on purpose. The collective methods of {@code SuborderService} either drop
     * hidden suborders — a booking on one would silently lose its sign and its rate — or load every
     * suborder there is. A plan touches a handful of them, and the bookings of the rendered list
     * share them with the aggregated ones.
     */
    private List<Suborder> subordersOf(List<AssignedEmployeeDay> days, List<AssignedBooking> rendered) {
        var ids = new LinkedHashSet<Long>();
        days.forEach(day -> ids.add(day.suborderId()));
        rendered.forEach(booking -> ids.add(booking.suborderId()));

        var suborders = new ArrayList<Suborder>(ids.size());
        for (var id : ids) {
            var suborder = suborderService.getSuborderById(id);
            if (suborder == null) {
                log.warn("Booking assigned to budget references the unknown suborder {}", id);
                continue;
            }
            suborders.add(suborder);
        }
        return suborders;
    }

    /**
     * One row per person, the person with the largest share first.
     *
     * <p>Ties are broken by the sign so that two people with the same hours do not swap places
     * between two reloads. The rates are listed rather than chosen: which cost category and which
     * condition apply hangs on suborder, person and date, and picking one of several would be a
     * silent claim about the others.
     */
    private static List<BudgetEmployee> rowsOf(List<AssignedEmployeeDay> days, AppliedRateLookup lookup) {
        Comparator<BudgetEmployee> byHours = comparing(BudgetEmployee::duration, reverseOrder());
        return days.stream()
            .collect(groupingBy(AssignedEmployeeDay::employeeSign, LinkedHashMap::new, toList()))
            .values().stream()
            .map(group -> row(group, lookup))
            .sorted(byHours.thenComparing(BudgetEmployee::employeeSign))
            .toList();
    }

    /** One day of one person on one suborder, together with what applies to it. */
    private record Resolved(AssignedEmployeeDay day, AppliedRate rate) {}

    private static BudgetEmployee row(List<AssignedEmployeeDay> group, AppliedRateLookup lookup) {
        var resolved = group.stream()
            .map(day -> new Resolved(day,
                lookup.resolve(day.employeeSign(), day.suborderId(), day.day())))
            .toList();

        var costs = new TreeSet<>(comparing(CostCategoryRate::name)
            .thenComparing(CostCategoryRate::centsPerHour));
        var prices = new TreeSet<Integer>();
        for (var entry : resolved) {
            if (entry.rate().hasCost()) {
                costs.add(new CostCategoryRate(entry.rate().costName(), entry.rate().costCentsPerHour()));
            }
            // A condition resolved on a suborder that is not invoiceable never takes effect, so it
            // is not named here either — the row says "not invoiceable" instead, exactly as the
            // booking list does.
            if (entry.rate().hasPrice() && !entry.rate().notInvoiceable()) {
                prices.add(entry.rate().priceCentsPerHour());
            }
        }

        var first = group.getFirst();
        return new BudgetEmployee(
            first.employeeSign(),
            first.employeeName(),
            group.stream().mapToLong(AssignedEmployeeDay::bookings).sum(),
            sum(resolved, rate -> true),
            List.copyOf(costs),
            List.copyOf(prices),
            sum(resolved, AppliedRate::missingCost),
            sum(resolved, AppliedRate::missingPrice),
            sum(resolved, AppliedRate::notInvoiceable));
    }

    private static Duration sum(List<Resolved> resolved, Predicate<AppliedRate> of) {
        return resolved.stream()
            .filter(entry -> of.test(entry.rate()))
            .map(entry -> entry.day().duration())
            .reduce(Duration.ZERO, Duration::plus);
    }

}
