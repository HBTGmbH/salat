package org.tb.web.action;

import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;
import org.tb.GlobalConstants;
import org.tb.bdom.*;
import org.tb.helper.JiraSalatHelper;
import org.tb.helper.TimereportHelper;
import org.tb.persistence.*;
import org.tb.util.DateUtils;
import org.tb.web.form.AddDailyReportForm;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Action class for editing of a timereport
 *
 * @author oda
 */
public class EditDailyReportAction extends DailyReportAction {

    private TimereportDAO timereportDAO;
    private CustomerorderDAO customerorderDAO;
    private SuborderDAO suborderDAO;
    private EmployeecontractDAO employeecontractDAO;
    private WorkingdayDAO workingdayDAO;
    private TicketDAO ticketDAO;

    public TimereportDAO getTimereportDAO() {
        return timereportDAO;
    }

    public void setTimereportDAO(TimereportDAO timereportDAO) {
        this.timereportDAO = timereportDAO;
    }

    public void setCustomerorderDAO(CustomerorderDAO customerorderDAO) {
        this.customerorderDAO = customerorderDAO;
    }

    public void setSuborderDAO(SuborderDAO suborderDAO) {
        this.suborderDAO = suborderDAO;
    }

    public void setEmployeecontractDAO(EmployeecontractDAO employeecontractDAO) {
        this.employeecontractDAO = employeecontractDAO;
    }

    public void setWorkingdayDAO(WorkingdayDAO workingdayDAO) {
        this.workingdayDAO = workingdayDAO;
    }

    public void setTicketDAO(TicketDAO ticketDAO) {
        this.ticketDAO = ticketDAO;
    }

    @Override
    public ActionForward executeAuthenticated(ActionMapping mapping, ActionForm form, HttpServletRequest request, HttpServletResponse response) {

        AddDailyReportForm reportForm = (AddDailyReportForm) form;
        long trId = Long.parseLong(request.getParameter("trId"));
        Timereport tr = timereportDAO.getTimereportById(trId);

        // set collections
        request.getSession().setAttribute("hoursDuration", DateUtils.getHoursDurationToDisplay());
        request.getSession().setAttribute("minutes", DateUtils.getMinutesToDisplay());

        // make sure that overtimeCompensation is set in the session so that the duration-dropdown-menu will be disabled
        // if the current suborder is overtime compensation.
        if (request.getSession().getAttribute("overtimeCompensation") == null
                || request.getSession().getAttribute("overtimeCompensation") != GlobalConstants.SUBORDER_SIGN_OVERTIME_COMPENSATION) {
            request.getSession().setAttribute("overtimeCompensation", GlobalConstants.SUBORDER_SIGN_OVERTIME_COMPENSATION);
        }

        // adjust the jsp with entries for Jira-Ticket-Keys, if a timereport for a customerorder with Jira-Project-ID is edited 
        JiraSalatHelper.setJiraTicketKeysForSuborder(request, ticketDAO, tr.getSuborder().getId());

        // fill the form with properties of the timereport to be edited
        setFormEntries(mapping, request, reportForm, tr);

        request.getSession().setAttribute("timereport", tr);
        request.getSession().setAttribute("currentEmployeeContract", tr.getEmployeecontract());

        // save the filter settings
        request.getSession().setAttribute("lastCurrentDay", request.getSession().getAttribute("currentDay"));
        request.getSession().setAttribute("lastCurrentMonth", request.getSession().getAttribute("currentMonth"));
        request.getSession().setAttribute("lastCurrentYear", request.getSession().getAttribute("currentYear"));
        request.getSession().setAttribute("lastLastDay", request.getSession().getAttribute("lastDay"));
        request.getSession().setAttribute("lastLastMonth", request.getSession().getAttribute("lastMonth"));
        request.getSession().setAttribute("lastLastYear", request.getSession().getAttribute("lastYear"));
        request.getSession().setAttribute("lastOrder", request.getSession().getAttribute("currentOrder"));
        request.getSession().setAttribute("lastSuborderId", request.getSession().getAttribute("suborderFilerId"));
        request.getSession().setAttribute("lastView", request.getSession().getAttribute("view"));
        request.getSession().setAttribute("lastEmployeeContractId", reportForm.getEmployeeContractId());
        return mapping.findForward("success");
    }

