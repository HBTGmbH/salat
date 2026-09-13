package org.tb.budget.domain;

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
 */
public record SegmentControllingGroup(
    Long segmentId,
    String segmentName,
    List<SegmentControllingOrder> orders,
    BudgetControllingRow total
) {

    public boolean hasSegment() {
        return segmentId != null;
    }
}
