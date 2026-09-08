package org.tb.budget.service;

import static java.lang.Boolean.TRUE;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.tb.auth.domain.Authorized;
import org.tb.auth.domain.AuthorizedUser;
import org.tb.budget.domain.OrderBudget;
import org.tb.budget.domain.TimereportBudgetAssignment;
import org.tb.budget.persistence.TimereportBudgetAssignmentRepository;
import org.tb.common.exception.AuthorizationException;
import org.tb.common.exception.BusinessRuleException;
import org.tb.common.exception.ErrorCode;
import org.tb.common.exception.InvalidDataException;
import org.tb.dailyreport.domain.TimereportDTO;
import org.tb.dailyreport.service.TimereportService;
import org.tb.order.service.CustomerorderService;

/**
 * The explicit assignment of time reports to budget plans. Only the stored assignment counts — a
 * booking without one belongs to no budget, there is no derived fallback (#908).
 */
@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
@Authorized
public class TimereportBudgetAssignmentService {

    private final TimereportBudgetAssignmentRepository assignmentRepository;
    private final OrderBudgetService orderBudgetService;
    private final TimereportService timereportService;
    private final BudgetResolver budgetResolver;
    private final CustomerorderService customerorderService;
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
            .orElseGet(() -> newAssignment(timereportId));
        assignment.setOrderBudget(budget);
        assignmentRepository.save(assignment);
    }

    /**
     * Assigns the bookings automatically, as far as the plan is unambiguous: exactly one active plan
     * of the order covers {@code (suborder, date)}. With several or with no matching plan the booking
     * stays unassigned — the automatic assignment never guesses (#909). Unassigned bookings are not
     * an error; they are reported in controlling and can be caught up on by bulk assignment (#911).
     *
     * <p>An assignment that already exists is left alone as long as it is still valid, so a
     * deliberate manual assignment is never overwritten. Only one that the change invalidated — the
     * booking moved to another suborder, or its date left the plan's validity — is resolved anew.
     *
     * <p>Deliberately not manager-only: this runs for whoever booked, as the direct consequence of
     * their own already authorized booking.
     */
    @Authorized(permitAll = true)
    public void resolveAssignments(Collection<Long> timereportIds) {
        for (var timereportId : timereportIds) {
            try {
                resolveAssignment(timereportId);
            } catch (RuntimeException e) {
                // The booking itself is already written and must not fail over its budget
                // assignment — an unassigned booking is a reported state, not a defect. Caught per
                // booking and inside this transactional method, so the exception never passes a
                // transaction boundary that would mark the booking's transaction rollback-only.
                log.warn("Could not resolve the budget assignment of time report {}", timereportId, e);
            }
        }
    }

    private void resolveAssignment(long timereportId) {
        var report = timereportService.getTimereportById(timereportId);
        if (report == null) {
            // Written and deleted again before this ran — there is nothing left to assign.
            return;
        }
        var existing = assignmentRepository.findByTimereportId(timereportId).orElse(null);
        if (existing != null && budgetResolver.isAssignable(existing.getOrderBudget(), report)) {
            return;
        }
        var resolved = budgetResolver.resolve(report).unique().orElse(null);
        if (resolved == null) {
            if (existing != null) {
                assignmentRepository.delete(existing);
            }
            return;
        }
        // Retargeted rather than replaced, as in assign(): the row keeps its audit trail, and no
        // insert races the delete of a row the unique index still holds.
        var assignment = existing != null ? existing : newAssignment(timereportId);
        assignment.setOrderBudget(resolved);
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

    /**
     * The bookings assigned to the plan within the period (#912). Reading them requires access to
     * the plan itself.
     *
     * <p>Read as "the bookings of the plan's order in the period, minus those not assigned here"
     * rather than one lookup per assigned id — a plan holds hundreds of bookings, and this is two
     * statements instead of hundreds.
     */
    @Transactional(readOnly = true)
    public List<TimereportDTO> getAssignedTimereports(long orderBudgetId, LocalDate from, LocalDate until) {
        var plan = orderBudgetService.getById(orderBudgetId);
        var assignedIds = new HashSet<>(assignmentRepository.findTimereportIdsByOrderBudgetId(orderBudgetId));
        if (assignedIds.isEmpty()) {
            return List.of();
        }
        var customerorder = customerorderService.getCustomerorderBySign(plan.getCustomerorderSign());
        if (customerorder == null) {
            log.warn("Budget plan {} references the unknown customer order {}",
                orderBudgetId, plan.getCustomerorderSign());
            return List.of();
        }
        return timereportService.getTimereportsByDatesAndCustomerOrderId(from, until, customerorder.getId())
            .stream()
            .filter(report -> assignedIds.contains(report.getId()))
            .toList();
    }

    /**
     * Moves the bookings to another plan, or dissolves their assignment when {@code targetBudgetId}
     * is {@code null} — a booking that belongs to no budget is a legitimate state (#908), so
     * dissolving needs no target and no scope check.
     *
     * <p>All or nothing: every booking is checked against the target <em>before</em> anything is
     * written. A selection that contains one booking outside the target's scope or validity is
     * rejected whole, with the error naming that booking — half a moved selection would leave the
     * person who triggered it with no idea what actually happened.
     */
    @Authorized(requiresManager = true)
    public void move(Collection<Long> timereportIds, Long targetBudgetId) {
        checkManager();
        if (timereportIds.isEmpty()) {
            return;
        }
        if (targetBudgetId == null) {
            assignmentRepository.deleteByTimereportIdIn(timereportIds);
            return;
        }
        // Also runs the authorization check on the plan's customer order.
        var target = orderBudgetService.getById(targetBudgetId);
        var reports = timereportIds.stream().map(this::getReport).toList();
        reports.forEach(report -> checkAssignable(target, report));

        var assignments = new ArrayList<TimereportBudgetAssignment>(reports.size());
        for (var report : reports) {
            // Retargeted rather than replaced, so the audit fields keep saying who moved it.
            var assignment = assignmentRepository.findByTimereportId(report.getId())
                .orElseGet(() -> newAssignment(report.getId()));
            assignment.setOrderBudget(target);
            assignments.add(assignment);
        }
        assignmentRepository.saveAll(assignments);
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

    /**
     * The same rule as {@link BudgetResolver#isAssignable}, checked condition by condition so that
     * the manual assignment can say which one was violated. It delegates to the resolver instead of
     * repeating the conditions — the automatic and the manual path must not be able to disagree
     * about what a plan covers.
     */
    private void checkAssignable(OrderBudget budget, TimereportDTO report) {
        if (!TRUE.equals(budget.getActive())) {
            throw new BusinessRuleException(ErrorCode.BU_BUDGET_INACTIVE, budget.getName());
        }
        var day = report.getReferenceday();
        if (!budgetResolver.coversPeriod(budget, day)) {
            throw new BusinessRuleException(ErrorCode.BU_TIMEREPORT_OUTSIDE_BUDGET_PERIOD, day, budget.getName());
        }
        if (!budgetResolver.coversScope(budget, report)) {
            throw new BusinessRuleException(ErrorCode.BU_TIMEREPORT_NOT_IN_BUDGET_SCOPE,
                report.getCompleteOrderSign(), budget.getName());
        }
    }

    private static TimereportBudgetAssignment newAssignment(long timereportId) {
        var assignment = new TimereportBudgetAssignment();
        assignment.setTimereportId(timereportId);
        return assignment;
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
