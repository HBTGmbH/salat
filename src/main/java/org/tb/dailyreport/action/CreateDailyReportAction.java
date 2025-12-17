package org.tb.dailyreport.action;

import static org.tb.common.GlobalConstants.DEFAULT_WORK_DAY_START;
import static org.tb.common.util.DateTimeUtils.getDaysToDisplay;
import static org.tb.common.util.DateTimeUtils.getHoursToDisplay;
import static org.tb.common.util.DateTimeUtils.getMonthsToDisplay;
import static org.tb.common.util.DateTimeUtils.getSerialDayList;
import static org.tb.common.util.DateTimeUtils.getTimeReportHoursOptions;
import static org.tb.common.util.DateTimeUtils.getTimeReportMinutesOptions;
import static org.tb.common.util.DateTimeUtils.now;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;
import org.springframework.stereotype.Component;
import org.tb.common.GlobalConstants;
import org.tb.common.util.DateUtils;
import org.tb.dailyreport.domain.Workingday;
import org.tb.dailyreport.service.WorkingdayService;
import org.tb.dailyreport.viewhelper.TimereportHelper;
import org.tb.employee.domain.Employeecontract;
import org.tb.employee.service.EmployeecontractService;
import org.tb.order.domain.Customerorder;
import org.tb.order.domain.Suborder;
import org.tb.order.domain.comparator.SubOrderByDescriptionComparator;
import org.tb.order.service.CustomerorderService;
import org.tb.order.service.SuborderService;

