package org.tb.dailyreport.action;

import static org.tb.common.DateTimeViewHelper.getBreakHoursOptions;
import static org.tb.common.DateTimeViewHelper.getDaysToDisplay;
import static org.tb.common.DateTimeViewHelper.getHoursToDisplay;
import static org.tb.common.DateTimeViewHelper.getMonthMMStringFromShortstring;
import static org.tb.common.DateTimeViewHelper.getMonthsToDisplay;
import static org.tb.common.DateTimeViewHelper.getShortstringFromMonthMM;
import static org.tb.common.DateTimeViewHelper.getTimeReportHoursOptions;
import static org.tb.common.DateTimeViewHelper.getTimeReportMinutesOptions;
import static org.tb.common.DateTimeViewHelper.getYearsToDisplay;
import static org.tb.common.util.DateUtils.format;
import static org.tb.common.util.DateUtils.formatDayOfMonth;
import static org.tb.common.util.DateUtils.formatMonth;
import static org.tb.common.util.DateUtils.formatYear;
import static org.tb.common.util.DateUtils.getDateAsStringArray;
import static org.tb.common.util.DateUtils.getDateFormStrings;
import static org.tb.common.util.DateUtils.getYearString;
import static org.tb.common.util.DateUtils.today;
import static org.tb.common.util.TimeFormatUtils.timeFormatMinutes;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;
import org.apache.struts.action.ActionMessages;
import org.mapstruct.factory.Mappers;
import org.springframework.stereotype.Component;
import org.tb.auth.AuthorizedUser;
import org.tb.common.GlobalConstants;
import org.tb.common.exception.AuthorizationException;
import org.tb.common.exception.BusinessRuleException;
import org.tb.common.exception.ErrorCodeException;
import org.tb.common.exception.InvalidDataException;
import org.tb.common.util.DateUtils;
import org.tb.common.util.DurationUtils;
import org.tb.dailyreport.domain.TimereportDTO;
import org.tb.dailyreport.domain.Workingday;
import org.tb.dailyreport.domain.Workingday.WorkingDayType;
import org.tb.dailyreport.domain.comparator.TimereportByEmployeeAscComparator;
import org.tb.dailyreport.domain.comparator.TimereportByEmployeeDescComparator;
import org.tb.dailyreport.domain.comparator.TimereportByOrderAscComparator;
import org.tb.dailyreport.domain.comparator.TimereportByOrderDescComparator;
import org.tb.dailyreport.domain.comparator.TimereportByRefdayAscComparator;
import org.tb.dailyreport.domain.comparator.TimereportByRefdayDescComparator;
import org.tb.dailyreport.persistence.TimereportDAO;
import org.tb.dailyreport.persistence.WorkingdayDAO;
import org.tb.dailyreport.service.TimereportService;
import org.tb.dailyreport.service.WorkingdayService;
import org.tb.dailyreport.viewhelper.TimereportHelper;
import org.tb.employee.domain.Employee;
import org.tb.employee.domain.Employeecontract;
import org.tb.employee.persistence.EmployeeDAO;
import org.tb.employee.persistence.EmployeecontractDAO;
import org.tb.employee.service.OvertimeService;
import org.tb.employee.viewhelper.EmployeeViewHelper;
import org.tb.favorites.domain.Favorite;
import org.tb.favorites.rest.FavoriteDto;
import org.tb.favorites.rest.FavoriteDtoMapper;
import org.tb.favorites.service.FavoriteService;
import org.tb.order.domain.Customerorder;
import org.tb.order.domain.Suborder;
import org.tb.order.persistence.CustomerorderDAO;
import org.tb.order.persistence.EmployeeorderDAO;
import org.tb.order.persistence.SuborderDAO;
import org.tb.order.viewhelper.CustomerorderHelper;
import org.tb.order.viewhelper.SuborderHelper;

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
    private final FavoriteService favoriteService;
    private final WorkingdayDAO workingdayDAO;
    private final WorkingdayService workingdayService;
    private final EmployeeDAO employeeDAO;
    private final SuborderHelper suborderHelper;
    private final CustomerorderHelper customerorderHelper;
    private final TimereportHelper timereportHelper;
    private final TimereportService timereportService;
    private final AuthorizedUser authorizedUser;
    private final OvertimeService overtimeService;
    private final FavoriteDtoMapper favoriteDtoMapper = Mappers.getMapper(FavoriteDtoMapper.class);

    @Override
    public ActionForward executeAuthenticated(ActionMapping mapping, ShowDailyReportForm reportForm, HttpServletRequest request, HttpServletResponse response) {
        boolean doRefreshEmployeeSummaryData = false;
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
        } else if ("toggleShowAllMinutes".equalsIgnoreCase(task)) {
            request.getSession().setAttribute("minutes", getTimeReportMinutesOptions(reportForm.isShowAllMinutes()));
            request.getSession().setAttribute("breakminutes", getTimeReportMinutesOptions(reportForm.isShowAllMinutes()));
            return mapping.getInputForward();
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
        } else if("switchEmployee".equalsIgnoreCase(task)) {
            doRefreshEmployeeSummaryData = true;
            task = "refreshTimereports";
        }

        request.getSession().setAttribute("vacationBudgetOverrun", false);
        Employeecontract ec = getEmployeeContractFromRequest(request);

        updateFavorites(request, ec.getEmployee().getId());

                // check if special tasks initiated from the daily display need to be carried out...
        String sortModus = (String) request.getSession().getAttribute("timereportSortModus");
        if (sortModus == null || !(sortModus.equals("+") || sortModus.equals("-"))) {
            sortModus = "+";
            request.getSession().setAttribute("timereportSortModus", sortModus);
        }
        String sortColumn = (String) request.getSession().getAttribute("timereportSortColumn");
        if (sortColumn == null || sortColumn.trim().isEmpty()) {
            sortColumn = "employee";
            request.getSession().setAttribute("timereportSortColumn", sortColumn);
        }
        final ActionForward actionResult;
        if ("sort".equals(task)) {
            actionResult = doSort(mapping, request, sortModus, sortColumn);
        } else if ("saveBegin".equals(task) || "saveBreak".equals(task) || "saveWorkingDayType".equals(task)) {
            actionResult = doSaveWorkingDay(mapping, request, reportForm, ec, task);
        } else if ("refreshTimereports".equals(task)) {
            actionResult = doRefreshTimereports(mapping, request, reportForm, ec);
        } else if ("refreshOrders".equals(task)) {
            actionResult = doRefreshOrders(mapping, request, reportForm);
        } else if ("refreshSuborders".equals(task)) {
            actionResult = doRefreshSuborders(mapping, request, reportForm);
        } else if ("print".equals(task)) {
            actionResult = doPrint(mapping, request.getSession(), reportForm);
        } else if ("back".equalsIgnoreCase(task)) {
            // just go back to main menu
            actionResult = mapping.findForward("backtomenu");
        } else if ("addFavoriteAsReport".equalsIgnoreCase(task)) {
          // just go back to main menu
          actionResult = addFavoriteAsReport(mapping, request, reportForm, ec);
        } else if ("deleteFavorite".equalsIgnoreCase(task)) {
          // just go back to main menu
          actionResult = deleteFavorite(mapping, request, ec);
        }else if ("createFavorite".equalsIgnoreCase(task)) {
          // just go back to main menu
          actionResult = createFavorite(mapping, request, ec);
        } else if(task != null) {
            actionResult = mapping.findForward("success");
        } else {
            //*** initialisation ***
            var initForward = init(request, reportForm);
            if(initForward != null) {
                return mapping.findForward(initForward);
            }

            if (request.getParameter("day") != null && request.getParameter("month") != null && request.getParameter("year") != null) {
                // these parameters are only set when user clicked on day in matrix view -> redirected to showDailyReport with specific date
                String date = request.getParameter("year") + "-" + getMonthMMStringFromShortstring(request.getParameter("month")) + "-" + request.getParameter("day");
                reportForm.setStartdate(date);

                // sync form fields - see https://github.com/HBTGmbH/salat/issues/219
                LocalDate startdate = LocalDate.parse(date);
                String day = formatDayOfMonth(startdate);
                String month = formatMonth(startdate);
                reportForm.setDay(day);
                reportForm.setMonth(month);
                reportForm.setYear(formatYear(startdate));
                request.getSession().setAttribute("currentDay", reportForm.getDay());
                request.getSession().setAttribute("currentMonth", reportForm.getMonth());
                request.getSession().setAttribute("currentYear", reportForm.getYear());
                request.getSession().setAttribute("startdate", reportForm.getStartdate());
            }
            //  make sure that overtimeCompensation is set in the session so that the duration-dropdown-menu will be disabled for timereports with suborder uesa00
            if (request.getSession().getAttribute("overtimeCompensation") == null
                    || !Objects.equals(request.getSession().getAttribute("overtimeCompensation"), GlobalConstants.SUBORDER_SIGN_OVERTIME_COMPENSATION)) {
                request.getSession().setAttribute("overtimeCompensation", GlobalConstants.SUBORDER_SIGN_OVERTIME_COMPENSATION);
            }
            request.getSession().setAttribute("reportForm", reportForm);
            request.getSession().setAttribute("currentSuborderId", reportForm.getSuborderId());
            actionResult = mapping.findForward("success");
        }

        // check if full minutes is required - when we have minutes durations that do not match the 5 minute schema
        var timereports = (List<TimereportDTO>) request.getSession().getAttribute("timereports");
        var anyTimereportNotMatches5MinuteSchema = timereports.stream()
            .map(TimereportDTO::matches5MinuteSchema)
            .filter(matches5MinuteSchema -> matches5MinuteSchema == false)
            .findAny()
            .isPresent();
        reportForm.setShowAllMinutes(anyTimereportNotMatches5MinuteSchema);
        request.getSession().setAttribute("breakminutes", getTimeReportMinutesOptions(reportForm.isShowAllMinutes()));
        request.getSession().setAttribute("minutes", getTimeReportMinutesOptions(reportForm.isShowAllMinutes()));

        // check if vacation and overtime should be recalculated - see https://github.com/HBTGmbH/salat/issues/226
        if(doRefreshEmployeeSummaryData) {
            Employeecontract currentEmployeeContract = (Employeecontract) request.getSession().getAttribute("currentEmployeeContract");
            if(currentEmployeeContract != null) {
                refreshEmployeeSummaryData(request, currentEmployeeContract);
            }
        }

        return actionResult;
    }

    private ActionForward createFavorite(ActionMapping mapping, HttpServletRequest request, Employeecontract ec) {
      if (ec == null || ec.getId() == null) {
        throw new IllegalStateException("Employeecontract is null");
      }
        long timereportId = Long.parseLong(request.getParameter("timereportId"));
        TimereportDTO timereport = timereportDAO.getTimereportById(timereportId);
        if (timereport == null) {
          throw new IllegalArgumentException("timereport not found");
        }
        if (timereport.getEmployeecontractId() != ec.getId()) {
          throw new IllegalArgumentException("not your timereport");
        }

      Favorite favorite =Favorite.builder()
          .employeeorderId(timereport.getEmployeeorderId())
          .employeeId(timereport.getEmployeeId())
          .hours((int) timereport.getDurationhours())
          .minutes((int) timereport.getDurationminutes())
          .comment(timereport.getTaskdescription())
          .build();
      favoriteService.addFavorite(favorite);
      updateFavorites(request, ec.getEmployee().getId());
        return mapping.findForward("success");
    }

    private ActionForward deleteFavorite(ActionMapping mapping, HttpServletRequest request, Employeecontract ec) {
    long favoriteId = Long.parseLong(request.getParameter("favoriteID"));
    favoriteService.deleteFavorite(favoriteId);
      updateFavorites(request, ec.getEmployee().getId());

      return mapping.findForward("success");
  }

  private ActionForward addFavoriteAsReport(ActionMapping mapping, HttpServletRequest request,
      ShowDailyReportForm reportForm, Employeecontract ec) {
      if (ec == null) {
        throw new IllegalStateException("Employeecontract is null");
      }
      Long favoriteId = Long.parseLong(request.getParameter("favoriteID"));
      Favorite favorite = favoriteService.getFavorite(favoriteId)
          .orElseThrow(() -> new IllegalArgumentException("favoriteID not found"));

      LocalDate date = DateUtils.parse(request.getParameter("date"));

      timereportService.createTimereports(authorizedUser, ec.getId(), favorite.getEmployeeorderId(), date,
          favorite.getComment(), false,
          favorite.getHours(), favorite.getMinutes(), 1);

      return doRefreshTimereports(mapping, request, reportForm, ec);
    }

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
            TimereportDTO timereport = timereportDAO.getTimereportById(id);
            LocalDate shiftedDate = DateUtils.addDays(timereport.getReferenceday(), days);
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

    private ActionForward doPrint(ActionMapping mapping, HttpSession session, ShowDailyReportForm reportForm) {
        //*** task for print view ***
        //conversion and localization of day values
        Map<String, String> monthMap = new HashMap<>();
        monthMap.put("Jan", "main.timereport.select.month.jan.text");
        monthMap.put("01", "main.timereport.select.month.jan.text");
        monthMap.put("Feb", "main.timereport.select.month.feb.text");
        monthMap.put("02", "main.timereport.select.month.feb.text");
        monthMap.put("Mar", "main.timereport.select.month.mar.text");
        monthMap.put("03", "main.timereport.select.month.mar.text");
        monthMap.put("Apr", "main.timereport.select.month.apr.text");
        monthMap.put("04", "main.timereport.select.month.apr.text");
        monthMap.put("May", "main.timereport.select.month.may.text");
        monthMap.put("05", "main.timereport.select.month.may.text");
        monthMap.put("Jun", "main.timereport.select.month.jun.text");
        monthMap.put("06", "main.timereport.select.month.jun.text");
        monthMap.put("Jul", "main.timereport.select.month.jul.text");
        monthMap.put("07", "main.timereport.select.month.jul.text");
        monthMap.put("Aug", "main.timereport.select.month.aug.text");
        monthMap.put("08", "main.timereport.select.month.aug.text");
        monthMap.put("Sep", "main.timereport.select.month.sep.text");
        monthMap.put("09", "main.timereport.select.month.sep.text");
        monthMap.put("Oct", "main.timereport.select.month.oct.text");
        monthMap.put("10", "main.timereport.select.month.oct.text");
        monthMap.put("Nov", "main.timereport.select.month.nov.text");
        monthMap.put("11", "main.timereport.select.month.nov.text");
        monthMap.put("Dec", "main.timereport.select.month.dec.text");
        monthMap.put("12", "main.timereport.select.month.dec.text");
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
            List<TimereportDTO> timereports = (List<TimereportDTO>) request.getSession().getAttribute("timereports");
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
            List<TimereportDTO> timereports = (List<TimereportDTO>) request.getSession().getAttribute("timereports");
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
            // change startdate or enddate by buttons
            if (request.getParameter("change") != null) {
                LocalDate beginDate = getDateFormStrings(reportForm.getDay(), reportForm.getMonth(), reportForm.getYear(), true);
                int change = Integer.parseInt(request.getParameter("change"));
                if(change == 0) {
                    beginDate = today();
                } else {
                    beginDate = changeMonths(beginDate, change);
                }
                reportForm.setMonth(getShortstringFromMonthMM(beginDate.getMonthValue()));
                reportForm.setYear(getYearString(beginDate));
            }

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
                        startdate = changeDays(startdate, change);
                    } else if ("end".equals(request.getParameter("date"))) {
                        enddate = changeDays(enddate, change);
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
                suborders = orders.getFirst().getSuborders();
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
            request.getSession().setAttribute("timereports", new LinkedList<TimereportDTO>());
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
                List<TimereportDTO> timereports = (List<TimereportDTO>) request.getSession().getAttribute("timereports");

                // calculate overtime
                var  employeeContractId = reportForm.getEmployeeContractId();
                if (employeeContractId != 0 && employeeContractId != -1) {
                    overtimeService.calculateOvertime(employeeContractId, true).ifPresent(status -> {

                        var overtimeIsNegative = status.getTotal().isNegative();
                        request.getSession().setAttribute("overtimeIsNegative", overtimeIsNegative);

                        String overtimeString = DurationUtils.format(status.getTotal().getDuration());
                        request.getSession().setAttribute("overtime", overtimeString);

                        if(status.getCurrentMonth() != null) {
                            var monthlyOvertimeIsNegative = status.getCurrentMonth().isNegative();
                            request.getSession().setAttribute("monthlyOvertimeIsNegative", monthlyOvertimeIsNegative);

                            String monthlyOvertimeString = DurationUtils.format(status.getCurrentMonth().getDuration());
                            request.getSession().setAttribute("monthlyOvertime", monthlyOvertimeString);

                            request.getSession().setAttribute("overtimeMonth", DateUtils.format(status.getCurrentMonth().getBegin(), "yyyy-MM"));
                        } else {
                            request.getSession().setAttribute("monthlyOvertimeIsNegative", false);
                            request.getSession().setAttribute("monthlyOvertime", "");
                            request.getSession().setAttribute("overtimeMonth", "");
                        }

                    });
                }

                //check if only project based training should be shown
                if (reportForm.getShowTraining()) {
                    for (Iterator<TimereportDTO> iterator = timereports.iterator(); iterator.hasNext(); ) {
                        TimereportDTO c = iterator.next();
                        if (!c.isTraining()) {
                            iterator.remove();
                        }
                    }
                }
                //check if overtime should be computed until enddate (not today)
                if (reportForm.getShowOvertimeUntil()) {
                    if (!Objects.equals(ec.getId(), reportForm.getEmployeeContractId())) {
                        ec = employeecontractDAO.getEmployeeContractById(reportForm.getEmployeeContractId());
                    }
                    final LocalDate date;
                    if (GlobalConstants.VIEW_MONTHLY.equals(reportForm.getView())) {
                        date = DateUtils.getEndOfMonth(DateUtils.parse(reportForm.getEnddate()));
                    } else {
                        date = DateUtils.parse(reportForm.getEnddate());
                    }
                    request.setAttribute("showOvertimeUntil", reportForm.getShowOvertimeUntil());

                    long otStaticMinutes = ec.getOvertimeStatic().toMinutes();
                    LocalDate dynamicDate = today();
                    if(ec.getReportAcceptanceDateString() != null) {
                        dynamicDate = DateUtils.addDays(ec.getReportAcceptanceDate(), 1);
                    }
                    var overtimeDynamic = overtimeService.calculateOvertime(ec.getId(), dynamicDate, date);
                    overtimeDynamic.ifPresentOrElse(value -> {
                        var overtime = otStaticMinutes + value.toMinutes();
                        boolean overtimeUntilIsNeg = overtime < 0;
                        request.getSession().setAttribute("overtimeUntilIsNeg", overtimeUntilIsNeg);
                        request.getSession().setAttribute("enddate", DateUtils.format(date));
                        String overtimeString = timeFormatMinutes(overtime);
                        request.getSession().setAttribute("overtimeUntil", overtimeString);
                    }, () -> {
                        request.getSession().setAttribute("overtimeUntilIsNeg", false);
                        request.getSession().setAttribute("enddate", DateUtils.format(date));
                        request.getSession().setAttribute("overtimeUntil", "");
                    });
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

                    updateFavorites(request,employeecontract.getEmployee().getId());

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

    private void updateFavorites(HttpServletRequest request, Long employeeId) {
        List<FavoriteDto> favorites = favoriteDtoMapper.map(favoriteService.getFavorites(employeeId));
        request.getSession().setAttribute("favorites", favorites);
    }

    private ActionForward doSaveWorkingDay(ActionMapping mapping, HttpServletRequest request, ShowDailyReportForm reportForm, Employeecontract ec, String task) {
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
        } else if (task.equals("saveWorkingDayType")) {
            workingday.setType(reportForm.getWorkingDayTypeTyped());
        } else {
            // unreachable code
            assert false;
        }
        try {
            workingdayService.upsertWorkingday(workingday);
        } catch(BusinessRuleException e) {
            addToErrors(request, e.getErrorCode());
            return mapping.getInputForward();
        }

        //show break time, quitting time and working day ends on the showdailyreport.jsp
        request.getSession().setAttribute("visibleworkingday", workingday.getType() != WorkingDayType.NOT_WORKED);
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
        List<TimereportDTO> timereports = (List<TimereportDTO>) request.getSession().getAttribute("timereports");
        String column = request.getParameter("column");
        Comparator<TimereportDTO> comparator = TimereportByEmployeeAscComparator.INSTANCE;
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

    private LocalDate changeDays(LocalDate date, int change) {
        if (change != 0) {
            date = DateUtils.addDays(date, change);
        } else {
            date = today();
        }
        return date;
    }

    private LocalDate changeMonths(LocalDate date, int change) {
        if (change != 0) {
            date = DateUtils.addMonths(date, change);
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
        Employee loginEmployee = (Employee) request.getSession().getAttribute("loginEmployee");
        Employeecontract ec = new EmployeeViewHelper().getAndInitCurrentEmployee(request, employeeDAO, employeecontractDAO);
        if (ec == null) {
            request.setAttribute("errorMessage", "No employee contract found for employee - please call system administrator.");
            return "error";
        }
        List<Employeecontract> employeecontracts = employeecontractDAO.getViewableEmployeeContractsForAuthorizedUser(false);
        if (employeecontracts == null || employeecontracts.isEmpty()) {
            request.setAttribute("errorMessage", "No employees with valid contracts found - please call system administrator.");
            return "error";
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
        request.getSession().setAttribute("breakminutes", getTimeReportMinutesOptions(reportForm.isShowAllMinutes()));
        request.getSession().setAttribute("hoursDuration", getTimeReportHoursOptions());
        request.getSession().setAttribute("minutes", getTimeReportMinutesOptions(reportForm.isShowAllMinutes()));
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
            List<TimereportDTO> timereports;
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
                log.error("Could not refreshWorkingday.", e);
                return "error";
            }

            if (workingday != null) {
                request.getSession().setAttribute("quittingtime", timereportHelper.calculateQuittingTime(workingday, request, "quittingtime"));
                // calculate Working Day End
                request.getSession().setAttribute("workingDayEnds", timereportHelper.calculateQuittingTime(workingday, request, "workingDayEnds"));
            }
            if (request.getSession().getAttribute("timereportComparator") != null) {
                @SuppressWarnings("unchecked")
                Comparator<TimereportDTO> comparator = (Comparator<TimereportDTO>) request.getSession().getAttribute("timereportComparator");
                Collections.sort(timereports, comparator);
            }
            request.getSession().setAttribute("timereports", timereports);
        } else {
            LocalDate refDate = DateUtils.today();
            Workingday workingday = workingdayDAO.getWorkingdayByDateAndEmployeeContractId(refDate, ec.getId());
            if (workingday != null) {
                // show break time, quitting time and working day ends on
                // the showdailyreport.jsp
                request.getSession().setAttribute("visibleworkingday", workingday.getType() != WorkingDayType.NOT_WORKED);
                reportForm.setSelectedWorkHourBegin(workingday.getStarttimehour());
                reportForm.setSelectedWorkMinuteBegin(workingday.getStarttimeminute());
                reportForm.setSelectedBreakHour(workingday.getBreakhours());
                reportForm.setSelectedBreakMinute(workingday.getBreakminutes());
                reportForm.setWorkingDayTypeTyped(workingday.getType());
            } else {
                // don't show break time, quitting time and working day ends
                // on the showdailyreport.jsp
                request.getSession().setAttribute("visibleworkingday", false);
                reportForm.setSelectedWorkHourBegin(0);
                reportForm.setSelectedWorkMinuteBegin(0);
                reportForm.setSelectedBreakHour(0);
                reportForm.setSelectedBreakMinute(0);
                reportForm.setWorkingDayTypeTyped(WorkingDayType.WORKED);

            }

            // call from main menu: set current month, year, timereports,
            // orders, suborders...
            var dateStringArray = getDateAsStringArray(today());
            // get day string (e.g., '31') from java.time.LocalDate
            String dayString = dateStringArray[0];
            // get month string (e.g., 'Jan') from java.time.LocalDate
            String monthString = dateStringArray[1];
            // get year string (e.g., '2006') from java.time.LocalDate
            String yearString = dateStringArray[2];
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
            List<TimereportDTO> timereports;
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
                Comparator<TimereportDTO> comparator = (Comparator<TimereportDTO>) request.getSession().getAttribute("timereportComparator");
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
        refreshEmployeeSummaryData(request, ec);

        // set current order = all orders
        request.getSession().setAttribute("currentOrder", "ALL ORDERS");
        request.getSession().setAttribute("currentOrderId", -1L);

        return null; // nothing to forward to
    }

    @Override
    protected boolean isAllowedForRestrictedUsers() {
        return true;
    }
}
