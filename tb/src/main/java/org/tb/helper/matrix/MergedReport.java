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

import org.tb.bdom.Customerorder;
import org.tb.bdom.Suborder;

/**
 * @author cb
 * @since 04.12.2006
 */
public class MergedReport implements Comparable<MergedReport> {

	/**
     * @param subOrderSign
     * * @param customOrderSign
     * @param taskdescription
     * @param bookingDay
     * @author cb
     * @since 04.12.2006
     */
    public MergedReport(Customerorder customOrder, Suborder subOrder, String taskdescription, Date date, long durationHours, long durationMinutes) {
        super();
        this.subOrder = subOrder;
        this.customOrder = customOrder;
        addBookingDay(date, durationHours, durationMinutes, taskdescription);
    }

    private Suborder subOrder;
    private Customerorder customOrder;
    private double sum;
    private ArrayList<BookingDay> bookingDay = new ArrayList<BookingDay>();

    public int getCountOfDays() {
        return bookingDay.size();
    }

    public void mergeBookingDay(BookingDay tempBookingDay, Date date, long durationHours, long durationMinutes, String taskdescription) {
        bookingDay.set(bookingDay.indexOf(tempBookingDay), new BookingDay(date, tempBookingDay.getDurationHours() + durationHours, tempBookingDay.getDurationMinutes() + durationMinutes, tempBookingDay.getTaskdescription()+taskdescription));
    }

    public void addBookingDay(Date date, long durationHours, long durationMinutes, String taskdescription) {
        /*BookingDay tempBookingDay = null;
         for(Iterator iter = bookingDay.iterator();iter.hasNext();){
         tempBookingDay = (BookingDay)iter.next();
         if(tempBookingDay.getDate().equals(date)){
         bookingDay.set(bookingDay.indexOf(tempBookingDay), new BookingDay(date, durationHours, durationMinutes));
         }
         }*/
        bookingDay.add(new BookingDay(date, durationHours, durationMinutes, taskdescription));
    }

    public void setSum() {
        BookingDay tempBookingDay;
        double tempMinutes = 0;
        for (Iterator<BookingDay> iter = bookingDay.iterator(); iter.hasNext();) {
            tempBookingDay = iter.next();
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
            for (Iterator<BookingDay> iter = bookingDay.iterator(); iter.hasNext();) {
                tempBookingDay = iter.next();
                if (tempBookingDay.getDate().equals(gc.getTime())) {
                    dateAvailable = true;
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
        String test = "";
        BookingDay temp;
        for (Iterator<BookingDay> iter = bookingDay.iterator(); iter.hasNext();) {
            temp = iter.next();
            test = test + temp.getDate() + "-" + temp.getDurationHours() + "/" + temp.getDurationMinutes() + " // ";
        }
        return "<br>" + customOrder.getSign() + subOrder.getSign() + " - " + test;
    }

    public int compareTo(MergedReport o) {
    	return (this.customOrder.getSign() + this.subOrder.getSign()).compareTo(o.customOrder.getSign() + o.subOrder.getSign());
    }
    
    public Double getRoundSum(){
        Double duration=(sum+0.05)*10;
        int temp = duration.intValue();
        return temp/10.0;
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