package org.tb.dailyreport.viewhelper;

import static java.math.RoundingMode.DOWN;
import static java.util.Locale.GERMAN;
import static org.tb.common.util.DateUtils.today;
import static org.tb.common.util.TimeFormatUtils.timeFormatMinutes;

import java.io.Serializable;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import javax.servlet.http.HttpSession;
import org.tb.common.util.DurationUtils;
import org.tb.dailyreport.persistence.TimereportDAO;
import org.tb.employee.domain.Employeecontract;
import org.tb.order.domain.Employeeorder;
import org.tb.order.persistence.EmployeeorderDAO;

public class VacationViewer implements Serializable {

    private static final long serialVersionUID = 1L;
    private final Employeecontract employeecontract;
    private String suborderSign;
    private Duration budget;
    private int usedVacationMinutes;

    public VacationViewer(Employeecontract employeecontract) {
        this.employeecontract = employeecontract;
    }

    public Duration getBudget() {
        return budget;
    }

    public void setBudget(Duration budget) {
        this.budget = budget;
    }

    public String getSuborderSign() {
        return suborderSign;
    }

    public void setSuborderSign(String suborderSign) {
        this.suborderSign = suborderSign;
    }

    public int getUsedVacationMinutes() {
        return usedVacationMinutes;
    }

    public void setUsedVacationMinutes(int usedVacationMinutes) {
        this.usedVacationMinutes = usedVacationMinutes;
    }

    public void addVacationMinutes(int minutes) {
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
     * vacation v2
     * <p>
     * computes a list of VacationViews. Every VacationView contains one vacation-based suborder (Urlaub <Jahr>, Sonderurlaub, Resturlaub)
     * that is valid at the date of request with a sum of all durations booked for this suborder for this employee.
     * Saves the Vacations-List as an attribute in the Request.
     */
    public void computeVacations(HttpSession session, Employeecontract employeecontract, EmployeeorderDAO employeeorderDAO, TimereportDAO timereportDAO) {

        LocalDate today = today();

        List<VacationViewer> vacations = new ArrayList<VacationViewer>();

        List<Employeeorder> orders = employeeorderDAO.getVacationEmployeeOrdersByEmployeeContractIdAndDate(employeecontract.getId(), today);

        for (Employeeorder employeeorder : orders) {
            VacationViewer vacationView = new VacationViewer(employeecontract);
            vacationView.setSuborderSign(employeeorder.getSuborder().getDescription());
            if (employeeorder.getDebithours() != null) {
                vacationView.setBudget(employeeorder.getDebithours());
            }

            int vacationMinutes = (int) timereportDAO.getTotalDurationMinutesForSuborderAndEmployeeContract(employeeorder.getSuborder().getId(), employeecontract.getId());

            vacationView.addVacationMinutes(vacationMinutes);
            vacations.add(vacationView);
        }
        session.setAttribute("vacations", vacations);
    }

}
