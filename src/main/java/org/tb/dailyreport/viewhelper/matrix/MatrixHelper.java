package org.tb.dailyreport.viewhelper.matrix;

import static java.time.DayOfWeek.FRIDAY;
import static java.time.DayOfWeek.MONDAY;
import static java.time.DayOfWeek.SATURDAY;
import static java.time.DayOfWeek.SUNDAY;
import static java.time.DayOfWeek.THURSDAY;
import static java.time.DayOfWeek.TUESDAY;
import static java.time.DayOfWeek.WEDNESDAY;
import static java.time.Duration.ZERO;
import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toMap;
import static org.tb.common.GlobalConstants.MATRIX_SPECIFICDATE_ALLORDERS_ALLEMPLOYEES;
import static org.tb.common.GlobalConstants.MATRIX_SPECIFICDATE_ALLORDERS_SPECIFICEMPLOYEES;
import static org.tb.common.GlobalConstants.MATRIX_SPECIFICDATE_SPECIFICORDERS_ALLEMPLOYEES;
import static org.tb.common.GlobalConstants.MATRIX_SPECIFICDATE_SPECIFICORDERS_SPECIFICEMPLOYEES;
import static org.tb.common.GlobalConstants.MINUTES_PER_HOUR;
import static org.tb.common.util.DateTimeUtils.getDaysToDisplay;
import static org.tb.common.util.DateTimeUtils.getYearsToDisplay;
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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.tb.auth.domain.AuthorizedUser;
import org.tb.common.GlobalConstants;
import org.tb.common.util.DateUtils;
import org.tb.common.util.DurationUtils;
import org.tb.dailyreport.action.ShowMatrixForm;
import org.tb.dailyreport.domain.Publicholiday;
import org.tb.dailyreport.domain.TimereportDTO;
import org.tb.dailyreport.domain.Workingday;
import org.tb.dailyreport.service.OvertimeService;
import org.tb.dailyreport.service.PublicholidayService;
import org.tb.dailyreport.service.TimereportService;
import org.tb.dailyreport.service.WorkingdayService;
import org.tb.dailyreport.viewhelper.matrix.MatrixLine.OrderSummaryData;
import org.tb.employee.domain.Employeecontract;
import org.tb.employee.service.EmployeeService;
import org.tb.employee.service.EmployeecontractService;
import org.tb.order.domain.Customerorder;
import org.tb.order.service.CustomerorderService;
import org.tb.order.service.SuborderService;

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

    private final EmployeecontractService employeecontractService;
    private final PublicholidayService publicholidayService;
    private final CustomerorderService customerorderService;
    private final SuborderService suborderService;
    private final EmployeeService employeeService;
    private final OvertimeService overtimeService;
    private final WorkingdayService workingdayService;
    private final TimereportService timereportService;

    public Matrix createMatrix(LocalDate dateFirst, LocalDate dateLast, long employeeContractId, int method, long customerOrderId, boolean invoiceable, boolean nonInvoiceable, boolean startAndBreakTime) {
        Employeecontract employeecontract = employeeContractId != -1 ? employeecontractService.getEmployeecontractById(employeeContractId) : null;

        List<TimereportDTO> timeReportList;
        if (invoiceable || nonInvoiceable) {
            timeReportList = queryTimereports(dateFirst, dateLast, employeeContractId, method, customerOrderId);

            //filter billable orders if necessary
            filterInvoiceable(timeReportList, invoiceable, nonInvoiceable);
        } else {
            timeReportList = new ArrayList<>();
        }

        List<MatrixLine> matrixLines = new ArrayList<>();
        //filling a list with new or merged MatrixLines
        for (TimereportDTO timeReport : timeReportList) {
            String taskdescription = extendedTaskDescription(timeReport, employeecontract == null);
            //insert into list if its not empty
            insertIntoMatrixLine(matrixLines, timeReport, taskdescription);
        }

        //set all empty bookingdays to 0, calculate total for each MatrixLine and sort them
        for (MatrixLine matrixLine : matrixLines) {
            matrixLine.fillGapsWithEmptyBookingDays(dateFirst, dateLast);
            Collections.sort(matrixLine.getBookingDays());
        }

        Map<LocalDate, Publicholiday> publicHolidayMap = publicholidayService
            .getPublicHolidaysBetween(dateFirst, dateLast)
            .stream()
            .collect(toMap(Publicholiday::getRefdate, identity()));

        var dayTotals = initializeDayTotals(employeecontract, dateFirst, dateLast, publicHolidayMap, startAndBreakTime);
        var dayTotalsMap = dayTotals.stream().collect(toMap(MatrixDayTotal::getDate, identity()));


        //setting publicholidays(status and name) and weekend for bookingday in MatrixLine
        markBookingDays(matrixLines, dayTotalsMap);
        sumUpDayTotals(matrixLines, dayTotalsMap);

        //sort MatrixLines by custom- and subordersign
        Collections.sort(matrixLines);

        //calculate dayhourssum
        Duration totalWorkingTime = dayTotals.stream()
            .map(MatrixDayTotal::getWorkingTime)
            .reduce(ZERO, Duration::plus);

        Duration totalWorkingTimeTarget = null;
        Duration totalWorkingTimeDiff = null;
        Duration totalOvertimeCompensation = null;
        Duration totalWorkingTimeDiffWithCompensation = null;

        if(method == MATRIX_SPECIFICDATE_ALLORDERS_SPECIFICEMPLOYEES && employeecontract != null) {
            //calculate target working time
            totalWorkingTimeTarget = overtimeService.calculateWorkingTimeTarget(employeecontract.getId(), dateFirst, dateLast);

            // calculate overtime compensation
            totalOvertimeCompensation = overtimeService.calculateOvertimeCompensation(employeecontract.getId(), dateFirst, dateLast);

            //calculate totals
            totalWorkingTimeDiff = totalWorkingTime.minus(totalWorkingTimeTarget);
            totalWorkingTimeDiffWithCompensation = totalWorkingTimeDiff.plus(totalOvertimeCompensation);
        }

        return new Matrix(
            matrixLines,
            dayTotals,
            totalWorkingTime,
            totalWorkingTimeTarget,
            totalWorkingTimeDiff,
            totalOvertimeCompensation,
            totalWorkingTimeDiffWithCompensation
        );
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

    private List<MatrixDayTotal> initializeDayTotals(Employeecontract contract,
                                  LocalDate dateFirst,
                                  LocalDate dateLast,
                                  Map<LocalDate, Publicholiday> publicHolidayMap,
                                  boolean fillStartAndBreakTime) {
        // initialize MatrixDayTotals for each dates between dateFirst and dateLast
        List<MatrixDayTotal> dayTotals = new ArrayList<>();

        var dates = dateFirst.datesUntil(dateLast.plusDays(1)).toList(); // to include the last date too
        int day = 1;
        for(var date: dates) {
            var dayTotal = new MatrixDayTotal(date, day, ZERO, contract != null ? contract.getDailyWorkingTime() : ZERO);

            // mark weekends
            var dayOfWeek = dayTotal.getDate().getDayOfWeek();
            if (dayOfWeek == SATURDAY || dayOfWeek == SUNDAY) {
                dayTotal.setSatSun(true);
                dayTotal.setContractWorkingTime(ZERO);
            }
            dayTotal.setWeekDay(WEEK_DAYS_MAP.get(dayOfWeek));

            // mark public holidays
            if (publicHolidayMap.containsKey(date)) {
                dayTotal.setPublicHoliday(true);
                dayTotal.setPublicHolidayName(publicHolidayMap.get(date).getName());
                dayTotal.setContractWorkingTime(ZERO);
            }

            dayTotals.add(dayTotal);
            day++;
        }

        if (contract != null) {
            var employeeContractId = contract.getId();
            Map<LocalDate, Workingday> workingDays = queryWorkingDays(dateFirst, dateLast, employeeContractId);
            var invalidBreakTimes = fillStartAndBreakTime ? timereportService.validateBreakTimes(employeeContractId, dateFirst, dateLast) : Map.of();
            var invalidStartOfWorkDays = fillStartAndBreakTime ? timereportService.validateBeginOfWorkingDays(employeeContractId, dateFirst, dateLast) : Map.of();
            for(var dayTotal : dayTotals) {
                var date = dayTotal.getDate();
                var workingDay = workingDays.get(date);

                if(workingDay != null) {
                    dayTotal.setWorkingDayType(workingDay.getType());
                }

                if(fillStartAndBreakTime) {
                    if (workingDay != null) {
                        dayTotal.setBreakMinutes(workingDay.getBreakminutes() + workingDay.getBreakhours() * MINUTES_PER_HOUR);
                        dayTotal.setStartOfWorkMinute(workingDay.getStarttimeminute() + workingDay.getStarttimehour() * MINUTES_PER_HOUR);
                    }
                    boolean invalidStartOfWork = invalidStartOfWorkDays.containsKey(date);
                    boolean invalidBreakTime = invalidBreakTimes.containsKey(date);
                    dayTotal.setInvalidStartOfWork(invalidStartOfWork);
                    dayTotal.setInvalidBreakTime(invalidBreakTime);
                }
            }
        }

        return dayTotals;
    }

    private void markBookingDays(List<MatrixLine> matrixLines, Map<LocalDate, MatrixDayTotal> dayTotalsMap) {
        for (MatrixLine matrixLine : matrixLines) {
            for (BookingDay bookingDay : matrixLine.getBookingDays()) {
                var dayHourCount = dayTotalsMap.get(bookingDay.getDate());
                bookingDay.setPublicHoliday(dayHourCount.isPublicHoliday());
                bookingDay.setSatSun(dayHourCount.isSatSun());
            }
        }
    }

    private void sumUpDayTotals(List<MatrixLine> matrixLines, Map<LocalDate, MatrixDayTotal> dayTotalsMap) {
        for (MatrixLine matrixLine : matrixLines) {
            for (BookingDay bookingDay : matrixLine.getBookingDays()) {
                MatrixDayTotal matrixDayTotal = dayTotalsMap.get(bookingDay.getDate());
                matrixDayTotal.addWorkingTime(bookingDay.getDuration());
            }
        }
    }

    private void insertIntoMatrixLine(List<MatrixLine> matrixLines, TimereportDTO timeReport, String taskdescription) {
        // Update existing MatrixLine
        for (MatrixLine matrixLine : matrixLines) {
            if(matrixLine.matchesOrder(timeReport.getCustomerorderSign(), timeReport.getCompleteOrderSign())) {
                matrixLine.addTimereport(timeReport, taskdescription);
                return;
            }
        }
        // add a new MatrixLine
        var customerorderData = new OrderSummaryData(timeReport.getCustomerorderSign(), timeReport.getCustomerorderDescription());
        var suborderData = new OrderSummaryData(timeReport.getCompleteOrderSign(), timeReport.getSuborderDescription());
        matrixLines.add(
            new MatrixLine(
                timeReport.getCustomerShortname(),
                customerorderData,
                suborderData,
                taskdescription,
                timeReport.getReferenceday(),
                timeReport.getDuration()
            )
        );
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
        return workingdayService.getWorkingdaysByEmployeeContractId(employeeContractId, dateFirst, dateLast)
                .stream()
                .collect(toMap(Workingday::getRefday, workingDay -> workingDay));
    }

    private List<TimereportDTO> queryTimereports(LocalDate dateFirst, LocalDate dateLast, long employeeContractId, int method, long customerOrderId) {
        //choice of timereports by date, employeecontractid and/or customerorderid
        if (method == MATRIX_SPECIFICDATE_ALLORDERS_ALLEMPLOYEES || method == MATRIX_SPECIFICDATE_ALLORDERS_SPECIFICEMPLOYEES) {
            if (employeeContractId == -1) {
                return timereportService.getTimereportsByDates(dateFirst, dateLast);
            } else {
                return timereportService.getTimereportsByDatesAndEmployeeContractId(employeeContractId, dateFirst, dateLast);
            }
        } else if (method == MATRIX_SPECIFICDATE_SPECIFICORDERS_ALLEMPLOYEES || method == MATRIX_SPECIFICDATE_SPECIFICORDERS_SPECIFICEMPLOYEES) {
            if (employeeContractId == -1) {
                return timereportService.getTimereportsByDatesAndCustomerOrderId(dateFirst, dateLast, customerOrderId);
            } else {
                return timereportService.getTimereportsByDatesAndEmployeeContractIdAndCustomerOrderId(employeeContractId, dateFirst, dateLast, customerOrderId);
            }
        } else {
            throw new RuntimeException("this should not happen!");
        }
    }

    public Map<String, Object> refreshMatrix(ShowMatrixForm reportForm, AuthorizedUser authorizedUser) {
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

        Customerorder order = customerorderService.getCustomerorderBySign(reportForm.getOrder());
        Matrix matrix;
        long ecId = reportForm.getEmployeeContractId();
        boolean isInvoiceable = reportForm.getInvoice();
        boolean isNonInvoiceable = reportForm.getNonInvoice();
        boolean isStartAndBreakTime = reportForm.getStartAndBreakTime();
        if (ecId == -1) {
            // consider timereports for all employees
            List<Customerorder> orders = customerorderService.getAllCustomerorders();
            results.put("orders", orders);

            if (reportForm.getOrder() == null || reportForm.getOrder().equals("ALL ORDERS")) {
                // get the timereports for specific date, all employees, all
                // orders
                matrix = createMatrix(dateFirst, dateLast, ecId, MATRIX_SPECIFICDATE_ALLORDERS_ALLEMPLOYEES, -1, isInvoiceable, isNonInvoiceable, isStartAndBreakTime);
            } else {
                // get the timereports for specific date, all employees,
                // specific order
                matrix = createMatrix(dateFirst, dateLast, ecId, MATRIX_SPECIFICDATE_SPECIFICORDERS_ALLEMPLOYEES, order.getId(), isInvoiceable, isNonInvoiceable, isStartAndBreakTime);
            }

            results.put("currentEmployee", "ALL EMPLOYEES");
            results.put("currentEmployeeContract", null);
            results.put("currentEmployeeId", -1L);
            results.put("csvDownloadUrl", null);
        } else {
            // consider timereports for specific employee
            Employeecontract employeeContract = employeecontractService.getEmployeecontractById(ecId);
            if (employeeContract == null) {
                results.put(HANDLING_RESULTED_IN_ERROR_ERRORMESSAGE, "No employee contract found for employee - please call system administrator.");
                return results;
            }

            // also refresh orders/suborders to be displayed for specific
            // employee
            List<Customerorder> orders = customerorderService.getCustomerordersByEmployeeContractId(ecId);
            results.put("orders", orders);
            if (!orders.isEmpty()) {
                results.put("suborders", suborderService.getSubordersByEmployeeContractId(employeeContract.getId()));
            }

            if (reportForm.getOrder() == null || reportForm.getOrder().equals("ALL ORDERS")) {
                // get the timereports for specific date, specific employee,
                // all orders
                matrix = createMatrix(dateFirst, dateLast, ecId, MATRIX_SPECIFICDATE_ALLORDERS_SPECIFICEMPLOYEES, -1, isInvoiceable, isNonInvoiceable, isStartAndBreakTime);
            } else {
                // get the timereports for specific date, specific employee,
                // specific order
                List<Customerorder> customerOrder = customerorderService.getCustomerordersByEmployeeContractId(ecId);
                if (customerOrder.contains(order)) {
                    matrix = createMatrix(dateFirst, dateLast, ecId, MATRIX_SPECIFICDATE_SPECIFICORDERS_SPECIFICEMPLOYEES, order.getId(), isInvoiceable, isNonInvoiceable, isStartAndBreakTime);
                } else {
                    matrix = createMatrix(dateFirst, dateLast, ecId, MATRIX_SPECIFICDATE_ALLORDERS_SPECIFICEMPLOYEES, -1, isInvoiceable, isNonInvoiceable, isStartAndBreakTime);
                }
            }

            results.put("currentEmployee", employeeContract.getEmployee().getName());
            results.put("currentEmployeeContract", employeeContract);
            results.put("currentEmployeeId", employeeContract.getEmployee().getId());
            if (authorizedUser.isManager() || authorizedUser.getEmployeeId().equals(employeeContract.getEmployee().getId())) {
                results.put("csvDownloadUrl", getCsvDownloadUrl(reportForm, employeeContract.getEmployee().getSign()));
            } else {
                results.put("csvDownloadUrl", null);
            }

            // testing availability of the shown month
            boolean isInvalid = ((employeeContract.getValidUntil() != null && dateFirst.isAfter(employeeContract.getValidUntil()))
                    || (employeeContract.getValidFrom() != null) && dateLast.isBefore(employeeContract.getValidFrom()));
            results.put("invalid", isInvalid);
        }

        // refresh all relevant attributes
        String sOrder = reportForm.getOrder();
        results.put("matrixlines", matrix.getMatrixLines());
        results.put("matrixdaytotals", matrix.getMatrixDayTotals());
        results.put("totalworkingtime", matrix.getTotalWorkingTime());
        results.put("totalworkingtimestring", matrix.getTotalWorkingTimeString());
        results.put("totalworkingtimetarget", matrix.getTotalWorkingTimeTarget());
        results.put("totalworkingtimetargetstring", matrix.getTotalWorkingTimeTargetString());
        results.put("totalworkingtimediff", matrix.getTotalWorkingTimeDiff());
        results.put("totalworkingtimediffstring", matrix.getTotalWorkingTimeDiffString());
        results.put("totalovertimecompensation", matrix.getTotalOvertimeCompensation());
        results.put("totalovertimecompensationstring", matrix.getTotalOvertimeCompensationString());
        results.put("totalworkingtimediffwithcompensation", matrix.getTotalWorkingTimeDiffWithCompensation());
        results.put("totalworkingtimediffwithcompensationstring", matrix.getTotalWorkingTimeDiffWithCompensationString());
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
        reportForm.setEmployeeContractId(ec.getId());

        List<Employeecontract> employeeContracts = employeecontractService.getViewableEmployeeContractsValidAt(today());

        if (employeeContracts == null || employeeContracts.isEmpty()) {
            results.put(HANDLING_RESULTED_IN_ERROR_ERRORMESSAGE, "No employees with valid contracts found - please call system administrator.");
            return results;
        }
        results.put("employeecontracts", employeeContracts);
        results.put("days", getDaysToDisplay());
        results.put("years", getYearsToDisplay());

        boolean isInvoiceable = reportForm.getInvoice();
        boolean isNonInvoiceable = reportForm.getNonInvoice();
        boolean isStartAndBreakTime = reportForm.getStartAndBreakTime();

        Matrix matrix;
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

            matrix = createMatrix(dateFirst, dateLast, ecId, MATRIX_SPECIFICDATE_ALLORDERS_SPECIFICEMPLOYEES, reportForm.getOrderId(), isInvoiceable, isNonInvoiceable, isStartAndBreakTime);
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
                orders = customerorderService.getAllCustomerorders();
                results.put("currentEmployee", "ALL EMPLOYEES");
            } else {
                orders = customerorderService.getCustomerordersByEmployeeContractId(ec.getId());
                if (currentEmployeeId != null) {
                    results.put("currentEmployee", employeeService.getEmployeeById(currentEmployeeId).getName());
                }
            }
            results.put("orders", orders);
            results.put("currentOrder", "ALL ORDERS");
            if (!orders.isEmpty()) {
                results.put("suborders", suborderService.getSubordersByEmployeeContractId(ec.getId()));
            }

            matrix = createMatrix(dateFirst, dateLast, ecId, MATRIX_SPECIFICDATE_ALLORDERS_SPECIFICEMPLOYEES, -1, isInvoiceable, isNonInvoiceable, isStartAndBreakTime);
        }
        results.put("matrixlines", matrix.getMatrixLines());
        results.put("matrixdaytotals", matrix.getMatrixDayTotals());
        results.put("totalworkingtime", matrix.getTotalWorkingTime());
        results.put("totalworkingtimestring", matrix.getTotalWorkingTimeString());
        results.put("totalworkingtimetarget", matrix.getTotalWorkingTimeTarget());
        results.put("totalworkingtimetargetstring", matrix.getTotalWorkingTimeTargetString());
        results.put("totalworkingtimediff", matrix.getTotalWorkingTimeDiff());
        results.put("totalworkingtimediffstring", matrix.getTotalWorkingTimeDiffString());
        results.put("totalovertimecompensation", matrix.getTotalOvertimeCompensation());
        results.put("totalovertimecompensationstring", matrix.getTotalOvertimeCompensationString());
        results.put("totalworkingtimediffwithcompensation", matrix.getTotalWorkingTimeDiffWithCompensation());
        results.put("totalworkingtimediffwithcompensationstring", matrix.getTotalWorkingTimeDiffWithCompensationString());
        results.put("daysofmonth", maxDays);
        results.put("showStartAndBreakTime", reportForm.getStartAndBreakTime());
        results.put("csvDownloadUrl", getCsvDownloadUrl(reportForm, ec.getEmployee().getSign()));

        return results;
    }

    private String getCsvDownloadUrl(ShowMatrixForm reportForm, String employee) {
        var dateFirst = initStartEndDate("01", reportForm.getFromMonth(), reportForm.getFromYear(), reportForm.getFromMonth(), reportForm.getFromYear());
        var days = DateUtils.getMonthDays(dateFirst);
        return String.format("/rest/daily-working-reports/list?refDate=%s&days=%d&csv=true&login-name=%s", DateUtils.format(dateFirst), days, employee);
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
