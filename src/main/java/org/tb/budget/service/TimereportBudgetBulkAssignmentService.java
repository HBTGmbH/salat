package org.tb.budget.service;

import static java.lang.Boolean.TRUE;
import static java.util.Comparator.comparing;
import static java.util.Comparator.naturalOrder;
import static java.util.Comparator.nullsLast;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.tb.auth.domain.Authorized;
import org.tb.auth.domain.AuthorizedUser;
import org.tb.budget.domain.BudgetBookingCounts;
import org.tb.budget.domain.BulkAssignmentData;
import org.tb.budget.domain.BulkAssignmentEmployee;
import org.tb.budget.domain.BulkAssignmentPreview;
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
 * Assigns the bookings of a period to one budget plan in one go (#911).
 *
 * <p>Where the automatic assignment cannot decide — overlapping plans (#914), or a booking nobody
 * planned for — a person has to. Doing that booking by booking is not workable for a month of a busy
 * order, so the decision is made once for a selection.
 *
 * <p>The rules are the same as for the single assignment (#908): a booking outside the scope or the
 * validity of the target plan is never assigned, however the selection was made. The bulk path is a
 * faster way to make a decision, not a way around the rules.
 */
@Service
@Transactional
@RequiredArgsConstructor
@Authorized
public class TimereportBudgetBulkAssignmentService {

    private final TimereportBudgetAssignmentRepository assignmentRepository;
    private final OrderBudgetService orderBudgetService;
    private final BudgetResolver budgetResolver;
    private final TimereportService timereportService;
    private final CustomerorderService customerorderService;
    private final AuthorizedUser authorizedUser;

    /**
     * The people who booked within the current selection (#953), as Kürzel plus name. Derived from
     * the same bookings the run itself acts on, so the list can never offer a person whose selection
     * would come up empty. It deliberately does not require the target plan: the selection is built
     * from the top down, and the plan is picked after the people.
     */
    @Authorized(requiresManager = true)
    @Transactional(readOnly = true)
    public List<BulkAssignmentEmployee> selectableEmployees(BulkAssignmentData data) {
        checkManager();
        if (!data.hasSelectableReports()) {
            return List.of();
        }
        var customerorder = customerorderService.getCustomerorderBySign(data.customerorderSign());
        if (customerorder == null) {
            return List.of();
        }
        return reportsInScope(customerorder.getId(), data).stream()
            .map(report -> new BulkAssignmentEmployee(
                report.getEmployeeId(), report.getEmployeeSign(), report.getEmployeeName()))
            .distinct()
            .sorted(comparing(BulkAssignmentEmployee::sign, nullsLast(naturalOrder())))
            .toList();
    }

    /** What the run would do, without doing it. */
    @Authorized(requiresManager = true)
    @Transactional(readOnly = true)
    public BulkAssignmentPreview preview(BulkAssignmentData data) {
        checkManager();
        return classify(data).preview();
    }

    /**
     * Applies the assignment and reports what was written. Bookings outside the scope or validity of
     * the target plan are left alone rather than making the call fail — the preview named them, and a
     * single unassignable booking must not block the decision about the other hundred.
     */
    @Authorized(requiresManager = true)
    public BudgetBookingCounts assign(BulkAssignmentData data) {
        checkManager();
        var classified = classify(data);

        var toAssign = new ArrayList<>(classified.unassigned());
        if (data.includeAssigned()) {
            toAssign.addAll(classified.assignedElsewhere());
        }
        var written = new ArrayList<TimereportBudgetAssignment>(toAssign.size());
        for (var selected : toAssign) {
            // Retargeted through the entity rather than by a bulk JPQL UPDATE: an UPDATE would leave
            // lastupdate/lastupdatedby and the @Version stale, so the row would stop saying who moved
            // the booking — which is the whole point of a manual override.
            var assignment = selected.assignment() != null
                ? selected.assignment()
                : newAssignment(selected.report().getId());
            assignment.setOrderBudget(classified.target());
            written.add(assignment);
        }
        assignmentRepository.saveAll(written);

        return Classified.count(toAssign);
    }

    /** One booking of the selection together with the assignment it currently has, if any. */
    private record Selected(TimereportDTO report, TimereportBudgetAssignment assignment) {}

    /**
     * The selection sorted into the four outcomes. The assignment entities are kept so the write can
     * retarget them instead of loading them a second time.
     */
    private record Classified(
        OrderBudget target,
        List<Selected> unassigned,
        List<Selected> assignedElsewhere,
        List<Selected> alreadyOnTarget,
        List<Selected> notAssignable) {

        BulkAssignmentPreview preview() {
            return new BulkAssignmentPreview(count(unassigned), count(assignedElsewhere),
                count(alreadyOnTarget), count(notAssignable));
        }

        static BudgetBookingCounts count(List<Selected> bucket) {
            return bucket.stream()
                .map(selected -> selected.report().getDuration())
                .reduce(BudgetBookingCounts.NONE, BudgetBookingCounts::plus, BudgetBookingCounts::plus);
        }
    }

    private Classified classify(BulkAssignmentData data) {
        if (data.targetBudgetId() == null) {
            // The plan is optional only while the selection is being built (#953); a run needs one.
            throw new InvalidDataException(ErrorCode.BU_BUDGET_NOT_FOUND, "");
        }
        // Also runs the authorization check on the plan's customer order.
        var target = orderBudgetService.getById(data.targetBudgetId());
        if (!TRUE.equals(target.getActive())) {
            throw new BusinessRuleException(ErrorCode.BU_BUDGET_INACTIVE, target.getName());
        }
        var reports = selectedReports(data);
        var assignments = assignmentsOf(reports);

        List<Selected> unassigned = new ArrayList<>();
        List<Selected> assignedElsewhere = new ArrayList<>();
        List<Selected> alreadyOnTarget = new ArrayList<>();
        List<Selected> notAssignable = new ArrayList<>();

        for (var report : reports) {
            var selected = new Selected(report, assignments.get(report.getId()));
            if (!budgetResolver.isAssignable(target, report)) {
                notAssignable.add(selected);
            } else if (selected.assignment() == null) {
                unassigned.add(selected);
            } else if (selected.assignment().getOrderBudget().getId().equals(target.getId())) {
                alreadyOnTarget.add(selected);
            } else {
                assignedElsewhere.add(selected);
            }
        }
        return new Classified(target, unassigned, assignedElsewhere, alreadyOnTarget, notAssignable);
    }

    private List<TimereportDTO> selectedReports(BulkAssignmentData data) {
        var customerorder = customerorderService.getCustomerorderBySign(data.customerorderSign());
        if (customerorder == null) {
            throw new InvalidDataException(ErrorCode.CO_NOT_FOUND, data.customerorderSign());
        }
        return reportsInScope(customerorder.getId(), data).stream()
            // Applied after the scope, so the option list of the people (#953) sees everyone who
            // booked in the scope — narrowing to a person must not remove them from their own list.
            .filter(report -> data.coversEmployee(report.getEmployeeId()))
            .toList();
    }

    /** The bookings of order, suborder and period — everything but the choice of people. */
    private List<TimereportDTO> reportsInScope(long customerorderId, BulkAssignmentData data) {
        return timereportService
            .getTimereportsByDatesAndCustomerOrderId(data.from(), data.until(), customerorderId)
            .stream()
            // The booking already carries its suborder's complete sign, so narrowing to a suborder
            // needs no lookup — and the prefix match includes the levels below it.
            .filter(report -> data.coversSuborder(report.getCompleteOrderSign()))
            .toList();
    }

    private Map<Long, TimereportBudgetAssignment> assignmentsOf(List<TimereportDTO> reports) {
        if (reports.isEmpty()) {
            // An empty IN () is not valid SQL, and there is nothing to look up anyway.
            return Map.of();
        }
        var ids = reports.stream().map(TimereportDTO::getId).toList();
        Map<Long, TimereportBudgetAssignment> byTimereportId = new HashMap<>();
        assignmentRepository.findByTimereportIdIn(ids)
            .forEach(assignment -> byTimereportId.put(assignment.getTimereportId(), assignment));
        return byTimereportId;
    }

    private static TimereportBudgetAssignment newAssignment(long timereportId) {
        var assignment = new TimereportBudgetAssignment();
        assignment.setTimereportId(timereportId);
        return assignment;
    }

    /** ADR-0006: the annotation is enforced by the aspect, the guard also holds for internal callers. */
    private void checkManager() {
        if (!authorizedUser.isManager()) {
            throw new AuthorizationException(ErrorCode.AA_NEEDS_MANAGER);
        }
    }

}
