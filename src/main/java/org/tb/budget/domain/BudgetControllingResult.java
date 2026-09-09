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
    /**
     * Short name and full name of the customer. The header names the customer the way the order
     * selects do: short name, separator, full name (see {@code CustomerorderViewHelper}).
     */
    String customerShortname,
    String customerName,
    LocalDateRange filter,
    List<BudgetControllingSection> sections
) {
    public boolean isEmpty() {
        return sections.isEmpty();
    }
}
