package org.tb.dailyreport.controller;

import java.time.LocalDate;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;
import org.tb.dailyreport.preferences.DurationInputMode;

@Data
public class TimereportForm {

    private Long id;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate referenceday;

    private Long orderId;
    private Long suborderId;

    /**
     * Key of a {@link DurationInputMode} — controlled by the JS toggle buttons. Stays a String
     * because that is what the hidden form field submits; the enum keys are the wire format.
     * Empty on a create form while the user has no preferred entry mode yet (#844); the form then
     * decides on load.
     */
    private String durationMode = DurationInputMode.DURATION.getKey();

    /** HH:MM (e.g. "01:30") — only used in {@link DurationInputMode#DURATION} mode */
    private String durationTime = "";

    /** HH:MM — only used in {@link DurationInputMode#BEGIN_END} mode */
    private String beginTime;
    /** HH:MM — only used in {@link DurationInputMode#BEGIN_END} mode */
    private String endTime;

    private String comment = "";
    private boolean training;

    /** 1 = no repeat; > 1 = create one timereport per working day, skipping weekends/holidays */
    private int numberOfSerialDays = 1;

    private boolean saveAsFavorite;
}
