package org.tb.budget.domain;

import java.time.Duration;
import java.util.List;

/**
 * One person on a budget plan, as the "Mitarbeitende" card lists them (#964): what they booked in
 * the period, and which rates apply to that work.
 *
 * <p>The rates are lists rather than one entry each, and deliberately so. Which cost category and
 * which condition apply hangs on suborder, person and date, so several of them applying over the
 * bookings of one person is the rule as soon as somebody works through a rate change or on a
 * suborder with a rate of its own. Picking one of them would be a silent claim about the others.
 *
 * <p>The three durations are what the card adds up at the bottom. They are kept per person rather
 * than only as a total because a person whose work is half covered is neither "with a rate" nor
 * "without one" — {@link #missingCost()} follows from the duration, not the other way round.
 *
 * <p>Hours on a suborder that is not invoiceable are counted apart and are <em>not</em> hours
 * without a condition: 0 EUR revenue is right there, whatever rate would match. They are reported
 * for the plan as a whole rather than per person — the suborder is what is not invoiceable, not
 * the person's work on it.
 */
public record BudgetEmployee(
    String employeeSign,
    String employeeName,
    long bookings,
    Duration duration,
    List<CostCategoryRate> costs,
    List<Integer> priceCentsPerHour,
    Duration durationWithoutCost,
    Duration durationWithoutPrice,
    Duration durationNotInvoiceable) {

    public boolean missingCost() {
        return !durationWithoutCost.isZero();
    }

    public boolean missingPrice() {
        return !durationWithoutPrice.isZero();
    }

}
