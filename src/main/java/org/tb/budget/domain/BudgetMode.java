package org.tb.budget.domain;

/**
 * How a customer order is budgeted at a given point in time (#914). Exactly one of these applies:
 * the two modes answer different questions, so a period in which both were in force would have no
 * defined answer. Overlapping plans <em>within</em> a mode are allowed.
 */
public enum BudgetMode {
    /** One or more plans covering the whole customer order. */
    ORDER_WIDE,
    /** Plans on first level suborders. */
    PER_SUBORDER,
    /** No active plan is in force — the bookings of this time belong to no budget. */
    NONE
}
