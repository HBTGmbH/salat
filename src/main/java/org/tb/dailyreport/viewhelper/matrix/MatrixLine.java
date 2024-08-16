package org.tb.dailyreport.viewhelper.matrix;

import static org.tb.common.util.TimeFormatUtils.timeFormatMinutes;

import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;
import lombok.Getter;
import org.tb.common.util.DateUtils;
import org.tb.dailyreport.domain.TimereportDTO;

public class MatrixLine implements Comparable<MatrixLine> {

    @Getter
    private OrderSummaryData customOrder;
    @Getter
    private OrderSummaryData subOrder;
    private long sumMinutes;
    @Getter
    private final List<BookingDay> bookingDays = new ArrayList<>();

    public MatrixLine(OrderSummaryData customOrder, OrderSummaryData subOrder, String taskdescription, LocalDate date, long durationHours, long durationMinutes) {
        super();
        this.subOrder = subOrder;
        this.customOrder = customOrder;
        addBookingDay(date, durationHours, durationMinutes, taskdescription);
    }

    public int getCountOfDays() {
        return bookingDays.size();
    }

    public void addBookingDay(LocalDate date, long durationHours, long durationMinutes, String taskdescription) {
        bookingDays.add(new BookingDay(date, durationHours, durationMinutes, taskdescription));
    }

    public void addEmptyBookingDay(LocalDate date) {
        bookingDays.add(new BookingDay(date));
    }

    public void calcTotals() {
        sumMinutes = bookingDays.stream().map(BookingDay::getDuration).mapToLong(Duration::toMinutes).sum();
    }

    public void fillBookingDaysWithNull(LocalDate dateFirst, LocalDate dateLast) {
        LocalDate loopDate = dateFirst;
        while ((loopDate.isAfter(dateFirst) && loopDate.isBefore(dateLast)) || loopDate.equals(dateFirst) || loopDate.equals(dateLast)) {
            boolean dateAvailable = false;
            for (BookingDay bookingDay : bookingDays) {
                if (bookingDay.getDate().equals(loopDate)) {
                    dateAvailable = true;
                    break;
                }
            }
            if (!dateAvailable) {
                addEmptyBookingDay(loopDate);
            }
            loopDate = DateUtils.addDays(loopDate, 1);
        }
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("<br>")
                .append(customOrder.getSign())
                .append(subOrder.getSign())
                .append(" - ");
        for (BookingDay temp : bookingDays) {
            sb.append(temp.getDate())
                    .append("-")
                    .append(temp.getDurationString())
                    .append(" // ");
        }
        return sb.toString();
    }

    public int compareTo(MatrixLine o) {
        return (this.customOrder.getSign() + this.subOrder.getSign()).compareTo(o.customOrder.getSign() + o.subOrder.getSign());
    }

    public String getSumString() {
        return timeFormatMinutes(sumMinutes);
    }

    public boolean matchesOrder(String customerorderSign, String suborderSign) {
        return customerorderSign.equals(this.customOrder.getSign()) && suborderSign.equals(this.subOrder.getSign());
    }

    public void addTimereport(TimereportDTO timeReport, String taskdescription) {
        for (BookingDay bookingDay : bookingDays) {
            if (bookingDay.getDate().equals(timeReport.getReferenceday())) {
                bookingDay.addBooking(timeReport.getDurationhours(), timeReport.getDurationminutes(), taskdescription);
                return;
            }
        }
        //if bookingday is not available, add new bookingday
        addBookingDay(timeReport.getReferenceday(), timeReport.getDurationhours(), timeReport.getDurationminutes(), taskdescription);
    }


    @Data
    public static class OrderSummaryData {

        private final String sign;
        private final String shortdescription;

    }

}
