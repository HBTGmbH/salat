package org.tb.budget.domain;

import java.util.List;
import org.tb.common.LocalDateRange;

/**
 * The controlling of every budgeted customer order, grouped by the segment of its customer (#779).
 *
 * <p>Same figures as the single order view, one line per order instead of one page. The columns are
 * decided once for the whole page rather than per segment: the tables sit below one another, and
 * columns that come and go between them would make two lines look comparable that are not.
 */
public record SegmentControllingResult(
    LocalDateRange filter,
    List<SegmentControllingGroup> segments,
    BudgetControllingColumns columns
) {

    public boolean isEmpty() {
        return segments.isEmpty();
    }
}
