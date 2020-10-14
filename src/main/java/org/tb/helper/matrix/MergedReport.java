/*
 * File:          $RCSfile$
 * Version:       $Revision$
 *
 * Created:       04.12.2006 by cb
 * Last changed:  $Date$ by $Author$
 *
 * Copyright (C) 2006 by HBT GmbH, www.hbt.de
 *
 */
package org.tb.helper.matrix;

import org.tb.bdom.Customerorder;
import org.tb.bdom.Suborder;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;

/**
 * @author cb
 * @since 04.12.2006
 */
public class MergedReport implements Comparable<MergedReport> {
    private Suborder subOrder;
    private Customerorder customOrder;
    private double sum;
    private ArrayList<BookingDay> bookingDay = new ArrayList<BookingDay>();

    /**
     * @param subOrderSign    * @param customOrderSign
     * @param taskdescription
     * @param bookingDay
     * @author cb
     * @since 04.12.2006
     */
    public MergedReport(Customerorder customOrder, Suborder subOrder, String taskdescription, Date date, long durationHours, long durationMinutes) {
        this.subOrder = subOrder;
        this.customOrder = customOrder;
        addBookingDay(date, durationHours, durationMinutes, taskdescription);
    }

    public int getCountOfDays() {
        return bookingDay.size();
    }

    public void mergeBookingDay(BookingDay tempBookingDay, Date date, long durationHours, long durationMinutes, String taskdescription) {
        bookingDay.set(bookingDay.indexOf(tempBookingDay), new BookingDay(date, tempBookingDay.getDurationHours() + durationHours, tempBookingDay.getDurationMinutes() + durationMinutes, tempBookingDay.getTaskdescription() + taskdescription));
    }

    public void addBookingDay(Date date, long durationHours, long durationMinutes, String taskdescription) {
        bookingDay.add(new BookingDay(date, durationHours, durationMinutes, taskdescription));
    }

    public void setSum() {
        double tempMinutes = 0;
        for (BookingDay tempBookingDay : bookingDay) {
            tempMinutes += tempBookingDay.getDurationHours() * 60 + tempBookingDay.getDurationMinutes();
        }
        sum = (tempMinutes / 60);
    }

    public double getSum() {
        return sum;
    }

    public void fillBookingDaysWithNull(Date dateFirst, Date dateLast) {
        Calendar gc = GregorianCalendar.getInstance();
        gc.setTime(dateFirst);
        while ((gc.getTime().after(dateFirst) && gc.getTime().before(dateLast)) || gc.getTime().equals(dateFirst) || gc.getTime().equals(dateLast)) {
            boolean dateAvailable = false;
            for (BookingDay tempBookingDay : bookingDay) {
                if (tempBookingDay.getDate().equals(gc.getTime())) {
                    dateAvailable = true;
                    break;
                }
            }
            if (!dateAvailable) {
                addBookingDay(gc.getTime(), 0, 0, null);
            }
            gc.add(Calendar.DAY_OF_WEEK, 1);
        }
    }

    public ArrayList<BookingDay> getBookingDay() {
        return bookingDay;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("<br>")
                .append(customOrder.getSign())
                .append(subOrder.getSign())
                .append(" - ");
        for (BookingDay temp : bookingDay) {
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

    public Double getRoundSum() {
        long duration = (long) (sum * 100);
        return (double) duration / 100.0;
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

/*
 $Log$
 */