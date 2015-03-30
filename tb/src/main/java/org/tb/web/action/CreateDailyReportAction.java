package org.tb.web.action;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;
import org.tb.GlobalConstants;
import org.tb.bdom.Customerorder;
import org.tb.bdom.Employeecontract;
import org.tb.bdom.ProjectID;
import org.tb.bdom.Suborder;
import org.tb.bdom.Workingday;
import org.tb.bdom.comparators.SubOrderByDescriptionComparator;
import org.tb.helper.JiraSalatHelper;
import org.tb.helper.TimereportHelper;
import org.tb.persistence.CustomerorderDAO;
import org.tb.persistence.EmployeecontractDAO;
import org.tb.persistence.SuborderDAO;
import org.tb.persistence.TicketDAO;
import org.tb.persistence.TimereportDAO;
import org.tb.persistence.WorkingdayDAO;
import org.tb.util.DateUtils;
import org.tb.web.form.AddDailyReportForm;

/**
 * Action class for creation of a timereport
 * 
 * @author oda
 *
 */
public class CreateDailyReportAction extends DailyReportAction {
    
    private EmployeecontractDAO employeecontractDAO;
    private CustomerorderDAO customerorderDAO;
    private SuborderDAO suborderDAO;
    private TimereportDAO timereportDAO;
    private WorkingdayDAO workingdayDAO;
    private TicketDAO ticketDAO;
    
    public void setEmployeecontractDAO(EmployeecontractDAO employeecontractDAO) {
        this.employeecontractDAO = employeecontractDAO;
    }
    public void setCustomerorderDAO(CustomerorderDAO customerorderDAO) {
        this.customerorderDAO = customerorderDAO;
    }
    public void setSuborderDAO(SuborderDAO suborderDAO) {
        this.suborderDAO = suborderDAO;
    }
    public void setTimereportDAO(TimereportDAO timereportDAO) {
        this.timereportDAO = timereportDAO;
    }
    public void setWorkingdayDAO(WorkingdayDAO workingdayDAO) {
        this.workingdayDAO = workingdayDAO;
    }
    public void setTicketDAO(TicketDAO ticketDAO) {
        this.ticketDAO = ticketDAO;
    }
    
