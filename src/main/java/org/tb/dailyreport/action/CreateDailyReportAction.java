package org.tb.dailyreport.action;

import static org.tb.common.DateTimeViewHelper.getDaysToDisplay;
import static org.tb.common.DateTimeViewHelper.getHoursToDisplay;
import static org.tb.common.DateTimeViewHelper.getMonthsToDisplay;
import static org.tb.common.DateTimeViewHelper.getSerialDayList;
import static org.tb.common.DateTimeViewHelper.getTimeReportHoursOptions;
import static org.tb.common.DateTimeViewHelper.getTimeReportMinutesOptions;
import static org.tb.common.util.DateUtils.now;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;
import org.springframework.stereotype.Component;
import org.tb.common.GlobalConstants;
import org.tb.common.util.DateUtils;
import org.tb.dailyreport.domain.Workingday;
import org.tb.dailyreport.persistence.WorkingdayDAO;
import org.tb.dailyreport.viewhelper.TimereportHelper;
import org.tb.employee.domain.Employeecontract;
import org.tb.employee.persistence.EmployeecontractDAO;
import org.tb.order.domain.Customerorder;
import org.tb.order.domain.Suborder;
import org.tb.order.domain.comparator.SubOrderByDescriptionComparator;
import org.tb.order.persistence.CustomerorderDAO;
import org.tb.order.persistence.SuborderDAO;

