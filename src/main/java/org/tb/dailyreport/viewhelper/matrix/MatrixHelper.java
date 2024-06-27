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
package org.tb.dailyreport.viewhelper.matrix;

import static java.time.DayOfWeek.FRIDAY;
import static java.time.DayOfWeek.MONDAY;
import static java.time.DayOfWeek.SATURDAY;
import static java.time.DayOfWeek.SUNDAY;
import static java.time.DayOfWeek.THURSDAY;
import static java.time.DayOfWeek.TUESDAY;
import static java.time.DayOfWeek.WEDNESDAY;
import static java.util.Collections.emptyMap;
import static java.util.stream.Collectors.toMap;
import static org.tb.common.DateTimeViewHelper.getDaysToDisplay;
import static org.tb.common.DateTimeViewHelper.getYearsToDisplay;
import static org.tb.common.GlobalConstants.MATRIX_SPECIFICDATE_ALLORDERS_ALLEMPLOYEES;
import static org.tb.common.GlobalConstants.MATRIX_SPECIFICDATE_ALLORDERS_SPECIFICEMPLOYEES;
import static org.tb.common.GlobalConstants.MATRIX_SPECIFICDATE_SPECIFICORDERS_ALLEMPLOYEES;
import static org.tb.common.GlobalConstants.MATRIX_SPECIFICDATE_SPECIFICORDERS_SPECIFICEMPLOYEES;
import static org.tb.common.util.DateUtils.formatDayOfMonth;
import static org.tb.common.util.DateUtils.formatMonth;
import static org.tb.common.util.DateUtils.formatYear;
import static org.tb.common.util.DateUtils.getDateAsStringArray;
import static org.tb.common.util.DateUtils.getDateFormStrings;
import static org.tb.common.util.DateUtils.today;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.tb.auth.AfterLogin;
import org.tb.common.GlobalConstants;
import org.tb.common.util.DateUtils;
import org.tb.common.util.DurationUtils;
import org.tb.dailyreport.action.ShowMatrixForm;
import org.tb.dailyreport.domain.Publicholiday;
import org.tb.dailyreport.domain.TimereportDTO;
import org.tb.dailyreport.domain.Workingday;
import org.tb.dailyreport.persistence.PublicholidayDAO;
import org.tb.dailyreport.persistence.TimereportDAO;
import org.tb.dailyreport.persistence.WorkingdayDAO;
import org.tb.dailyreport.viewhelper.matrix.MergedReport.OrderSummaryData;
import org.tb.employee.domain.Employeecontract;
import org.tb.employee.persistence.EmployeeDAO;
import org.tb.employee.persistence.EmployeecontractDAO;
import org.tb.employee.service.OvertimeService;
import org.tb.order.domain.Customerorder;
import org.tb.order.persistence.CustomerorderDAO;
import org.tb.order.persistence.SuborderDAO;

@Component
@Slf4j
@RequiredArgsConstructor
public class MatrixHelper {

    private static final String HANDLING_RESULTED_IN_ERROR_ERRORMESSAGE = "HANDLING_ERROR_MESSAGE";
    /**
     * conversion and localization of day values
     */
    private static final Map<String, String> MONTH_MAP = new HashMap<>();
    /**
     * conversion and localization of weekday values
     */
    private static final Map<DayOfWeek, String> WEEK_DAYS_MAP = new HashMap<>();
    /**
     * conversion and localization of day values
     */
    private static final Map<String, String> NUMBER_TO_SHORT_MONTH = new HashMap<>();

    private static final String LINE_SEPARATOR = System.lineSeparator();

    static {
        String[] SHORT_MONTH = new String[]{"Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"};
        String[] NUMBER_MONTH = new String[]{"01", "02", "03", "04", "05", "06", "07", "08", "09", "10", "11", "12"};
        for (int i = 0; i < SHORT_MONTH.length; i++) {
            String mon = SHORT_MONTH[i];
            MONTH_MAP.put(mon, "main.timereport.select.month." + mon.toLowerCase() + ".text");

            String num_mon = NUMBER_MONTH[i];
            MONTH_MAP.put(num_mon, "main.timereport.select.month." + mon.toLowerCase() + ".text");

            NUMBER_TO_SHORT_MONTH.put(num_mon, mon);
        }

        WEEK_DAYS_MAP.put(MONDAY, "main.matrixoverview.weekdays.monday.text");
        WEEK_DAYS_MAP.put(TUESDAY, "main.matrixoverview.weekdays.tuesday.text");
        WEEK_DAYS_MAP.put(WEDNESDAY, "main.matrixoverview.weekdays.wednesday.text");
        WEEK_DAYS_MAP.put(THURSDAY, "main.matrixoverview.weekdays.thursday.text");
        WEEK_DAYS_MAP.put(FRIDAY, "main.matrixoverview.weekdays.friday.text");
        WEEK_DAYS_MAP.put(SATURDAY, "main.matrixoverview.weekdays.saturday.text");
        WEEK_DAYS_MAP.put(SUNDAY, "main.matrixoverview.weekdays.sunday.text");
    }

