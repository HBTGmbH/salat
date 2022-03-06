package org.tb.dailyreport;

import static org.tb.common.DateTimeViewHelper.getBreakHoursOptions;
import static org.tb.common.DateTimeViewHelper.getDaysToDisplay;
import static org.tb.common.DateTimeViewHelper.getTimeReportHoursOptions;
import static org.tb.common.DateTimeViewHelper.getHoursToDisplay;
import static org.tb.common.DateTimeViewHelper.getTimeReportMinutesOptions;
import static org.tb.common.DateTimeViewHelper.getMonthMMStringFromShortstring;
import static org.tb.common.DateTimeViewHelper.getMonthsToDisplay;
import static org.tb.common.DateTimeViewHelper.getYearsToDisplay;
import static org.tb.common.util.DateUtils.format;
import static org.tb.common.util.DateUtils.formatDayOfMonth;
import static org.tb.common.util.DateUtils.formatMonth;
import static org.tb.common.util.DateUtils.formatYear;
import static org.tb.common.util.DateUtils.today;
import static org.tb.common.util.TimeFormatUtils.timeFormatMinutes;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;
import org.apache.struts.action.ActionMessages;
import org.springframework.stereotype.Component;
import org.tb.auth.AuthorizedUser;
import org.tb.common.GlobalConstants;
import org.tb.common.exception.AuthorizationException;
import org.tb.common.exception.BusinessRuleException;
import org.tb.common.exception.ErrorCodeException;
import org.tb.common.exception.InvalidDataException;
import org.tb.common.util.DateUtils;
import org.tb.dailyreport.viewhelper.TimereportHelper;
import org.tb.employee.Employee;
import org.tb.employee.EmployeeDAO;
import org.tb.employee.EmployeeHelper;
import org.tb.employee.Employeecontract;
import org.tb.employee.EmployeecontractDAO;
import org.tb.order.Customerorder;
import org.tb.order.CustomerorderDAO;
import org.tb.order.CustomerorderHelper;
import org.tb.order.EmployeeorderDAO;
import org.tb.order.Suborder;
import org.tb.order.SuborderDAO;
import org.tb.order.SuborderHelper;

/**
 * Action class for a timereport to be shown in the daily display
 *
 * @author oda, th
 */

@Slf4j
@Component
@RequiredArgsConstructor
public class ShowDailyReportAction extends DailyReportAction<ShowDailyReportForm> {

    private final CustomerorderDAO customerorderDAO;
    private final TimereportDAO timereportDAO;
    private final EmployeecontractDAO employeecontractDAO;
    private final SuborderDAO suborderDAO;
    private final EmployeeorderDAO employeeorderDAO;
    private final WorkingdayDAO workingdayDAO;
    private final EmployeeDAO employeeDAO;
    private final SuborderHelper suborderHelper;
    private final CustomerorderHelper customerorderHelper;
    private final TimereportHelper timereportHelper;
    private final TimereportService timereportService;
    private final AuthorizedUser authorizedUser;