/**
 * Action class for creation of a timereport
 *
 * @author oda
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CreateDailyReportAction extends DailyReportAction<AddDailyReportForm> {

    private final EmployeecontractDAO employeecontractDAO;
    private final CustomerorderDAO customerorderDAO;
    private final SuborderDAO suborderDAO;
    private final WorkingdayDAO workingdayDAO;
    private final TimereportHelper timereportHelper;

    @Override
    public ActionForward executeAuthenticated(ActionMapping mapping, AddDailyReportForm form, HttpServletRequest request, HttpServletResponse response) {
        Employeecontract loginEmployeeContract = (Employeecontract) request.getSession().getAttribute("loginEmployeeContract");
        Employeecontract employeecontract;

        if (request.getSession().getAttribute("currentEmployeeContract") != null && authorizedUser.isManager()) {
            employeecontract = (Employeecontract) request.getSession().getAttribute("currentEmployeeContract");
        } else {
            employeecontract = loginEmployeeContract;
        }

        if (employeecontract == null) {
            request.setAttribute("errorMessage", "No employee contract found for employee - please call system administrator."); //TODO
            return mapping.findForward("error");
        }

        // get selected date for new report
        LocalDate selectedDate = getSelectedDateFromRequest(request);

        Employeecontract matchingEC = employeecontractDAO.getEmployeeContractByEmployeeIdAndDate(employeecontract.getEmployee().getId(), selectedDate);
        if (matchingEC != null) {
            employeecontract = matchingEC;
        }

        request.getSession().setAttribute("currentEmployee", employeecontract.getEmployee().getName());
        request.getSession().setAttribute("currentEmployeeId", employeecontract.getEmployee().getId());
        request.getSession().setAttribute("currentEmployeeContract", employeecontract);

        List<Employeecontract> employeecontracts = employeecontractDAO.getTimeReportableEmployeeContractsForAuthorizedUser();
        request.getSession().setAttribute("employeecontracts", employeecontracts);

        List<Customerorder> orders = customerorderDAO.getCustomerordersWithValidEmployeeOrders(employeecontract.getId(), selectedDate);

        // set attributes to be analyzed by target jsp
        request.getSession().setAttribute("orders", orders);
        request.getSession().setAttribute("days", getDaysToDisplay());
        request.getSession().setAttribute("months", getMonthsToDisplay());
        request.getSession().setAttribute("hours", getHoursToDisplay());
        request.getSession().setAttribute("hoursDuration", getTimeReportHoursOptions());
        request.getSession().setAttribute("minutes", getTimeReportMinutesOptions(form.isShowAllMinutes()));
        request.getSession().setAttribute("serialBookings", getSerialDayList());

        // search for adequate workingday and set status in session
        Workingday workingday = workingdayDAO.getWorkingdayByDateAndEmployeeContractId(selectedDate, employeecontract.getId());

        boolean workingDayIsAvailable = workingday != null;

      // workingday should only be available for today
        LocalDate today = DateUtils.today();
        if (!selectedDate.equals(today)) {
            workingDayIsAvailable = false;
        }

        request.getSession().setAttribute("workingDayIsAvailable", workingDayIsAvailable);

        // set the begin time as the end time of the latest existing timereport of current employee
        // for current day. If no other reports exist so far, set standard begin time (0800).
        long[] beginTime = timereportHelper.determineBeginTimeToDisplay(employeecontract.getId(), selectedDate, workingday);
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
            timereportHelper.refreshHours(form);
        } else {
            form.setSelectedHourDuration(0);
            form.setSelectedMinuteDuration(0);
        }

        // init form with selected Date
        form.setReferenceday(DateUtils.format(selectedDate));

        // init form with first order and corresponding suborders
        List<Suborder> theSuborders;
        if (orders != null && !orders.isEmpty()) {
            form.setOrder(orders.get(0).getSign());
            form.setOrderId(orders.get(0).getId());

            theSuborders = suborderDAO.getSubordersByEmployeeContractIdAndCustomerorderIdWithValidEmployeeOrders(employeecontract.getId(), orders.get(0).getId(), selectedDate);

            if (theSuborders == null || theSuborders.isEmpty()) {
                request.setAttribute("errorMessage", "Orders/suborders inconsistent for employee - please call system administrator."); //TODO
                return mapping.findForward("error");
            }
            // set isEdit = false into the session, so order/suborder menu will not be disabled
            request.getSession().setAttribute("isEdit", false);
        } else {
            request.setAttribute("errorMessage", "no orders found for employee - please call system administrator."); //TODO
            log.error("no orders found for employee {} - please call system administrator.", employeecontract.getEmployee().getSign());
            return mapping.findForward("error");
        }
        // prepare second collection of suborders sorted by description
        List<Suborder> subordersByDescription = new ArrayList<>(theSuborders);
        subordersByDescription.sort(SubOrderByDescriptionComparator.INSTANCE);
        request.getSession().setAttribute("suborders", theSuborders);
        request.getSession().setAttribute("subordersByDescription", subordersByDescription);
        request.getSession().setAttribute("currentSuborderId", theSuborders.get(0).getId());
        request.getSession().setAttribute("currentSuborderSign", theSuborders.get(0).getSign());

        // get first Suborder to synchronize suborder lists
        Suborder so = theSuborders.get(0);
        request.getSession().setAttribute("currentSuborderId", so.getId());

        // make sure, no cuId still exists in session, remove from form, too
        request.getSession().removeAttribute("trId");
        form.setAsNewTimereport();

        if (request.getParameter("task") != null && request.getParameter("task").equals("matrix")) {
            form.setReferenceday(DateUtils.format(today));
        }

        // init the rest of the form
        form.setTraining(false);
        form.setComment("");

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

        //  make sure that overtimeCompensation is set in the session so that the duration-dropdown-menu will be disabled for timereports with suborder uesa00
        if (request.getSession().getAttribute("overtimeCompensation") == null
            || !Objects.equals(request.getSession().getAttribute("overtimeCompensation"), GlobalConstants.SUBORDER_SIGN_OVERTIME_COMPENSATION)) {
            request.getSession().setAttribute("overtimeCompensation", GlobalConstants.SUBORDER_SIGN_OVERTIME_COMPENSATION);
        }

        return mapping.findForward("success");
    }

    @Override
    protected boolean isAllowedForRestrictedUsers() {
        return true;
    }

}
