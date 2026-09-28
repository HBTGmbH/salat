package org.tb.dailyreport.viewhelper;

import static java.math.RoundingMode.DOWN;
import static java.util.Locale.GERMAN;
import static org.tb.common.util.DateUtils.today;
import static org.tb.common.util.TimeFormatUtils.timeFormatMinutes;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;
import lombok.Data;
import org.tb.dailyreport.domain.VacationInfo;
import org.tb.dailyreport.service.VacationService;
import org.tb.employee.domain.Employeecontract;

@Data
public class VacationViewHelper {

    private final Employeecontract employeecontract;
    private String suborderSign;
    private Duration budget;
    private long usedVacationMinutes;
    private long plannedVacationMinutes;
    private List<Long> suborderIds;
    private boolean special;
    private LocalDate validFrom;
    private LocalDate validUntil;

    public static VacationViewHelper from(Employeecontract employeecontract, VacationInfo info) {
        var helper = new VacationViewHelper(employeecontract);
        helper.setSuborderSign(info.suborderSign());
        helper.setBudget(info.budget());
        helper.setUsedVacationMinutes(info.usedVacationMinutes());
        helper.setPlannedVacationMinutes(info.plannedVacationMinutes());
        helper.setSuborderIds(info.suborderIds());
        helper.setSpecial(info.special());
        helper.setValidFrom(info.validFrom());
        helper.setValidUntil(info.validUntil());
        return helper;
    }

    /**
     * Der letzte Tag, bis zu dem die Buchungen auf diesen Urlaub gesucht werden (#1175). Die
     * Buchungsliste braucht ein Ende; bei offenem Ende reicht es so weit in die Zukunft wie die
     * geplanten Tage der Kontenuebersicht, zwei Jahre.
     */
    public LocalDate getBookingsUntil() {
        return validUntil != null ? validUntil : today().plusYears(VacationService.PLANNED_HORIZON_YEARS);
    }

    public void addVacationMinutes(long minutes) {
        this.usedVacationMinutes += minutes;
    }

    /** Sonderurlaub hat kein Budget und ist deshalb nie ueberschritten (#1175). */
    public boolean isVacationBudgetExceeded() {
        return !special && usedVacationMinutes > budget.toMinutes();
    }

    /** Die Unterauftraege fuer den Filter der Buchungsliste, durch Komma getrennt wie dort ueblich. */
    public String getSuborderIdsParam() {
        return suborderIds.stream().map(String::valueOf).collect(Collectors.joining(","));
    }

    /** Die Farbe des Balkens im Dashboard ({@link DashboardGrades#vacation}). */
    public String getColorClass() {
        return DashboardGrades.vacation(isVacationBudgetExceeded());
    }

    public int getUsedPercent() {
        if (budget == null || budget.toMinutes() == 0) return 0;
        return (int) Math.min(100, usedVacationMinutes * 100L / budget.toMinutes());
    }

    /**
     * Der Teil des Balkens fuer den genommenen Urlaub. Genommen und geplant stehen nebeneinander und
     * ergeben zusammen {@link #getUsedPercent()}; so reicht der Balken nie ueber das Budget hinaus.
     */
    public int getTakenPercent() {
        if (budget == null || budget.toMinutes() == 0) return 0;
        return (int) Math.min(100, (usedVacationMinutes - plannedVacationMinutes) * 100L / budget.toMinutes());
    }

    /** Der Teil des Balkens fuer den geplanten Urlaub, siehe {@link #getTakenPercent()}. */
    public int getPlannedPercent() {
        return getUsedPercent() - getTakenPercent();
    }

    public boolean hasPlannedVacation() {
        return plannedVacationMinutes > 0;
    }

    /** Genommener Urlaub ohne den geplanten, in derselben Form wie {@link #getUsedVacationString()}. */
    public String getTakenVacationString() {
        return daysThenHours(usedVacationMinutes - plannedVacationMinutes);
    }

    /** Geplanter Urlaub in derselben Form wie {@link #getUsedVacationString()}. */
    public String getPlannedVacationString() {
        return daysThenHours(plannedVacationMinutes);
    }

    /**
     * Genommener Urlaub, die Tage vorn und die Stunden dahinter: Tage sind die Einheit, in der
     * Urlaub gedacht wird, die Stunden sind das Detail (#1175). Ohne taegliche Sollarbeitszeit gibt
     * es keine Tage, dann bleiben nur die Stunden.
     */
    public String getUsedVacationString() {
        return daysThenHours(usedVacationMinutes);
    }

    /** Das Budget in derselben Form wie {@link #getUsedVacationString()}. */
    public String getBudgetVacationString() {
        return daysThenHours(budget.toMinutes());
    }

    private String daysThenHours(long minutes) {
        var hours = timeFormatMinutes(minutes);
        var dailyWorkingTimeMinutes = BigDecimal.valueOf(employeecontract.getDailyWorkingTime().toMinutes());
        if (dailyWorkingTimeMinutes.compareTo(BigDecimal.ZERO) <= 0) {
            return hours;
        }
        var days = BigDecimal.valueOf(minutes)
            .setScale(2, DOWN)
            .divide(dailyWorkingTimeMinutes, DOWN);
        var nf = NumberFormat.getNumberInstance(GERMAN);
        nf.setMinimumFractionDigits(2);
        nf.setMaximumFractionDigits(2);
        return nf.format(days) + " Tage (" + hours + ")";
    }
}
