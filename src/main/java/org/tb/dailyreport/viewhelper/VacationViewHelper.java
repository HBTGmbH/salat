package org.tb.dailyreport.viewhelper;

import static java.math.RoundingMode.DOWN;
import static java.util.Locale.GERMAN;
import static org.tb.common.util.TimeFormatUtils.timeFormatMinutes;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.Duration;
import lombok.Data;
import org.tb.common.util.DurationUtils;
import org.tb.employee.domain.Employeecontract;

@Data
public class VacationViewHelper {

    private final Employeecontract employeecontract;
    private String suborderSign;
    private Duration budget;
    private long usedVacationMinutes;

    public void addVacationMinutes(long minutes) {
        this.usedVacationMinutes += minutes;
    }

    public boolean isVacationBudgetExceeded() {
        return usedVacationMinutes > budget.toMinutes();
    }

    public int getUsedPercent() {
        if (budget == null || budget.toMinutes() == 0) return 0;
        return (int) Math.min(100, usedVacationMinutes * 100L / budget.toMinutes());
    }

    public String getUsedVacationString() {
        var usedVacation = new StringBuilder();
        usedVacation.append(timeFormatMinutes(this.usedVacationMinutes));

        var dailyWorkingTimeMinutes = BigDecimal.valueOf(employeecontract.getDailyWorkingTime().toMinutes());
        if (dailyWorkingTimeMinutes.compareTo(BigDecimal.ZERO) > 0) {
            var usedVacationDays = BigDecimal.valueOf(this.usedVacationMinutes)
                .setScale(2, DOWN)
                .divide(dailyWorkingTimeMinutes, DOWN);
            var nf = NumberFormat.getNumberInstance(GERMAN);
            nf.setMinimumFractionDigits(2);
            nf.setMaximumFractionDigits(2);
            usedVacation.append(" (").append(nf.format(usedVacationDays)).append(" Tage)");
        }
        return usedVacation.toString();
    }

    public String getBudgetVacationString() {
        var budgetVacation = new StringBuilder();
        budgetVacation.append(DurationUtils.format(this.budget));

        var dailyWorkingTimeMinutes = BigDecimal.valueOf(employeecontract.getDailyWorkingTime().toMinutes());
        if (dailyWorkingTimeMinutes.compareTo(BigDecimal.ZERO) > 0) {
            var budgetDays = BigDecimal.valueOf(this.budget.toMinutes())
                .setScale(2, DOWN)
                .divide(dailyWorkingTimeMinutes, DOWN);
            var nf = NumberFormat.getNumberInstance(GERMAN);
            nf.setMinimumFractionDigits(2);
            nf.setMaximumFractionDigits(2);
            budgetVacation.append(" (").append(nf.format(budgetDays)).append(" Tage)");
        }
        return budgetVacation.toString();
    }
}