    /**
     * fills the AddDailyReportForm with properties of the timereport to be edited
     */
    private void setFormEntries(ActionMapping mapping, HttpServletRequest request,
                                AddDailyReportForm reportForm, Timereport tr) {

        Employeecontract ec = tr.getEmployeecontract();
        Date utilDate = new Date(tr.getReferenceday().getRefdate().getTime()); // convert to java.util.Date

        List<Customerorder> orders = customerorderDAO.getCustomerordersWithValidEmployeeOrders(ec.getId(), utilDate);
        List<Suborder> theSuborders = new ArrayList<>();
        if (orders != null && !orders.isEmpty()) {
            reportForm.setOrder(orders.get(0).getSign());
            reportForm.setOrderId(orders.get(0).getId());
            theSuborders = suborderDAO.getSubordersByEmployeeContractIdAndCustomerorderIdWithValidEmployeeOrders(ec.getId(), tr.getEmployeeorder().getSuborder().getCustomerorder().getId(), utilDate);
            if (theSuborders == null || theSuborders.isEmpty()) {
                request.setAttribute("errorMessage", "Orders/suborders inconsistent for employee - please call system administrator.");
                mapping.findForward("error");
            }
        } else {
            request.setAttribute("errorMessage", "no orders found for employee - please call system administrator.");
            mapping.findForward("error");
        }

        Employee loginEmployee = (Employee) request.getSession().getAttribute("loginEmployee");
        List<Employeecontract> employeecontracts = employeecontractDAO.getVisibleEmployeeContractsForEmployee(loginEmployee);
        request.getSession().setAttribute("employeecontracts", employeecontracts);

        // set isEdit into the Session, so that the order/suborder menu will be disabled if a timereport for a customerorder with Jira-Project-ID is edited
        request.getSession().setAttribute("isEdit", false);

        /* set hours list in session in case of that the dialog is triggered from the welcome page */
        request.getSession().setAttribute("hours", DateUtils.getHoursToDisplay());

        request.getSession().setAttribute("trId", tr.getId());
        request.getSession().setAttribute("orders", orders);
        request.getSession().setAttribute("suborders", theSuborders);
        request.getSession().setAttribute("currentSuborderId", tr.getEmployeeorder().getSuborder().getId());
        request.getSession().setAttribute("serialBookings", getSerialDayList());

        reportForm.reset(mapping, request);
        reportForm.setEmployeeContractId(ec.getId());

        reportForm.setReferenceday(DateUtils.getSqlDateString(utilDate));
        java.sql.Date reportDate = tr.getReferenceday().getRefdate();
        Workingday workingday = workingdayDAO.getWorkingdayByDateAndEmployeeContractId(reportDate, ec.getId());

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
        if (!utilDate.equals(today)) {
            workingDayIsAvailable = false;
        }

        request.getSession().setAttribute("workingDayIsAvailable", workingDayIsAvailable);
        TimereportHelper th = new TimereportHelper();
        int[] displayTime = th.determineTimesToDisplay(ec.getId(), timereportDAO, reportDate, workingday, tr);

        if (workingDayIsAvailable) {
            reportForm.setSelectedHourBegin(displayTime[0]);
            reportForm.setSelectedMinuteBegin(displayTime[1]);
            reportForm.setSelectedHourEnd(displayTime[2]);
            reportForm.setSelectedMinuteEnd(displayTime[3]);

            TimereportHelper.refreshHours(reportForm);
        } else {
            reportForm.setSelectedHourDuration(tr.getDurationhours());
            reportForm.setSelectedMinuteDuration(tr.getDurationminutes());
        }

        reportForm.setSortOfReport(tr.getSortofreport());
        request.getSession().setAttribute("report", tr.getSortofreport());

        if (tr.getSortofreport().equals("W")) {
            if (tr.getSuborder() != null && tr.getSuborder().getCustomerorder() != null) {
                reportForm.setSuborder(tr.getSuborder().getSign());
                reportForm.setSuborderSignId(tr.getSuborder().getId());
                reportForm.setSuborderDescriptionId(tr.getSuborder().getId());
                reportForm.setOrder(tr.getSuborder().getCustomerorder().getSign());
                reportForm.setOrderId(tr.getSuborder().getCustomerorder().getId());
            }
            reportForm.setCosts(tr.getCosts());
            reportForm.setStatus(tr.getStatus());
        }
        reportForm.setComment(tr.getTaskdescription());
        reportForm.setTraining(tr.getTraining());
        if (tr.getTicket() != null) {
            request.getSession().setAttribute("projectIDExists", true);
            request.getSession().setAttribute("isEdit", true);
            reportForm.setJiraTicketKey(tr.getTicket().getJiraTicketKey());
            request.getSession().setAttribute("jiraTicketKey", reportForm.getJiraTicketKey());
        }
    }

    @Override
    protected boolean isAllowedForRestrictedUsers() {
        return true;
    }
}
