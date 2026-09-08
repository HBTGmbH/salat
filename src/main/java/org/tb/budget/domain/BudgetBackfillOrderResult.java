package org.tb.budget.domain;

import java.time.LocalDate;

/**
 * What one backfill run did to one customer order (#910).
 *
 * <p>{@code examinedFrom}/{@code examinedUntil} is the period the run looked at: the span of the
 * order's active plans. Outside it no plan can match, so a booking there would produce an
 * unavoidable "no plan" line and bury the orders that actually need attention. Reporting the period
 * makes that limit visible rather than silent.
 *
 * <p>{@code alreadyAssigned} is what the run deliberately left alone. It is the evidence that a
 * second run changes nothing: everything the first run assigned shows up here the next time.
 */
public record BudgetBackfillOrderResult(
    String customerorderSign,
    String customerorderDescription,
    LocalDate examinedFrom,
    LocalDate examinedUntil,
    BudgetBookingCounts assigned,
    BudgetBookingCounts ambiguous,
    BudgetBookingCounts withoutPlan,
    BudgetBookingCounts alreadyAssigned) {

    /** Whether the run has anything to say about this order at all. */
    public boolean hasContent() {
        return !assigned.isEmpty() || !ambiguous.isEmpty()
            || !withoutPlan.isEmpty() || !alreadyAssigned.isEmpty();
    }

}
