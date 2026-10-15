package de.hbt.salat.dailyreport.domain;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import de.hbt.salat.order.domain.SurchargeKind;

public record MatrixData(
    List<DayHeader> dayHeaders,
    List<Row> rows,
    /**
     * The standby orders, shown below the sum row and counted in no sum of it (#463). They keep
     * their own row total; an aggregated standby sum is deliberately not offered.
     */
    List<Row> standbyRows,
    List<FooterDay> footerDays,
    String totalString,
    String targetString,
    String diffString,
    boolean diffNegative,
    String prevDayDiffString,
    boolean prevDayDiffNegative,
    /**
     * The time surcharge credit of the month (#1469), that of each day in {@link FooterDay#surchargeCredit}:
     * a row of its own above the sum row, shown only where the month has some. It counts towards the
     * time account, not towards the booked hours of the sum row.
     */
    Duration surchargeCreditTotal) {

    public record DayHeader(
        int day,
        LocalDate date,
        String weekdayKey,
        boolean weekend,
        boolean publicHoliday,
        String holidayName,
        boolean today) {}

    public record Row(
        String customerOrderSign,
        String suborderSign,
        String customer,
        String customerOrderDesc,
        String suborderDesc,
        List<Cell> cells,
        String totalString) {}

    /**
     * One booking behind a cell: its ticket references, which since #1326 are no part of the comment,
     * and its time surcharge (#1469), {@code null} kind for none.
     */
    public record ReportDetail(
        String durationString,
        String taskDescription,
        List<String> ticketReferences,
        SurchargeKind surchargeKind,
        Integer surchargePercent,
        Duration surchargeCredit) {}

    public record Cell(
        String durationString,
        boolean empty,
        boolean weekend,
        boolean publicHoliday,
        List<ReportDetail> details) {}

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
        boolean totalError,
        /** the time surcharge credit of the day (#1469) */
        Duration surchargeCredit) {}
}
