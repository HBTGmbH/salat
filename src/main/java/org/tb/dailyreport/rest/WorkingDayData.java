package org.tb.dailyreport.rest;

import lombok.Builder;
import lombok.Getter;
import org.tb.common.util.DateUtils;
import org.tb.dailyreport.domain.Workingday;
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

    static WorkingDayData valueOf(Workingday wd) {
        return WorkingDayData.builder()
                .id(wd.getId())
                .starthour(wd.getStarttimehour())
                .startminute(wd.getStarttimeminute())
                .breakhours(wd.getBreakhours())
                .breakminutes(wd.getBreakminutes())
                .date(DateUtils.format(wd.getRefday()))
                .type(wd.getType())
                .build();
    }
}
