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
public class BookingDay implements Comparable{
    /**
     * @param date
     * @param durationHours
     * @param durationMinutes
     * @author cb
     * @since 04.12.2006
     */
    public BookingDay(Date date, long durationHours, long durationMinutes) {
        super();
        this.date = date;
        this.durationHours = durationHours;
        this.durationMinutes = durationMinutes;
        this.satSun=false;
    }

    private boolean satSun; 
    private Date date;
    private long durationHours;
    private long durationMinutes;

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
}

/*
 $Log$
 */