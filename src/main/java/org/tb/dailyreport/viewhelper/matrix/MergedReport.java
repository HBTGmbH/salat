package org.tb.dailyreport.viewhelper.matrix;

import static org.tb.common.util.TimeFormatUtils.timeFormatMinutes;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.tb.common.util.DateUtils;
import org.tb.order.domain.Customerorder;
import org.tb.order.domain.Suborder;

public class MergedReport implements Comparable<MergedReport> {
    private Customerorder customOrder;
    private Suborder subOrder;
    private double sumHours;
    private long sumMinutes;
    private final List<BookingDay> bookingDays = new ArrayList<>();

    public MergedReport(Customerorder customOrder, Suborder subOrder, String taskdescription, LocalDate date, long durationHours, long durationMinutes) {
        this.subOrder = subOrder;
        this.customOrder = customOrder;
        addBookingDay(date, durationHours, durationMinutes, taskdescription);
    }

    public int getCountOfDays() {
        return bookingDays.size();
    }

    public void mergeBookingDay(BookingDay tempBookingDay, LocalDate date, long durationHours, long durationMinutes, String taskdescription) {
        bookingDays.set(
            bookingDays.indexOf(tempBookingDay), new BookingDay(date, tempBookingDay.getDurationHours() + durationHours, tempBookingDay.getDurationMinutes() + durationMinutes, tempBookingDay.getTaskdescription() + taskdescription));
    }

    public void addBookingDay(LocalDate date, long durationHours, long durationMinutes, String taskdescription) {
        bookingDays.add(new BookingDay(date, durationHours, durationMinutes, taskdescription));
    }

    public void setSum() {
        sumMinutes = 0;
        for (BookingDay tempBookingDay : bookingDays) {
            sumMinutes += tempBookingDay.getDurationHours() * 60 + tempBookingDay.getDurationMinutes();
        }
        sumHours = (double)sumMinutes / 60;
    }

    public double getSumHours() {
        return sumHours;
    }

    public void fillBookingDaysWithNull(LocalDate dateFirst, LocalDate dateLast) {
        LocalDate loopDate = dateFirst;
        while ((loopDate.isAfter(dateFirst) && loopDate.isBefore(dateLast)) || loopDate.equals(dateFirst) || loopDate.equals(dateLast)) {
            boolean dateAvailable = false;
            for (BookingDay tempBookingDay : bookingDays) {
                if (tempBookingDay.getDate().equals(loopDate)) {
                    dateAvailable = true;
                    break;
                }
            }
            if (!dateAvailable) {
                addBookingDay(loopDate, 0, 0, null);
            }
            loopDate = DateUtils.addDays(loopDate, 1);
        }
    }

    public List<BookingDay> getBookingDays() {
        return bookingDays;
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
                    .append(temp.getDurationHours())
                    .append("/")
                    .append(temp.getDurationMinutes())
                    .append(" // ");
        }
        return sb.toString();
    }

    public int compareTo(MergedReport o) {
        return (this.customOrder.getSign() + this.subOrder.getSign()).compareTo(o.customOrder.getSign() + o.subOrder.getSign());
    }

    public String getSumString() {
        return timeFormatMinutes(sumMinutes);
    }

    public Customerorder getCustomOrder() {
        return customOrder;
    }

    public void setCustomOrder(Customerorder customOrder) {
        this.customOrder = customOrder;
    }

    public Suborder getSubOrder() {
        return subOrder;
    }

    public void setSubOrder(Suborder subOrder) {
        this.subOrder = subOrder;
    }

}
