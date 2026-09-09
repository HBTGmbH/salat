package org.tb.budget.domain;

import java.util.List;
import org.tb.common.LocalDateRange;

/**
 * The controlling view of one customer order: one section per budget period, plus one for the time
 * no plan covers. Sections without anything to report are left out, so an empty list means there is
 * nothing to show at all.
 */
public record BudgetControllingResult(
    String customerorderSign,
    String customerorderDescription,
    /** Short name of the customer — that is what the header shows. */
    String customerShortname,
    LocalDateRange filter,
    List<BudgetControllingSection> sections
) {
    public boolean isEmpty() {
        return sections.isEmpty();
    }
}