    private final TimereportDAO timereportDAO;
    private final EmployeecontractDAO employeecontractDAO;
    private final PublicholidayDAO publicholidayDAO;
    private final CustomerorderDAO customerorderDAO;
    private final SuborderDAO suborderDAO;
    private final EmployeeDAO employeeDAO;
    private final OvertimeService overtimeService;
    private final AfterLogin afterLogin;
    private final WorkingdayDAO workingdayDAO;

    public ReportWrapper getEmployeeMatrix(LocalDate dateFirst, LocalDate dateLast, long employeeContractId, int method, long customerOrderId, boolean invoiceable, boolean nonInvoiceable, boolean startAndBreakTime) {
        Employeecontract employeecontract = employeeContractId != -1 ? employeecontractDAO.getEmployeeContractById(employeeContractId) : null;
        LocalDate validFrom = dateFirst;
        LocalDate validUntil = dateLast;
        if (employeecontract != null) {
            if (employeecontract.getValidFrom() != null && dateFirst.isBefore(employeecontract.getValidFrom()))
                validFrom = employeecontract.getValidFrom();
            if (employeecontract.getValidUntil() != null && dateLast.isAfter(employeecontract.getValidUntil()))
                validUntil = employeecontract.getValidUntil();
        }

        Map<LocalDate, Workingday> workingDays = startAndBreakTime
                ? queryWorkingDays(dateFirst, dateLast, employeeContractId)
                : emptyMap();

        List<TimereportDTO> timeReportList;
        if (invoiceable || nonInvoiceable) {
            timeReportList = queryTimereports(dateFirst, dateLast, employeeContractId, method, customerOrderId);

            //filter billable orders if necessary
            filterInvoiceable(timeReportList, invoiceable, nonInvoiceable);
        } else {
            timeReportList = new ArrayList<>();
        }

        List<MergedReport> mergedReportList = new ArrayList<>();
        //filling a list with new or merged 'mergedreports'
        for (TimereportDTO timeReport : timeReportList) {
            String taskdescription = extendedTaskDescription(timeReport, employeecontract == null);
            LocalDate date = timeReport.getReferenceday();
            long durationHours = timeReport.getDuration().toHours();
            long durationMinutes = timeReport.getDuration().toMinutesPart();

            // if timereport-suborder is overtime compensation, check if taskdescription is empty. If so, write "Überstundenausgleich" into it
            // -> needed because overtime compensation should be shown in matrix overview! (taskdescription as if-clause in jsp!)
            if (timeReport.getSuborderSign().equals(GlobalConstants.SUBORDER_SIGN_OVERTIME_COMPENSATION)) {
                if (taskdescription.isEmpty()) {
                    taskdescription = GlobalConstants.OVERTIME_COMPENSATION_TEXT;
                }
            }

            //insert into list if its not empty
            mergeTimereport(mergedReportList, timeReport, taskdescription, date, durationHours, durationMinutes);
        }

        //set all empty bookingdays to 0, calculate sum of the bookingdays for each MergedReport and sort them
        for (MergedReport mergedReport : mergedReportList) {
            mergedReport.fillBookingDaysWithNull(dateFirst, dateLast);
            mergedReport.setSum();
            Collections.sort(mergedReport.getBookingDays());
        }

        List<Publicholiday> publicHolidayList = publicholidayDAO.getPublicHolidaysBetween(dateFirst, dateLast);

        List<DayAndWorkingHourCount> dayHoursCount = new ArrayList<>();
        int workdayCount = fillDayHoursCount(dateFirst, dateLast, validFrom, validUntil, dayHoursCount, publicHolidayList, workingDays);

        //setting publicholidays(status and name) and weekend for dayandworkinghourcount and bookingday in mergedreportlist
        handlePublicHolidays(dateFirst, dateLast, mergedReportList, dayHoursCount, publicHolidayList);

        //sort mergedreportlist by custom- and subordersign
        Collections.sort(mergedReportList);

        //calculate dayhourssum
        Duration dayHoursSum = Duration.ZERO;
        for (DayAndWorkingHourCount dayAndWorkingHourCount : dayHoursCount) {
            dayHoursSum = dayHoursSum.plus(dayAndWorkingHourCount.getWorkingHour());
        }

        Duration dayHoursTarget = null;
        Duration dayHoursDiff = null;
        Duration overtimeCompensation = null;

        if(method == MATRIX_SPECIFICDATE_ALLORDERS_SPECIFICEMPLOYEES && employeecontract != null) {
            //calculate dayhourstarget
            dayHoursTarget = employeecontract.getDailyWorkingTime().multipliedBy(workdayCount);

            // calculate overtime compensation
            overtimeCompensation = overtimeService.calculateOvertimeCompensation(employeecontract.getId(), dateFirst, dateLast);

            //calculate dayhoursdiff
            dayHoursDiff = dayHoursSum.minus(dayHoursTarget).plus(overtimeCompensation);
        }

        return new ReportWrapper(mergedReportList, dayHoursCount, dayHoursSum, dayHoursTarget, dayHoursDiff, overtimeCompensation);
    }

