package de.hbt.salat.budget.domain;

import de.hbt.salat.order.domain.Customerorder;

/**
 * One row of the customer rate list (#957): the rate, the order it hangs off, and how the validity
 * of the two disagrees.
 *
 * @param customerorder   the order behind the sign, or {@code null} when it no longer exists — a
 *                        rate outlives its order and stays reachable either way (→
 *                        {@code CustomerorderFilterOption}).
 * @param employeeSign    the current sign of the person the rate is for, or {@code null} for a rate
 *                        for everyone. Read off the person rather than the rate (#968), so it follows
 *                        a rename.
 * @param orderBudgetName the name of the budget plan the rate is bound to, or {@code null} for a
 *                        plan-less rate (#1065). Resolved here rather than read off the entity in
 *                        the template, which would load one plan per row.
 */
public record OrderPricingRow(
    OrderPricing pricing,
    Customerorder customerorder,
    OrderPricingDeviation deviation,
    String employeeSign,
    String orderBudgetName) {
}
