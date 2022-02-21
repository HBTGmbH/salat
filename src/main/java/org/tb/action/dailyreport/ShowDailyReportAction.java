package org.tb.action.dailyreport;

import static org.tb.util.DateUtils.parse;
import static org.tb.util.TimeFormatUtils.timeFormatMinutes;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.GregorianCalendar;
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
import org.tb.GlobalConstants;
import org.tb.bdom.AuthorizedUser;
import org.tb.bdom.Customerorder;
import org.tb.bdom.Employee;
import org.tb.bdom.Employeecontract;
import org.tb.bdom.Suborder;
import org.tb.bdom.Timereport;
import org.tb.bdom.Workingday;
import org.tb.bdom.comparators.TimereportByEmployeeAscComparator;
import org.tb.bdom.comparators.TimereportByEmployeeDescComparator;
import org.tb.bdom.comparators.TimereportByOrderAscComparator;
import org.tb.bdom.comparators.TimereportByOrderDescComparator;
import org.tb.bdom.comparators.TimereportByRefdayAscComparator;
import org.tb.bdom.comparators.TimereportByRefdayDescComparator;
import org.tb.exception.AuthorizationException;
import org.tb.exception.BusinessRuleException;
import org.tb.exception.ErrorCodeException;
import org.tb.exception.InvalidDataException;
import org.tb.helper.CustomerorderHelper;
import org.tb.helper.EmployeeHelper;
import org.tb.helper.SuborderHelper;
import org.tb.helper.TimereportHelper;
import org.tb.persistence.CustomerorderDAO;
import org.tb.persistence.EmployeeDAO;
import org.tb.persistence.EmployeecontractDAO;
import org.tb.persistence.EmployeeorderDAO;
import org.tb.persistence.SuborderDAO;
import org.tb.persistence.TimereportDAO;
import org.tb.persistence.WorkingdayDAO;
import org.tb.service.TimereportService;
import org.tb.util.DateUtils;

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
    private Collection<Long> checkShiftedDays(Collection<Long> ids, int days, Employeecontract loginEmployeeContract, boolean authorized) {
        Collection<Long> errors = new ArrayList<>();
        ids.forEach(id -> {
            Timereport timereport = timereportDAO.getTimereportById(id);
            Date shiftedDate = DateUtils.addDays(timereport.getReferenceday().getRefdate(), days);
            ActionMessages actionErrors = timereportHelper.validateNewDate(new ActionMessages(), shiftedDate,
                timereport, loginEmployeeContract, authorized);
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
    private Collection<Long> massShiftDays(String[] sIds, String byDays, Employeecontract loginEmployeeContract, boolean authorized) {
        int days = Integer.parseInt(byDays);

        List<Long> ids = Arrays.stream(sIds)
                .map(this::safeParse)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        Collection<Long> errors = checkShiftedDays(ids, days, loginEmployeeContract, authorized);
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
            Boolean authorized = (Boolean) request.getSession().getAttribute("employeeAuthorized");
            Collection<Long> errors = massShiftDays(sIds.split(","), days, loginEmployeecontract, authorized);
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
                String date = request.getParameter("year") + "-" + DateUtils.getMonthMMStringFromShortstring(request.getParameter("month")) + "-" + request.getParameter("day");
                reportForm.setStartdate(date);
            }
            //  make sure that overtimeCompensation is set in the session so that the duration-dropdown-menu will be disabled for timereports with suborder uesa00
            if (request.getSession().getAttribute("overtimeCompensation") == null
                    || request.getSession().getAttribute("overtimeCompensation") != GlobalConstants.SUBORDER_SIGN_OVERTIME_COMPENSATION) {
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
            request.getSession().setAttribute("dailycosts", timereportHelper.calculateDailyCosts(timereports));
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
            request.getSession().setAttribute("dailycosts", timereportHelper.calculateDailyCosts(timereports));
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
            reportForm.setStartdate(reportForm.getYear() + "-" + DateUtils.getMonthMMStringFromShortstring(reportForm.getMonth()) + "-" + reportForm.getDay());
            reportForm.setLastday(reportForm.getDay());
            reportForm.setLastmonth(reportForm.getMonth());
            reportForm.setLastyear(reportForm.getYear());
            reportForm.setEnddate(reportForm.getStartdate());
        } else {
            LocalDate startdate;
            DateTimeFormatter dtf = DateTimeFormatter.ofPattern(GlobalConstants.DEFAULT_DATE_FORMAT);
            if (reportForm.getStartdate() != null) {
                try {
                    startdate = LocalDate.parse(reportForm.getStartdate(), dtf);
                } catch (DateTimeParseException e) {
                    startdate = LocalDate.now();
                }
            } else {
                startdate = LocalDate.now();
            }

            LocalDate enddate;
            if (reportForm.getEnddate() != null) {
                try {
                    enddate = LocalDate.parse(reportForm.getEnddate(), dtf);
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
            String day = (startdate.getDayOfMonth() > 9 ? "" : "0") + startdate.getDayOfMonth();
            String month = (startdate.getMonthValue() > 9 ? "" : "0") + startdate.getMonthValue();
            reportForm.setDay(day);
            reportForm.setMonth(month);
            reportForm.setYear("" + startdate.getYear());
            reportForm.setStartdate(dtf.format(startdate));
            if (view == null || GlobalConstants.VIEW_DAILY.equals(view)) {
                // daily view -> synchronize enddate and fields with startdate
                reportForm.setLastday(reportForm.getDay());
                reportForm.setLastmonth(reportForm.getMonth());
                reportForm.setLastyear(reportForm.getYear());
                reportForm.setEnddate(reportForm.getStartdate());
            } else if (GlobalConstants.VIEW_CUSTOM.equals(view)) {
                if (!enddate.isBefore(startdate)) {
                    // custom view -> parse enddate and set lastday/-month/-year-fields
                    day = (enddate.getDayOfMonth() > 9 ? "" : "0") + enddate.getDayOfMonth();
                    month = (enddate.getMonthValue() > 9 ? "" : "0") + enddate.getMonthValue();
                    reportForm.setLastday(day);
                    reportForm.setLastmonth(month);
                    reportForm.setLastyear("" + enddate.getYear());
                    reportForm.setEnddate(dtf.format(enddate));
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
                    if (ec.getId() != reportForm.getEmployeeContractId()) {
                        ec = employeecontractDAO.getEmployeeContractById(reportForm.getEmployeeContractId());
                    }
                    SimpleDateFormat simpleDateFormat = new SimpleDateFormat(GlobalConstants.DEFAULT_DATE_FORMAT);
                    Date date = new Date();
                    try {
                        date = simpleDateFormat.parse(reportForm.getEnddate());
                    } catch (ParseException e) {
                        throw new RuntimeException("this should not happen!");
                    }
                    if (GlobalConstants.VIEW_MONTHLY.equals(reportForm.getView())) {
                        GregorianCalendar gc = new GregorianCalendar();
                        gc.setTime(date);
                        int maxday = gc.getActualMaximum(Calendar.DAY_OF_MONTH);
                        gc.set(Calendar.DATE, maxday);
                        date = gc.getTime();
                    }
                    request.setAttribute("showOvertimeUntil", reportForm.getShowOvertimeUntil());
                    int overtime;
                    if (ec.getReportAcceptanceDate().before(date) && ec.getUseOvertimeOld() == false) {
                        Double overtimeStatic = ec.getOvertimeStatic();
                        int otStaticMinutes = (int) (overtimeStatic * 60);
                        Date dynamicDate = DateUtils.addDays(ec.getReportAcceptanceDate(), 1);
                        int overtimeDynamic = timereportHelper.calculateOvertime(dynamicDate, date, ec, true);
                        overtime = otStaticMinutes + overtimeDynamic;
                    } else {
                        overtime = timereportHelper.calculateOvertime(ec.getValidFrom(), date, ec, true);
                    }
                    boolean overtimeUntilIsNeg = overtime < 0;
                    request.getSession().setAttribute("overtimeUntilIsNeg", overtimeUntilIsNeg);
                    request.getSession().setAttribute("enddate", simpleDateFormat.format(date));
                    String overtimeString = timeFormatMinutes(overtime);
                    request.getSession().setAttribute("overtimeUntil", overtimeString);
                }

                request.getSession().setAttribute("labortime", timereportHelper.calculateLaborTime(timereports));
                request.getSession().setAttribute("maxlabortime", timereportHelper.checkLaborTimeMaximum(timereports, GlobalConstants.MAX_HOURS_PER_DAY));
                request.getSession().setAttribute("dailycosts", timereportHelper.calculateDailyCosts(timereports));

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
        Workingday duplicate = workingdayDAO.getWorkingdayByDateAndEmployeeContractId(workingday.getRefday(), workingday.getEmployeecontract().getId());
        if (duplicate != null) {
            workingday.setId(duplicate.getId());
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
            date = date.plusDays(change);
        } else {
            date = LocalDate.now();
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
        List<Employeecontract> employeecontracts = employeecontractDAO.getVisibleEmployeeContractsForEmployee(loginEmployee);
        if (employeecontracts == null || employeecontracts.isEmpty()) {
            request.setAttribute("errorMessage", "No employees with valid contracts found - please call system administrator.");
            forward = "error";
            return forward;
        }

        reportForm.setView(GlobalConstants.VIEW_DAILY);
        reportForm.setShowOnlyValid(true);
        request.getSession().setAttribute("view", GlobalConstants.VIEW_DAILY);
        request.getSession().setAttribute("employeecontracts", employeecontracts);
        request.getSession().setAttribute("years", DateUtils.getYearsToDisplay());
        request.getSession().setAttribute("days", DateUtils.getDaysToDisplay());
        request.getSession().setAttribute("months", DateUtils.getMonthsToDisplay());
        request.getSession().setAttribute("hours", DateUtils.getHoursToDisplay());
        request.getSession().setAttribute("breakhours", DateUtils.getCompleteHoursToDisplay());
        request.getSession().setAttribute("breakminutes", DateUtils.getMinutesToDisplay());
        request.getSession().setAttribute("hoursDuration", DateUtils.getHoursDurationToDisplay());
        request.getSession().setAttribute("minutes", DateUtils.getMinutesToDisplay());
        if (reportForm.getMonth() != null) {
            // call from list select change
            request.getSession().setAttribute("currentDay", reportForm.getDay());
            request.getSession().setAttribute("currentMonth", reportForm.getMonth());
            request.getSession().setAttribute("currentYear", reportForm.getYear());
            String dateString = reportForm.getYear() + "-" + DateUtils.getMonthMMStringFromShortstring(reportForm.getMonth()) + "-" + reportForm.getDay();
            Date date = parse(dateString, (Date)null);
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
            request.getSession().setAttribute("dailycosts", timereportHelper.calculateDailyCosts(timereports));
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
            Date refDate = DateUtils.today();
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
            Date dt = new Date();
            // get day string (e.g., '31') from java.util.Date
            String dayString = dt.toString().substring(8, 10);
            // get month string (e.g., 'Jan') from java.util.Date
            String monthString = dt.toString().substring(4, 7);
            // get year string (e.g., '2006') from java.util.Date
            int length = dt.toString().length();
            String yearString = dt.toString().substring(length - 4, length);
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

            String dateString = yearString + "-" + DateUtils.getMonthMMStringFromShortstring(monthString) + "-" + dayString;
            reportForm.setStartdate(dateString);
            request.getSession().setAttribute("startdate", dateString);
            reportForm.setEnddate(dateString);
            request.getSession().setAttribute("enddate", dateString);
            Date date = parse(dateString, (Date)null);

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
            request.getSession().setAttribute("dailycosts", timereportHelper.calculateDailyCosts(timereports));
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
        request.getSession().setAttribute("currentOrderId", -1);
        return forward;
    }

    @Override
    protected boolean isAllowedForRestrictedUsers() {
        return true;
    }
}