    private String extendedTaskDescription(TimereportDTO tr, boolean withSign) {
        StringBuilder sb = new StringBuilder();
        if (withSign) {
            sb.append(tr.getEmployeeSign());
            sb.append(": ");
        }
        sb.append(tr.getTaskdescription());
        sb.append(" (");
        sb.append(DurationUtils.format(tr.getDuration()));
        sb.append(")");
        sb.append(LINE_SEPARATOR);
        return sb.toString();
    }

    private int fillDayHoursCount(LocalDate dateFirst,
                                  LocalDate dateLast,
                                  LocalDate validFrom,
                                  LocalDate validUntil,
                                  List<DayAndWorkingHourCount> dayHoursCount,
                                  List<Publicholiday> publicHolidayList,
                                  Map<LocalDate, Workingday> workingDays) {
        //fill dayhourscount list with dayandworkinghourcounts for the time between dateFirst and dateLast
        LocalDate dateLoop = dateFirst;
        int day = 0;
        while (dateLoop.isAfter(dateFirst) && dateLoop.isBefore(dateLast) || dateLoop.equals(dateFirst)
                || dateLoop.equals(dateLast)) {
            day++;
            var workingHourCount = new DayAndWorkingHourCount(day, Duration.ZERO, dateLoop);
            var workingDay = workingDays.get(dateLoop);
            if (workingDay != null) {
                workingHourCount.setBreakMinutes(workingDay.getBreakminutes() + workingDay.getBreakhours() * 60L);
                workingHourCount.setStartOfWorkMinute(workingDay.getStarttimeminute() + workingDay.getStarttimehour() * 60L);
            }
            dayHoursCount.add(workingHourCount);
            dateLoop = DateUtils.addDays(dateLoop, 1);
        }

        day = 0;
        dateLoop = dateFirst;
        int workdayCount = 0;
        while (dateLoop.isAfter(dateFirst) && dateLoop.isBefore(dateLast) || dateLoop.equals(dateFirst)
                || dateLoop.equals(dateLast)) {
            day++;
            boolean dayIsPublicHoliday = false;
            //counting weekdays for dayhourstargettime
            var dayOfWeek = dateLoop.getDayOfWeek();
            if (dayOfWeek != SATURDAY && dayOfWeek != SUNDAY) {
                for (Publicholiday publicHoliday : publicHolidayList) {
                    if (publicHoliday.getRefdate().equals(dateLoop)) {
                        dayIsPublicHoliday = true;
                        break;
                    }
                }
                if (!dayIsPublicHoliday && (
                    dateLoop.isAfter(validFrom) &&
                    dateLoop.isBefore(validUntil) ||
                    dateLoop.equals(validFrom) ||
                    dateLoop.equals(validUntil))) {
                    workdayCount++;
                }
            }
            //setting publicholidays and weekend for dayhourscount(status and name)
            for (DayAndWorkingHourCount dayAndWorkingHourCount : dayHoursCount) {
                if (dayAndWorkingHourCount.getDay() == day) {
                    for (Publicholiday publicHoliday : publicHolidayList) {
                        if (publicHoliday.getRefdate().equals(dateLoop)) {
                            dayAndWorkingHourCount.setPublicHoliday(true);
                            dayAndWorkingHourCount.setPublicHolidayName(publicHoliday.getName());
                        }
                    }
                    if (dayOfWeek == SATURDAY || dayOfWeek == SUNDAY) {
                        dayAndWorkingHourCount.setSatSun(true);
                    }
                    dayAndWorkingHourCount.setWeekDay(WEEK_DAYS_MAP.get(dayOfWeek));
                }
            }
            dateLoop = DateUtils.addDays(dateLoop, 1);
        }
        return workdayCount;
    }

