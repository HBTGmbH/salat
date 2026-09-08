package org.tb.budget.service;

import static java.lang.Boolean.TRUE;
import static java.util.Comparator.naturalOrder;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.tb.auth.domain.Authorized;
import org.tb.auth.domain.AuthorizedUser;
import org.tb.budget.domain.BudgetBookingCounts;
import org.tb.budget.domain.BudgetBackfillOrderResult;
import org.tb.budget.domain.BudgetBackfillResult;
import org.tb.budget.domain.OrderBudget;
import org.tb.budget.domain.TimereportBudgetAssignment;
import org.tb.budget.persistence.OrderBudgetRepository;
import org.tb.budget.persistence.TimereportBudgetAssignmentRepository;
import org.tb.common.exception.AuthorizationException;
import org.tb.common.exception.ErrorCode;
import org.tb.dailyreport.domain.TimereportDTO;
import org.tb.dailyreport.service.TimereportService;
import org.tb.order.service.CustomerorderService;

/**
 * Catches the existing bookings up on their budget assignment (#910).
 *
 * <p>Since #908 only the stored assignment counts and #909 only assigns while booking, so without
 * this run every booking made before the switch would be missing from the evaluations — the
 * controlling of #913 would report empty numbers on a full database.
 *
 * <p>The run is repeatable: a booking that already has an assignment is skipped, not re-resolved,
 * so a second run over the same data writes nothing. It resolves through {@link BudgetResolver} —
 * the same rule as the automatic assignment, because this run writes its answer into the database
 * once and a wrong scope resolution would stop being a miscalculation and become the data.
 */
@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
@Authorized
public class TimereportBudgetBackfillService {

    private final OrderBudgetRepository orderBudgetRepository;
    private final TimereportBudgetAssignmentRepository assignmentRepository;
    private final BudgetResolver budgetResolver;
    private final TimereportService timereportService;
    private final CustomerorderService customerorderService;
    private final AuthorizedUser authorizedUser;

    /**
     * Assigns every so far unassigned booking for which exactly one active plan fits. Pass a
     * customer order sign to restrict the run to that order, or {@code null} to run over every order
     * that has an active plan — the restriction is what makes it possible to work through a large
     * installation order by order instead of in one go.
     */
    @Authorized(requiresManager = true)
    public BudgetBackfillResult backfill(String customerorderSign) {
        checkManager();
        var signs = customerorderSign == null || customerorderSign.isBlank()
            ? orderBudgetRepository.findActiveCustomerorderSigns()
            : List.of(customerorderSign);
        return new BudgetBackfillResult(signs.stream()
            .map(this::backfillOrder)
            .filter(Objects::nonNull)
            .filter(BudgetBackfillOrderResult::hasContent)
            .toList());
    }

    private BudgetBackfillOrderResult backfillOrder(String customerorderSign) {
        var plans = orderBudgetRepository.findByCustomerorderSignAndActive(customerorderSign, TRUE);
        if (plans.isEmpty()) {
            // Without an active plan nothing can be assigned. Listing the order's whole history as
            // "no plan" would only bury the orders that do need attention.
            return null;
        }
        var customerorder = customerorderService.getCustomerorderBySign(customerorderSign);
        if (customerorder == null) {
            log.warn("Budget plans reference the unknown customer order {}", customerorderSign);
            return null;
        }
        // Only the span of the active plans is examined — see BudgetBackfillOrderResult.
        var from = plans.stream().map(OrderBudget::getValidFrom).min(naturalOrder()).orElseThrow();
        var until = plans.stream().map(OrderBudget::getValidUntil).max(naturalOrder()).orElseThrow();

        var reports = timereportService.getTimereportsByDatesAndCustomerOrderId(from, until, customerorder.getId());
        var assignedIds = new HashSet<>(assignmentRepository.findTimereportIdsByCustomerorderSign(customerorderSign));
        var pending = split(reports, assignedIds);

        var assigned = BudgetBookingCounts.NONE;
        var ambiguous = BudgetBookingCounts.NONE;
        var withoutPlan = BudgetBookingCounts.NONE;
        var newAssignments = new ArrayList<TimereportBudgetAssignment>();

        var resolutions = budgetResolver.resolveAll(pending.reports());
        for (var report : pending.reports()) {
            var resolution = resolutions.get(report.getId());
            var plan = resolution.unique().orElse(null);
            if (plan != null) {
                assigned = assigned.plus(report.getDuration());
                newAssignments.add(assignmentOf(report.getId(), plan));
            } else if (resolution.isAmbiguous()) {
                // Never guessed here either: which plan is meant is a decision, and #911 is where it
                // is made.
                ambiguous = ambiguous.plus(report.getDuration());
            } else {
                withoutPlan = withoutPlan.plus(report.getDuration());
            }
        }
        // One plain bulk save per order; no batching machinery until it is measurably needed.
        assignmentRepository.saveAll(newAssignments);

        return new BudgetBackfillOrderResult(customerorderSign, customerorder.getShortdescription(),
            from, until, assigned, ambiguous, withoutPlan, pending.alreadyAssigned());
    }

    /** The bookings still to resolve, and what was left alone because it is already assigned. */
    private record Pending(List<TimereportDTO> reports, BudgetBookingCounts alreadyAssigned) {}

    private static Pending split(List<TimereportDTO> reports, Set<Long> assignedIds) {
        var pending = new ArrayList<TimereportDTO>();
        var alreadyAssigned = BudgetBookingCounts.NONE;
        for (var report : reports) {
            if (assignedIds.contains(report.getId())) {
                alreadyAssigned = alreadyAssigned.plus(report.getDuration());
            } else {
                pending.add(report);
            }
        }
        return new Pending(pending, alreadyAssigned);
    }

    private static TimereportBudgetAssignment assignmentOf(long timereportId, OrderBudget plan) {
        var assignment = new TimereportBudgetAssignment();
        assignment.setTimereportId(timereportId);
        assignment.setOrderBudget(plan);
        return assignment;
    }

    /** ADR-0006: the annotation is enforced by the aspect, the guard also holds for internal callers. */
    private void checkManager() {
        if (!authorizedUser.isManager()) {
            throw new AuthorizationException(ErrorCode.AA_NEEDS_MANAGER);
        }
    }

}
