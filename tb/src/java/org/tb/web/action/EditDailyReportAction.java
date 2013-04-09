package org.tb.web.action;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
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
import org.tb.bdom.Suborder;
import org.tb.bdom.Timereport;
import org.tb.bdom.Workingday;
import org.tb.helper.TimereportHelper;
import org.tb.persistence.CustomerorderDAO;
import org.tb.persistence.EmployeeDAO;
import org.tb.persistence.EmployeecontractDAO;
import org.tb.persistence.SuborderDAO;
import org.tb.persistence.TimereportDAO;
import org.tb.persistence.WorkingdayDAO;
import org.tb.util.DateUtils;
import org.tb.web.form.AddDailyReportForm;

/**
 * Action class for editing of a timereport
 * 
 * @author oda
 *
 */
public class EditDailyReportAction extends DailyReportAction {
    
    private TimereportDAO timereportDAO;
    private CustomerorderDAO customerorderDAO;
    private SuborderDAO suborderDAO;
    private EmployeecontractDAO employeecontractDAO;
    private WorkingdayDAO workingdayDAO;
    private EmployeeDAO employeeDAO;
    
    public void setEmployeeDAO(EmployeeDAO employeeDAO) {
        this.employeeDAO = employeeDAO;
    }
    
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
    
    @Override
    public ActionForward executeAuthenticated(ActionMapping mapping, ActionForm form, HttpServletRequest request, HttpServletResponse response) {
        
        AddDailyReportForm reportForm = (AddDailyReportForm)form;
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
     * 
     * @param mapping
     * @param request
     * @param reportForm
     * @param tr
     */
    private void setFormEntries(ActionMapping mapping, HttpServletRequest request,
            AddDailyReportForm reportForm, Timereport tr) {
        
        Employeecontract ec = tr.getEmployeecontract();
        //		Employee theEmployee = ec.getEmployee();
        
        Date utilDate = new Date(tr.getReferenceday().getRefdate().getTime()); // convert to java.util.Date
        
        //		List<Suborder> theSuborders = suborderDAO.getSubordersByEmployeeContractId(ec.getId());
        List<Customerorder> orders = customerorderDAO.getCustomerordersWithValidEmployeeOrders(ec.getId(), utilDate);
        List<Suborder> theSuborders = new ArrayList<Suborder>();
        if (orders != null && !orders.isEmpty()) {
            reportForm.setOrder(orders.get(0).getSign());
            reportForm.setOrderId(orders.get(0).getId());
            theSuborders =
                    suborderDAO.getSubordersByEmployeeContractIdAndCustomerorderIdWithValidEmployeeOrders(ec.getId(), tr.getEmployeeorder().getSuborder().getCustomerorder().getId(), utilDate);
            if (theSuborders == null || theSuborders.isEmpty()) {
                request.setAttribute("errorMessage",
                        "Orders/suborders inconsistent for employee - please call system administrator.");
                mapping.findForward("error");
            }
        } else {
            request.setAttribute("errorMessage",
                    "no orders found for employee - please call system administrator.");
            mapping.findForward("error");
        }
        
        //		 prepare second collection of suborders sorted by description
        //		List<Suborder> subordersByDescription = new ArrayList<Suborder>();
        //		subordersByDescription.addAll(theSuborders);
        //		Collections.sort(subordersByDescription, new SubOrderByDescriptionComparator());
        
        //		List<Employee> employeeOptionList = employeeDAO.getEmployeesWithContracts();
        //		request.getSession().setAttribute("employees", employeeOptionList);
        
        List<Employeecontract> employeecontracts = employeecontractDAO.getVisibleEmployeeContractsOrderedByEmployeeSign();
        request.getSession().setAttribute("employeecontracts", employeecontracts);
        
        /* set hours list in session in case of that the dialog is triggered from the welcome page */
        request.getSession().setAttribute("hours", DateUtils.getHoursToDisplay());
        
        request.getSession().setAttribute("trId", tr.getId());
        request.getSession().setAttribute("orders", orders);
        request.getSession().setAttribute("suborders", theSuborders);
        request.getSession().setAttribute("currentSuborderId", tr.getEmployeeorder().getSuborder().getId());
        //		request.getSession().setAttribute("subordersByDescription", subordersByDescription);
        request.getSession().setAttribute("serialBookings", getSerialDayList());
        
        reportForm.reset(mapping, request);
        //		reportForm.setEmployeename(theEmployee.getFirstname() + theEmployee.getLastname());
        reportForm.setEmployeeContractId(ec.getId());
        
        reportForm.setReferenceday(DateUtils.getSqlDateString(utilDate));
        java.sql.Date reportDate = tr.getReferenceday().getRefdate();
        Workingday workingday = workingdayDAO.getWorkingdayByDateAndEmployeeContractId(reportDate, ec.getId());
        
        boolean workingDayIsAvailable = false;
        if (workingday != null) {
            workingDayIsAvailable = true;
        }
        
        //		 workingday should only be available for today
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
                
                //				theSuborders = tr.getSuborder().getCustomerorder().getSuborders();
                
                //				 prepare second collection of suborders sorted by description
                //				subordersByDescription.clear();
                //				subordersByDescription.addAll(theSuborders);
                //				Collections.sort(subordersByDescription, new SubOrderByDescriptionComparator());
                
                //				request.getSession().setAttribute("currentSuborderId", tr.getSuborder().getId());
                //				request.getSession().setAttribute("suborders", tr.getSuborder().getCustomerorder().getSuborders());
                //				request.getSession().setAttribute("subordersByDescription", subordersByDescription);
            }
            reportForm.setCosts(tr.getCosts());
            reportForm.setStatus(tr.getStatus());
        }
        reportForm.setComment(tr.getTaskdescription());
        reportForm.setTraining(tr.getTraining());
    }
    
}
