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

    /**
     * What the order comes to over all its sections (#779).
     *
     * <p>Until now the view stopped at the section: every budget plan and the unplanned residue got
     * its own total, and what the order as a whole had booked, earned and cost was left to the
     * reader to add up. This is that sum, and it is the same line the segment listing shows per
     * order — built here so both read one calculation rather than two that can drift apart.
     *
     * <p>The budget is the sum of the section budgets, so the unplanned section contributes hours
     * and revenue but no budget. That is deliberate: those bookings answer to no plan, but they were
     * still worked and still cost money. The utilization therefore reports everything the order
     * earned against everything that was planned for it, which is the honest reading — hiding the
     * unassigned part would make a plan look better than the order it belongs to.
     */
    public BudgetControllingRow total() {
        var sectionTotals = sections.stream().map(BudgetControllingSection::total).toList();
        return BudgetControllingRow.sum(null, null, sectionTotals,
            BudgetControllingRow.sumBudget(sectionTotals), includesCosts());
    }

    /**
     * Whether a total over the sections says anything the sections do not. With a single section its
     * own total already is that of the order, and repeating it below would only invite the question
     * what the difference is.
     */
    public boolean hasTotal() {
        return sections.size() > 1;
    }

    /** The columns of the total: everything any section offers (→ {@code BudgetControllingColumns}). */
    public BudgetControllingColumns totalColumns() {
        return sections.stream().map(BudgetControllingSection::columns)
            .reduce(BudgetControllingColumns.NONE, BudgetControllingColumns::merge);
    }

    /**
     * Whether costs are part of this evaluation. Read off the sections rather than passed down: the
     * privilege decided it when they were built, and a second flag could contradict them.
     */
    private boolean includesCosts() {
        return sections.stream().anyMatch(section -> section.total().costEuro() != null);
    }
}
