package org.tb.dailyreport.action;

import static java.lang.Boolean.TRUE;
import static org.tb.common.DateTimeViewHelper.getBreakHoursOptions;
import static org.tb.common.DateTimeViewHelper.getDaysToDisplay;
import static org.tb.common.DateTimeViewHelper.getTimeReportHoursOptions;
import static org.tb.common.DateTimeViewHelper.getHoursToDisplay;
import static org.tb.common.DateTimeViewHelper.getTimeReportMinutesOptions;
import static org.tb.common.DateTimeViewHelper.getMonthMMStringFromShortstring;
import static org.tb.common.DateTimeViewHelper.getMonthsToDisplay;
import static org.tb.common.DateTimeViewHelper.getYearsToDisplay;
import static org.tb.common.GlobalConstants.MINUTES_PER_HOUR;
import static org.tb.common.util.DateUtils.parse;

import java.io.IOException;
import java.time.Duration;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import javax.annotation.Nonnull;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;
import org.springframework.stereotype.Component;
import org.tb.auth.AuthorizedUser;
import org.tb.common.GlobalConstants;
import org.tb.common.exception.AuthorizationException;
import org.tb.common.exception.BusinessRuleException;
import org.tb.common.exception.InvalidDataException;
import org.tb.common.util.DateUtils;
import org.tb.dailyreport.domain.Timereport;
import org.tb.dailyreport.persistence.TimereportDAO;
import org.tb.dailyreport.service.TimereportService;
import org.tb.dailyreport.domain.Workingday;
import org.tb.dailyreport.persistence.WorkingdayDAO;
import org.tb.dailyreport.viewhelper.TimereportHelper;
import org.tb.employee.domain.Employee;
import org.tb.employee.domain.Employeecontract;
import org.tb.employee.persistence.EmployeecontractDAO;
import org.tb.order.domain.Customerorder;
import org.tb.order.persistence.CustomerorderDAO;
import org.tb.order.viewhelper.CustomerorderHelper;
import org.tb.order.domain.Employeeorder;
import org.tb.order.persistence.EmployeeorderDAO;
import org.tb.order.domain.Suborder;
import org.tb.order.persistence.SuborderDAO;
import org.tb.order.viewhelper.SuborderHelper;

