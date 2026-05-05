package org.tb.employee.controller;

import static org.tb.common.GlobalConstants.DEFAULT_VACATION_PER_YEAR;
import static org.tb.common.util.DateUtils.format;
import static org.tb.common.util.DateUtils.today;

import java.time.Duration;
import java.time.LocalDate;
import lombok.Data;
import org.tb.common.util.DateUtils;
import org.tb.common.util.DurationUtils;

@Data
public class EmployeecontractForm {

    private Long id;
    private Long employeeId;
    private Long supervisorId;
    private String taskdescription;
    private String validFrom;
    private String validUntil;
    private Boolean freelancer = Boolean.FALSE;
    private String dailyWorkingTime = "8:00";
    private String yearlyVacation = String.valueOf(DEFAULT_VACATION_PER_YEAR);
    private String initialOvertime = "0:00";
    private Boolean hide = Boolean.FALSE;
    private Boolean resolveConflicts = Boolean.FALSE;

    // Overtime form fields (edit only)
    private String newOvertimeEffective = format(today());
    private String newOvertime = "0:00";
    private String newOvertimeComment = "";

    public LocalDate getValidFromTyped() {
        return DateUtils.parseOrNull(validFrom);
    }

    public LocalDate getValidUntilTyped() {
        if (validUntil == null || validUntil.isBlank()) return null;
        return DateUtils.parseOrNull(validUntil);
    }

    public Duration getDailyWorkingTimeTyped() {
        return DurationUtils.parseDuration(dailyWorkingTime);
    }

    public int getYearlyVacationTyped() {
        if (yearlyVacation == null || yearlyVacation.isBlank()) return DEFAULT_VACATION_PER_YEAR;
        return Integer.parseInt(yearlyVacation);
    }

    public Duration getInitialOvertimeTyped() {
        return DurationUtils.parseDuration(initialOvertime);
    }

    public Duration getNewOvertimeTyped() {
        return DurationUtils.parseDuration(newOvertime);
    }

    public LocalDate getNewOvertimeEffectiveTyped() {
        if (newOvertimeEffective == null || newOvertimeEffective.isBlank()) return today();
        return DateUtils.parseOrNull(newOvertimeEffective);
    }

}
