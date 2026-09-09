package org.tb.budget.domain;

import java.time.LocalDate;
import java.util.List;
import org.tb.common.LocalDateRange;

/**
 * One evaluation of the controlling view: the plans of one period and mode, or — for
 * {@link SectionKind#UNPLANNED} — the bookings that belong to no plan at all (#913).
 *
 * <p>The hours a section reports are the ones booked inside the evaluated window; the amounts are
 * the full figures up to its end, so the budget and its utilization read against the whole plan
 * rather than against a remainder (#917). How much was booked before the window is a column of its
 * own.
 */
public record BudgetControllingSection(
    SectionKind kind,
    /** The period of the plans in this section; {@code null} for UNPLANNED, where rows differ. */
    LocalDateRange period,
    List<String> budgetNames,
    /**
     * Validity of the plans in this section — their own period, not the evaluated window. The header
     * shows it so a reader sees how long the budget runs, next to the window it is looking at.
     * {@code null} for UNPLANNED, which has no plan.
     */
    LocalDate planFrom,
    LocalDate planUntil,
    List<BudgetControllingGroup> groups,
    BudgetControllingRow total
) {
    /** A section worth showing at all — same rule as for a row (#901). */
    public boolean hasContent() {
        return total.hasContent();
    }

    public List<BudgetControllingRow> rows() {
        return groups.stream().flatMap(g -> g.rows().stream()).toList();
    }

    public boolean hasPlannedData() {
        return rows().stream().anyMatch(BudgetControllingRow::hasPlanned);
    }

    public boolean hasProgressData() {
        return total.hasProgress() || rows().stream().anyMatch(BudgetControllingRow::hasProgress);
    }

    /**
     * Whether anything was booked before the window opened. Same reasoning as for the overrun
     * column: without data it is a row of dashes under the longest header of the table — the date
     * makes it wide — and the width is better spent on the columns that have something to say.
     */
    public boolean hasBookedBeforeWindowData() {
        return total.hasBookedBeforeWindow()
            || rows().stream().anyMatch(BudgetControllingRow::hasBookedBeforeWindow);
    }

    /**
     * Whether a margin can be computed anywhere in the section. It needs a gross profit and a
     * revenue to divide by, so a section without revenue shows dashes throughout.
     */
    public boolean hasGrossProfitMarginData() {
        return total.hasGrossProfitMargin()
            || rows().stream().anyMatch(BudgetControllingRow::hasGrossProfitMargin);
    }

    /** Only planned sections have a budget, and only there is a utilization meaningful. */
    public boolean hasBudgetColumn() {
        return kind != SectionKind.UNPLANNED;
    }

    /**
     * Whether anything actually went over budget. An empty column of dashes says nothing and costs
     * width in a table that already has nine of them, so it only appears when it has news.
     */
    public boolean hasOverrunData() {
        return hasBudgetColumn()
            && (total.hasOverrun() || rows().stream().anyMatch(BudgetControllingRow::hasOverrun));
    }

    /** Whether the plans of this section carry a validity worth printing. */
    public boolean hasPlanPeriod() {
        return planFrom != null && planUntil != null;
    }

    public boolean hasSubtotals() {
        return groups.stream().anyMatch(BudgetControllingGroup::hasSubtotal);
    }
}