/**
 * Action class for a timereport to be stored permanently.
 *
 * @author oda
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StoreDailyReportAction extends DailyReportAction<AddDailyReportForm> {

    private final EmployeecontractDAO employeecontractDAO;
    private final SuborderDAO suborderDAO;
    private final CustomerorderDAO customerorderDAO;
    private final TimereportDAO timereportDAO;
    private final WorkingdayDAO workingdayDAO;
    private final EmployeeorderDAO employeeorderDAO;
    private final SuborderHelper suborderHelper;
    private final CustomerorderHelper customerorderHelper;
    private final TimereportHelper timereportHelper;
    private final TimereportService timereportService;
    private final AuthorizedUser authorizedUser;

    @Override
    public ActionForward executeAuthenticated(ActionMapping mapping, AddDailyReportForm form, HttpServletRequest request, HttpServletResponse response) throws IOException {
        boolean refreshOrders = false;
        boolean refreshSuborders = false;
        boolean refreshWorkdayAvailability = false;

        // TODO split logic into different methods
        Employeecontract employeeContract = getEmployeeContractAndSetSessionVars(request);

        // task for setting the date
        if (request.getParameter("task") != null && request.getParameter("task").equals("setDate")) {
            int howMuch = Integer.parseInt(request.getParameter("howMuch"));
            String referenceDayFormValue = form.getReferenceday();
            LocalDate calculatedReferenceDay;

            /* check if today is to be set or not, 0 indicates "set to today" */
            if(howMuch == 0) {
                calculatedReferenceDay = DateUtils.today();
            } else {
                calculatedReferenceDay = DateUtils.parseOrDefault(referenceDayFormValue, DateUtils.today());
                calculatedReferenceDay = DateUtils.addDays(calculatedReferenceDay, howMuch);
            }

            String calculatedReferenceDayFormValue = DateUtils.format(calculatedReferenceDay);
            request.getSession().setAttribute("referenceday", calculatedReferenceDayFormValue);
            form.setReferenceday(calculatedReferenceDayFormValue);
            refreshOrders = true;
            refreshSuborders = true;
            refreshWorkdayAvailability = true;
        }

        if (request.getParameter("task") != null && request.getParameter("task").equals("toggleShowAllMinutes")) {
            request.getSession().setAttribute("minutes", getTimeReportMinutesOptions(form.isShowAllMinutes()));
            return mapping.getInputForward();
        }

        if (request.getParameter("task") != null && request.getParameter("task").equals("refreshOrders")) {
            refreshOrders = true;
        }

        if (request.getParameter("task") != null && request.getParameter("task").equals("refreshSuborders")) {
            refreshSuborders = true;
        }

        if (request.getParameter("task") != null && request.getParameter("task").equals("adjustBeginTime")) {
            refreshWorkdayAvailability = true;
            Duration dailyWorkingTime = employeeContract.getDailyWorkingTime();
            Customerorder selectedOrder = customerorderDAO.getCustomerorderById(form.getOrderId());
            boolean standardOrder = customerorderHelper.isOrderStandard(selectedOrder);
            Boolean workingDayAvailable = (Boolean) request.getSession().getAttribute("workingDayIsAvailable");
            if (TRUE == workingDayAvailable) {
                LocalDate referenceDay = DateUtils.parseOrDefault(form.getReferenceday(), DateUtils.today());
                Workingday workingday = workingdayDAO.getWorkingdayByDateAndEmployeeContractId(referenceDay, employeeContract.getId());

                // set the begin time as the end time of the latest existing timereport of current employee
                // for current day. If no other reports exist so far, set standard begin time (0800).
                int[] beginTime = timereportHelper.determineBeginTimeToDisplay(employeeContract.getId(), referenceDay, workingday);
                int beginHours = beginTime[0];
                int beginMinutes = beginTime[1];
                form.setSelectedHourBegin(beginHours);
                form.setSelectedMinuteBegin(beginMinutes);

                // determine end time
                int currentHours = DateUtils.getCurrentHours();
                int currentMinutes = DateUtils.getCurrentMinutes();
                LocalDate today = DateUtils.today();
                if (standardOrder) {
                    int minutes = form.getSelectedHourBegin() * MINUTES_PER_HOUR + form.getSelectedMinuteBegin();
                    minutes += dailyWorkingTime.toMinutes();
                    int hours = minutes / MINUTES_PER_HOUR;
                    minutes = minutes % MINUTES_PER_HOUR;
                    form.setSelectedMinuteEnd(minutes);
                    form.setSelectedHourEnd(hours);
                } else if (workStartedEarlier(beginHours, beginMinutes, currentHours, currentMinutes) && referenceDay.equals(today)) {
                    form.setSelectedMinuteEnd(currentMinutes);
                    form.setSelectedHourEnd(currentHours);
                } else {
                    form.setSelectedHourEnd(beginHours);
                    form.setSelectedMinuteEnd(beginMinutes);
                }
                timereportHelper.refreshHours(form);
            } else {
                // TODO wird dieser Code je durchlaufen?
                if (standardOrder) {
                    int hours = dailyWorkingTime.toHoursPart();
                    int minutes = dailyWorkingTime.toMinutesPart();
                    form.setSelectedHourDuration(hours);
                    form.setSelectedMinuteDuration(minutes);
                }
            }
        }

        if (request.getParameter("task") != null && request.getParameter("task").equals("adjustSuborderSignChanged")) {

            // refresh suborder sign/description select menus
            suborderHelper.adjustSuborderSignChanged(request.getSession(), form);
            Suborder suborder = suborderDAO.getSuborderById(form.getSuborderSignId());
            request.getSession().setAttribute("currentSuborderSign", suborder.getSign());
            setSubOrder(suborder, request, form);
        }

        if (request.getParameter("task") != null && request.getParameter("task").equals("refreshHours")) {
            // refreshes the hours displayed after a change of duration period
            timereportHelper.refreshHours(form);
        }

        if (request.getParameter("task") != null && request.getParameter("task").equals("refreshPeriod")) {
            // refreshes the duration period after a change of begin/end times
            timereportHelper.refreshPeriod(request, form);
        }

        if (refreshOrders) {
            customerorderHelper.refreshOrders(request, form);
        }

        if (refreshSuborders) {
            // refresh suborders to be displayed in the select menu
            String defaultSuborderIndexStr = request.getParameter("defaultSuborderIndex");
            suborderHelper.refreshSuborders(request, form, defaultSuborderIndexStr);

            // check if we can prefill the form with daily working time - this helps for standard orders like URLAUB
            Customerorder selectedOrder = customerorderDAO.getCustomerorderById(form.getOrderId());
            boolean standardOrder = customerorderHelper.isOrderStandard(selectedOrder);
            if (standardOrder) {
                Duration dailyWorkingTime = employeeContract.getDailyWorkingTime();
                int hours = dailyWorkingTime.toHoursPart();
                int minutes = dailyWorkingTime.toMinutesPart();

                form.setSelectedHourDuration(hours);
                form.setSelectedMinuteDuration(minutes);

                if(minutes % 5 != 0) {
                    form.setShowAllMinutes(true);
                    request.getSession().setAttribute("minutes", getTimeReportMinutesOptions(form.isShowAllMinutes()));
                }
            }
        }

        if (refreshWorkdayAvailability) {
            // search for adequate workingday and set status in session
            LocalDate selectedDate = DateUtils.parseOrDefault(form.getReferenceday(), DateUtils.today());
            Workingday workingday = workingdayDAO.getWorkingdayByDateAndEmployeeContractId(selectedDate, employeeContract.getId());
            boolean workingDayIsAvailable = workingday != null && DateUtils.today().equals(selectedDate);
            request.getSession().setAttribute("workingDayIsAvailable", workingDayIsAvailable);
        }

        if (request.getParameter("task") != null &&
                request.getParameter("task").equals("save") ||
                request.getParameter("trId") != null) {

            LocalDate referencedayRefDate = parse(form.getReferenceday());

            List<Employeeorder> employeeorders = employeeorderDAO
                .getEmployeeOrderByEmployeeContractIdAndSuborderIdAndDate2(
                    form.getEmployeeContractId(),
                    form.getSuborderSignId(),
                    referencedayRefDate
                );
            long employeeorderId = -1;
            if(!employeeorders.isEmpty()) {
                employeeorderId = employeeorders.get(0).getId();
            }

            if(form.isNewTimeReport()) {
                try {
                    timereportService.createTimereports(
                        authorizedUser,
                        form.getEmployeeContractId(),
                        employeeorderId,
                        referencedayRefDate,
                        form.getComment(),
                        Boolean.TRUE.equals(form.getTraining()),
                        form.getSelectedHourDuration(),
                        form.getSelectedMinuteDuration(),
                        Math.max(form.getNumberOfSerialDays(), 1) // ensure at least one
                    );
                } catch (AuthorizationException | BusinessRuleException | InvalidDataException e) {
                    addToErrors(request, e.getErrorCode());
                    return mapping.getInputForward();
                }
            } else {
                long timeReportId = form.getId();
                try {
                    timereportService.updateTimereport(
                        authorizedUser,
                        timeReportId,
                        form.getEmployeeContractId(),
                        employeeorderId,
                        referencedayRefDate,
                        form.getComment(),
                        Boolean.TRUE.equals(form.getTraining()),
                        form.getSelectedHourDuration(),
                        form.getSelectedMinuteDuration()
                    );
                } catch (AuthorizationException | BusinessRuleException | InvalidDataException e) {
                    addToErrors(request, e.getErrorCode());
                    return mapping.getInputForward();
                }
            }

            request.getSession().setAttribute("currentDay", DateUtils.getDayString(referencedayRefDate));
            request.getSession().setAttribute("currentMonth", DateUtils.getMonthShortString(referencedayRefDate));
            request.getSession().setAttribute("currentYear", DateUtils.getYearString(referencedayRefDate));
            request.getSession().removeAttribute("trId");

            Workingday workingday = workingdayDAO.getWorkingdayByDateAndEmployeeContractId(referencedayRefDate, form.getEmployeeContractId());

            if (request.getParameter("continue") == null || !Boolean.parseBoolean(request.getParameter("continue"))) {
                // FIXME geht das nicht leichter?
                // set new ShowDailyReportForm with saved filter settings
                ShowDailyReportForm continueForm = new ShowDailyReportForm();

                LocalDate referenceday = DateUtils.parseOrDefault(form.getReferenceday(), DateUtils.today());
                // TODO pruefen, warum diese Felder gebraucht werden
                continueForm.setDay(DateUtils.getDayString(referenceday));
                continueForm.setMonth(DateUtils.getMonthShortString(referenceday));
                continueForm.setYear(DateUtils.getYearString(referenceday));

                continueForm.setStartdate(continueForm.getYear() + "-" + getMonthMMStringFromShortstring(continueForm.getMonth()) + "-" + continueForm.getDay());
                if (request.getSession().getAttribute("lastLastMonth") != null) {
                    continueForm.setLastday((String) request.getSession().getAttribute("lastLastDay"));
                    continueForm.setLastmonth((String) request.getSession().getAttribute("lastLastMonth"));
                    continueForm.setLastyear((String) request.getSession().getAttribute("lastLastYear"));
                } else {
                    continueForm.setLastday(DateUtils.getDayString(referenceday));
                    continueForm.setLastmonth(DateUtils.getMonthShortString(referenceday));
                    continueForm.setLastyear(DateUtils.getYearString(referenceday));
                }
                continueForm.setEnddate(continueForm.getLastyear() + "-" + getMonthMMStringFromShortstring(continueForm.getLastmonth()) + "-"
                        + continueForm.getLastday());
                request.getSession().removeAttribute("lastCurrentDay");
                request.getSession().removeAttribute("lastCurrentMonth");
                request.getSession().removeAttribute("lastCurrentYear");
                request.getSession().removeAttribute("lastLastDay");
                request.getSession().removeAttribute("lastLastMonth");
                request.getSession().removeAttribute("lastLastYear");

                if (request.getSession().getAttribute("lastView") != null) {
                    continueForm.setView((String) request.getSession().getAttribute("lastView"));
                } else {
                    continueForm.setView(GlobalConstants.VIEW_DAILY);
                }
                request.getSession().removeAttribute("lastView");
                continueForm.setOrder((String) request.getSession().getAttribute("lastOrder"));
                if (request.getSession().getAttribute("lastSuborderId") != null) {
                    continueForm.setSuborderId((Long) request.getSession().getAttribute("lastSuborderId"));
                } else {
                    continueForm.setSuborderId(-1);
                }
                if (request.getSession().getAttribute("lastEmployeeContractId") != null) {
                    continueForm.setEmployeeContractId((Long) request.getSession().getAttribute("lastEmployeeContractId"));
                } else {
                    continueForm.setEmployeeContractId(form.getEmployeeContractId());
                }
                request.getSession().removeAttribute("lastSuborderId");
                request.getSession().removeAttribute("lastOrder");
                request.getSession().removeAttribute("lastEmployeeContractId");

                // get updated list of timereports from DB
                refreshTimereports(
                        request,
                        continueForm,
                        customerorderDAO,
                        timereportDAO,
                        employeecontractDAO,
                        suborderDAO,
                        employeeorderDAO
                );

                request.getSession().setAttribute("suborderFilerId", continueForm.getSuborderId());
                List<Timereport> timereports = (List<Timereport>) request.getSession().getAttribute("timereports");

                request.getSession().setAttribute("labortime", timereportHelper.calculateLaborTime(timereports));
                request.getSession().setAttribute("maxlabortime", timereportHelper.checkLaborTimeMaximum(timereports, GlobalConstants.MAX_HOURS_PER_DAY));
                request.getSession().setAttribute("quittingtime", timereportHelper.calculateQuittingTime(workingday, request, "quittingtime"));

                //calculate Working Day End
                request.getSession().setAttribute("workingDayEnds", timereportHelper.calculateQuittingTime(workingday, request, "workingDayEnds"));

                request.getSession().setAttribute("years", getYearsToDisplay());
                request.getSession().setAttribute("days", getDaysToDisplay());
                request.getSession().setAttribute("months", getMonthsToDisplay());
                request.getSession().setAttribute("hours", getHoursToDisplay());
                request.getSession().setAttribute("breakhours", getBreakHoursOptions());
                request.getSession().setAttribute("breakminutes", getTimeReportMinutesOptions(false));
                request.getSession().setAttribute("hoursDuration", getTimeReportHoursOptions());
                request.getSession().setAttribute("minutes", getTimeReportMinutesOptions(form.isShowAllMinutes()));

                // save values from the data base into form-bean, when working day != null
                if (workingday != null) {
                    // show break time, quitting time and working day ends on the
                    // showdailyreport.jsp
                    request.getSession().setAttribute("visibleworkingday", true);
                    continueForm.setSelectedWorkHourBegin(workingday.getStarttimehour());
                    continueForm.setSelectedWorkMinuteBegin(workingday.getStarttimeminute());
                    continueForm.setSelectedBreakHour(workingday.getBreakhours());
                    continueForm.setSelectedBreakMinute(workingday.getBreakminutes());
                } else {
                    // don't show break time, quitting time and working day ends on
                    // the showdailyreport.jsp
                    request.getSession().setAttribute("visibleworkingday", false);
                    continueForm.setSelectedWorkHourBegin(0);
                    continueForm.setSelectedWorkMinuteBegin(0);
                    continueForm.setSelectedBreakHour(0);
                    continueForm.setSelectedBreakMinute(0);
                }

                // refresh overtime and vacation
                var employeecontract = employeecontractDAO.getEmployeeContractById(form.getEmployeeContractId());
                refreshVacationAndOvertime(request, employeecontract);

                return mapping.findForward("showDaily");

            } else { // Continue = true

                LocalDate selectedDate = getSelectedDateFromRequest(request);

                //deleting comment and days of serialBookings in the addDailyReport-Form
                form.setComment("");
                form.setNumberOfSerialDays(0);

                if (workingday != null) {
                    int[] beginTime = timereportHelper.determineBeginTimeToDisplay(form.getEmployeeContractId(), selectedDate, workingday);
                    int beginHours = beginTime[0];
                    int beginMinutes = beginTime[1];
                    form.setSelectedHourBegin(beginHours);
                    form.setSelectedMinuteBegin(beginMinutes);
                    form.setNumberOfSerialDays(0);
                    LocalDate today = DateUtils.today();
                    int currentHours = DateUtils.getCurrentHours();
                    int currentMinutes = DateUtils.getCurrentMinutes();

                    if (workStartedEarlier(beginHours, beginMinutes, currentHours, currentMinutes) && selectedDate.equals(today)) {
                        form.setSelectedMinuteEnd(currentMinutes);
                        form.setSelectedHourEnd(currentHours);
                    } else {
                        form.setSelectedMinuteEnd(beginHours);
                        form.setSelectedHourEnd(beginMinutes);
                    }
                    timereportHelper.refreshHours(form);

                } else {
                    form.setSelectedHourDuration(0);
                    form.setSelectedMinuteDuration(0);
                }

                // load orders and suborders
                List<Customerorder> orders = customerorderDAO.getCustomerordersWithValidEmployeeOrders(form.getEmployeeContractId(), selectedDate);
                List<Suborder> theSuborders;
                if (!orders.isEmpty()) {
                    long orderId = form.getOrderId();
                    if (orderId == 0) {
                        orderId = orders.get(0).getId();
                    }
                    theSuborders = suborderDAO.getSubordersByEmployeeContractIdAndCustomerorderIdWithValidEmployeeOrders(form.getEmployeeContractId(), orderId, selectedDate);
                } else {
                    theSuborders = Collections.emptyList();
                }

                request.getSession().setAttribute("orders", orders);
                request.getSession().setAttribute("suborders", theSuborders);

                return mapping.findForward("addDaily");
            }

        }
        if (request.getParameter("task") != null && request.getParameter("task").equals("back")) {
            // go back
            request.getSession().removeAttribute("trId");
            form.reset(mapping, request);
        }
        if (request.getParameter("task") != null && request.getParameter("task").equals("reset")) {
            // reset form
            doResetActions(mapping, request, form);
        }

        return mapping.getInputForward();
    }

    private Employeecontract getEmployeeContractAndSetSessionVars(HttpServletRequest request) {
        Employeecontract employeecontract;

        if (request.getSession().getAttribute("currentEmployeeContract") != null) {
            employeecontract = (Employeecontract) request.getSession().getAttribute("currentEmployeeContract");
        } else {
            employeecontract = (Employeecontract) request.getSession().getAttribute("loginEmployeeContract");
        }
        long employeeContractId = employeecontract.getId();

        // TODO only store the id in the session, not the whole entity
        employeecontract = employeecontractDAO.getEmployeeContractById(employeeContractId);

        request.getSession().setAttribute("currentEmployee", employeecontract.getEmployee().getName());
        request.getSession().setAttribute("currentEmployeeId", employeecontract.getEmployee().getId());
        request.getSession().setAttribute("currentEmployeeContract", employeecontract);
        return employeecontract;
    }

    private void setSubOrder(@Nonnull Suborder suborder, HttpServletRequest request, AddDailyReportForm reportForm) {

        // if selected Suborder is Overtime Compensation, delete the previously automatically set daily working time
        // also make sure that overtimeCompensation is set in the session so that the duration-dropdown-menu will be disabled
        if (suborder.getSign().equalsIgnoreCase(GlobalConstants.SUBORDER_SIGN_OVERTIME_COMPENSATION)) {
            reportForm.setSelectedHourDuration(0);
            reportForm.setSelectedMinuteDuration(0);
            if (request.getSession().getAttribute("overtimeCompensation") == null ||
                !Objects.equals(request.getSession().getAttribute("overtimeCompensation"), GlobalConstants.SUBORDER_SIGN_OVERTIME_COMPENSATION)) {
                request.getSession().setAttribute("overtimeCompensation", GlobalConstants.SUBORDER_SIGN_OVERTIME_COMPENSATION);
            }

        }

        // if selected Suborder has a default-flag for projectbased training, set training in the form to true, so that the training-box in the jsp is checked
        if (TRUE.equals(suborder.getTrainingFlag())) {
            reportForm.setTraining(true);
        }
    }

    /**
     * resets the 'add report' form to default values
     */
    private void doResetActions(ActionMapping mapping, HttpServletRequest request, AddDailyReportForm reportForm) {
        reportForm.reset(mapping, request);

        //reset the current employee Session Variables
        Employee loginEmployee = (Employee) request.getSession().getAttribute("loginEmployee");
        Employeecontract loginEmployeeContract = (Employeecontract) request.getSession().getAttribute("loginEmployeeContract");

        List<Employeecontract> employeecontracts = employeecontractDAO.getVisibleEmployeeContractsForAuthorizedUser();
        String dateString = reportForm.getReferenceday();
        LocalDate date = DateUtils.parseOrDefault(dateString, DateUtils.today());

        List<Customerorder> orders = customerorderDAO.getCustomerordersWithValidEmployeeOrders(loginEmployeeContract.getId(), date);
        List<Suborder> suborders;

        //reset first order and corresponding suborders
        if (!orders.isEmpty()) {
            reportForm.setOrder(orders.get(0).getSign());
            reportForm.setOrderId(orders.get(0).getId());
            // prepare second collection of suborders sorted by description
            suborders = suborderDAO.getSubordersByEmployeeContractIdAndCustomerorderIdWithValidEmployeeOrders(loginEmployeeContract.getId(), orders.get(0).getId(), date);
        } else {
            reportForm.setOrder(null);
            reportForm.setOrderId(-1);
            suborders = Collections.emptyList();
        }

        if (request.getSession().getAttribute("trId") != null) {
            //get the Timereport object
            long trId = Long.parseLong(request.getSession().getAttribute("trId").toString());
            Timereport timereport = timereportDAO.getTimereportById(trId);

            //reset the rest
            reportForm.setReferenceday(timereport.getReferenceday().getRefdate().toString());
            request.getSession().setAttribute("isEdit", false);
            reportForm.setSelectedHourDuration(timereport.getDurationhours());
            reportForm.setSelectedMinuteDuration(timereport.getDurationminutes());
            reportForm.setTraining(timereport.getTraining());
            reportForm.setComment(timereport.getTaskdescription());
        } else {
            reportForm.reset();
        }

        request.getSession().setAttribute("employeecontracts", employeecontracts);
        request.getSession().setAttribute("currentEmployee", loginEmployee.getName());
        request.getSession().setAttribute("currentEmployeeId", loginEmployee.getId());
        request.getSession().setAttribute("currentEmployeeContract", loginEmployeeContract);

        request.getSession().setAttribute("orders", orders);
        request.getSession().setAttribute("suborders", suborders);
    }

    private boolean workStartedEarlier(int beginHours, int beginMinutes, int currentHours, int currentMinutes) {
        return beginHours < currentHours || (beginHours == currentHours && beginMinutes < currentMinutes);
    }

    @Override
    protected boolean isAllowedForRestrictedUsers() {
        return true;
    }
}
