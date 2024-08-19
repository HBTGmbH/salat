package org.tb.dailyreport.rest;

import lombok.Builder;
import lombok.Getter;
import org.tb.dailyreport.domain.Workingday.WorkingDayType;

@Getter
@Builder
public class WorkingDayData {

    @Builder.Default
    private Long id = -1L;
    private int starthour;
    private int startminute;
    private int breakhours;
    private int breakminutes;
    private String date;
    private WorkingDayType type;

}
