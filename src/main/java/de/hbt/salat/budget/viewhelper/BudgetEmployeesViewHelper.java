package de.hbt.salat.budget.viewhelper;

import java.util.List;
import de.hbt.salat.budget.domain.BudgetEmployees;
import de.hbt.salat.common.util.DurationUtils;

/**
 * The "Mitarbeitende" card of a budget plan (#964): its rows and the hours it has no rate for.
 *
 * <p>{@code costsIncluded} is what hides the cost side from everybody but managers, exactly as
 * controlling does. It is not the same as "no cost rate applies": a card that does not report costs
 * shows no cost column at all and no hours without one, rather than an empty column that would read
 * like a finding.
 *
 * <p>{@code pricesIncluded} does the same for the customer rates (#1435). A fixed-price plan has none
 * by design — what it earns comes from its flat rates —, so a missing rate there is the normal case,
 * and a rate column would only ever warn. Costs stay: the calculation reads its actual costs from
 * them.
 */
public record BudgetEmployeesViewHelper(
    List<BudgetEmployeeViewHelper> rows,
    boolean costsIncluded,
    boolean pricesIncluded,
    boolean anyWithoutCost,
    boolean anyWithoutPrice,
    boolean anyNotInvoiceable,
    String hoursWithoutCost,
    String hoursWithoutPrice,
    String hoursNotInvoiceable) {

    /** @param pricesIncluded whether the plan is read against customer rates — every plan but a fixed price */
    public static BudgetEmployeesViewHelper from(BudgetEmployees employees, boolean pricesIncluded) {
        return new BudgetEmployeesViewHelper(
            employees.rows().stream().map(BudgetEmployeeViewHelper::from).toList(),
            employees.costsIncluded(),
            pricesIncluded,
            !employees.durationWithoutCost().isZero(),
            pricesIncluded && !employees.durationWithoutPrice().isZero(),
            !employees.durationNotInvoiceable().isZero(),
            DurationUtils.format(employees.durationWithoutCost()),
            DurationUtils.format(employees.durationWithoutPrice()),
            DurationUtils.format(employees.durationNotInvoiceable()));
    }

    public boolean isEmpty() {
        return rows.isEmpty();
    }

    /** Whether the card shows a rate column at all, and so a hint explaining it. */
    public boolean hasRateColumn() {
        return costsIncluded || pricesIncluded;
    }

    /** Whether there is anything to say below the table at all. */
    public boolean hasFindings() {
        return anyWithoutCost || anyWithoutPrice || anyNotInvoiceable;
    }

}
