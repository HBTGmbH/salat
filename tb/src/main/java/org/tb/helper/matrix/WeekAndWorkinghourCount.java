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
public class WeekAndWorkinghourCount {
    /**
     * @param week
     * @param workingHour
     * @author cb
     * @since 29.12.2006
     */
    public WeekAndWorkinghourCount(int week, double workingHour) {
        this.week = week;
        this.workingHour = workingHour;
    }
    
    public int week;
    public double workingHour;
    
    public int getWeek() {
        return week;
    }
    public void setWeek(int week) {
        this.week = week;
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