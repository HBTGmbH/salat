package org.tb.dailyreport.viewhelper.matrix;

import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;
import lombok.Getter;
import org.tb.common.util.DurationUtils;
import org.tb.dailyreport.domain.TimereportDTO;

public class MatrixLine implements Comparable<MatrixLine> {

    @Getter
    private OrderSummaryData customOrder;
    @Getter
    private OrderSummaryData subOrder;
    private Duration total;
    @Getter
    private final List<BookingDay> bookingDays = new ArrayList<>();

    public MatrixLine(OrderSummaryData customOrder, OrderSummaryData subOrder, String taskdescription, LocalDate date, Duration duration) {
        super();
        this.subOrder = subOrder;
        this.customOrder = customOrder;
        total = Duration.ZERO;
        addBookingDay(date, duration, taskdescription);
    }

    public void addBookingDay(LocalDate date, Duration duration, String taskdescription) {
        bookingDays.add(new BookingDay(date, duration, taskdescription));
        total = total.plus(duration);
    }

    public void addEmptyBookingDay(LocalDate date) {
        bookingDays.add(new BookingDay(date));
    }

    public void fillGapsWithEmptyBookingDays(LocalDate dateFirst, LocalDate dateLast) {
        var dates = dateFirst.datesUntil(dateLast.plusDays(1)).toList(); // to include the last date too
        for(var date: dates) {
            var bookingFound = bookingDays.stream().map(BookingDay::getDate).anyMatch(d -> d.equals(date));
            if (!bookingFound) {
                addEmptyBookingDay(date);
            }
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

    public String getTotalString() {
        return DurationUtils.format(total);
    }

    public boolean matchesOrder(String customerorderSign, String suborderSign) {
        return customerorderSign.equals(this.customOrder.getSign()) && suborderSign.equals(this.subOrder.getSign());
    }

    public void addTimereport(TimereportDTO timeReport, String taskdescription) {
        for (BookingDay bookingDay : bookingDays) {
            if (bookingDay.getDate().equals(timeReport.getReferenceday())) {
                bookingDay.addBooking(timeReport.getDuration(), taskdescription);
                total = total.plus(timeReport.getDuration());
                return;
            }
        }
        //if bookingday is not available, add new bookingday
        addBookingDay(timeReport.getReferenceday(), timeReport.getDuration(), taskdescription);
    }


    @Data
    public static class OrderSummaryData {

        private final String sign;
        private final String shortdescription;

    }

}
