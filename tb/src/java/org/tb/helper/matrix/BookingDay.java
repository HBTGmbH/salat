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

import java.util.Date;

/**
 * @author cb
 * @since 04.12.2006
 */
public class BookingDay implements Comparable {
    /**
     * @param date
     * @param durationHours
     * @param durationMinutes
     * @author cb
     * @since 04.12.2006
     */
    public BookingDay(Date date, long durationHours, long durationMinutes, String taskdescription) {
        super();
        this.date = date;
        this.durationHours = durationHours;
        this.durationMinutes = durationMinutes;
        this.satSun = false;
        this.publicHoliday = false;
        this.taskdescription = taskdescription;
    }

    private boolean satSun;
    private boolean publicHoliday;
    private Date date;
    private long durationHours;
    private long durationMinutes;
    private String taskdescription;

    public long getDurationHours() {
        return durationHours;
    }

    public void setDurationHours(long durationHours) {
        this.durationHours = durationHours;
    }

    public long getDurationMinutes() {
        return durationMinutes;
    }

    public void setDurationMinutes(long durationMinutes) {
        this.durationMinutes = durationMinutes;
    }

    public void setDate(Date date) {
        this.date = date;
    }

    public Date getDate() {
        return date;
    }

    public int compareTo(Object o) {
        if (o instanceof BookingDay) {
            return this.date.compareTo(((BookingDay)o).date);
        } else {
            throw new IllegalArgumentException("Parameter must be a MergedReport");
        }

    }

    public boolean getSatSun() {
        return satSun;
    }

    public void setSatSun(boolean satSun) {
        this.satSun = satSun;
    }

    public double getRoundHours() {
        Double duration = (((durationHours * 60) + (durationMinutes)) / 60.0);
        duration = (duration + 0.05) * 10;
        int temp = duration.intValue();
        return temp / 10.0;
    }

    public boolean getPublicHoliday() {
        return publicHoliday;
    }

    public void setPublicHoliday(boolean publicHoliday) {
        this.publicHoliday = publicHoliday;
    }

    public String getTaskdescription() {
        return taskdescription;
    }

    public void setTaskdescription(String taskdescription) {
        this.taskdescription = taskdescription;
    }

}

/*
 $Log$
 */