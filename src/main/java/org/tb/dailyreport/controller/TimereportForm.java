package org.tb.dailyreport.controller;

import java.time.LocalDate;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

@Data
public class TimereportForm {

    private Long id;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate referenceday;

    private Long orderId;
    private Long suborderId;

    /**
     * "duration" or "beginEnd" — controlled by the JS toggle buttons, see
     * {@link org.tb.dailyreport.preferences.DurationInputMode}. Empty on a create form while the
     * user has no preferred entry mode yet (#844); the form then decides on load.
     */
    private String durationMode = "duration";

    /** HH:MM (e.g. "01:30") — only used when durationMode == "duration" */
    private String durationTime = "";

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
