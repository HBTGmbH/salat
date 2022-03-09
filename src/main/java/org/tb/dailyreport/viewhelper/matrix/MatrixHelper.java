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
import static org.tb.common.DateTimeViewHelper.getDaysToDisplay;
import static org.tb.common.DateTimeViewHelper.getYearsToDisplay;
import static org.tb.common.GlobalConstants.SUBORDER_INVOICE_YES;
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
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.tb.auth.AuthorizedUser;
import org.tb.common.GlobalConstants;
import org.tb.common.util.DateUtils;
import org.tb.dailyreport.domain.Publicholiday;
import org.tb.dailyreport.persistence.PublicholidayDAO;
import org.tb.dailyreport.action.ShowMatrixForm;
import org.tb.dailyreport.domain.Timereport;
import org.tb.dailyreport.persistence.TimereportDAO;
import org.tb.employee.domain.Employee;
import org.tb.employee.persistence.EmployeeDAO;
import org.tb.employee.domain.Employeecontract;
import org.tb.employee.persistence.EmployeecontractDAO;
import org.tb.order.Customerorder;
import org.tb.order.CustomerorderDAO;
import org.tb.order.SuborderDAO;

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

    private static final String LINE_SEPARATOR = System.getProperty("line.separator");

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
    private final AuthorizedUser authorizedUser;

    public ReportWrapper getEmployeeMatrix(LocalDate dateFirst, LocalDate dateLast, long employeeContractId, int method, long customerOrderId, boolean invoiceable, boolean nonInvoiceable) {
        Employeecontract employeecontract = employeeContractId != -1 ? employeecontractDAO.getEmployeeContractById(employeeContractId) : null;
        LocalDate validFrom = dateFirst;
        LocalDate validUntil = dateLast;
        if (employeecontract != null) {
            if (employeecontract.getValidFrom() != null && dateFirst.isBefore(employeecontract.getValidFrom()))
                validFrom = employeecontract.getValidFrom();
            if (employeecontract.getValidUntil() != null && dateLast.isAfter(employeecontract.getValidUntil()))
                validUntil = employeecontract.getValidUntil();
        }

        List<Timereport> timeReportList;
        if (invoiceable || nonInvoiceable) {
            timeReportList = queryTimereports(dateFirst, dateLast, employeeContractId, method, customerOrderId);

            //filter billable orders if necessary
            filterInvoiceable(timeReportList, invoiceable, nonInvoiceable);
        } else {
            timeReportList = new ArrayList<>();
        }

        List<MergedReport> mergedReportList = new ArrayList<>();
        //filling a list with new or merged 'mergedreports'
        for (Timereport timeReport : timeReportList) {
            String taskdescription = extendedTaskDescription(timeReport, employeecontract == null);
            LocalDate date = timeReport.getReferenceday().getRefdate();
            long durationHours = timeReport.getDurationhours();
            long durationMinutes = timeReport.getDurationminutes();

            // if timereport-suborder is overtime compensation, check if taskdescription is empty. If so, write "Überstundenausgleich" into it
            // -> needed because overtime compensation should be shown in matrix overview! (taskdescription as if-clause in jsp!)
            if (timeReport.getSuborder().getSign().equals(GlobalConstants.SUBORDER_SIGN_OVERTIME_COMPENSATION)) {
                if (taskdescription.length() == 0) {
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
        int workdayCount = fillDayHoursCount(dateFirst, dateLast, validFrom, validUntil, dayHoursCount, publicHolidayList);

        //setting publicholidays(status and name) and weekend for dayandworkinghourcount and bookingday in mergedreportlist
        handlePublicHolidays(dateFirst, dateLast, mergedReportList, dayHoursCount, publicHolidayList);

        //sort mergedreportlist by custom- and subordersign
        Collections.sort(mergedReportList);

        //calculate dayhourssum
        Duration dayHoursSum = Duration.ZERO;
        for (DayAndWorkingHourCount dayAndWorkingHourCount : dayHoursCount) {
            dayHoursSum = dayHoursSum.plus(dayAndWorkingHourCount.getWorkingHour());
        }

        Duration dayHoursTarget = Duration.ZERO;

        //calculate dayhourstarget
        if (employeeContractId == -1) {
            List<Employeecontract> employeeContractList = employeecontractDAO.getEmployeeContracts();
            Duration dailyWorkingTime = Duration.ZERO;
            for (Employeecontract employeeContract : employeeContractList) {
                dailyWorkingTime = dailyWorkingTime.plus(employeeContract.getDailyWorkingTime());
            }
            dayHoursTarget = dailyWorkingTime.multipliedBy(workdayCount);
        } else {
            dayHoursTarget = employeecontract.getDailyWorkingTime().multipliedBy(workdayCount);
        }

        //calculate dayhoursdiff
        Duration dayHoursDiff = dayHoursSum.minus(dayHoursTarget);

        return new ReportWrapper(mergedReportList, dayHoursCount, dayHoursSum, dayHoursTarget, dayHoursDiff);
    }

    private String extendedTaskDescription(Timereport tr, boolean withSign) {
        StringBuilder sb = new StringBuilder();
        if (withSign) {
            sb.append(tr.getEmployeecontract().getEmployee().getSign());
            sb.append(": ");
        }
        sb.append(tr.getTaskdescription());
        sb.append(" (");
        sb.append(tr.getDurationhours());
        sb.append(":");
        if (tr.getDurationminutes() < 10) sb.append("0");
        sb.append(tr.getDurationminutes());
        sb.append(")");
        sb.append(LINE_SEPARATOR);
        return sb.toString();
    }

    private int fillDayHoursCount(LocalDate dateFirst, LocalDate dateLast, LocalDate validFrom, LocalDate validUntil, List<DayAndWorkingHourCount> dayHoursCount, List<Publicholiday> publicHolidayList) {
        //fill dayhourscount list with dayandworkinghourcounts for the time between dateFirst and dateLast
        LocalDate dateLoop = dateFirst;
        int day = 0;
        while (dateLoop.isAfter(dateFirst) && dateLoop.isBefore(dateLast) || dateLoop.equals(dateFirst)
                || dateLoop.equals(dateLast)) {
            day++;
            dayHoursCount.add(new DayAndWorkingHourCount(day, Duration.ZERO, dateLoop));
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

    private void handlePublicHolidays(LocalDate dateFirst, LocalDate dateLast, List<MergedReport> mergedReportList, List<DayAndWorkingHourCount> dayHoursCount, List<Publicholiday> publicHolidayList) {
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
                                    .plusHours(bookingDay.getDurationHours())
                                    .plusMinutes(bookingDay.getDurationMinutes())
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

    private void mergeTimereport(List<MergedReport> mergedReportList, Timereport timeReport, String taskdescription,
                                 LocalDate date, long durationHours, long durationMinutes) {
        if (!mergedReportList.isEmpty()) {
            //search until timereport matching mergedreport; merge bookingdays in case of match
            for (int mergedReportIndex = 0; mergedReportIndex < mergedReportList.size(); mergedReportIndex++) {
                MergedReport mergedReport = mergedReportList.get(mergedReportIndex);
                if ((mergedReport.getCustomOrder().getSign() + mergedReport.getSubOrder().getSign()).equals(timeReport.getSuborder().getCustomerorder().getSign()
                        + timeReport.getSuborder().getSign())) {
                    for (BookingDay tempBookingDay : mergedReport.getBookingDays()) {
                        if (tempBookingDay.getDate().equals(date)) {
                            mergedReport.mergeBookingDay(tempBookingDay, date, durationHours, durationMinutes, taskdescription);
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
        mergedReportList.add(new MergedReport(timeReport.getSuborder().getCustomerorder(), timeReport.getSuborder(), taskdescription, date, durationHours, durationMinutes));
    }

    private void filterInvoiceable(List<Timereport> timeReportList, boolean invoiceable, boolean nonInvoiceable) {
        if (invoiceable && nonInvoiceable) return;

        ArrayList<Timereport> tempTimeReportList = new ArrayList<>();
        for (Timereport timeReport : timeReportList) {
            boolean invoice = timeReport.getSuborder().getInvoice() == SUBORDER_INVOICE_YES;
            if ((invoiceable && invoice) || (nonInvoiceable && !invoice)) {
                tempTimeReportList.add(timeReport);
            }
        }
        timeReportList.clear();
        timeReportList.addAll(tempTimeReportList);
    }

    private List<Timereport> queryTimereports(LocalDate dateFirst, LocalDate dateLast, long employeeContractId, int method, long customerOrderId) {
        //choice of timereports by date, employeecontractid and/or customerorderid
        if (method == 1 || method == 3) { // FIXME magic numbers
            if (employeeContractId == -1) {
                return timereportDAO.getTimereportsByDates(dateFirst, dateLast);
            } else {
                return timereportDAO.getTimereportsByDatesAndEmployeeContractId(employeeContractId, dateFirst, dateLast);
            }
        } else if (method == 2 || method == 4) { // FIXME magic numbers
            if (employeeContractId == -1) {
                return timereportDAO.getTimereportsByDatesAndCustomerOrderId(dateFirst, dateLast, customerOrderId);
            } else {
                return timereportDAO.getTimereportsByDatesAndEmployeeContractIdAndCustomerOrderId(employeeContractId, dateFirst, dateLast, customerOrderId);
            }
        } else {
            throw new RuntimeException("this should not happen!");
        }
    }

    public Map<String, Object> refreshMergedReports(ShowMatrixForm reportForm) {
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
        boolean isAcceptanceWarning = false;
        String acceptedBy = null;
        boolean isInvoiceable = reportForm.getInvoice();
        boolean isNonInvoiceable = reportForm.getNonInvoice();
        if (ecId == -1) {
            // consider timereports for all employees
            List<Customerorder> orders = customerorderDAO.getCustomerorders();
            results.put("orders", orders);

            if (reportForm.getOrder() == null || reportForm.getOrder().equals("ALL ORDERS")) {
                // get the timereports for specific date, all employees, all
                // orders
                reportWrapper = getEmployeeMatrix(dateFirst, dateLast, ecId, GlobalConstants.MATRIX_SPECIFICDATE_ALLORDERS_ALLEMPLOYEES, -1, isInvoiceable, isNonInvoiceable);
            } else {
                // get the timereports for specific date, all employees,
                // specific order
                reportWrapper = getEmployeeMatrix(dateFirst, dateLast, ecId, GlobalConstants.MATRIX_SPECIFICDATE_SPECIFICORDERS_ALLEMPLOYEES, order.getId(), isInvoiceable, isNonInvoiceable);
            }

            results.put("currentEmployee", "ALL EMPLOYEES");
            results.put("currentEmployeeContract", null);
            results.put("currentEmployeeId", -1L);
            List<Employeecontract> ecList = employeecontractDAO.getEmployeeContracts();
            for (Employeecontract employeeContract : ecList) {
                if (!authorizedUser.isAdmin()) {
                    isAcceptanceWarning = checkAcceptanceWarning(employeeContract, dateLast);
                    if (!isAcceptanceWarning) {
                        break;
                    }
                }
            }
            if (isAcceptanceWarning) {
                acceptedBy = "";
            }
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
            if (orders.size() > 0) {
                results.put("suborders", suborderDAO.getSubordersByEmployeeContractId(employeeContract.getId()));
            }

            if (reportForm.getOrder() == null || reportForm.getOrder().equals("ALL ORDERS")) {
                // get the timereports for specific date, specific employee,
                // all orders
                reportWrapper = getEmployeeMatrix(dateFirst, dateLast, ecId, GlobalConstants.MATRIX_SPECIFICDATE_ALLORDERS_SPECIFICEMPLOYEES, -1, isInvoiceable, isNonInvoiceable);
            } else {
                // get the timereports for specific date, specific employee,
                // specific order
                List<Customerorder> customerOrder = customerorderDAO.getCustomerordersByEmployeeContractId(ecId);
                if (customerOrder.contains(order)) {
                    reportWrapper = getEmployeeMatrix(dateFirst, dateLast, ecId, GlobalConstants.MATRIX_SPECIFICDATE_SPECIFICORDERS_SPECIFICEMPLOYEES, order.getId(), isInvoiceable, isNonInvoiceable);
                } else {
                    reportWrapper = getEmployeeMatrix(dateFirst, dateLast, ecId, GlobalConstants.MATRIX_SPECIFICDATE_ALLORDERS_SPECIFICEMPLOYEES, -1, isInvoiceable, isNonInvoiceable);
                }
            }

            results.put("currentEmployee", employeeContract.getEmployee().getName());
            results.put("currentEmployeeContract", employeeContract);
            results.put("currentEmployeeId", employeeContract.getEmployee().getId());

            // testing availability of the shown month
            boolean isInvalid = ((employeeContract.getValidUntil() != null && dateFirst.isAfter(employeeContract.getValidUntil()))
                    || (employeeContract.getValidFrom() != null) && dateLast.isBefore(employeeContract.getValidFrom()));
            results.put("invalid", isInvalid);

            isAcceptanceWarning = checkAcceptanceWarning(employeeContract, dateLast);
            if (isAcceptanceWarning) {
                Timereport timereport = timereportDAO.getLastAcceptedTimereportByDateAndEmployeeContractId(dateLast, employeeContract.getId());
                if (timereport != null) {
                    Employee employee = employeeDAO.getEmployeeBySign(timereport.getAcceptedby());
                    acceptedBy = employee.getFirstname() + " " + employee.getLastname() + " (" + employee.getStatus() + ")";
                }
            }
        }

        // refresh all relevant attributes
        String sOrder = reportForm.getOrder();
        if (acceptedBy != null) {
            results.put("acceptedby", acceptedBy);
        }
        results.put("acceptance", isAcceptanceWarning);
        results.put("mergedreports", reportWrapper.getMergedReportList());
        results.put("dayhourcounts", reportWrapper.getDayAndWorkingHourCountList());
        results.put("dayhourssumstring", reportWrapper.getDayHoursSumString());
        results.put("dayhourstargetstring", reportWrapper.getDayHoursTargetString());
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

        return results;
    }

    public Map<String, Object> handleNoArgs(ShowMatrixForm reportForm, Employeecontract ec, Employeecontract currentEc, Long currentEmployeeId, String currentMonth, Employee loginEmployee) {
        // selected view and selected dates
        Map<String, Object> results = new HashMap<>();
        // set daily view as standard
        reportForm.setMatrixview(GlobalConstants.VIEW_MONTHLY);
        results.put("matrixview", GlobalConstants.VIEW_MONTHLY);

        if (ec == null) {
            results.put(HANDLING_RESULTED_IN_ERROR_ERRORMESSAGE, "No employee contract found for employee - please call system administrator.");
            return results;
        }

        List<Employeecontract> employeeContracts = employeecontractDAO.getVisibleEmployeeContractsForAuthorizedUser();

        if (employeeContracts == null || employeeContracts.size() <= 0) {
            results.put(HANDLING_RESULTED_IN_ERROR_ERRORMESSAGE, "No employees with valid contracts found - please call system administrator.");
            return results;
        }
        results.put("employeecontracts", employeeContracts);
        results.put("days", getDaysToDisplay());
        results.put("years", getYearsToDisplay());

        boolean isInvoiceable = reportForm.getInvoice();
        boolean isNonInvoiceable = reportForm.getNonInvoice();

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
            boolean isAcceptanceWarning = false;
            if (currentEc != null) {
                ecId = currentEc.getId();
                isAcceptanceWarning = checkAcceptanceWarning(currentEc, dateLast);
            } else {
                List<Employeecontract> ecList = employeecontractDAO.getEmployeeContracts();
                for (Employeecontract employeeContract : ecList) {
                    if (!authorizedUser.isAdmin()) {
                        isAcceptanceWarning = checkAcceptanceWarning(employeeContract, dateLast);
                        if (!isAcceptanceWarning) {
                            break;
                        }
                    }
                }
            }
            results.put("acceptance", isAcceptanceWarning);
            if (isAcceptanceWarning) {
                Timereport tr = timereportDAO.getLastAcceptedTimereportByDateAndEmployeeContractId(dateLast, Objects.requireNonNull(currentEc).getId());
                Employee employee = employeeDAO.getEmployeeBySign(tr.getAcceptedby());
                results.put("acceptedby", employee.getFirstname() + " " + employee.getLastname() + " (" + employee.getStatus() + ")");
            }

            reportWrapper = getEmployeeMatrix(dateFirst, dateLast, ecId, GlobalConstants.MATRIX_SPECIFICDATE_ALLORDERS_SPECIFICEMPLOYEES, reportForm.getOrderId(), isInvoiceable, isNonInvoiceable);
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
                if (month == null || month.trim().equals("")) {
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

            long ecId;
            boolean newAcceptance = false;
            ecId = ec.getId();
            if (!ec.getAcceptanceWarningByDate(dateLast)) {
                if (ec.getReportAcceptanceDate() != null && !dateLast.isAfter(ec.getReportAcceptanceDate())) {
                    newAcceptance = true;
                    Employee employee = employeeDAO.getEmployeeBySign(
                        timereportDAO.getLastAcceptedTimereportByDateAndEmployeeContractId(dateLast, ec.getId()).getAcceptedby());
                    results.put("acceptedby", employee.getFirstname() + " " + employee.getLastname() + " (" + employee.getStatus() + ")");
                }
            }
            results.put("acceptance", newAcceptance);

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
            if (orders.size() > 0) {
                results.put("suborders", suborderDAO.getSubordersByEmployeeContractId(ec.getId()));
            }

            reportWrapper = getEmployeeMatrix(dateFirst, dateLast, ecId, GlobalConstants.MATRIX_SPECIFICDATE_ALLORDERS_SPECIFICEMPLOYEES, -1, isInvoiceable, isNonInvoiceable);
        }
        results.put("mergedreports", reportWrapper.getMergedReportList());
        results.put("dayhourcounts", reportWrapper.getDayAndWorkingHourCountList());
        results.put("dayhourssumstring", reportWrapper.getDayHoursSumString());
        results.put("dayhourstargetstring", reportWrapper.getDayHoursTargetString());
        results.put("dayhoursdiff", reportWrapper.getDayHoursDiff());
        results.put("dayhoursdiffstring", reportWrapper.getDayHoursDiffString());
        results.put("daysofmonth", maxDays);
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

    private boolean checkAcceptanceWarning(Employeecontract ec, LocalDate dateLast) {
        if (!ec.getAcceptanceWarningByDate(dateLast)) {
            LocalDate acceptanceDate = ec.getReportAcceptanceDate();
            return acceptanceDate != null && !dateLast.isAfter(acceptanceDate);
        }
        return false;
    }

    private String getTwoDigitStr(int i) {
        if (i < 10) {
            return "0" + i;
        }
        return Integer.toString(i);
    }
}
