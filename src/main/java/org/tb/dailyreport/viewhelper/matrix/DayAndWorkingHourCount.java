package org.tb.dailyreport.viewhelper.matrix;

import java.time.Duration;
import java.time.LocalDate;
import lombok.Data;
import org.tb.common.util.DurationUtils;

@Data
public class DayAndWorkingHourCount {
    private int day;
    private Duration workingHour;
    private LocalDate date;
    private boolean publicHoliday;
    private boolean satSun;
    private String weekDay;
    private String publicHolidayName;

    public DayAndWorkingHourCount(int day, Duration workingHour, LocalDate date) {
        this.day = day;
        this.date = date;
        this.workingHour = workingHour;
        this.publicHoliday = false;
        this.satSun = false;
        this.weekDay = null;
        this.publicHolidayName = null;
    }

    public String getWorkingHourString() {
        return DurationUtils.format(workingHour);
    }

    public String getDayString() {
        return day < 10 ? "0" + day : Integer.toString(day);
    }

}
