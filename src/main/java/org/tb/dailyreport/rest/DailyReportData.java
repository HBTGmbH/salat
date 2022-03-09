package org.tb.dailyreport.rest;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class DailyReportData {

    private long employeeorderId;
    private String orderLabel;
    private String suborderLabel;
    private int hours;
    private int minutes;
    private String comment;
    private boolean isTraining;
    private String suborderSign;
    private String orderSign;
    private String date;

}
