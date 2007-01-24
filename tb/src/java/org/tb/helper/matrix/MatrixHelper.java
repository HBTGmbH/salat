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

    public ReportWrapper getEmployeeMatrix(Date dateFirst, Date dateLast, long employeeId, TimereportDAO trDAO, EmployeecontractDAO ecDAO, PublicholidayDAO phDAO, int method, long customerOrderId) {
        List<Timereport> timeReportList;
        java.sql.Date beginSqlDate = new java.sql.Date(dateFirst.getTime());
        java.sql.Date endSqlDate = new java.sql.Date(dateLast.getTime());

        List<MergedReport> mergedReportList = new ArrayList<MergedReport>();
        int mrIndex = 0;
        MergedReport tempMergedReport;
        BookingDay tempBookingDay;
        Publicholiday tempPublicHoliday;
        long durationMinutes;
        long durationHours;
        Date date;
        String taskdescription;
        Timereport tempTimeReport;
        boolean mergedReportAvailable;
        boolean bookingDayAvailable;
        Map<Integer, String> weekDaysMap = new HashMap<Integer, String>();
        weekDaysMap.put(2, "main.matrixoverview.weekdays.monday.text");
        weekDaysMap.put(3, "main.matrixoverview.weekdays.tuesday.text");
        weekDaysMap.put(4, "main.matrixoverview.weekdays.wednesday.text");
        weekDaysMap.put(5, "main.matrixoverview.weekdays.thursday.text");
        weekDaysMap.put(6, "main.matrixoverview.weekdays.friday.text");
        weekDaysMap.put(7, "main.matrixoverview.weekdays.saturday.text");
        weekDaysMap.put(1, "main.matrixoverview.weekdays.sunday.text");
        //        List test = getMonths(dateFirst, dateLast);
         
        if (method == 1 || method == 3) {
            timeReportList = trDAO.getTimereportsByDatesAndEmployeeContractId(ecDAO.getEmployeeContractByEmployeeId(employeeId).getId(), beginSqlDate, endSqlDate);
        } else if (method == 2 || method == 4) {
            timeReportList = trDAO.getTimereportsByDatesAndEmployeeContractIdAndCustomerOrderId(ecDAO.getEmployeeContractByEmployeeId(employeeId).getId(), beginSqlDate, endSqlDate, customerOrderId);
        } else {
            timeReportList = new ArrayList<Timereport>();
            throw new RuntimeException("this should not happen!");
        }

        for (Iterator iter = timeReportList.iterator(); iter.hasNext();) {
            tempTimeReport = (Timereport)iter.next();
            taskdescription = tempTimeReport.getTaskdescription();
            date = tempTimeReport.getReferenceday().getRefdate();
            durationHours = tempTimeReport.getDurationhours();
            durationMinutes = tempTimeReport.getDurationminutes();
            //            if ((date.after(dateFirst) || date.equals(dateFirst)) && (date.before(dateLast) || date.equals(dateLast))) {
            if (!mergedReportList.isEmpty()) {
                mergedReportAvailable = false;
                bookingDayAvailable = false;
                tempMergedReport = null;
                for (Iterator iter2 = mergedReportList.iterator(); iter2.hasNext();) {
                    tempMergedReport = (MergedReport)iter2.next();
                    mrIndex = mergedReportList.indexOf(tempMergedReport);
                    if ((tempMergedReport.getCustomOrder().getSign() + tempMergedReport.getSubOrder().getSign()).equals(tempTimeReport.getSuborder().getCustomerorder().getSign()
                            + tempTimeReport.getSuborder().getSign())) {
                        for (Iterator iter3 = tempMergedReport.getBookingDay().iterator(); iter3.hasNext();) {
                            tempBookingDay = (BookingDay)iter3.next();
                            if (tempBookingDay.getDate().equals(date)) {
                                tempMergedReport.mergeBookingDay(tempBookingDay, date, durationHours, durationMinutes, taskdescription);
                                bookingDayAvailable = true;
                                break;
                            } else {

                            }
                        }
                        mergedReportAvailable = true;
                        break;
                    } else {
                        mergedReportAvailable = false;
                    }
                }
                if (!bookingDayAvailable) {
                    if (mergedReportAvailable) {
                        tempMergedReport.addBookingDay(date, durationHours, durationMinutes, taskdescription);
                        mergedReportList.set(mrIndex, tempMergedReport);
                    } else {
                        mergedReportList.add(new MergedReport(tempTimeReport.getSuborder().getCustomerorder(),
                                tempTimeReport.getSuborder(), taskdescription, date, durationHours, durationMinutes));
                    }
                }
            } else {
                mergedReportList.add(new MergedReport(tempTimeReport.getSuborder().getCustomerorder(), tempTimeReport
                        .getSuborder(), taskdescription, date, durationHours, durationMinutes));
            }
        }
        //        }

        for (Iterator iter = mergedReportList.iterator(); iter.hasNext();) {
            tempMergedReport = (MergedReport)iter.next();
            tempMergedReport.fillBookingDaysWithNull(dateFirst, dateLast);
            tempMergedReport.setSum();
            Collections.sort(tempMergedReport.getBookingDay());
        }

        Calendar gc = GregorianCalendar.getInstance();
        gc.setTime(dateFirst);
        int day = 0;
        DayAndWorkingHourCount tempDayAndWorkingHourCount;
        DayAndWorkingHourCount tempDayAndWorkingHourCount2;
        ArrayList<DayAndWorkingHourCount> dayHoursCount = new ArrayList<DayAndWorkingHourCount>();
        while ((gc.getTime().after(dateFirst) && gc.getTime().before(dateLast)) || gc.getTime().equals(dateFirst) || gc.getTime().equals(dateLast)) {
            day++;
            dayHoursCount.add(new DayAndWorkingHourCount(day, 0, gc.getTime()));
            gc.add(Calendar.DAY_OF_MONTH, 1);
        }
        day = 0;
        gc.setTime(dateFirst);
        Double dayHoursTarget = 0.0;
        List<Publicholiday> publicHolidayList = phDAO.getPublicHolidaysBetween(dateFirst, dateLast);
        while ((gc.getTime().after(dateFirst) && gc.getTime().before(dateLast)) || gc.getTime().equals(dateFirst) || gc.getTime().equals(dateLast)) {
            day++;

            for (Iterator iter = mergedReportList.iterator(); iter.hasNext();) {
                tempMergedReport = (MergedReport)iter.next();
                for (Iterator iter2 = tempMergedReport.getBookingDay().iterator(); iter2.hasNext();) {
                    tempBookingDay = (BookingDay)iter2.next();
                    if (tempBookingDay.getDate().equals(gc.getTime())) {
                        if (!dayHoursCount.isEmpty()) {
                            if ((gc.get(GregorianCalendar.DAY_OF_WEEK) == GregorianCalendar.SATURDAY) || (gc.get(GregorianCalendar.DAY_OF_WEEK) == GregorianCalendar.SUNDAY)) {
                                tempBookingDay.setSatSun(true);
//                                tempDayAndWorkingHourCount.setSatSun(true);
                            } else {
                                dayHoursTarget++;
                            }
                            for (Iterator iter4 = dayHoursCount.iterator(); iter4.hasNext();) {
                                tempDayAndWorkingHourCount = (DayAndWorkingHourCount)iter4.next();
                                if (tempDayAndWorkingHourCount.getDay() == day) {
                                    tempDayAndWorkingHourCount2 = new DayAndWorkingHourCount(day, ((tempBookingDay.getDurationHours() * 60)
                                            + tempBookingDay.getDurationMinutes() + (tempDayAndWorkingHourCount.getWorkingHour() * 60)) / 60, tempBookingDay.getDate());
                                    dayHoursCount.set(dayHoursCount.indexOf(tempDayAndWorkingHourCount), tempDayAndWorkingHourCount2);
                                    for (Iterator iter3 = publicHolidayList.iterator(); iter3.hasNext();) {
                                        tempPublicHoliday = (Publicholiday)iter3.next();
                                        if (tempPublicHoliday.getRefdate().equals(gc.getTime())) {
                                            tempBookingDay.setPublicHoliday(true);
                                            dayHoursCount.get(dayHoursCount.indexOf(tempDayAndWorkingHourCount2)).setPublicHoliday(true);
                                            dayHoursCount.get(dayHoursCount.indexOf(tempDayAndWorkingHourCount2)).setPublicHolidayName(tempPublicHoliday.getName());
                                        }

                                    }
                                    if ((gc.get(GregorianCalendar.DAY_OF_WEEK) == GregorianCalendar.SATURDAY) || (gc.get(GregorianCalendar.DAY_OF_WEEK) == GregorianCalendar.SUNDAY)) {
                                        dayHoursCount.get(dayHoursCount.indexOf(tempDayAndWorkingHourCount2)).setSatSun(true);
                                    }
                                    dayHoursCount.get(dayHoursCount.indexOf(tempDayAndWorkingHourCount2)).setWeekDay((String)weekDaysMap.get(gc.get(gc.DAY_OF_WEEK)));
                                }
                            }
                        } else {
                            throw new RuntimeException("This should not happen!");
                        }
                    }
                }

            }
            gc.add(Calendar.DAY_OF_MONTH, 1);
        }
        Collections.sort(mergedReportList);
        Double dayHoursSum = 0.0;
        for (Iterator iter5 = dayHoursCount.iterator(); iter5.hasNext();) {
            tempDayAndWorkingHourCount = (DayAndWorkingHourCount)iter5.next();
            dayHoursSum += tempDayAndWorkingHourCount.getWorkingHour();
        }

        dayHoursSum = (dayHoursSum + 0.05) * 10;
        int dayHoursSumTemp = dayHoursSum.intValue();
        dayHoursSum = dayHoursSumTemp / 10.0;

        int mergedReportListSize = mergedReportList.size();
        dayHoursTarget = (dayHoursTarget / mergedReportListSize * ecDAO.getEmployeeContractByEmployeeId(employeeId).getDailyWorkingTime());

        dayHoursTarget = (dayHoursTarget + 0.05) * 10;
        int dayHoursTargetTemp = dayHoursTarget.intValue();
        dayHoursTarget = dayHoursTargetTemp / 10.0;

        Double dayHoursDiff = dayHoursSum - dayHoursTarget;
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