    private void handlePublicHolidays(LocalDate dateFirst,
                                      LocalDate dateLast,
                                      List<MergedReport> mergedReportList,
                                      List<DayAndWorkingHourCount> dayHoursCount,
                                      List<Publicholiday> publicHolidayList) {
        LocalDate dateLoop = dateFirst;
        int day = 0;
        while (dateLoop.isAfter(dateFirst) && dateLoop.isBefore(dateLast) || dateLoop.equals(dateFirst)
                || dateLoop.equals(dateLast)) {
            day++;
            for (MergedReport mergedReport : mergedReportList) {
                for (BookingDay bookingDay : mergedReport.getBookingDays()) {
                    if (bookingDay.getDate().equals(dateLoop)) {
                        var dayOfWeek = dateLoop.getDayOfWeek();
                        if (dayOfWeek == SATURDAY || dayOfWeek == SUNDAY) {
                            bookingDay.setSatSun(true);
                        }
                        for (int i = 0; i < dayHoursCount.size(); i++) {
                            DayAndWorkingHourCount dayAndWorkingHourCount = dayHoursCount.get(i);
                            if (dayAndWorkingHourCount.getDay() == day) {
                                Duration workingHour = Duration.ZERO;
                                workingHour = workingHour
                                    .plus(bookingDay.getDuration())
                                    .plus(dayAndWorkingHourCount.getWorkingHour());
                                DayAndWorkingHourCount otherDayAndWorkingHourCount = new DayAndWorkingHourCount(
                                    day,
                                    workingHour,
                                    bookingDay.getDate()
                                );
                                otherDayAndWorkingHourCount.setPublicHoliday(dayAndWorkingHourCount.isPublicHoliday());
                                otherDayAndWorkingHourCount.setPublicHolidayName(dayAndWorkingHourCount.getPublicHolidayName());
                                otherDayAndWorkingHourCount.setSatSun(dayAndWorkingHourCount.isSatSun());
                                otherDayAndWorkingHourCount.setWeekDay(dayAndWorkingHourCount.getWeekDay());
                                otherDayAndWorkingHourCount.setStartOfWorkMinute(dayAndWorkingHourCount.getStartOfWorkMinute());
                                // TODO start and break time validation should be moved to another class
                                // TODO A start time could be invalid by itself. It depends on the start time and total work amount of the previous day.
                                otherDayAndWorkingHourCount.setInvalidStartOfWork(dayAndWorkingHourCount.getStartOfWorkMinute() == null && workingHour.isPositive());
                                otherDayAndWorkingHourCount.setBreakMinutes(dayAndWorkingHourCount.getBreakMinutes());
                                // TODO start and break time validation should be moved to another class
                                // TODO Having no break time is not invalid by itself. It depends on the total work amount.
                                otherDayAndWorkingHourCount.setInvalidBreakTime((dayAndWorkingHourCount.getBreakMinutes() == null || dayAndWorkingHourCount.getBreakMinutes() == 0L) && workingHour.isPositive());
                                dayHoursCount.set(i, otherDayAndWorkingHourCount);
                                for (Publicholiday publicHoliday : publicHolidayList) {
                                    if (publicHoliday.getRefdate().equals(dateLoop)) {
                                        bookingDay.setPublicHoliday(true);
                                    }
                                }
                            }
                        }
                    }
                }

            }
            dateLoop = DateUtils.addDays(dateLoop, 1);
        }
    }

