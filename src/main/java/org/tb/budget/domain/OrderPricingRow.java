package org.tb.budget.domain;

import org.tb.order.domain.Customerorder;

/**
 * One row of the customer rate list (#957): the rate, the order it hangs off, and how the validity
 * of the two disagrees.
 *
 * @param customerorder the order behind the sign, or {@code null} when it no longer exists — a rate
 *                      outlives its order and stays reachable either way (→
 *                      {@code OrderPricingFilterOption}).
 */
public record OrderPricingRow(
    OrderPricing pricing,
    Customerorder customerorder,
    OrderPricingDeviation deviation) {

}
