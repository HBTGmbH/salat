package org.tb.dailyreport.domain;

import java.time.LocalDate;
import java.util.List;

public record MatrixData(
    List<DayHeader> dayHeaders,
    List<Row> rows,
    List<FooterDay> footerDays,
    String totalString) {

    public record DayHeader(
        int day,
        LocalDate date,
        String weekdayKey,
        boolean weekend,
        boolean publicHoliday,
        boolean today) {}

    public record Row(
        String customerOrderSign,
        String suborderSign,
        String customer,
        String customerOrderDesc,
        String suborderDesc,
        List<Cell> cells,
        String totalString) {}

    public record Cell(
        String durationString,
        boolean empty,
        boolean weekend,
        boolean publicHoliday) {}

    public record FooterDay(
        String workingTimeString,
        boolean notWorked,
        boolean weekend,
        boolean publicHoliday,
        boolean empty,
        String beginString,
        String breakString,
        String endString,
        boolean beginError,
        boolean breakError,
        boolean totalError) {}
}