    private void mergeTimereport(List<MergedReport> mergedReportList, TimereportDTO timeReport, String taskdescription,
                                 LocalDate date, long durationHours, long durationMinutes) {
        if (!mergedReportList.isEmpty()) {
            //search until timereport matching mergedreport; merge bookingdays in case of match
            for (int mergedReportIndex = 0; mergedReportIndex < mergedReportList.size(); mergedReportIndex++) {
                MergedReport mergedReport = mergedReportList.get(mergedReportIndex);
                if ((mergedReport.getCustomOrder().getSign() + mergedReport.getSubOrder().getSign()).equals(timeReport.getCustomerorderSign()
                        + timeReport.getSuborderSign())) {
                    for (BookingDay bookingDay : mergedReport.getBookingDays()) {
                        if (bookingDay.getDate().equals(date)) {
                            bookingDay.addBooking(durationHours, durationMinutes, taskdescription);
                            return;
                        }
                    }
                    //if bookingday is not available, add new or merge report by adding a new bookingday and substitute the mergedreportlist entrys
                    mergedReport.addBookingDay(date, durationHours, durationMinutes, taskdescription);
                    mergedReportList.set(mergedReportIndex, mergedReport);
                    return;
                }
            }
        }
        //if bookingday is not available, add new or merge report by adding a new bookingday and substitute the mergedreportlist entrys
        OrderSummaryData customerorderData = new OrderSummaryData(timeReport.getCustomerorderSign(), timeReport.getCustomerorderDescription());
        OrderSummaryData suborderData = new OrderSummaryData(timeReport.getSuborderSign(), timeReport.getSuborderDescription());
        mergedReportList.add(new MergedReport(customerorderData, suborderData, taskdescription, date, durationHours, durationMinutes));
    }

    private void filterInvoiceable(List<TimereportDTO> timeReportList, boolean invoiceable, boolean nonInvoiceable) {
        if (invoiceable && nonInvoiceable) return;

        ArrayList<TimereportDTO> tempTimeReportList = new ArrayList<>();
        for (TimereportDTO timeReport : timeReportList) {
            if ((invoiceable && timeReport.isBillable()) || (nonInvoiceable && !timeReport.isBillable())) {
                tempTimeReportList.add(timeReport);
            }
        }
        timeReportList.clear();
        timeReportList.addAll(tempTimeReportList);
    }

    private Map<LocalDate, Workingday> queryWorkingDays(LocalDate dateFirst, LocalDate dateLast, long employeeContractId) {
        return workingdayDAO.getWorkingdaysByEmployeeContractId(employeeContractId, dateFirst, dateLast)
                .stream()
                .collect(toMap(Workingday::getRefday, workingDay -> workingDay));
    }

    private List<TimereportDTO> queryTimereports(LocalDate dateFirst, LocalDate dateLast, long employeeContractId, int method, long customerOrderId) {
        //choice of timereports by date, employeecontractid and/or customerorderid
        if (method == MATRIX_SPECIFICDATE_ALLORDERS_ALLEMPLOYEES || method == MATRIX_SPECIFICDATE_ALLORDERS_SPECIFICEMPLOYEES) {
            if (employeeContractId == -1) {
                return timereportDAO.getTimereportsByDates(dateFirst, dateLast);
            } else {
                return timereportDAO.getTimereportsByDatesAndEmployeeContractId(employeeContractId, dateFirst, dateLast);
            }
        } else if (method == MATRIX_SPECIFICDATE_SPECIFICORDERS_ALLEMPLOYEES || method == MATRIX_SPECIFICDATE_SPECIFICORDERS_SPECIFICEMPLOYEES) {
            if (employeeContractId == -1) {
                return timereportDAO.getTimereportsByDatesAndCustomerOrderId(dateFirst, dateLast, customerOrderId);
            } else {
                return timereportDAO.getTimereportsByDatesAndEmployeeContractIdAndCustomerOrderId(employeeContractId, dateFirst, dateLast, customerOrderId);
            }
        } else {
            throw new RuntimeException("this should not happen!");
        }
    }

