package de.hbt.salat.budget.domain;

/**
 * What deleting a budget plan takes with it (#1424), counted per kind: what the plan owns, the
 * rates and flat rates bound to it, and the bookings that lose their assignment. The dialog names
 * these figures before the deletion, the log the same ones after it.
 *
 * <p>The bookings themselves stay; only their assignment goes, and with it they are without a
 * budget until somebody assigns them again.
 */
public record OrderBudgetDeletion(int adjustments, int scopeEntries, int calculationLines,
                                  long assignedBookings, long pricings, long flatRates) {
}
