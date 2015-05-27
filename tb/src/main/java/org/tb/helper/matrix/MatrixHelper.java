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
import java.util.Collections;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.tb.GlobalConstants;
import org.tb.bdom.Employeecontract;
import org.tb.bdom.Publicholiday;
import org.tb.bdom.Timereport;
import org.tb.persistence.EmployeecontractDAO;
import org.tb.persistence.PublicholidayDAO;
import org.tb.persistence.TimereportDAO;

/**
 * @author cb
 * @since 04.12.2006
 */
public class MatrixHelper {
    
    /**
     * @param dateFirst
     * @param dateLast
     * @param employeeId
     * @param trDAO
     * @param ecDAO
     * @param phDAO
     * @param method
     * @param customerOrderId
     * @return
     * @author cb
     * @since 08.02.2007
     */
    public ReportWrapper getEmployeeMatrix(Date dateFirst, Date dateLast, long employeeContractId, TimereportDAO trDAO, EmployeecontractDAO ecDAO, PublicholidayDAO phDAO, int method,
            long customerOrderId,
            boolean invoice) {
        List<Timereport> timeReportList;
        List<Timereport> tempTimeReportList;
        java.sql.Date beginSqlDate = new java.sql.Date(dateFirst.getTime());
        java.sql.Date endSqlDate = new java.sql.Date(dateLast.getTime());
        List<MergedReport> mergedReportList = new ArrayList<MergedReport>();
        int mergedReportIndex = 0;
        BookingDay tempBookingDay;
        Publicholiday tempPublicHoliday;
        long durationMinutes;
        long durationHours;
        Date date;
        String taskdescription;
        Timereport tempTimeReport;
        boolean mergedReportAvailable;
        boolean bookingDayAvailable;
        Calendar gregorianCalendar = GregorianCalendar.getInstance();
        gregorianCalendar.setTime(dateFirst);
        int day = 0;
        ArrayList<DayAndWorkingHourCount> dayHoursCount = new ArrayList<DayAndWorkingHourCount>();
        Double dayHoursTarget = 0.0;
        List<Publicholiday> publicHolidayList = phDAO.getPublicHolidaysBetween(dateFirst, dateLast);
        boolean dayIsPublicHoliday = false;
        Double dayHoursSum = 0.0;
        Double tempDailyWorkingTime;
        int dayHoursSumTemp;
        int dayHoursTargetTemp;
        Double dayHoursDiff;
        String separator = System.getProperty("line.separator");
        
        //conversion and localization of weekday values
        Map<Integer, String> weekDaysMap = new HashMap<Integer, String>();
        weekDaysMap.put(2, "main.matrixoverview.weekdays.monday.text");
        weekDaysMap.put(3, "main.matrixoverview.weekdays.tuesday.text");
        weekDaysMap.put(4, "main.matrixoverview.weekdays.wednesday.text");
        weekDaysMap.put(5, "main.matrixoverview.weekdays.thursday.text");
        weekDaysMap.put(6, "main.matrixoverview.weekdays.friday.text");
        weekDaysMap.put(7, "main.matrixoverview.weekdays.saturday.text");
        weekDaysMap.put(1, "main.matrixoverview.weekdays.sunday.text");
        
        //choice of timereports by date, employeecontractid and/or customerorderid
        if (method == 1 || method == 3) {
            if (employeeContractId == -1) {
                timeReportList = trDAO.getTimereportsByDates(beginSqlDate, endSqlDate);
            } else {
                timeReportList = trDAO.getTimereportsByDatesAndEmployeeContractId(employeeContractId, beginSqlDate, endSqlDate);
            }
        } else if (method == 2 || method == 4) {
            if (employeeContractId == -1) {
                timeReportList = trDAO.getTimereportsByDatesAndCustomerOrderId(beginSqlDate, endSqlDate, customerOrderId);
            } else {
                timeReportList = trDAO.getTimereportsByDatesAndEmployeeContractIdAndCustomerOrderId(employeeContractId,
                        beginSqlDate,
                        endSqlDate,
                        customerOrderId);
            }
        } else {
            timeReportList = new ArrayList<Timereport>();
            throw new RuntimeException("this should not happen!");
        }
        
        //devide billable orders if necessary
        tempTimeReportList = new ArrayList<Timereport>();
        if (invoice == true) {
            for (Object element : timeReportList) {
                tempTimeReport = (Timereport)element;
                if (tempTimeReport.getSuborder().getInvoice() == 'Y') {
                    tempTimeReportList.add(tempTimeReport);
                }
            }
            timeReportList.clear();
            timeReportList.addAll(tempTimeReportList);
        }
        
		//filling a list with new or merged 'mergedreports'
        for (Object element : timeReportList) {
            tempTimeReport = (Timereport)element;
            taskdescription = tempTimeReport.getTaskdescription() + separator;
            date = tempTimeReport.getReferenceday().getRefdate();
            durationHours = tempTimeReport.getDurationhours();
            durationMinutes = tempTimeReport.getDurationminutes();
            
            // if timereport-suborder is overtime compensation, check if taskdescription is empty. If so, write "Überstundenausgleich" into it
            // -> needed because overtime compensation should be shown in matrix overview! (taskdescription as if-clause in jsp!)
            if (tempTimeReport.getSuborder().getSign().equals(GlobalConstants.SUBORDER_SIGN_OVERTIME_COMPENSATION)) {
                if (taskdescription == null || taskdescription.length() == 0) {
                    taskdescription = GlobalConstants.OVERTIME_COMPENSATION_TEXT;
                }
            }
            
            //insert into list if its not empty
            if (!mergedReportList.isEmpty()) {
                mergedReportAvailable = false;
                bookingDayAvailable = false;
                MergedReport tempMergedReport = null;
                //search until timereport matching mergedreport; merge bookingdays in case of match
                for (MergedReport element2 : mergedReportList) {
                    tempMergedReport = element2;
                    mergedReportIndex = mergedReportList.indexOf(tempMergedReport);
                    if ((tempMergedReport.getCustomOrder().getSign() + tempMergedReport.getSubOrder().getSign()).equals(tempTimeReport.getSuborder().getCustomerorder().getSign()
                            + tempTimeReport.getSuborder().getSign())) {
                        for (Iterator<BookingDay> iter3 = tempMergedReport.getBookingDay().iterator(); iter3.hasNext();) {
                            tempBookingDay = (BookingDay)iter3.next();
                            if (tempBookingDay.getDate().equals(date)) {
                                tempMergedReport.mergeBookingDay(tempBookingDay, date, durationHours, durationMinutes, taskdescription);
                                bookingDayAvailable = true;
                                break;
                            }
                        }
                        mergedReportAvailable = true;
                        break;
                    } else {
                        mergedReportAvailable = false;
                    }
                }
                //if bookingday is not available, add new or merge report by adding a new bookingday and substitute the mergedreportlist entrys 
                if (!bookingDayAvailable) {
                    if (mergedReportAvailable) {
                        tempMergedReport.addBookingDay(date, durationHours, durationMinutes, taskdescription);
                        mergedReportList.set(mergedReportIndex, tempMergedReport);
                    } else {
                        mergedReportList.add(new MergedReport(tempTimeReport.getSuborder().getCustomerorder(), tempTimeReport.getSuborder(), taskdescription, date, durationHours, durationMinutes));
                    }
                }
            } else {
                //create a first entry before begin to fill the list
                mergedReportList.add(new MergedReport(tempTimeReport.getSuborder().getCustomerorder(), tempTimeReport.getSuborder(), taskdescription, date, durationHours, durationMinutes));
            }
        }
        
		//set all empty bookingdays to 0, calculate sum of the bookingdays for each MergedReport and sort them
        for (Object element : mergedReportList) {
            MergedReport tempMergedReport = (MergedReport)element;
            tempMergedReport.fillBookingDaysWithNull(dateFirst, dateLast);
            tempMergedReport.setSum();
            Collections.sort(tempMergedReport.getBookingDay());
        }
        
        //fill dayhourscount list with dayandworkinghourcounts for the time between dateFirst and dateLast
        while (gregorianCalendar.getTime().after(dateFirst) && gregorianCalendar.getTime().before(dateLast) || gregorianCalendar.getTime().equals(dateFirst)
                || gregorianCalendar.getTime().equals(dateLast)) {
            day++;
            dayHoursCount.add(new DayAndWorkingHourCount(day, 0, gregorianCalendar.getTime()));
            gregorianCalendar.add(Calendar.DAY_OF_MONTH, 1);
        }
        
        day = 0;
        gregorianCalendar.setTime(dateFirst);
        while (gregorianCalendar.getTime().after(dateFirst) && gregorianCalendar.getTime().before(dateLast) || gregorianCalendar.getTime().equals(dateFirst)
                || gregorianCalendar.getTime().equals(dateLast)) {
            day++;
            dayIsPublicHoliday = false;
            //counting weekdays for dayhourstargettime
            if (gregorianCalendar.get(GregorianCalendar.DAY_OF_WEEK) != GregorianCalendar.SATURDAY && gregorianCalendar.get(GregorianCalendar.DAY_OF_WEEK) != GregorianCalendar.SUNDAY) {
                for (Object element : publicHolidayList) {
                    tempPublicHoliday = (Publicholiday)element;
                    if (tempPublicHoliday.getRefdate().equals(gregorianCalendar.getTime())) {
                        dayIsPublicHoliday = true;
                    }
                }
                if (!dayIsPublicHoliday) {
                    dayHoursTarget++;
                }
            }
            //setting publicholidays and weekend for dayhourscount(status and name)
            for (DayAndWorkingHourCount tempDayAndWorkingHourCount : dayHoursCount) {
                if (tempDayAndWorkingHourCount.getDay() == day) {
                    for (Object element2 : publicHolidayList) {
                        tempPublicHoliday = (Publicholiday)element2;
                        if (tempPublicHoliday.getRefdate().equals(gregorianCalendar.getTime())) {
                            dayHoursCount.get(dayHoursCount.indexOf(tempDayAndWorkingHourCount)).setPublicHoliday(true);
                            dayHoursCount.get(dayHoursCount.indexOf(tempDayAndWorkingHourCount)).setPublicHolidayName(tempPublicHoliday.getName());
                        }
                    }
                    if (gregorianCalendar.get(GregorianCalendar.DAY_OF_WEEK) == GregorianCalendar.SATURDAY || gregorianCalendar.get(GregorianCalendar.DAY_OF_WEEK) == GregorianCalendar.SUNDAY) {
                        dayHoursCount.get(dayHoursCount.indexOf(tempDayAndWorkingHourCount)).setSatSun(true);
                    }
                    dayHoursCount.get(dayHoursCount.indexOf(tempDayAndWorkingHourCount)).setWeekDay(weekDaysMap.get(gregorianCalendar.get(Calendar.DAY_OF_WEEK)));
                }
            }
            gregorianCalendar.add(Calendar.DAY_OF_MONTH, 1);
        }
        
        //setting publicholidays(status and name) and weekend for dayandworkinghourcount and bookingday in mergedreportlist
        gregorianCalendar.setTime(dateFirst);
        day = 0;
        while (gregorianCalendar.getTime().after(dateFirst) && gregorianCalendar.getTime().before(dateLast) || gregorianCalendar.getTime().equals(dateFirst)
                || gregorianCalendar.getTime().equals(dateLast)) {
            day++;
            for (MergedReport tempMergedReport : mergedReportList) {
                for (Iterator<BookingDay> iter2 = tempMergedReport.getBookingDay().iterator(); iter2.hasNext();) {
                    tempBookingDay = iter2.next();
                    if (tempBookingDay.getDate().equals(gregorianCalendar.getTime())) {
                        //                        if (!dayHoursCount.isEmpty()) {
                        if (gregorianCalendar.get(GregorianCalendar.DAY_OF_WEEK) == GregorianCalendar.SATURDAY || gregorianCalendar.get(GregorianCalendar.DAY_OF_WEEK) == GregorianCalendar.SUNDAY) {
                            tempBookingDay.setSatSun(true);
                        }
                        for (DayAndWorkingHourCount tempDayAndWorkingHourCount : dayHoursCount) {
                            if (tempDayAndWorkingHourCount.getDay() == day) {
                                DayAndWorkingHourCount tempDayAndWorkingHourCount2 = new DayAndWorkingHourCount(day,
                                        (tempBookingDay.getDurationHours() * 60 + tempBookingDay.getDurationMinutes() + tempDayAndWorkingHourCount.getWorkingHour() * 60) / 60, tempBookingDay
                                                .getDate());
                                tempDayAndWorkingHourCount2.setPublicHoliday(tempDayAndWorkingHourCount.getPublicHoliday());
                                tempDayAndWorkingHourCount2.setPublicHolidayName(tempDayAndWorkingHourCount.getPublicHolidayName());
                                tempDayAndWorkingHourCount2.setSatSun(tempDayAndWorkingHourCount.getSatSun());
                                tempDayAndWorkingHourCount2.setWeekDay(tempDayAndWorkingHourCount.getWeekDay());
                                dayHoursCount.set(dayHoursCount.indexOf(tempDayAndWorkingHourCount), tempDayAndWorkingHourCount2);
                                for (Object element3 : publicHolidayList) {
                                    tempPublicHoliday = (Publicholiday)element3;
                                    if (tempPublicHoliday.getRefdate().equals(gregorianCalendar.getTime())) {
                                        tempBookingDay.setPublicHoliday(true);
                                    }
                                    
                                }
                            }
                        }
                    }
                }
                
            }
            gregorianCalendar.add(Calendar.DAY_OF_MONTH, 1);
        }
        
        //sort mergedreportlist by custom- and subordersign
        Collections.sort(mergedReportList);
        
        //calculate dayhourssum
        for (DayAndWorkingHourCount tempDayAndWorkingHourCount : dayHoursCount) {
            dayHoursSum += tempDayAndWorkingHourCount.getWorkingHour();
        }
        dayHoursSum = (dayHoursSum + 0.05) * 10;
        dayHoursSumTemp = dayHoursSum.intValue();
        dayHoursSum = dayHoursSumTemp / 10.0;
        
        //calculate dayhourstarget
        if (employeeContractId == -1) {
            List<Employeecontract> employeeContractList = ecDAO.getEmployeeContracts();
            Employeecontract tempEmployeeContract;
            tempDailyWorkingTime = 0.0;
            for (Object element : employeeContractList) {
                tempEmployeeContract = (Employeecontract)element;
                tempDailyWorkingTime += tempEmployeeContract.getDailyWorkingTime();
            }
            dayHoursTarget = dayHoursTarget * tempDailyWorkingTime;
        } else {
            Employeecontract employeecontract = ecDAO.getEmployeeContractById(employeeContractId);
            dayHoursTarget = dayHoursTarget * employeecontract.getDailyWorkingTime();
        }
        dayHoursTarget = (dayHoursTarget + 0.05) * 10;
        dayHoursTargetTemp = dayHoursTarget.intValue();
        dayHoursTarget = dayHoursTargetTemp / 10.0;
        
        //calculate dayhoursdiff
        dayHoursDiff = dayHoursSum - dayHoursTarget;
        if (dayHoursDiff < 0) {
            dayHoursDiff = (dayHoursDiff - 0.05) * 10;
        } else {
            dayHoursDiff = (dayHoursDiff + 0.05) * 10;
        }
        int dayHoursDiffTemp = dayHoursDiff.intValue();
        dayHoursDiff = dayHoursDiffTemp / 10.0;
        
        return new ReportWrapper(mergedReportList, dayHoursCount, dayHoursSum, dayHoursTarget, dayHoursDiff);
    }
    