/**
 * Action class for creation of a timereport
 *
 * @author oda
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CreateDailyReportAction extends DailyReportAction<AddDailyReportForm> {

    private final EmployeecontractService employeecontractService;
    private final CustomerorderService customerorderService;
    private final SuborderService suborderService;
    private final WorkingdayService workingdayService;
    private final TimereportHelper timereportHelper;

    @Override
    public ActionForward executeAuthenticated(ActionMapping mapping, AddDailyReportForm form, HttpServletRequest request, HttpServletResponse response) {
        Employeecontract loginEmployeeContract = (Employeecontract) request.getSession().getAttribute("loginEmployeeContract");
        Employeecontract ec;

        if (request.getSession().getAttribute("currentEmployeeContract") != null && authorizedUser.isManager()) {
            ec = (Employeecontract) request.getSession().getAttribute("currentEmployeeContract");
        } else {
            ec = loginEmployeeContract;
        }

        if (ec == null) {
            request.setAttribute("errorMessage", "No employee contract found for employee - please call system administrator."); //TODO
            return mapping.findForward("error");
        }

        // get selected date for new report
        LocalDate selectedDate = getSelectedDateFromRequest(request);

        Employeecontract matchingEC = employeecontractService.getEmployeeContractValidAt(ec.getEmployee().getId(), selectedDate);
        if (matchingEC != null) {
            ec = matchingEC;
        }

        request.getSession().setAttribute("currentEmployee", ec.getEmployee().getName());
        request.getSession().setAttribute("currentEmployeeId", ec.getEmployee().getId());
        request.getSession().setAttribute("currentEmployeeContract", ec);

        List<Employeecontract> employeecontracts = employeecontractService.getTimeReportableEmployeeContractsForAuthorizedUser();
        request.getSession().setAttribute("employeecontracts", employeecontracts);

        List<Customerorder> orders = customerorderService.getCustomerordersWithValidEmployeeOrders(ec.getId(), selectedDate);

        // set attributes to be analyzed by target jsp
        request.getSession().setAttribute("orders", orders);
        request.getSession().setAttribute("days", getDaysToDisplay());
        request.getSession().setAttribute("months", getMonthsToDisplay());
        request.getSession().setAttribute("hours", getHoursToDisplay());
        request.getSession().setAttribute("hoursDuration", getTimeReportHoursOptions());
        request.getSession().setAttribute("minutes", getTimeReportMinutesOptions(form.isShowAllMinutes()));
        request.getSession().setAttribute("serialBookings", getSerialDayList());

        // search for adequate workingday and set status in session
        Workingday workingday = workingdayService.getWorkingday(ec.getId(), selectedDate);

        boolean workingDayIsAvailable = false;
        if (workingday != null) {
            workingDayIsAvailable = true;
        }

        // workingday should only be available for today
        LocalDate today = DateUtils.today();
        if (!selectedDate.equals(today)) {
            workingDayIsAvailable = false;
        }

        request.getSession().setAttribute("workingDayIsAvailable", workingDayIsAvailable);

        // set the begin time as the end time of the latest existing timereport of current employee
        // for current day. If no other reports exist so far, set standard begin time (0800).
        long[] beginTime = timereportHelper.determineBeginTimeToDisplay(ec.getId(), selectedDate, workingday);
        form.setSelectedHourBegin(beginTime[0]);
        form.setSelectedMinuteBegin(beginTime[1]);
        //		TimereportHelper.refreshHours(reportForm);

        if (workingDayIsAvailable) {
            // set end time in reportform
            var now = now();

            int hour = Integer.parseInt(DateUtils.formatHours(now));
            int minute = Integer.parseInt(DateUtils.formatMinutes(now));

            // propose minutes with quarter hour precision
            minute = minute - minute % GlobalConstants.QUARTER_HOUR_IN_MINUTES;

            if ((beginTime[0] < hour || beginTime[0] == hour && beginTime[1] < minute) && selectedDate.equals(today)) {
                form.setSelectedMinuteEnd(minute);
                form.setSelectedHourEnd(hour);
            } else {
                form.setSelectedMinuteEnd(beginTime[1]);
                form.setSelectedHourEnd(beginTime[0]);
            }

            form.setSelectedHourBeginDay(workingday.getStarttimehour());
            form.setSelectedMinuteBeginDay(workingday.getStarttimeminute());

            timereportHelper.refreshHours(form);
        } else {
            form.setSelectedHourDuration(0);
            form.setSelectedMinuteDuration(0);
            form.setSelectedHourBeginDay(DEFAULT_WORK_DAY_START);
            form.setSelectedMinuteBeginDay(0);
        }

        // init form with selected Date
        form.setReferenceday(DateUtils.format(selectedDate));

        // init form with first order and corresponding suborders
        List<Suborder> theSuborders = List.of();
        Optional<Suborder> currentSuborder = Optional.empty();
        if (orders != null && !orders.isEmpty()) {
            form.setOrder(orders.getFirst().getSign());
            form.setOrderId(orders.getFirst().getId());
            theSuborders = suborderService.getSubordersByEmployeeContractIdAndCustomerorderIdWithValidEmployeeOrders(ec.getId(), orders.getFirst().getId(), selectedDate);
            if(!theSuborders.isEmpty()) {
                currentSuborder = Optional.of(theSuborders.getFirst());
            }
            // set isEdit = false into the session, so order/suborder menu will not be disabled
            request.getSession().setAttribute("isEdit", false);
        }
        // prepare second collection of suborders sorted by description
        List<Suborder> subordersByDescription = new ArrayList<>(theSuborders);
        subordersByDescription.sort(SubOrderByDescriptionComparator.INSTANCE);
        request.getSession().setAttribute("suborders", theSuborders);
        request.getSession().setAttribute("subordersByDescription", subordersByDescription);
        request.getSession().setAttribute("currentSuborderId", currentSuborder.map(Suborder::getId).orElse(-1L));
        request.getSession().setAttribute("currentSuborderSign", currentSuborder.map(Suborder::getCompleteOrderSign).orElse(""));

        // get first Suborder to synchronize suborder lists
        request.getSession().setAttribute("currentSuborderId", currentSuborder.map(Suborder::getId).orElse(-1L));

        // make sure, no cuId still exists in session, remove from form, too
        request.getSession().removeAttribute("trId");
        form.setAsNewTimereport();

        if (request.getParameter("task") != null && request.getParameter("task").equals("matrix")) {
            form.setReferenceday(DateUtils.format(today));
        }

        // init the rest of the form
        form.setTraining(false);

        if ( request.getParameter("comment") != null) {
            String comment = request.getParameter("comment");
            form.setComment(comment);
        }
        else {
            form.setComment("");
        }

        // store last selected order
        String lastOrder;
        try {
            lastOrder = (String) request.getSession().getAttribute("currentOrder");
        } catch (ClassCastException e) {
            Customerorder customerorder = (Customerorder) request.getSession().getAttribute("currentOrder");
            lastOrder = customerorder.getSign();
        }
        request.getSession().setAttribute("lastOrder", lastOrder);

        // save the filter settings
        request.getSession().setAttribute("lastCurrentDay", request.getSession().getAttribute("currentDay"));
        request.getSession().setAttribute("lastCurrentMonth", request.getSession().getAttribute("currentMonth"));
        request.getSession().setAttribute("lastCurrentYear", request.getSession().getAttribute("currentYear"));
        request.getSession().setAttribute("lastLastDay", request.getSession().getAttribute("lastDay"));
        request.getSession().setAttribute("lastLastMonth", request.getSession().getAttribute("lastMonth"));
        request.getSession().setAttribute("lastLastYear", request.getSession().getAttribute("lastYear"));
        request.getSession().setAttribute("lastSuborderId", request.getSession().getAttribute("suborderFilerId"));
        request.getSession().setAttribute("lastView", request.getSession().getAttribute("view"));
        request.getSession().setAttribute("lastEmployeeContractId", form.getEmployeeContractId());

        return mapping.findForward("success");
    }

    @Override
    protected boolean isAllowedForRestrictedUsers() {
        return true;
    }

}
