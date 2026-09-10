package org.tb.budget.domain;

import org.tb.order.domain.Customerorder;

/**
 * One row of the customer rate list (#957): the rate, the order it hangs off, and how the validity
 * of the two disagrees.
 *
 * @param customerorder   the order behind the sign, or {@code null} when it no longer exists — a
 *                        rate outlives its order and stays reachable either way (→
 *                        {@code CustomerorderFilterOption}).
 * @param employeeUnknown whether the rate names an employee that no longer carries that sign
 *                        (#966). Such a rate never matches and the work falls back to the
 *                        order-wide rate without a word, so the list says so.
 */
public record OrderPricingRow(
    OrderPricing pricing,
    Customerorder customerorder,
    OrderPricingDeviation deviation,
    boolean employeeUnknown) {

}
