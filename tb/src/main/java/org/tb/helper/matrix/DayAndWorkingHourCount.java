/*
 * File:          $RCSfile$
 * Version:       $Revision$
 * 
 * Created:       29.12.2006 by cb
 * Last changed:  $Date$ by $Author$
 * 
 * Copyright (C) 2006 by HBT GmbH, www.hbt.de
 *
 */
package org.tb.helper.matrix;

import java.io.Serializable;
import java.util.Date;

/**
 * @author cb
 * @since 29.12.2006
 */
public class DayAndWorkingHourCount implements Serializable {
    
    /**
     * @param day
     * @param workingHour
     * @param date
     * @author cb
     * @since 09.02.2007
     */
    public DayAndWorkingHourCount(int day, double workingHour, Date date) {
        super();
        this.day = day;
        this.date = date;
        this.workingHour = workingHour;
        this.publicHoliday = false;
        this.satSun = false;
        this.weekDay = null;
        this.publicHolidayName = null;
    }
    
    private int day;
    private Date date;
    private double workingHour;
    private boolean publicHoliday;
    private boolean satSun;
    private String weekDay;
    private String publicHolidayName;
    
    public boolean getSatSun() {
        return satSun;
    }
    
    public void setSatSun(boolean satSun) {
        this.satSun = satSun;
    }
    
    public int getDay() {
        return day;
    }
    
    public void setDay(int day) {
        this.day = day;
    }
    
    public double getWorkingHour() {
        return workingHour;
    }
    
    public void setWorkingHour(double workingHour) {
        this.workingHour = workingHour;
    }
    
    public double getRoundWorkingHour() {
        Double duration = (workingHour + 0.05) * 10;
        int temp = duration.intValue();
        return temp / 10.0;
    }
    
    public String getDayString() {
        String dayString = "";
        if (day < 10) {
            dayString += "0";
        }
        dayString = dayString + day;
        return dayString;
    }
    
    public boolean getPublicHoliday() {
        return publicHoliday;
    }
    
    public void setPublicHoliday(boolean publicHoliday) {
        this.publicHoliday = publicHoliday;
    }
    
    public Date getDate() {
        return date;
    }
    
    public void setDate(Date date) {
        this.date = date;
    }
    
    public String getPublicHolidayName() {
        return publicHolidayName;
    }
    
    public void setPublicHolidayName(String publicHolidayName) {
        this.publicHolidayName = publicHolidayName;
    }
    
    public String getWeekDay() {
        return weekDay;
    }
    
    public void setWeekDay(String weekDay) {
        this.weekDay = weekDay;
    }
}

/*
 $Log$
 */