    public Map<String, Object> refreshMergedReports(ShowMatrixForm reportForm, HttpServletRequest request) {
        // selected view and selected dates
        Map<String, Object> results = new HashMap<>();
        String selectedView = reportForm.getMatrixview();
        LocalDate dateFirst;
        LocalDate dateLast;
        try {
            if (selectedView.equals(GlobalConstants.VIEW_MONTHLY)) {
                dateFirst = getDateFormStrings("1", reportForm.getFromMonth(), reportForm.getFromYear(), false);
                int maxDays = DateUtils.getMonthDays(dateFirst);
                String maxDayString = getTwoDigitStr(maxDays);
                dateLast = getDateFormStrings(maxDayString, reportForm.getFromMonth(), reportForm.getFromYear(), false);
            } else if (selectedView.equals(GlobalConstants.VIEW_CUSTOM)) {
                dateFirst = getDateFormStrings(reportForm.getFromDay(), reportForm.getFromMonth(), reportForm.getFromYear(), false);
                if (reportForm.getUntilDay() == null || reportForm.getUntilMonth() == null || reportForm.getUntilYear() == null) {
                    int maxDays = DateUtils.getMonthDays(dateFirst);
                    String maxDayString = getTwoDigitStr(maxDays);
                    reportForm.setUntilDay(maxDayString);
                    reportForm.setUntilMonth(reportForm.getFromMonth());
                    reportForm.setUntilYear(reportForm.getFromYear());
                }
                dateLast = getDateFormStrings(reportForm.getUntilDay(), reportForm.getUntilMonth(), reportForm.getUntilYear(), false);
            } else {
                throw new RuntimeException("no view type selected");
            }
        } catch (RuntimeException e) {
            throw e; // keep them going
        } catch (Exception e) {
            throw new RuntimeException("date cannot be parsed for form", e);
        }
        results.put("matrixview", selectedView);

        Customerorder order = customerorderDAO.getCustomerorderBySign(reportForm.getOrder());
        ReportWrapper reportWrapper;
        Long ecId = reportForm.getEmployeeContractId();
        boolean isInvoiceable = reportForm.getInvoice();
        boolean isNonInvoiceable = reportForm.getNonInvoice();
        boolean isStartAndBreakTime = reportForm.getStartAndBreakTime();
        if (ecId == -1) {
            // consider timereports for all employees
            List<Customerorder> orders = customerorderDAO.getCustomerorders();
            results.put("orders", orders);

            if (reportForm.getOrder() == null || reportForm.getOrder().equals("ALL ORDERS")) {
                // get the timereports for specific date, all employees, all
                // orders
                reportWrapper = getEmployeeMatrix(dateFirst, dateLast, ecId, MATRIX_SPECIFICDATE_ALLORDERS_ALLEMPLOYEES, -1, isInvoiceable, isNonInvoiceable, isStartAndBreakTime);
            } else {
                // get the timereports for specific date, all employees,
                // specific order
                reportWrapper = getEmployeeMatrix(dateFirst, dateLast, ecId, MATRIX_SPECIFICDATE_SPECIFICORDERS_ALLEMPLOYEES, order.getId(), isInvoiceable, isNonInvoiceable, isStartAndBreakTime);
            }

            results.put("currentEmployee", "ALL EMPLOYEES");
            results.put("currentEmployeeContract", null);
            results.put("currentEmployeeId", -1L);
        } else {
            // consider timereports for specific employee
            Employeecontract employeeContract = employeecontractDAO.getEmployeeContractById(ecId);
            if (employeeContract == null) {
                results.put(HANDLING_RESULTED_IN_ERROR_ERRORMESSAGE, "No employee contract found for employee - please call system administrator.");
                return results;
            }

            // also refresh orders/suborders to be displayed for specific
            // employee
            List<Customerorder> orders = customerorderDAO.getCustomerordersByEmployeeContractId(ecId);
            results.put("orders", orders);
            if (!orders.isEmpty()) {
                results.put("suborders", suborderDAO.getSubordersByEmployeeContractId(employeeContract.getId()));
            }

            if (reportForm.getOrder() == null || reportForm.getOrder().equals("ALL ORDERS")) {
                // get the timereports for specific date, specific employee,
                // all orders
                reportWrapper = getEmployeeMatrix(dateFirst, dateLast, ecId, MATRIX_SPECIFICDATE_ALLORDERS_SPECIFICEMPLOYEES, -1, isInvoiceable, isNonInvoiceable, isStartAndBreakTime);
            } else {
                // get the timereports for specific date, specific employee,
                // specific order
                List<Customerorder> customerOrder = customerorderDAO.getCustomerordersByEmployeeContractId(ecId);
                if (customerOrder.contains(order)) {
                    reportWrapper = getEmployeeMatrix(dateFirst, dateLast, ecId, MATRIX_SPECIFICDATE_SPECIFICORDERS_SPECIFICEMPLOYEES, order.getId(), isInvoiceable, isNonInvoiceable, isStartAndBreakTime);
                } else {
                    reportWrapper = getEmployeeMatrix(dateFirst, dateLast, ecId, MATRIX_SPECIFICDATE_ALLORDERS_SPECIFICEMPLOYEES, -1, isInvoiceable, isNonInvoiceable, isStartAndBreakTime);
                }
            }

            results.put("currentEmployee", employeeContract.getEmployee().getName());
            results.put("currentEmployeeContract", employeeContract);
            results.put("currentEmployeeId", employeeContract.getEmployee().getId());

            // testing availability of the shown month
            boolean isInvalid = ((employeeContract.getValidUntil() != null && dateFirst.isAfter(employeeContract.getValidUntil()))
                    || (employeeContract.getValidFrom() != null) && dateLast.isBefore(employeeContract.getValidFrom()));
            results.put("invalid", isInvalid);

            // calculate overtime and holiday
            afterLogin.handleOvertime(employeeContract, request.getSession());
        }

        // refresh all relevant attributes
        String sOrder = reportForm.getOrder();
        results.put("mergedreports", reportWrapper.getMergedReportList());
        results.put("dayhourcounts", reportWrapper.getDayAndWorkingHourCountList());
        results.put("dayhourssumstring", reportWrapper.getDayHoursSumString());
        results.put("dayhourstarget", reportWrapper.getDayHoursTarget());
        results.put("dayhourstargetstring", reportWrapper.getDayHoursTargetString());
        results.put("overtimecompensation", reportWrapper.getOvertimeCompensation());
        results.put("overtimecompensationstring", reportWrapper.getOvertimeCompensationString());
        results.put("dayhoursdiff", reportWrapper.getDayHoursDiff());
        results.put("dayhoursdiffstring", reportWrapper.getDayHoursDiffString());
        results.put("currentOrder", sOrder == null ? "ALL ORDERS" : sOrder);
        results.put("currentDay", reportForm.getFromDay());
        results.put("currentMonth", reportForm.getFromMonth());
        results.put("MonthKey", MONTH_MAP.get(reportForm.getFromMonth()));
        results.put("currentYear", reportForm.getFromYear());
        results.put("lastDay", reportForm.getUntilDay());
        results.put("lastMonth", reportForm.getUntilMonth());
        results.put("lastYear", reportForm.getUntilYear());
        results.put("daysofmonth", DateUtils.getMonthDays(dateFirst));
        results.put("showStartAndBreakTime", reportForm.getStartAndBreakTime());

        return results;
    }

