package org.tb.dailyreport.rest;

import com.opencsv.bean.CsvBindByPosition;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;
import org.tb.common.util.DateUtils;
import org.tb.dailyreport.domain.TimereportDTO;

@Getter
@Builder(toBuilder = true)
@EqualsAndHashCode
@ToString
public class DailyReportData {

    private Long id;

    @CsvBindByPosition(position = 0)
    private String date;

    @CsvBindByPosition(position = 1)
    private long employeeorderId;

    @CsvBindByPosition(position = 2)
    private String orderSign;
    @CsvBindByPosition(position = 3)
    private String orderLabel;

    @CsvBindByPosition(position = 4)
    private String suborderSign;
    @CsvBindByPosition(position = 5)
    private String suborderLabel;

    @CsvBindByPosition(position = 6)
    private long hours;

    @CsvBindByPosition(position = 7)
    private long minutes;

    @CsvBindByPosition(position = 8)
    private String comment;

    private boolean training;

    public static DailyReportData valueOf(TimereportDTO timeReport) {
        return DailyReportData.builder()
                .id(timeReport.getId())
                .employeeorderId(timeReport.getEmployeeorderId())
                .date(DateUtils.format(timeReport.getReferenceday()))
                .orderLabel(timeReport.getCustomerorderDescription())
                .suborderLabel(timeReport.getSuborderDescription())
                .comment(timeReport.getTaskdescription())
                .training(timeReport.isTraining())
                .hours(timeReport.getDuration().toHours())
                .minutes(timeReport.getDuration().toMinutesPart())
                .suborderSign(timeReport.getCompleteOrderSign())
                .orderSign(timeReport.getCustomerorderSign())
                .build();
    }

    public DailyReportData withoutId(){
        return toBuilder().id(null).build();
    }
}
