package de.hbt.salat.budget.service;

import static java.lang.Boolean.TRUE;

import java.time.LocalDate;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import de.hbt.salat.auth.domain.Authorized;
import de.hbt.salat.budget.domain.BudgetResolution;
import de.hbt.salat.budget.domain.BudgetScope;
import de.hbt.salat.budget.domain.OrderBudget;
import de.hbt.salat.budget.domain.OrderPosition;
import de.hbt.salat.budget.persistence.OrderBudgetRepository;
import de.hbt.salat.dailyreport.domain.TimereportDTO;
import de.hbt.salat.order.service.SuborderService;

/**
 * Which budget plan a booking belongs to: the active plans of its customer order whose validity
 * contains the booking date and whose scope covers the booking.
 *
 * <p>The rule lives here exactly once. The automatic assignment while booking (#909), the initial
 * assignment of the existing stock (#910) and the bulk assignment (#911) all ask this class, so
 * they cannot arrive at different answers — a resolution that differs between the three would write
 * a contradiction into the data.
 *
 * <p>Resolving is a read, not an authorization decision: it answers which plan covers a booking,
 * never whether the caller may see that plan. That is why it is {@code permitAll} — it runs as a
 * consequence of a booking that was already authorized. Every path that shows a plan to a user goes
 * through {@link OrderBudgetService#getById(long)}, which runs the check.
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
@Authorized(permitAll = true)
public class BudgetResolver {

    private final OrderBudgetRepository orderBudgetRepository;
    private final SuborderService suborderService;

    /** The plans covering the booking. */
    public BudgetResolution resolve(TimereportDTO report) {
        return resolveAll(List.of(report)).get(report.getId());
    }

    /**
     * Resolves several bookings at once, keyed by time report id. The plans are read once per
     * distinct customer order rather than once per booking — a batch typically books the same order
     * over several days, and the derived assignment this replaces was the reason controlling read
     * every plan on every evaluation.
     */
    public Map<Long, BudgetResolution> resolveAll(Collection<TimereportDTO> reports) {
        Map<Long, List<OrderBudget>> activePlansByOrder = new HashMap<>();
        Map<Long, BudgetResolution> resolutions = new LinkedHashMap<>();
        for (var report : reports) {
            var plans = activePlansByOrder.computeIfAbsent(
                report.getCustomerorderId(),
                id -> orderBudgetRepository.findByCustomerorderIdAndActive(id, TRUE));
            resolutions.put(report.getId(), new BudgetResolution(
                plans.stream().filter(plan -> isAssignable(plan, report)).toList()));
        }
        return resolutions;
    }

    /**
     * Whether the plan may hold the booking. The same three conditions decide the automatic
     * assignment and whether an assignment that already exists is still valid, so they must not be
     * spelled out anywhere else.
     */
    public boolean isAssignable(OrderBudget plan, TimereportDTO report) {
        return TRUE.equals(plan.getActive())
            && coversPeriod(plan, report.getReferenceday())
            && coversScope(plan, report);
    }

    /** The booking date lies within the validity of the plan; both boundaries belong to it. */
    public boolean coversPeriod(OrderBudget plan, LocalDate day) {
        return !day.isBefore(plan.getValidFrom()) && !day.isAfter(plan.getValidUntil());
    }

    /**
     * The booking lies within the scope of the plan: on the plan's suborder or anywhere below it
     * (→ {@link BudgetScope}), compared by id (#1205). An order-wide plan needs no suborder at all and
     * therefore does not look one up; for every other plan it is exactly one lookup per booking.
     */
    public boolean coversScope(OrderBudget plan, TimereportDTO report) {
        var position = plan.isOrderWide()
            ? OrderPosition.orderWide(report.getCustomerorderId())
            : positionOf(report);
        return BudgetScope.covers(plan, position);
    }

    /**
     * Where the booking's own suborder sits in the tree, read from the suborder: its parents decide
     * the subtree, and a moved suborder takes its bookings along. {@code null} where the suborder
     * cannot be read — such a booking lies outside every plan that names a suborder.
     */
    private OrderPosition positionOf(TimereportDTO report) {
        var suborder = suborderService.getSuborderById(report.getSuborderId());
        return suborder == null ? null : OrderPosition.of(suborder);
    }

}