    public Map<String, Object> handleNoArgs(ShowMatrixForm reportForm, Employeecontract ec, Employeecontract currentEc, Long currentEmployeeId, String currentMonth) {
        // selected view and selected dates
        Map<String, Object> results = new HashMap<>();
        // set daily view as standard
        reportForm.setMatrixview(GlobalConstants.VIEW_MONTHLY);
        results.put("matrixview", GlobalConstants.VIEW_MONTHLY);

        if (ec == null) {
            results.put(HANDLING_RESULTED_IN_ERROR_ERRORMESSAGE, "No employee contract found for employee - please call system administrator.");
            return results;
        }

        List<Employeecontract> employeeContracts = employeecontractDAO.getViewableEmployeeContractsForAuthorizedUser(false);

        if (employeeContracts == null || employeeContracts.size() <= 0) {
            results.put(HANDLING_RESULTED_IN_ERROR_ERRORMESSAGE, "No employees with valid contracts found - please call system administrator.");
            return results;
        }
        results.put("employeecontracts", employeeContracts);
        results.put("days", getDaysToDisplay());
        results.put("years", getYearsToDisplay());

        boolean isInvoiceable = reportForm.getInvoice();
        boolean isNonInvoiceable = reportForm.getNonInvoice();
        boolean isStartAndBreakTime = reportForm.getStartAndBreakTime();

        ReportWrapper reportWrapper;
        int maxDays;
        if (reportForm.getFromMonth() != null) {
            // call from list select change
            results.put("currentDay", reportForm.getFromDay());
            results.put("currentMonth", reportForm.getFromMonth());
            results.put("MonthKey", MONTH_MAP.get(reportForm.getFromMonth()));
            results.put("currentYear", reportForm.getFromYear());

            LocalDate dateFirst = initStartEndDate("01", reportForm.getFromMonth(), reportForm.getFromYear(), reportForm.getFromMonth(), reportForm.getFromYear());

            maxDays = DateUtils.getMonthDays(dateFirst);
            String maxDayString = getTwoDigitStr(maxDays);
            LocalDate dateLast = initStartEndDate(maxDayString, reportForm.getFromMonth(), reportForm.getFromYear(), reportForm.getFromMonth(), reportForm.getFromYear());

            long ecId = -1L;
            if (currentEc != null) {
                ecId = currentEc.getId();
            }

            reportWrapper = getEmployeeMatrix(dateFirst, dateLast, ecId, MATRIX_SPECIFICDATE_ALLORDERS_SPECIFICEMPLOYEES, reportForm.getOrderId(), isInvoiceable, isNonInvoiceable, isStartAndBreakTime);
        } else {

            // call from main menu: set current month, year,
            // orders, suborders...
            LocalDate dt = DateUtils.today();
            // get day string (e.g., '31') from java.time.LocalDate
            String dayString = formatDayOfMonth(dt);
            // get month string (e.g., 'Jan') from java.time.LocalDate
            String monthString = formatMonth(dt);
            // get year string (e.g., '2006') from java.time.LocalDate
            String yearString = formatYear(dt);

            if (currentMonth != null && NUMBER_TO_SHORT_MONTH.containsKey(currentMonth)) {
                currentMonth = NUMBER_TO_SHORT_MONTH.get(currentMonth);
            }

            // set Month for first call
            if (reportForm.getFromMonth() == null || reportForm.getFromMonth().trim().equalsIgnoreCase("")) {
                String month = currentMonth;
                if (month == null || month.trim().isEmpty()) {
                    LocalDate date = today();
                    String[] dateArray = getDateAsStringArray(date);
                    month = dateArray[1];
                }
                reportForm.setFromMonth(month);
            }

            String currMonth = reportForm.getFromMonth();
            results.put("currentDay", dayString);
            results.put("currentMonth", reportForm.getFromMonth());
            results.put("MonthKey", MONTH_MAP.get(reportForm.getFromMonth()));
            results.put("currentYear", yearString);

            reportForm.setFromDay("01");
            reportForm.setFromMonth(monthString);
            reportForm.setFromYear(yearString);
            reportForm.setOrderId(-1);
            // reportForm.setInvoice(false);

            results.put("lastDay", dayString);
            results.put("lastMonth", monthString);
            results.put("lastYear", yearString);

            LocalDate dateFirst = initStartEndDate("01", currMonth, yearString, monthString, yearString);

            maxDays = DateUtils.getMonthDays(dateFirst);
            String maxDayString = getTwoDigitStr(maxDays);
            LocalDate dateLast = initStartEndDate(maxDayString, currMonth, yearString, monthString, yearString);

            long ecId = ec.getId();

            // orders
            List<Customerorder> orders;
            if (currentEmployeeId != null && currentEmployeeId == -1) {
                orders = customerorderDAO.getCustomerorders();
                results.put("currentEmployee", "ALL EMPLOYEES");
            } else {
                orders = customerorderDAO.getCustomerordersByEmployeeContractId(ec.getId());
                if (currentEmployeeId != null) {
                    results.put("currentEmployee", employeeDAO.getEmployeeById(currentEmployeeId).getName());
                }
            }
            results.put("orders", orders);
            results.put("currentOrder", "ALL ORDERS");
            if (!orders.isEmpty()) {
                results.put("suborders", suborderDAO.getSubordersByEmployeeContractId(ec.getId()));
            }

            reportWrapper = getEmployeeMatrix(dateFirst, dateLast, ecId, MATRIX_SPECIFICDATE_ALLORDERS_SPECIFICEMPLOYEES, -1, isInvoiceable, isNonInvoiceable, isStartAndBreakTime);
        }
        results.put("mergedreports", reportWrapper.getMergedReportList());
        results.put("dayhourcounts", reportWrapper.getDayAndWorkingHourCountList());
        results.put("dayhourssumstring", reportWrapper.getDayHoursSumString());
        results.put("dayhourstarget", reportWrapper.getDayHoursTarget());
        results.put("dayhourstargetstring", reportWrapper.getDayHoursTargetString());
        results.put("overtimecompensation", reportWrapper.getOvertimeCompensation());
        results.put("overtimecompensationstring", reportWrapper.getOvertimeCompensationString());
        results.put("dayhoursdiff", reportWrapper.getDayHoursDiff());
        results.put("dayhoursdiffstring", reportWrapper.getDayHoursDiffString());
        results.put("daysofmonth", maxDays);
        results.put("showStartAndBreakTime", reportForm.getStartAndBreakTime());

        return results;
    }

    private LocalDate initStartEndDate(String startEndStr, String currMonth, String currYear, String monthString, String yearString) {
        if (currMonth != null) {
            return getDateFormStrings(startEndStr, currMonth, currYear, false);
        } else {
            return getDateFormStrings(startEndStr, monthString, yearString, false);
        }
    }

    public boolean isHandlingError(String key) {
        return HANDLING_RESULTED_IN_ERROR_ERRORMESSAGE.equals(key);
    }

    private String getTwoDigitStr(int i) {
        if (i < 10) {
            return "0" + i;
        }
        return Integer.toString(i);
    }
}
