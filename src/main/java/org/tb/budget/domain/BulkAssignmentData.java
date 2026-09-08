package org.tb.budget.domain;

import java.time.LocalDate;

/**
 * What a bulk assignment run should act on (#911): the bookings of one customer order in a period,
 * optionally narrowed to one suborder, and the plan they should end up on.
 *
 * @param suborderSign   the complete order sign of the suborder ({@code CO/01}), or {@code null} for
 *                       the whole customer order. A suborder includes everything below it — plans
 *                       live on the first level while bookings happen further down, so narrowing to
 *                       {@code CO/01} that excluded {@code CO/01/02} would be a trap.
 * @param includeAssigned whether bookings that already belong to another plan are retargeted.
 *                        Off by default: moving a booking off a plan someone chose deliberately has
 *                        to be asked for.
 */
public record BulkAssignmentData(
    String customerorderSign,
    String suborderSign,
    LocalDate from,
    LocalDate until,
    long targetBudgetId,
    boolean includeAssigned) {

    /** Whether the booking's suborder lies within the selected scope. */
    public boolean coversSuborder(String completeOrderSign) {
        if (suborderSign == null || suborderSign.isBlank()) {
            return true;
        }
        return completeOrderSign != null
            && (completeOrderSign.equals(suborderSign) || completeOrderSign.startsWith(suborderSign + "/"));
    }

}
