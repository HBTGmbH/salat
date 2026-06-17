package org.tb.dailyreport.controller;

import java.time.LocalDate;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

@Data
public class WorkingdayForm {
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate date;
    private boolean notWorked;
    private String startTime = "08:00";
    private String breakTime = "00:30";
}
