/*
 * File:          $RCSfile$
 * Version:       $Revision$
 * 
 * Created:       02.01.2007 by cb
 * Last changed:  $Date$ by $Author$
 * 
 * Copyright (C) 2007 by HBT GmbH, www.hbt.de
 *
 */
package org.tb.helper.matrix;

import java.util.ArrayList;
import java.util.List;

/**
 * @author cb
 * @since 02.01.2007
 */
public class ReportWrapper {

    /**
     * @param mergedReportList
     * @param dayAndWorkingHourCountList
     * @author cb
     * @since 02.01.2007
     */
    public ReportWrapper(List<MergedReport> mergedReportList, List<DayAndWorkingHourCount> dayAndWorkingHourCountList, double dayHoursSum, double dayHoursTarget, double dayHoursDiff) {
        super();
        this.mergedReportList = mergedReportList;
        this.dayAndWorkingHourCountList = dayAndWorkingHourCountList;
        this.dayHoursSum = dayHoursSum;
        this.dayHoursTarget = dayHoursTarget;
        this.dayHoursDiff = dayHoursDiff;
    }
    
    List<MergedReport> mergedReportList = new ArrayList<MergedReport>();
    List<DayAndWorkingHourCount> dayAndWorkingHourCountList = new ArrayList<DayAndWorkingHourCount>();
    double dayHoursSum;
    double dayHoursTarget;
    double dayHoursDiff;
    
    public double getDayHoursDiff() {
        return dayHoursDiff;
    }
    public void setDayHoursDiff(double dayHoursDiff) {
        this.dayHoursDiff = dayHoursDiff;
    }
    public double getDayHoursTarget() {
        return dayHoursTarget;
    }
    public double getDayHoursSum() {
        return dayHoursSum;
    }
    public List<DayAndWorkingHourCount> getDayAndWorkingHourCountList() {
        return dayAndWorkingHourCountList;
    }
    public List<MergedReport> getMergedReportList() {
        return mergedReportList;
    }

}


/*
$Log$
*/