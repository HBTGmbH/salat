package org.tb.dailyreport.controller;

import java.time.LocalDate;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

@Data
public class TimereportForm {

    private Long id;
    private Long employeeContractId;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate referenceday;

    private Long orderId;
    private Long suborderId;

    /** "duration" (default) or "beginEnd" — controlled by JS toggle buttons */
    private String durationMode = "duration";

    private int durationHours;
    private int durationMinutes;

    /** HH:MM — only used when durationMode == "beginEnd" */
    private String beginTime;
    /** HH:MM — only used when durationMode == "beginEnd" */
    private String endTime;

    private String comment = "";
    private boolean training;

    /** 1 = no repeat; > 1 = create one timereport per working day, skipping weekends/holidays */
    private int numberOfSerialDays = 1;

    private boolean saveAsFavorite;
}