    /**
     * parses a string to a long value and returns its value
     * returns null if there is an error
     */
    private Long safeParse(String sValue) {
        try {
            return Long.parseLong(sValue);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * checks, if timereports may be shifted by days
     */
    private Collection<Long> checkShiftedDays(Collection<Long> ids, int days, Employeecontract loginEmployeeContract) {
        Collection<Long> errors = new ArrayList<>();
        ids.forEach(id -> {
            Timereport timereport = timereportDAO.getTimereportById(id);
            LocalDate shiftedDate = DateUtils.addDays(timereport.getReferenceday().getRefdate(), days);
            ActionMessages actionErrors = timereportHelper.validateNewDate(new ActionMessages(), shiftedDate,
                timereport, loginEmployeeContract);
            if (!actionErrors.isEmpty()) {
                errors.add(id);
            }
        });

        return errors;
    }

    /**
     * shifts timereports by days, does some checking
     *
     * @return null if successful, else a list of problematic timereports
     */
    private Collection<Long> massShiftDays(String[] sIds, String byDays, Employeecontract loginEmployeeContract) {
        int days = Integer.parseInt(byDays);

        List<Long> ids = Arrays.stream(sIds)
                .map(this::safeParse)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        Collection<Long> errors = checkShiftedDays(ids, days, loginEmployeeContract);
        if (!errors.isEmpty()) {
            return errors;
        }

        // FIXME consider shifting all time reports in a single transaction
        List<Long> problematicTimereportIds = new ArrayList<>();
        ids.forEach(id -> {
            try {
                timereportService.shiftDays(id, days, authorizedUser);
            } catch (ErrorCodeException e) {
                log.error(e.getMessage(), e);
                problematicTimereportIds.add(id);
            }
        });

        if(problematicTimereportIds.isEmpty()) {
            return null;
        }
        return problematicTimereportIds;
    }

    @Override
    public ActionForward executeAuthenticated(ActionMapping mapping, ShowDailyReportForm reportForm, HttpServletRequest request, HttpServletResponse response) {
        String task = request.getParameter("task");
        if ("massdelete".equalsIgnoreCase(task)) {
            // delete the selected ids from the database and continue as if this was a refreshTimereports task
            String sIds = request.getParameter("ids");
            List<Long> timereportIds = Arrays.stream(sIds.split(","))
                .map(this::safeParse)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

            try {
                timereportService.deleteTimereports(timereportIds, authorizedUser);
            } catch (AuthorizationException | BusinessRuleException | InvalidDataException e) {
                addToErrors(request, e.getErrorCode());
                return mapping.getInputForward();
            }

            task = "refreshTimereports";
        } else if ("massshiftdays".equalsIgnoreCase(task)) {
            // shift the selected ids by "byDays" and continue as if this was a refreshTimereports task
            String sIds = request.getParameter("ids");
            String days = request.getParameter("byDays");

            Employeecontract loginEmployeecontract = (Employeecontract) request.getSession().getAttribute("loginEmployeeContract");
            Collection<Long> errors = massShiftDays(sIds.split(","), days, loginEmployeecontract);
            if (errors != null) {
                if (!errors.isEmpty()) {
                    request.setAttribute("failedMassEditIds", errors);
                }
                return mapping.findForward("success");
            }
            task = "refreshTimereports";
        }

        request.getSession().setAttribute("vacationBudgetOverrun", false);
        Employeecontract ec = getEmployeeContractFromRequest(request);

        // check if special tasks initiated from the daily display need to be carried out...
        String sortModus = (String) request.getSession().getAttribute("timereportSortModus");
        if (sortModus == null || !(sortModus.equals("+") || sortModus.equals("-"))) {
            sortModus = "+";
            request.getSession().setAttribute("timereportSortModus", sortModus);
        }
        String sortColumn = (String) request.getSession().getAttribute("timereportSortColumn");
        if (sortColumn == null || sortColumn.trim().equals("")) {
            sortColumn = "employee";
            request.getSession().setAttribute("timereportSortColumn", sortColumn);
        }
        if (task != null) {
            if ("sort".equals(task)) {
                return doSort(mapping, request, sortModus, sortColumn);
            } else if ("saveBegin".equals(task) || "saveBreak".equals(task)) {
                return doSaveBeginOrBreak(mapping, request, reportForm, ec, task);

            } else if ("refreshTimereports".equals(task)) {
                return doRefreshTimereports(mapping, request, reportForm, ec);

            } else if ("refreshOrders".equals(task)) {
                return doRefreshOrders(mapping, request, reportForm);
            } else if ("refreshSuborders".equals(task)) {
                return doRefreshSuborders(mapping, request, reportForm);
            } else if ("print".equals(task)) {
                return doPrint(mapping, request.getSession(), reportForm);
            } else if ("back".equalsIgnoreCase(task)) {
                // just go back to main menu
                return mapping.findForward("backtomenu");
            } else {
                return mapping.findForward("success");
            }
        } else {
            //*** initialisation ***
            init(request, reportForm);
            //TODO: Hier bitte findForward zurückgeben.
            if (request.getParameter("day") != null && request.getParameter("month") != null && request.getParameter("year") != null) {
                // these parameters are only set when user clicked on day in matrix view -> redirected to showDailyReport with specific date
                String date = request.getParameter("year") + "-" + getMonthMMStringFromShortstring(request.getParameter("month")) + "-" + request.getParameter("day");
                reportForm.setStartdate(date);
            }
            //  make sure that overtimeCompensation is set in the session so that the duration-dropdown-menu will be disabled for timereports with suborder uesa00
            if (request.getSession().getAttribute("overtimeCompensation") == null
                    || !Objects.equals(request.getSession().getAttribute("overtimeCompensation"), GlobalConstants.SUBORDER_SIGN_OVERTIME_COMPENSATION)) {
                request.getSession().setAttribute("overtimeCompensation", GlobalConstants.SUBORDER_SIGN_OVERTIME_COMPENSATION);
            }
        }
        request.getSession().setAttribute("reportForm", reportForm);
        request.getSession().setAttribute("currentSuborderId", reportForm.getSuborderId());
        return mapping.findForward("success");
    }

    private ActionForward doPrint(ActionMapping mapping, HttpSession session, ShowDailyReportForm reportForm) {
        //*** task for print view ***
        //conversion and localization of day values
        Map<String, String> monthMap = new HashMap<>();
        monthMap.put("Jan", "main.timereport.select.month.jan.text");
        monthMap.put("Feb", "main.timereport.select.month.feb.text");
        monthMap.put("Mar", "main.timereport.select.month.mar.text");
        monthMap.put("Apr", "main.timereport.select.month.apr.text");
        monthMap.put("May", "main.timereport.select.month.may.text");
        monthMap.put("Jun", "main.timereport.select.month.jun.text");
        monthMap.put("Jul", "main.timereport.select.month.jul.text");
        monthMap.put("Aug", "main.timereport.select.month.aug.text");
        monthMap.put("Sep", "main.timereport.select.month.sep.text");
        monthMap.put("Oct", "main.timereport.select.month.oct.text");
        monthMap.put("Nov", "main.timereport.select.month.nov.text");
        monthMap.put("Dec", "main.timereport.select.month.dec.text");
        session.setAttribute("MonthKey", monthMap.get(reportForm.getMonth()));
        if (reportForm.getLastmonth() != null) {
            session.setAttribute("LastMonthKey", monthMap.get(reportForm.getLastmonth()));
        }
        return mapping.findForward("print");
    }

    private ActionForward doRefreshSuborders(ActionMapping mapping, HttpServletRequest request, ShowDailyReportForm reportForm) {
        //*** task for refreshing suborders ***
        // refresh suborders to be displayed in the select menu
        if (suborderHelper.refreshDailyOverviewSuborders(request, reportForm) != true) {
            return mapping.findForward("error");
        } else {
            @SuppressWarnings("unchecked")
            List<Timereport> timereports = (List<Timereport>) request.getSession().getAttribute("timereports");
            request.getSession().setAttribute("labortime", timereportHelper.calculateLaborTime(timereports));
            request.getSession().setAttribute("maxlabortime", timereportHelper.checkLaborTimeMaximum(timereports, GlobalConstants.MAX_HOURS_PER_DAY));
            request.getSession().setAttribute("reportForm", reportForm);
            return mapping.findForward("success");
        }
    }

    private ActionForward doRefreshOrders(ActionMapping mapping, HttpServletRequest request, ShowDailyReportForm reportForm) {
        //*** task for refreshing orders ***
        // refresh orders to be displayed in the select menu
        if (!customerorderHelper.refreshOrders(request, reportForm)) {
            return mapping.findForward("error");
        } else {
            @SuppressWarnings("unchecked")
            List<Timereport> timereports = (List<Timereport>) request.getSession().getAttribute("timereports");
            request.getSession().setAttribute("labortime", timereportHelper.calculateLaborTime(timereports));
            request.getSession().setAttribute("maxlabortime", timereportHelper.checkLaborTimeMaximum(timereports, GlobalConstants.MAX_HOURS_PER_DAY));
            request.getSession().setAttribute("reportForm", reportForm);
            return mapping.findForward("success");
        }
    }

    private ActionForward doRefreshTimereports(ActionMapping mapping, HttpServletRequest request, ShowDailyReportForm reportForm, Employeecontract ec) {
        //*** task for refreshing timereports table
        // set start and end dates
        String view = reportForm.getView();
        if (GlobalConstants.VIEW_MONTHLY.equals(view)) {
            // monthly view -> create date and synchronize with end-/lastdate-fields
            reportForm.setStartdate(reportForm.getYear() + "-" + getMonthMMStringFromShortstring(reportForm.getMonth()) + "-" + reportForm.getDay());
            reportForm.setLastday(reportForm.getDay());
            reportForm.setLastmonth(reportForm.getMonth());
            reportForm.setLastyear(reportForm.getYear());
            reportForm.setEnddate(reportForm.getStartdate());
        } else {
            LocalDate startdate;
            if (reportForm.getStartdate() != null) {
                startdate = DateUtils.parseOrDefault(reportForm.getStartdate(), today());
            } else {
                startdate = today();
            }

            LocalDate enddate;
            if (reportForm.getEnddate() != null) {
                try {
                    enddate = DateUtils.parseOrDefault(reportForm.getEnddate(), today());
                } catch (DateTimeParseException e) {
                    enddate = startdate;
                }
            } else {
                enddate = startdate;
            }
            // change startdate or enddate by buttons
            if (request.getParameter("change") != null) {
                try {
                    int change = Integer.parseInt(request.getParameter("change"));
                    if ("start".equals(request.getParameter("date"))) {
                        startdate = changeDate(startdate, change);
                    } else if ("end".equals(request.getParameter("date"))) {
                        enddate = changeDate(enddate, change);
                    }
                } catch (NumberFormatException e) {
                    return mapping.findForward("error");
                }
            }
            // no monthly view -> parse startdate and set day/month/year-fields
            String day = formatDayOfMonth(startdate);
            String month = formatMonth(startdate);
            reportForm.setDay(day);
            reportForm.setMonth(month);
            reportForm.setYear(formatYear(startdate));
            reportForm.setStartdate(format(startdate));
            if (view == null || GlobalConstants.VIEW_DAILY.equals(view)) {
                // daily view -> synchronize enddate and fields with startdate
                reportForm.setLastday(reportForm.getDay());
                reportForm.setLastmonth(reportForm.getMonth());
                reportForm.setLastyear(reportForm.getYear());
                reportForm.setEnddate(reportForm.getStartdate());
            } else if (GlobalConstants.VIEW_CUSTOM.equals(view)) {
                if (!enddate.isBefore(startdate)) {
                    // custom view -> parse enddate and set lastday/-month/-year-fields
                    day = formatDayOfMonth(enddate);
                    month = formatMonth(enddate);
                    reportForm.setLastday(day);
                    reportForm.setLastmonth(month);
                    reportForm.setLastyear("" + enddate.getYear());
                    reportForm.setEnddate(format(enddate));
                } else {
                    // custom view -> parse startdate and set lastday/-month/-year-fields
                    // failsafe if enddate is before startdate
                    reportForm.setLastday(reportForm.getDay());
                    reportForm.setLastmonth(reportForm.getMonth());
                    reportForm.setLastyear(reportForm.getYear());
                    reportForm.setEnddate(reportForm.getEnddate());
                }
            }
        }

        /* avoid refresh ? */
        if (reportForm.getAvoidRefresh()) {
            // get all necessary info from form
            long employeeContractId = reportForm.getEmployeeContractId();
            String orderSign = reportForm.getOrder();
            /* set session attributes */
            List<Customerorder> orders;
            if (employeeContractId == 0 || employeeContractId == -1) {
                orders = customerorderDAO.getCustomerorders();
                request.getSession().setAttribute("currentEmployeeContract", null);
            } else {
                orders = customerorderDAO.getCustomerordersByEmployeeContractId(employeeContractId);
                request.getSession().setAttribute("currentEmployeeContract", employeecontractDAO.getEmployeeContractById(employeeContractId));
            }
            List<Suborder> suborders = new LinkedList<>();
            Customerorder customerorder = customerorderDAO.getCustomerorderBySign(orderSign);
            if (orders.contains(customerorder)) {
                suborders = customerorder.getSuborders();
            } else if (!orders.isEmpty()) {
                suborders = orders.get(0).getSuborders();
            }

            // if <code>reportForm.showOnlyValid == true</code>, remove all invalid suborders
            if (reportForm.getShowOnlyValid()) {
                Iterator<Suborder> iter = suborders.iterator();
                while (iter.hasNext()) {
                    if (!iter.next().getCurrentlyValid()) {
                        iter.remove();
                    }
                }
            }

            request.getSession().setAttribute("orders", orders);
            request.getSession().setAttribute("suborders", suborders);
            request.getSession().setAttribute("currentOrder", orderSign);
            request.getSession().setAttribute("suborderFilerId", reportForm.getSuborderId());
            request.getSession().setAttribute("view", reportForm.getView());
            request.getSession().setAttribute("currentDay", reportForm.getDay());
            request.getSession().setAttribute("currentMonth", reportForm.getMonth());
            request.getSession().setAttribute("currentYear", reportForm.getYear());
            request.getSession().setAttribute("lastDay", reportForm.getLastday());
            request.getSession().setAttribute("lastMonth", reportForm.getLastmonth());
            request.getSession().setAttribute("lastYear", reportForm.getLastyear());
            request.getSession().setAttribute("timereports", new LinkedList<Timereport>());
            return mapping.findForward("success");
        } else {
            // refresh list of timereports to be displayed
            boolean refreshSuccessful = refreshTimereports(
                    request,
                    reportForm,
                    customerorderDAO,
                    timereportDAO,
                    employeecontractDAO,
                    suborderDAO,
                    employeeorderDAO
            );
            if (refreshSuccessful) {
                @SuppressWarnings("unchecked")
                List<Timereport> timereports = (List<Timereport>) request.getSession().getAttribute("timereports");

                //check if only project based training should be shown
                if (reportForm.getShowTraining()) {
                    for (Iterator<Timereport> iterator = timereports.iterator(); iterator.hasNext(); ) {
                        Timereport c = iterator.next();
                        if (!c.getTraining()) {
                            iterator.remove();
                        }
                    }
                }
                //check if overtime should be computed until enddate (not today)
                if (reportForm.getShowOvertimeUntil()) {
                    if (!Objects.equals(ec.getId(), reportForm.getEmployeeContractId())) {
                        ec = employeecontractDAO.getEmployeeContractById(reportForm.getEmployeeContractId());
                    }
                    LocalDate date = DateUtils.parse(reportForm.getEnddate(), e -> {
                        throw new RuntimeException(e);
                    });
                    if (GlobalConstants.VIEW_MONTHLY.equals(reportForm.getView())) {
                        date = DateUtils.getEndOfMonth(date);
                    }
                    request.setAttribute("showOvertimeUntil", reportForm.getShowOvertimeUntil());

                    long otStaticMinutes = ec.getOvertimeStatic().toMinutes();
                    LocalDate dynamicDate = DateUtils.addDays(ec.getReportAcceptanceDate(), 1);
                    long overtimeDynamic = timereportHelper.calculateOvertime(dynamicDate, date, ec, true);
                    long overtime = otStaticMinutes + overtimeDynamic;

                    boolean overtimeUntilIsNeg = overtime < 0;
                    request.getSession().setAttribute("overtimeUntilIsNeg", overtimeUntilIsNeg);
                    request.getSession().setAttribute("enddate", DateUtils.format(date));
                    String overtimeString = timeFormatMinutes(overtime);
                    request.getSession().setAttribute("overtimeUntil", overtimeString);
                }

                request.getSession().setAttribute("labortime", timereportHelper.calculateLaborTime(timereports));
                request.getSession().setAttribute("maxlabortime", timereportHelper.checkLaborTimeMaximum(timereports, GlobalConstants.MAX_HOURS_PER_DAY));

                if (reportForm.getEmployeeContractId() == -1) {
                    request.getSession().setAttribute("currentEmployeeId", -1);
                    request.getSession().setAttribute("currentEmployee", GlobalConstants.ALL_EMPLOYEES);
                    request.getSession().setAttribute("currentEmployeeContract", null);
                } else {
                    Employeecontract employeecontract = employeecontractDAO.getEmployeeContractById(reportForm.getEmployeeContractId());
                    request.getSession().setAttribute("currentEmployeeId", employeecontract.getEmployee().getId());
                    request.getSession().setAttribute("currentEmployee", employeecontract.getEmployee().getName());
                    request.getSession().setAttribute("currentEmployeeContract", employeecontract);
                }
                request.getSession().setAttribute("suborderFilerId", reportForm.getSuborderId());

                // refresh workingday
                Workingday workingday;
                try {
                    workingday = refreshWorkingday(reportForm, request, workingdayDAO);
                } catch (Exception e) {
                    return mapping.findForward("error");
                }
                request.getSession().setAttribute("quittingtime", timereportHelper.calculateQuittingTime(workingday, request, "quittingtime"));
                //calculate Working Day End
                request.getSession().setAttribute("workingDayEnds", timereportHelper.calculateQuittingTime(workingday, request, "workingDayEnds"));
                // save current filter settings in session
                request.getSession().setAttribute("currentOrder", reportForm.getOrder());
                request.getSession().setAttribute("suborderFilerId", reportForm.getSuborderId());
                request.getSession().setAttribute("view", reportForm.getView());
                request.getSession().setAttribute("currentDay", reportForm.getDay());
                request.getSession().setAttribute("currentMonth", reportForm.getMonth());
                request.getSession().setAttribute("currentYear", reportForm.getYear());
                request.getSession().setAttribute("lastDay", reportForm.getLastday());
                request.getSession().setAttribute("lastMonth", reportForm.getLastmonth());
                request.getSession().setAttribute("lastYear", reportForm.getLastyear());
                return mapping.findForward("success");
            } else {
                return mapping.findForward("error");
            }
        }
    }

    private ActionForward doSaveBeginOrBreak(ActionMapping mapping, HttpServletRequest request, ShowDailyReportForm reportForm, Employeecontract ec, String task) {
        //*** task for saving work starting time and saving work pausing time ***
        if (ec == null) {
            request.setAttribute("errorMessage", "No employee contract found for employee - please call system administrator.");
            return mapping.findForward("error");
        }
        Workingday workingday;
        try {
            workingday = getWorkingdayForReportformAndEmployeeContract(reportForm, ec, workingdayDAO, true);
        } catch (Exception e) {
            request.setAttribute("errorMessage", "More than one working day found");
            return mapping.findForward("error");
        }
        if (task.equals("saveBegin")) {
            workingday.setStarttimehour(reportForm.getSelectedWorkHourBegin());
            workingday.setStarttimeminute(reportForm.getSelectedWorkMinuteBegin());
        } else if (task.equals("saveBreak")) {
            workingday.setBreakhours(reportForm.getSelectedBreakHour());
            workingday.setBreakminutes(reportForm.getSelectedBreakMinute());
        } else {
            // unreachable code
            assert false;
        }
        workingdayDAO.save(workingday);
        //show break time, quitting time and working day ends on the showdailyreport.jsp
        request.getSession().setAttribute("visibleworkingday", true);
        request.getSession().setAttribute("quittingtime", timereportHelper.calculateQuittingTime(workingday, request, "quittingtime"));
        //calculate Working Day End
        request.getSession().setAttribute("workingDayEnds", timereportHelper.calculateQuittingTime(workingday, request, "workingDayEnds"));
        request.getSession().setAttribute("reportForm", reportForm);
        return mapping.findForward("success");
    }

    private ActionForward doSort(ActionMapping mapping, HttpServletRequest request, String sortModus, String sortColumn) {
        //*** task for sorting the timereports table ***

        HttpSession session = request.getSession();

        @SuppressWarnings("unchecked")
        List<Timereport> timereports = (List<Timereport>) request.getSession().getAttribute("timereports");
        String column = request.getParameter("column");
        Comparator<Timereport> comparator = TimereportByEmployeeAscComparator.INSTANCE;
        if ("employee".equals(column)) {
            if (sortColumn.equalsIgnoreCase(column) && sortModus.equals("+")) {
                comparator = TimereportByEmployeeDescComparator.INSTANCE;
                session.setAttribute("timereportSortModus", "-");
            } else {
                comparator = TimereportByEmployeeAscComparator.INSTANCE;
                session.setAttribute("timereportSortModus", "+");
                session.setAttribute("timereportSortColumn", column);
            }
        } else if ("refday".equals(column)) {
            if (sortColumn.equalsIgnoreCase(column) && sortModus.equals("+")) {
                comparator = TimereportByRefdayDescComparator.INSTANCE;
                session.setAttribute("timereportSortModus", "-");
            } else {
                comparator = TimereportByRefdayAscComparator.INSTANCE;
                session.setAttribute("timereportSortModus", "+");
                session.setAttribute("timereportSortColumn", column);
            }
        } else if ("order".equals(column)) {
            if (sortColumn.equalsIgnoreCase(column) && sortModus.equals("+")) {
                comparator = TimereportByOrderDescComparator.INSTANCE;
                session.setAttribute("timereportSortModus", "-");
            } else {
                comparator = TimereportByOrderAscComparator.INSTANCE;
                session.setAttribute("timereportSortModus", "+");
                session.setAttribute("timereportSortColumn", column);
            }
        }
        Collections.sort(timereports, comparator);
        session.setAttribute("timereports", timereports);
        session.setAttribute("timereportComparator", comparator);
        return mapping.findForward("success");
    }

    private LocalDate changeDate(LocalDate date, int change) {
        if (change != 0) {
            date = DateUtils.addDays(date, change);
        } else {
            date = today();
        }
        return date;
    }

    /**
     * Called if no special task is given, called from menu eg. Prepares everything to show timereports of
     * logged-in user.
     */
    private String init(HttpServletRequest request, ShowDailyReportForm reportForm) {
        String forward = "success";
        Employee loginEmployee = (Employee) request.getSession().getAttribute("loginEmployee");
        Employeecontract ec = new EmployeeHelper().setCurrentEmployee(loginEmployee, request, employeeDAO, employeecontractDAO);
        if (ec == null) {
            request.setAttribute("errorMessage", "No employee contract found for employee - please call system administrator.");
            forward = "error";
            return forward;
        }
        List<Employeecontract> employeecontracts = employeecontractDAO.getVisibleEmployeeContractsForAuthorizedUser();
        if (employeecontracts == null || employeecontracts.isEmpty()) {
            request.setAttribute("errorMessage", "No employees with valid contracts found - please call system administrator.");
            forward = "error";
            return forward;
        }

        reportForm.setView(GlobalConstants.VIEW_DAILY);
        reportForm.setShowOnlyValid(true);
        request.getSession().setAttribute("view", GlobalConstants.VIEW_DAILY);
        request.getSession().setAttribute("employeecontracts", employeecontracts);
        request.getSession().setAttribute("years", getYearsToDisplay());
        request.getSession().setAttribute("days", getDaysToDisplay());
        request.getSession().setAttribute("months", getMonthsToDisplay());
        request.getSession().setAttribute("hours", getHoursToDisplay());
        request.getSession().setAttribute("breakhours", getBreakHoursOptions());
        request.getSession().setAttribute("breakminutes", getTimeReportMinutesOptions(false));
        request.getSession().setAttribute("hoursDuration", getTimeReportHoursOptions());
        request.getSession().setAttribute("minutes", getTimeReportMinutesOptions(false));
        if (reportForm.getMonth() != null) {
            // call from list select change
            request.getSession().setAttribute("currentDay", reportForm.getDay());
            request.getSession().setAttribute("currentMonth", reportForm.getMonth());
            request.getSession().setAttribute("currentYear", reportForm.getYear());
            String dateString = reportForm.getYear() + "-" + getMonthMMStringFromShortstring(reportForm.getMonth()) + "-" + reportForm.getDay();
            LocalDate date = DateUtils.parseOrNull(dateString);
            Long currentEmployeeId = (Long) request.getSession().getAttribute("currentEmployeeId");
            if (currentEmployeeId == null || currentEmployeeId == 0) {
                currentEmployeeId = loginEmployee.getId();
                request.getSession().setAttribute("currentEmployeeId", currentEmployeeId);
            }
            List<Timereport> timereports;
            if (currentEmployeeId == -1) {
                // all employees
                timereports = timereportDAO.getTimereportsByDate(date);
            } else {
                timereports = timereportDAO.getTimereportsByDateAndEmployeeContractId(ec.getId(), date);
            }
            String laborTimeString = timereportHelper.calculateLaborTime(timereports);
            request.getSession().setAttribute("labortime", laborTimeString);
            request.getSession().setAttribute("maxlabortime", timereportHelper.checkLaborTimeMaximum(timereports, GlobalConstants.MAX_HOURS_PER_DAY));
            // refresh workingday
            Workingday workingday;
            try {
                workingday = refreshWorkingday(reportForm, request, workingdayDAO);
            } catch (Exception e) {
                forward = "error";
                return forward;
            }

            if (workingday != null) {
                request.getSession().setAttribute("quittingtime", timereportHelper.calculateQuittingTime(workingday, request, "quittingtime"));
                // calculate Working Day End
                request.getSession().setAttribute("workingDayEnds", timereportHelper.calculateQuittingTime(workingday, request, "workingDayEnds"));
            }
            if (request.getSession().getAttribute("timereportComparator") != null) {
                @SuppressWarnings("unchecked")
                Comparator<Timereport> comparator = (Comparator<Timereport>) request.getSession().getAttribute("timereportComparator");
                Collections.sort(timereports, comparator);
            }
            request.getSession().setAttribute("timereports", timereports);
        } else {
            LocalDate refDate = DateUtils.today();
            Workingday workingday = workingdayDAO.getWorkingdayByDateAndEmployeeContractId(refDate, ec.getId());
            if (workingday != null) {
                // show break time, quitting time and working day ends on
                // the showdailyreport.jsp
                request.getSession().setAttribute("visibleworkingday", true);
                reportForm.setSelectedWorkHourBegin(workingday.getStarttimehour());
                reportForm.setSelectedWorkMinuteBegin(workingday.getStarttimeminute());
                reportForm.setSelectedBreakHour(workingday.getBreakhours());
                reportForm.setSelectedBreakMinute(workingday.getBreakminutes());
            } else {
                // don't show break time, quitting time and working day ends
                // on the showdailyreport.jsp
                request.getSession().setAttribute("visibleworkingday", false);
                reportForm.setSelectedWorkHourBegin(0);
                reportForm.setSelectedWorkMinuteBegin(0);
                reportForm.setSelectedBreakHour(0);
                reportForm.setSelectedBreakMinute(0);
            }

            // call from main menu: set current month, year, timereports,
            // orders, suborders...
            LocalDate dt = today();
            // get day string (e.g., '31') from java.time.LocalDate
            String dayString = formatDayOfMonth(dt);
            // get month string (e.g., 'Jan') from java.time.LocalDate
            String monthString = formatMonth(dt);
            // get year string (e.g., '2006') from java.time.LocalDate
            String yearString = formatYear(dt);
            request.getSession().setAttribute("currentDay", dayString);
            request.getSession().setAttribute("currentMonth", monthString);
            request.getSession().setAttribute("currentYear", yearString);
            request.getSession().setAttribute("lastDay", dayString);
            request.getSession().setAttribute("lastMonth", monthString);
            request.getSession().setAttribute("lastYear", yearString);

            // set in form
            reportForm.setDay(dayString);
            reportForm.setMonth(monthString);
            reportForm.setYear(yearString);

            String dateString = yearString + "-" + getMonthMMStringFromShortstring(monthString) + "-" + dayString;
            reportForm.setStartdate(dateString);
            request.getSession().setAttribute("startdate", dateString);
            reportForm.setEnddate(dateString);
            request.getSession().setAttribute("enddate", dateString);
            LocalDate date = DateUtils.parseOrNull(dateString);

            Long employeeId = (Long) request.getSession().getAttribute("currentEmployeeId");
            List<Timereport> timereports;
            if (employeeId != null && employeeId == -1) {
                timereports = timereportDAO.getTimereportsByDate(date);
            } else {
                timereports = timereportDAO.getTimereportsByDateAndEmployeeContractId(ec.getId(), date);
            }
            String laborTimeString = timereportHelper.calculateLaborTime(timereports);
            request.getSession().setAttribute("labortime", laborTimeString);
            request.getSession().setAttribute("maxlabortime", timereportHelper.checkLaborTimeMaximum(timereports, GlobalConstants.MAX_HOURS_PER_DAY));
            if (request.getSession().getAttribute("timereportComparator") != null) {
                @SuppressWarnings("unchecked")
                Comparator<Timereport> comparator = (Comparator<Timereport>) request.getSession().getAttribute("timereportComparator");
                timereports.sort(comparator);
            }
            request.getSession().setAttribute("timereports", timereports);
            request.getSession().setAttribute("quittingtime", timereportHelper.calculateQuittingTime(workingday, request, "quittingtime"));
            // calculate Working Day End
            request.getSession().setAttribute("workingDayEnds", timereportHelper.calculateQuittingTime(workingday, request, "workingDayEnds"));
            // orders
            List<Customerorder> orders;
            if (employeeId != null && employeeId == -1) {
                orders = customerorderDAO.getCustomerorders();
            } else {
                orders = customerorderDAO.getCustomerordersByEmployeeContractId(ec.getId());
            }
            request.getSession().setAttribute("orders", orders);
            if (!orders.isEmpty()) {
                request.getSession().setAttribute("suborders", suborderDAO.getSubordersByEmployeeContractId(ec.getId()));
            }
        }
        // vacation and overtime balance
        refreshVacationAndOvertime(request, ec);

        // set current order = all orders
        request.getSession().setAttribute("currentOrder", "ALL ORDERS");
        request.getSession().setAttribute("currentOrderId", -1L);
        return forward;
    }

    @Override
    protected boolean isAllowedForRestrictedUsers() {
        return true;
    }
}
