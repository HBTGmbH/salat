package org.tb.dailyreport.rest;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class DailyReportData {

    private long id;
    private long employeeorderId;
    private String orderLabel;
    private String suborderLabel;
    private long hours;
    private long minutes;
    private String comment;
    private boolean training;
    private String suborderSign;
    private String orderSign;
    private String date;

}
