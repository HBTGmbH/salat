package org.tb.budget.viewhelper;

import java.util.List;
import org.tb.budget.domain.BudgetEmployeeMinutes;
import org.tb.common.util.DurationUtils;

/**
 * The "Mitarbeitende" cell of one row of the plan overview (#964): who booked on this plan, the
 * person with the most hours first.
 *
 * <p>Only the signs, no rates and no findings — the overview answers the question before those,
 * which plan anybody works on at all, and it has to stay narrow. Name and hours travel in the title
 * of each sign; whoever wants more goes to the detail page, where the full card is.
 *
 * <p>Beyond {@link #MAX_SIGNS} the rest is counted rather than listed. A plan a whole department
 * booked on would otherwise push every other column off the table.
 *
 * <p>A plan without a single booking yields no signs and no count, so the cell stays empty — a zero
 * or a dash there would read like something went wrong.
 */
public record BudgetEmployeeSignsViewHelper(List<Sign> shown, int more) {

    /** How many signs a row lists before it starts counting. */
    public static final int MAX_SIGNS = 5;

    /** One sign with what its title says: the person's name and their hours on this plan. */
    public record Sign(String employeeSign, String employeeName, String hours) {}

    public static final BudgetEmployeeSignsViewHelper NONE =
        new BudgetEmployeeSignsViewHelper(List.of(), 0);

    public static BudgetEmployeeSignsViewHelper from(List<BudgetEmployeeMinutes> employees) {
        if (employees == null || employees.isEmpty()) {
            return NONE;
        }
        return new BudgetEmployeeSignsViewHelper(
            employees.stream()
                .limit(MAX_SIGNS)
                .map(employee -> new Sign(employee.employeeSign(), employee.employeeName(),
                    DurationUtils.format(employee.duration())))
                .toList(),
            Math.max(0, employees.size() - MAX_SIGNS));
    }

    public boolean isEmpty() {
        return shown.isEmpty();
    }

    public boolean hasMore() {
        return more > 0;
    }

}
