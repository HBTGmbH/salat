package org.tb.action.dailyreport;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.tb.GlobalConstants;
import org.tb.bdom.Customerorder;
import org.tb.bdom.Employeecontract;
import org.tb.bdom.Suborder;
import org.tb.bdom.Workingday;
import org.tb.bdom.comparators.SubOrderByDescriptionComparator;
import org.tb.helper.AfterLogin;
import org.tb.helper.TimereportHelper;
import org.tb.persistence.CustomerorderDAO;
import org.tb.persistence.EmployeecontractDAO;
import org.tb.persistence.SuborderDAO;
import org.tb.persistence.WorkingdayDAO;
import org.tb.util.DateUtils;
import org.tb.form.AddDailyReportForm;

/**
 * Action class for creation of a timereport
 *
 * @author oda
 */
@Component("/CreateDailyReport")
@Slf4j
public class CreateDailyReportAction extends DailyReportAction<AddDailyReportForm> {

    private final EmployeecontractDAO employeecontractDAO;
    private final CustomerorderDAO customerorderDAO;
    private final SuborderDAO suborderDAO;
    private final WorkingdayDAO workingdayDAO;
    private final AfterLogin afterLogin;
    private final TimereportHelper timereportHelper;

    @Autowired
    public CreateDailyReportAction(AfterLogin afterLogin, EmployeecontractDAO employeecontractDAO,
        CustomerorderDAO customerorderDAO, SuborderDAO suborderDAO, WorkingdayDAO workingdayDAO,
        AfterLogin afterLogin1, TimereportHelper timereportHelper) {
        super(afterLogin);
        this.employeecontractDAO = employeecontractDAO;
        this.customerorderDAO = customerorderDAO;
        this.suborderDAO = suborderDAO;
        this.workingdayDAO = workingdayDAO;
        this.afterLogin = afterLogin1;
        this.timereportHelper = timereportHelper;
    }