    @Override
    public ActionForward executeAuthenticated(ActionMapping mapping, ActionForm form, HttpServletRequest request, HttpServletResponse response) {
        
        AddDailyReportForm reportForm = (AddDailyReportForm)form;
        Employeecontract loginEmployeeContract = (Employeecontract)request.getSession().getAttribute("loginEmployeeContract");
        Employeecontract ec = null;
        
        if (request.getSession().getAttribute("currentEmployeeContract") != null &&
                (Boolean)request.getSession().getAttribute("employeeAuthorized")) {
            Employeecontract currentEmployeeContract = (Employeecontract)request.getSession().getAttribute("currentEmployeeContract");
            ec = currentEmployeeContract;
            
        } else {
            ec = loginEmployeeContract;
        }
        request.getSession().setAttribute("currentEmployee", ec.getEmployee().getName());
        request.getSession().setAttribute("currentEmployeeId", ec.getEmployee().getId());
        request.getSession().setAttribute("currentEmployeeContract", ec);
        
        if (ec == null) {
            request.setAttribute("errorMessage", "No employee contract found for employee - please call system administrator."); //TODO
            return mapping.findForward("error");
        }
        
        List<Employeecontract> employeecontracts = employeecontractDAO.getVisibleEmployeeContractsOrderedByEmployeeSign();
        request.getSession().setAttribute("employeecontracts", employeecontracts);
        
        // get selected date for new report
        Date selectedDate = getSelectedDateFromRequest(request);
        
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
        
        TimereportHelper th = new TimereportHelper();
        
        // search for adequate workingday and set status in session
        java.sql.Date currentDate = DateUtils.getSqlDate(selectedDate);
        Workingday workingday = workingdayDAO.getWorkingdayByDateAndEmployeeContractId(currentDate, ec.getId());
        
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
        int[] beginTime = th.determineBeginTimeToDisplay(ec.getId(), timereportDAO, selectedDate, workingday);
        reportForm.setSelectedHourBegin(beginTime[0]);
        reportForm.setSelectedMinuteBegin(beginTime[1]);
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
                reportForm.setSelectedMinuteEnd(minute);
                reportForm.setSelectedHourEnd(hour);
            } else {
                reportForm.setSelectedMinuteEnd(beginTime[1]);
                reportForm.setSelectedHourEnd(beginTime[0]);
            }
            TimereportHelper.refreshHours(reportForm);
        } else {
            reportForm.setSelectedHourDuration(0);
            reportForm.setSelectedMinuteDuration(0);
        }
        
        // init form with selected Date
        reportForm.setReferenceday(simpleDateFormat.format(selectedDate));
        
        // init form with first order and corresponding suborders
        List<Suborder> theSuborders = new ArrayList<Suborder>();
        if (orders != null && !orders.isEmpty()) {
            reportForm.setOrder(orders.get(0).getSign());
            reportForm.setOrderId(orders.get(0).getId());
            
            theSuborders = suborderDAO.getSubordersByEmployeeContractIdAndCustomerorderIdWithValidEmployeeOrders(ec.getId(), orders.get(0).getId(), selectedDate);
            
            if (theSuborders == null || theSuborders.isEmpty()) {
                request.setAttribute("errorMessage", "Orders/suborders inconsistent for employee - please call system administrator."); //TODO
                return mapping.findForward("error");
            }
            // set isEdit = false into the session, so order/suborder menu will not be disabled
            request.getSession().setAttribute("isEdit", false);
            
            List<ProjectID> projectIDs = customerorderDAO.getCustomerorderById(reportForm.getOrderId()).getProjectIDs();
            request.getSession().setAttribute("projectIDExists", !projectIDs.isEmpty());
        } else {
            request.setAttribute("errorMessage", "no orders found for employee - please call system administrator."); //TODO
            return mapping.findForward("error");
        }
        // prepare second collection of suborders sorted by description
        List<Suborder> subordersByDescription = new ArrayList<Suborder>();
        subordersByDescription.addAll(theSuborders);
        Collections.sort(subordersByDescription, new SubOrderByDescriptionComparator());
        request.getSession().setAttribute("suborders", theSuborders);
        request.getSession().setAttribute("subordersByDescription", subordersByDescription);
        request.getSession().setAttribute("currentSuborderId", theSuborders.get(0).getId());
        request.getSession().setAttribute("currentSuborderSign", theSuborders.get(0).getSign());
        
        // get first Suborder to synchronize suborder lists
        Suborder so = theSuborders.get(0);
        request.getSession().setAttribute("currentSuborderId", so.getId());
        
        JiraSalatHelper.setJiraTicketKeysForSuborder(request, ticketDAO, so.getId());
        
        // make sure, no cuId still exists in session
        request.getSession().removeAttribute("trId");
        // make sure that no jiraTicketKey is still set in session
        request.getSession().removeAttribute("jiraTicketKey");
        
        if (request.getParameter("task") != null && request.getParameter("task").equals("matrix")) {
            reportForm.setReferenceday(todayString);
        }
        
        // init the rest of the form
        reportForm.setCosts(0d);
        reportForm.setTraining(false);
        reportForm.setComment("");
        
        // store last selected order
        String lastOrder;
        try {
            lastOrder = (String)request.getSession().getAttribute("currentOrder");
        } catch (ClassCastException e) {
            Customerorder customerorder = (Customerorder)request.getSession().getAttribute("currentOrder");
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
        request.getSession().setAttribute("lastEmployeeContractId", reportForm.getEmployeeContractId());
        
        //  make sure that overtimeCompensation is set in the session so that the duration-dropdown-menu will be disabled for timereports with suborder uesa00
        if (request.getSession().getAttribute("overtimeCompensation") == null
                || request.getSession().getAttribute("overtimeCompensation") != GlobalConstants.SUBORDER_SIGN_OVERTIME_COMPENSATION) {
            request.getSession().setAttribute("overtimeCompensation", GlobalConstants.SUBORDER_SIGN_OVERTIME_COMPENSATION);
        }
        
        return mapping.findForward("success");
    }
}
