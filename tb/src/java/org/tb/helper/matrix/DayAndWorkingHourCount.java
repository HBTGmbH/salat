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

/**
 * @author cb
 * @since 29.12.2006
 */
public class DayAndWorkingHourCount {
    /**
     * @param day
     * @param workingHour
     * @author cb
     * @since 29.12.2006
     */
    public DayAndWorkingHourCount(int day, double workingHour) {
        super();
        this.day = day;
        this.workingHour = workingHour;
    }
    
    public int day;
    public double workingHour;
    
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
}


/*
$Log$
*/