    @Override
    public ActionForward executeAuthenticated(ActionMapping mapping, AddDailyReportForm form, HttpServletRequest request, HttpServletResponse response) {
        Employeecontract loginEmployeeContract = (Employeecontract) request.getSession().getAttribute("loginEmployeeContract");
        Employeecontract ec;

        if (request.getSession().getAttribute("currentEmployeeContract") != null &&
                (Boolean) request.getSession().getAttribute("employeeAuthorized")) {
            ec = (Employeecontract) request.getSession().getAttribute("currentEmployeeContract");

        } else {
            ec = loginEmployeeContract;
        }

        if (ec == null) {
            request.setAttribute("errorMessage", "No employee contract found for employee - please call system administrator."); //TODO
            return mapping.findForward("error");
        }

        // get selected date for new report
        java.sql.Date selectedDate = getSelectedDateFromRequest(request);

        Employeecontract matchingEC = employeecontractDAO.getEmployeeContractByEmployeeIdAndDate(ec.getEmployee().getId(), selectedDate);
        if (matchingEC != null) {
            ec = matchingEC;
        }

        request.getSession().setAttribute("currentEmployee", ec.getEmployee().getName());
        request.getSession().setAttribute("currentEmployeeId", ec.getEmployee().getId());
        request.getSession().setAttribute("currentEmployeeContract", ec);

        List<Employeecontract> employeecontracts = employeecontractDAO.getVisibleEmployeeContractsForEmployee(loginEmployeeContract.getEmployee());
        request.getSession().setAttribute("employeecontracts", employeecontracts);

        List<Customerorder> orders = customerorderDAO.getCustomerordersWithValidEmployeeOrders(ec.getId(), selectedDate);

        // set attributes to be analyzed by target jsp
        request.getSession().setAttribute("orders", orders);
        request.getSession().setAttribute("report", "W");
        request.getSession().setAttribute("days", DateUtils.getDaysToDisplay());
        request.getSession().setAttribute("months", DateUtils.getMonthsToDisplay());
        request.getSession().setAttribute("hours", DateUtils.getHoursToDisplay());
        request.getSession().setAttribute("hoursDuration", DateUtils.getHoursDurationToDisplay());
        request.getSession().setAttribute("minutes", DateUtils.getMinutesToDisplay());
        request.getSession().setAttribute("serialBookings", getSerialDayList());

        // search for adequate workingday and set status in session
        Workingday workingday = workingdayDAO.getWorkingdayByDateAndEmployeeContractId(selectedDate, ec.getId());

        boolean workingDayIsAvailable = false;
        if (workingday != null) {
            workingDayIsAvailable = true;
        }

        // workingday should only be available for today
        java.util.Date today = new java.util.Date();
        SimpleDateFormat simpleDateFormat = new SimpleDateFormat(GlobalConstants.DEFAULT_DATE_FORMAT);
        String todayString = simpleDateFormat.format(today);
        try {
            today = simpleDateFormat.parse(todayString);
        } catch (Exception e) {
            throw new RuntimeException("this should never happen...!");
        }
        if (!selectedDate.equals(today)) {
            workingDayIsAvailable = false;
        }

        request.getSession().setAttribute("workingDayIsAvailable", workingDayIsAvailable);

        // set the begin time as the end time of the latest existing timereport of current employee
        // for current day. If no other reports exist so far, set standard begin time (0800).
        int[] beginTime = timereportHelper.determineBeginTimeToDisplay(ec.getId(), selectedDate, workingday);
        form.setSelectedHourBegin(beginTime[0]);
        form.setSelectedMinuteBegin(beginTime[1]);
        //		TimereportHelper.refreshHours(reportForm);

        if (workingDayIsAvailable) {
            // set end time in reportform
            today = new Date();
            SimpleDateFormat minuteFormat = new SimpleDateFormat("mm");
            SimpleDateFormat hourFormat = new SimpleDateFormat("HH");

            int hour = new Integer(hourFormat.format(today));
            int minute = new Integer(minuteFormat.format(today));
            minute = minute / 5 * 5;

            todayString = simpleDateFormat.format(today);
            try {
                today = simpleDateFormat.parse(todayString);
            } catch (Exception e) {
                throw new RuntimeException("this should never happen...!");
            }

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
        form.setReferenceday(simpleDateFormat.format(selectedDate));

        // init form with first order and corresponding suborders
        List<Suborder> theSuborders;
        if (orders != null && !orders.isEmpty()) {
            form.setOrder(orders.get(0).getSign());
            form.setOrderId(orders.get(0).getId());

            theSuborders = suborderDAO.getSubordersByEmployeeContractIdAndCustomerorderIdWithValidEmployeeOrders(ec.getId(), orders.get(0).getId(), selectedDate);

            if (theSuborders == null || theSuborders.isEmpty()) {
                request.setAttribute("errorMessage", "Orders/suborders inconsistent for employee - please call system administrator."); //TODO
                return mapping.findForward("error");
            }
            // set isEdit = false into the session, so order/suborder menu will not be disabled
            request.getSession().setAttribute("isEdit", false);
        } else {
            request.setAttribute("errorMessage", "no orders found for employee - please call system administrator."); //TODO
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

        // make sure, no cuId still exists in session
        request.getSession().removeAttribute("trId");

        if (request.getParameter("task") != null && request.getParameter("task").equals("matrix")) {
            form.setReferenceday(todayString);
        }

        // init the rest of the form
        form.setCosts(0d);
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
                || request.getSession().getAttribute("overtimeCompensation") != GlobalConstants.SUBORDER_SIGN_OVERTIME_COMPENSATION) {
            request.getSession().setAttribute("overtimeCompensation", GlobalConstants.SUBORDER_SIGN_OVERTIME_COMPENSATION);
        }

        return mapping.findForward("success");
    }

    @Override
    protected boolean isAllowedForRestrictedUsers() {
        return true;
    }

}
