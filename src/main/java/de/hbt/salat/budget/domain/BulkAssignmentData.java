package de.hbt.salat.budget.domain;

import java.time.LocalDate;
import java.util.List;

/**
 * What a bulk assignment run should act on (#911): the bookings of one customer order in a period,
 * optionally narrowed to one suborder and to single people, and the plan they should end up on.
 *
 * <p>Order and suborder are chosen by id (#1339). Signs stay changeable (ADR-0034): an order renamed
 * between preview and run would no longer be found by its sign, and a suborder moved meanwhile would
 * have the run act on a different subtree than the preview showed.
 *
 * @param customerorderId the customer order, {@code null} while none is chosen
 * @param suborderId     the suborder, or {@code null} for the whole customer order. A suborder
 *                       includes everything below it — plans live on the first level while bookings
 *                       happen further down, so narrowing to {@code CO/01} that excluded
 *                       {@code CO/01/02} would be a trap.
 * @param targetBudgetId the plan the bookings should end up on. {@code null} while the selection is
 *                       still being built — the option list of the people is derived from the same
 *                       selection and must not wait for the plan (#953).
 * @param employeeIds    the people the selection is narrowed to, empty for all of them (#953). The
 *                       choice only shrinks the selection; it never makes a booking assignable that
 *                       would not be assignable without it.
 * @param includeAssigned whether bookings that already belong to another plan are retargeted.
 *                        Off by default: moving a booking off a plan someone chose deliberately has
 *                        to be asked for.
 */
public record BulkAssignmentData(
    Long customerorderId,
    Long suborderId,
    LocalDate from,
    LocalDate until,
    Long targetBudgetId,
    List<Long> employeeIds,
    boolean includeAssigned) {

    /** Whether a suborder narrows the selection; without one the whole customer order is meant. */
    public boolean isNarrowedToSuborder() {
        return suborderId != null;
    }

    /**
     * Whether a booking whose suborder sits at this position lies within the selected scope: on the
     * selected suborder or anywhere below it. Decided by the path of suborder ids read from the
     * current tree, not by a sign prefix (#1339), so a moved suborder takes its bookings along.
     *
     * @param position where the booking's suborder sits, {@code null} where it cannot be read — such
     *                 a booking lies outside every selected suborder
     */
    public boolean coversPosition(OrderPosition position) {
        if (!isNarrowedToSuborder()) {
            return true;
        }
        return position != null && position.liesWithin(suborderId);
    }

    /** Whether the booking belongs to one of the selected people; no choice means all of them. */
    public boolean coversEmployee(long employeeId) {
        return employeeIds == null || employeeIds.isEmpty() || employeeIds.contains(employeeId);
    }

    /**
     * Whether order and period are chosen — enough to name the bookings of the selection, which is
     * what the option list of the people is derived from. The target plan is picked after them, so
     * the list must not depend on it.
     */
    public boolean hasSelectableReports() {
        return customerorderId != null
            && from != null && until != null && !from.isAfter(until);
    }

}
