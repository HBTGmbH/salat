package org.tb.budget.viewhelper;

import java.util.List;
import org.tb.budget.domain.BudgetEmployees;
import org.tb.common.util.DurationUtils;

/**
 * The "Mitarbeitende" card of a budget plan (#964): its rows and the hours it has no rate for.
 *
 * <p>{@code costsIncluded} is what hides the cost side from everybody but managers, exactly as
 * controlling does. It is not the same as "no cost rate applies": a card that does not report costs
 * shows no cost column at all and no hours without one, rather than an empty column that would read
 * like a finding.
 */
public record BudgetEmployeesViewHelper(
    List<BudgetEmployeeViewHelper> rows,
    boolean costsIncluded,
    boolean anyWithoutCost,
    boolean anyWithoutPrice,
    boolean anyNotInvoiceable,
    String hoursWithoutCost,
    String hoursWithoutPrice,
    String hoursNotInvoiceable) {

    public static BudgetEmployeesViewHelper from(BudgetEmployees employees) {
        return new BudgetEmployeesViewHelper(
            employees.rows().stream().map(BudgetEmployeeViewHelper::from).toList(),
            employees.costsIncluded(),
            !employees.durationWithoutCost().isZero(),
            !employees.durationWithoutPrice().isZero(),
            !employees.durationNotInvoiceable().isZero(),
            DurationUtils.format(employees.durationWithoutCost()),
            DurationUtils.format(employees.durationWithoutPrice()),
            DurationUtils.format(employees.durationNotInvoiceable()));
    }

    public boolean isEmpty() {
        return rows.isEmpty();
    }

    /** Whether there is anything to say below the table at all. */
    public boolean hasFindings() {
        return anyWithoutCost || anyWithoutPrice || anyNotInvoiceable;
    }

}
