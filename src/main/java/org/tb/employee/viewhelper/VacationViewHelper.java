package org.tb.employee.viewhelper;

import static java.math.RoundingMode.DOWN;
import static java.util.Locale.GERMAN;
import static org.tb.common.util.TimeFormatUtils.timeFormatMinutes;

import jakarta.servlet.http.HttpSession;
import java.io.Serializable;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;
import org.tb.common.util.DurationUtils;
import org.tb.dailyreport.service.TimereportService;
import org.tb.employee.domain.Employeecontract;
import org.tb.order.domain.Employeeorder;
import org.tb.order.service.EmployeeorderService;

@Data
public class VacationViewHelper implements Serializable {

    private static final long serialVersionUID = 1L;
    private final Employeecontract employeecontract;
    private String suborderSign;
    private Duration budget;
    private long usedVacationMinutes;

    private VacationViewHelper(Employeecontract employeecontract) {
        this.employeecontract = employeecontract;
    }

    public void addVacationMinutes(long minutes) {
        this.usedVacationMinutes += minutes;
    }

    public boolean isVacationBudgetExceeded() {
        return usedVacationMinutes > budget.toMinutes();
    }

    public String getUsedVacationString() {
        StringBuilder usedVacation = new StringBuilder();
        usedVacation.append(timeFormatMinutes(this.usedVacationMinutes));

        BigDecimal dailyWorkingTimeMinutes = BigDecimal
            .valueOf(employeecontract.getDailyWorkingTime().toMinutes());
        if(dailyWorkingTimeMinutes.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal usedVacationDays = BigDecimal.valueOf(this.usedVacationMinutes)
                .setScale(2, DOWN)
                .divide(dailyWorkingTimeMinutes, DOWN);
            NumberFormat nf = NumberFormat.getNumberInstance(GERMAN);
            nf.setMinimumFractionDigits(2);
            nf.setMaximumFractionDigits(2);
            usedVacation
                .append(" (")
                .append(nf.format(usedVacationDays))
                .append(" Tage)");
        }
        return usedVacation.toString();
    }

    public String getBudgetVacationString() {
        StringBuilder budgetVacation = new StringBuilder();
        budgetVacation.append(DurationUtils.format(this.budget));

        BigDecimal dailyWorkingTimeMinutes = BigDecimal
            .valueOf(employeecontract.getDailyWorkingTime().toMinutes());
        if(dailyWorkingTimeMinutes.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal usedVacationDays = BigDecimal.valueOf(this.budget.toMinutes())
                .setScale(2, DOWN)
                .divide(dailyWorkingTimeMinutes, DOWN);
            NumberFormat nf = NumberFormat.getNumberInstance(GERMAN);
            nf.setMinimumFractionDigits(2);
            nf.setMaximumFractionDigits(2);
            budgetVacation
                .append(" (")
                .append(nf.format(usedVacationDays))
                .append(" Tage)");
        }
        return budgetVacation.toString();
    }

    /**
     * computes a list of VacationViews. Every VacationView contains one vacation-based suborder (Urlaub <Jahr>, Sonderurlaub, Resturlaub)
     * that is valid at the date of request with a sum of all durations booked for this suborder for this employee.
     * Saves the Vacations-List as an attribute in the Request.
     */
    public static void calculateAndSetVacations(HttpSession session, Employeecontract employeecontract, EmployeeorderService employeeorderService, TimereportService timereportService) {
        List<VacationViewHelper> vacations = new ArrayList<VacationViewHelper>();
        List<Employeeorder> orders = employeeorderService.getVacationEmployeeOrders(employeecontract.getId());
        for (Employeeorder employeeorder : orders) {
            VacationViewHelper vacationView = new VacationViewHelper(employeecontract);
            vacationView.setSuborderSign(employeeorder.getSuborder().getDescription());
            if (employeeorder.getDebithours() != null) {
                vacationView.setBudget(employeeorder.getDebithours());
            }

            long vacationMinutes = timereportService.getTotalDurationMinutesForSuborderAndEmployeeContract(employeeorder.getSuborder().getId(), employeecontract.getId());

            vacationView.addVacationMinutes(vacationMinutes);
            vacations.add(vacationView);
        }
        session.setAttribute("vacations", vacations);
    }

}
