package org.tb.budget.service;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.tb.auth.domain.Authorized;
import org.tb.auth.domain.AuthorizedUser;
import org.tb.budget.domain.BudgetScope;
import org.tb.budget.domain.OrderBudget;
import org.tb.budget.domain.TimereportBudgetAssignment;
import org.tb.budget.persistence.TimereportBudgetAssignmentRepository;
import org.tb.common.exception.AuthorizationException;
import org.tb.common.exception.BusinessRuleException;
import org.tb.common.exception.ErrorCode;
import org.tb.common.exception.InvalidDataException;
import org.tb.dailyreport.domain.TimereportDTO;
import org.tb.dailyreport.service.TimereportService;
import org.tb.order.service.SuborderService;

/**
 * The explicit assignment of time reports to budget plans. Only the stored assignment counts — a
 * booking without one belongs to no budget, there is no derived fallback (#908).
 */
@Service
@Transactional
@RequiredArgsConstructor
@Authorized
public class TimereportBudgetAssignmentService {

    private final TimereportBudgetAssignmentRepository assignmentRepository;
    private final OrderBudgetService orderBudgetService;
    private final TimereportService timereportService;
    private final SuborderService suborderService;
    private final AuthorizedUser authorizedUser;

    /**
     * Assigns the booking to the plan. An existing assignment is retargeted rather than replaced, so
     * that the audit trail of the assignment survives a correction.
     */
    @Authorized(requiresManager = true)
    public void assign(long timereportId, long orderBudgetId) {
        checkManager();
        // Also runs the authorization check on the plan's customer order.
        var budget = orderBudgetService.getById(orderBudgetId);
        checkAssignable(budget, getReport(timereportId));

        var assignment = assignmentRepository.findByTimereportId(timereportId)
            .orElseGet(() -> {
                var fresh = new TimereportBudgetAssignment();
                fresh.setTimereportId(timereportId);
                return fresh;
            });
        assignment.setOrderBudget(budget);
        assignmentRepository.save(assignment);
    }

    /** Removes the assignment of the booking, if it has one. Assigning nothing is not an error. */
    @Authorized(requiresManager = true)
    public void unassign(long timereportId) {
        checkManager();
        assignmentRepository.findByTimereportId(timereportId).ifPresent(assignmentRepository::delete);
    }

    /** The plan the booking is assigned to, empty when it belongs to no budget. */
    @Transactional(readOnly = true)
    public Optional<Long> getAssignedBudgetId(long timereportId) {
        return assignmentRepository.findByTimereportId(timereportId)
            .map(assignment -> assignment.getOrderBudget().getId());
    }

    /** The bookings assigned to the plan. Reading them requires access to the plan itself. */
    @Transactional(readOnly = true)
    public List<Long> getAssignedTimereportIds(long orderBudgetId) {
        orderBudgetService.getById(orderBudgetId);
        return assignmentRepository.findTimereportIdsByOrderBudgetId(orderBudgetId);
    }

    /** How many bookings the plan holds. Reading it requires access to the plan itself. */
    @Transactional(readOnly = true)
    public long countAssignedTimereports(long orderBudgetId) {
        orderBudgetService.getById(orderBudgetId);
        return assignmentRepository.countByOrderBudgetId(orderBudgetId);
    }

    /**
     * Cleanup after bookings were deleted, driven by {@code TimereportsDeletedEvent}. Deliberately
     * not manager-only: it is the consequence of an already authorized delete, and whoever may
     * delete a booking must not leave an assignment behind that points at a booking which no longer
     * exists.
     */
    @Authorized(permitAll = true)
    public void removeAssignmentsOfDeletedTimereports(Collection<Long> timereportIds) {
        // An empty IN () is not valid SQL, and there is nothing to delete anyway.
        if (timereportIds.isEmpty()) {
            return;
        }
        assignmentRepository.deleteByTimereportIdIn(timereportIds);
    }

    private void checkAssignable(OrderBudget budget, TimereportDTO report) {
        if (!Boolean.TRUE.equals(budget.getActive())) {
            throw new BusinessRuleException(ErrorCode.BU_BUDGET_INACTIVE, budget.getName());
        }
        var day = report.getReferenceday();
        if (day.isBefore(budget.getValidFrom()) || day.isAfter(budget.getValidUntil())) {
            throw new BusinessRuleException(ErrorCode.BU_TIMEREPORT_OUTSIDE_BUDGET_PERIOD, day, budget.getName());
        }
        // An order-wide plan does not look at the suborder at all, so it needs no lookup either.
        var firstLevelSign = BudgetScope.isOrderWide(budget.getSuborderSign())
            ? null
            : firstLevelSignOf(report);
        if (!BudgetScope.covers(budget, report.getCustomerorderSign(), firstLevelSign)) {
            throw new BusinessRuleException(ErrorCode.BU_TIMEREPORT_NOT_IN_BUDGET_SCOPE,
                report.getCompleteOrderSign(), budget.getName());
        }
    }

    /**
     * A plan on a first level suborder also covers everything below it — plans only live on that
     * level, but bookings happen further down.
     */
    private String firstLevelSignOf(TimereportDTO report) {
        var suborder = suborderService.getSuborderById(report.getSuborderId());
        return suborder == null ? null : BudgetScope.firstLevelSignOf(suborder);
    }

    private TimereportDTO getReport(long timereportId) {
        var report = timereportService.getTimereportById(timereportId);
        if (report == null) {
            throw new InvalidDataException(ErrorCode.TR_TIME_REPORT_NOT_FOUND, timereportId);
        }
        return report;
    }

    /** ADR-0006: the annotation is enforced by the aspect, the guard also holds for internal callers. */
    private void checkManager() {
        if (!authorizedUser.isManager()) {
            throw new AuthorizationException(ErrorCode.AA_NEEDS_MANAGER);
        }
    }

}
