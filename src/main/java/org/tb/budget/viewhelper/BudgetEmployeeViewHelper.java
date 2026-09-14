package org.tb.budget.viewhelper;

import java.math.BigDecimal;
import java.util.List;
import org.tb.budget.domain.BudgetEmployee;
import org.tb.budget.domain.CostCategoryRate;
import org.tb.common.util.DurationUtils;

/**
 * One line of the "Mitarbeitende" card (#964): who booked on the plan, how much, and which rates
 * apply to that work.
 *
 * <p>Formatting only. Both rate lists are handed on in the order the service put them in — several
 * categories or several conditions over the bookings of one person are the normal case as soon as a
 * rate changed or a suborder carries one of its own, and all of them are named rather than one of
 * them chosen.
 *
 * <p>The hours behind each finding travel along so the marks can say how much work they are about.
 */
public record BudgetEmployeeViewHelper(
    String employeeSign,
    String employeeName,
    String hours,
    long bookings,
    List<CostCategoryRate> costs,
    List<BigDecimal> pricesEuroPerHour,
    boolean missingCost,
    boolean missingPrice,
    boolean notInvoiceable,
    String hoursWithoutCost,
    String hoursWithoutPrice,
    String hoursNotInvoiceable) {

    public static BudgetEmployeeViewHelper from(BudgetEmployee employee) {
        return new BudgetEmployeeViewHelper(
            employee.employeeSign(),
            employee.employeeName(),
            DurationUtils.format(employee.duration()),
            employee.bookings(),
            employee.costs(),
            employee.priceCentsPerHour().stream()
                .map(cents -> new BigDecimal(cents).movePointLeft(2))
                .toList(),
            employee.missingCost(),
            employee.missingPrice(),
            employee.hasNotInvoiceable(),
            DurationUtils.format(employee.durationWithoutCost()),
            DurationUtils.format(employee.durationWithoutPrice()),
            DurationUtils.format(employee.durationNotInvoiceable()));
    }

    /** Whether any cost category applies at all — an empty list is what the mark stands for. */
    public boolean hasCosts() {
        return !costs.isEmpty();
    }

    public boolean hasPrices() {
        return !pricesEuroPerHour.isEmpty();
    }

}
