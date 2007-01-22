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

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.Iterator;
import java.util.List;

/**
 * @author cb
 * @since 04.12.2006
 */
public class MergedReport implements Comparable {
    /**
     * @param subOrderSign
     * * @param customOrderSign
     * @param taskdescription
     * @param bookingDay
     * @author cb
     * @since 04.12.2006
     */
    public MergedReport(String sign, String customOrderSign, String subOrderSign, String taskdescription, Date date, long durationHours, long durationMinutes) {
        super();
        this.sign = sign;
        this.subOrderSign = subOrderSign;
        this.customOrderSign = customOrderSign;
        this.taskdescription = taskdescription;
        addBookingDay(date, durationHours, durationMinutes);
    }

    private String sign;
    private String subOrderSign;
    private String customOrderSign;
    private String taskdescription;
    private double sum;
    private ArrayList bookingDay = new ArrayList<BookingDay>();

    public int getCountOfDays() {
        return bookingDay.size();
    }

    public void mergeBookingDay(BookingDay tempBookingDay, Date date, long durationHours, long durationMinutes) {
        bookingDay.set(bookingDay.indexOf(tempBookingDay), new BookingDay(date, tempBookingDay.getDurationHours() + durationHours, tempBookingDay.getDurationMinutes() + durationMinutes));
    }

    public void addBookingDay(Date date, long durationHours, long durationMinutes) {
        /*BookingDay tempBookingDay = null;
         for(Iterator iter = bookingDay.iterator();iter.hasNext();){
         tempBookingDay = (BookingDay)iter.next();
         if(tempBookingDay.getDate().equals(date)){
         bookingDay.set(bookingDay.indexOf(tempBookingDay), new BookingDay(date, durationHours, durationMinutes));
         }
         }*/
        bookingDay.add(new BookingDay(date, durationHours, durationMinutes));
    }

    public void setSum() {
        BookingDay tempBookingDay;
        double tempMinutes = 0;
        for (Iterator iter = bookingDay.iterator(); iter.hasNext();) {
            tempBookingDay = (BookingDay)iter.next();
            tempMinutes = tempMinutes + ((tempBookingDay.getDurationHours() * 60) + tempBookingDay.getDurationMinutes());
        }
        sum = (tempMinutes / 60);
    }

    public double getSum() {
        return sum;
    }

    public void fillBookingDaysWithNull(Date dateFirst, Date dateLast) {
        Calendar gc = GregorianCalendar.getInstance();
        gc.setTime(dateFirst);
        BookingDay tempBookingDay;
        boolean dateAvailable;
        while ((gc.getTime().after(dateFirst) && gc.getTime().before(dateLast)) || gc.getTime().equals(dateFirst) || gc.getTime().equals(dateLast)) {
            dateAvailable = false;
            for (Iterator iter = bookingDay.iterator(); iter.hasNext();) {
                tempBookingDay = (BookingDay)iter.next();
                if (tempBookingDay.getDate().equals(gc.getTime())) {
                    dateAvailable = true;
                }
            }
            if (!dateAvailable) {
                addBookingDay(gc.getTime(), 0, 0);
            }
            gc.add(Calendar.DAY_OF_WEEK, 1);
        }
    }

    public void addTaskdescription(String taskdescription) {
        if (!taskdescription.equals("") && !taskdescription.equals(null)) {
            this.taskdescription = this.taskdescription + "/" + taskdescription;
        }
    }

    public ArrayList getBookingDay() {
        return bookingDay;
    }

    public String getSubOrderSign() {
        return subOrderSign;
    }

    public String getCustomOrderSign() {
        return customOrderSign;
    }

    public String toString() {
        String test = "";
        BookingDay temp;
        for (Iterator iter = bookingDay.iterator(); iter.hasNext();) {
            temp = (BookingDay)iter.next();
            test = test + temp.getDate() + "-" + temp.getDurationHours() + "/" + temp.getDurationMinutes() + " // ";
        }
        return "<br>" + customOrderSign + subOrderSign + " - " + taskdescription + " - " + test;
    }

    public int compareTo(Object o) {
        if (o instanceof MergedReport) {
            return (this.customOrderSign + this.subOrderSign).compareTo((((MergedReport)o).customOrderSign + ((MergedReport)o).subOrderSign));
        } else {
            throw new IllegalArgumentException("Parameter must be a MergedReport");
        }

    }

    public String getTaskdescription() {
        return taskdescription;
    }

    public String getSign() {
        return sign;
    }

    public void setSign(String sign) {
        this.sign = sign;
    }
    
    public Double getRoundSum(){
        Double duration=(sum+0.05)*10;
        int temp = duration.intValue();
        return temp/10.0;
    }

}

/*
 $Log$
 */