package org.tb.budget.domain;

import java.math.BigDecimal;
import java.util.List;
import org.tb.order.domain.Customerorder;

/**
 * One row of the flat rate list (#972): the definition, the order it hangs off, and the schedule it
 * amounts to.
 *
 * @param customerorder the order behind the sign, or {@code null} when it no longer exists — a flat
 *                      rate references its order by sign and outlives it, as a rate does.
 * @param dueAmounts    the amounts this definition puts on the calendar, so the list can say what a
 *                      monthly rate or a set of instalments actually adds up to.
 */
public record OrderFlatRateRow(
    OrderFlatRate flatRate,
    Customerorder customerorder,
    List<FlatRateDueAmount> dueAmounts) {

    public BigDecimal totalAmount() {
        return dueAmounts.stream().map(FlatRateDueAmount::amount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public int dueCount() {
        return dueAmounts.size();
    }

    /**
     * A definition that puts nothing on the calendar: instalments not entered yet, or a monthly rate
     * whose validity is shorter than the step. The list marks it, because it earns nothing and looks
     * like a complete record otherwise.
     */
    public boolean isEmptySchedule() {
        return dueAmounts.isEmpty();
    }

}
