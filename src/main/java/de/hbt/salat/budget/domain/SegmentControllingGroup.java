package de.hbt.salat.budget.domain;

import java.util.List;

/**
 * One customer segment with the orders belonging to it, and what they come to together (#779).
 *
 * @param segmentId   {@code null} for the group of orders whose customer is in no segment. They are
 *                    listed rather than dropped: leaving them out would make the page disagree with
 *                    the dashboard about which orders exist, and an unassigned customer is a gap in
 *                    the master data that the reader should see.
 * @param segmentName the name of the segment; {@code null} for that same group, which the view
 *                    labels itself.
 * @param orders      the orders the table lists — those with revenue, hours or cost in the window
 * @param hiddenOrderCount how many orders of the segment had none of them and are only counted
 *                    (#1407, → {@link SegmentControllingOrder#hasNoRevenueHoursOrCost()})
 * @param total       the sum over every order of the segment, the hidden ones included — they add
 *                    nothing but zeros, so it is the same figure as over the listed ones
 */
public record SegmentControllingGroup(
    Long segmentId,
    String segmentName,
    List<SegmentControllingOrder> orders,
    int hiddenOrderCount,
    BudgetControllingRow total
) {

    public boolean hasSegment() {
        return segmentId != null;
    }

    /** Whether the table has a line at all; without one the hint stands in its place. */
    public boolean hasOrders() {
        return !orders.isEmpty();
    }

    public boolean hasHiddenOrders() {
        return hiddenOrderCount > 0;
    }
}