    //    public List<List> getMonths(Date dateFirst, Date dateLast) {
    //        Calendar gcFirst = GregorianCalendar.getInstance();
    //        Calendar gcLast = GregorianCalendar.getInstance();
    //        Calendar tempGc = GregorianCalendar.getInstance();
    //        gcFirst.setTime(dateFirst);
    //        gcLast.setTime(dateLast);
    //        List<List> dateAL = new ArrayList<List>();
    //        List<Date> tempDateAl = new ArrayList<Date>();
    //
    //        Date dateFirstBegin = dateFirst;
    //        tempDateAl.add(dateFirstBegin);
    //        tempGc.set(gcFirst.get(GregorianCalendar.YEAR), gcFirst.get(GregorianCalendar.MONTH), gcFirst.getMaximum(GregorianCalendar.DAY_OF_MONTH));
    //        Date dateFirstEnd = tempGc.getTime();
    //        tempDateAl.add(dateFirstEnd);
    //        dateAL.add(tempDateAl);
    //        tempDateAl.clear();
    //
    //        if (gcFirst.get(GregorianCalendar.MONTH) != gcLast.get(GregorianCalendar.MONTH)) {
    //            tempGc.set(gcLast.get(GregorianCalendar.YEAR), gcLast.get(GregorianCalendar.MONTH), gcLast.getMinimum(GregorianCalendar.DAY_OF_MONTH));
    //            Date dateLastBegin = tempGc.getTime();
    //            Date dateLastEnd = dateLast;
    //
    //            tempDateAl.add(dateLastBegin);
    //            tempDateAl.add(dateLastEnd);
    //            dateAL.add(tempDateAl);
    //            tempDateAl.clear();
    //            gcFirst.add(Calendar.MONTH, 1);
    //            while ((gcFirst.getTime().after(dateFirst) && gcFirst.getTime().before(dateLast)) || gcFirst.getTime().equals(dateFirst) || gcFirst.getTime().equals(dateLast)) {
    //                tempGc.set(gcFirst.get(GregorianCalendar.YEAR), gcFirst.get(GregorianCalendar.MONTH), gcFirst.getMinimum(GregorianCalendar.DAY_OF_MONTH));
    //                dateFirstBegin = tempGc.getTime();
    //                tempDateAl.add(dateFirstBegin);
    //                tempGc.set(gcFirst.get(GregorianCalendar.YEAR), gcFirst.get(GregorianCalendar.MONTH), gcFirst.getMaximum(GregorianCalendar.DAY_OF_MONTH));
    //                dateLastBegin = tempGc.getTime();
    //                tempDateAl.add(dateLastBegin);
    //                dateAL.add(dateAL.size() - 1, tempDateAl);
    //                tempDateAl.clear();
    //                gcFirst.add(Calendar.MONTH, 1);
    //            }
    //        }
    //
    //        return dateAL;
    //
    //    }
}

/*
 $Log$
 */
