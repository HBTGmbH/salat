package org.tb.dailyreport.domain;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDate;

@AllArgsConstructor
@Getter
public class WorkingDayValidationError {

    public static final WorkingDayValidationError NONE = new WorkingDayValidationError(null, null);

    private LocalDate date;
    private String message;
}
