package org.tb.budget.domain;

import java.time.LocalDate;
import java.util.List;

/**
 * What a bulk assignment run should act on (#911): the bookings of one customer order in a period,
 * optionally narrowed to one suborder and to single people, and the plan they should end up on.
 *
 * @param suborderSign   the complete order sign of the suborder ({@code CO/01}), or {@code null} for
 *                       the whole customer order. A suborder includes everything below it — plans
 *                       live on the first level while bookings happen further down, so narrowing to
 *                       {@code CO/01} that excluded {@code CO/01/02} would be a trap.
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
    String customerorderSign,
    String suborderSign,
    LocalDate from,
    LocalDate until,
    Long targetBudgetId,
    List<Long> employeeIds,
    boolean includeAssigned) {

    /** Whether the booking's suborder lies within the selected scope. */
    public boolean coversSuborder(String completeOrderSign) {
        if (suborderSign == null || suborderSign.isBlank()) {
            return true;
        }
        return completeOrderSign != null
            && (completeOrderSign.equals(suborderSign) || completeOrderSign.startsWith(suborderSign + "/"));
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
        return customerorderSign != null && !customerorderSign.isBlank()
            && from != null && until != null && !from.isAfter(until);
    }

}
