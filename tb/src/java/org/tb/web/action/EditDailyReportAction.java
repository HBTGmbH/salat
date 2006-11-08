package org.tb.web.action;

import java.util.Date;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;
import org.tb.bdom.Employee;
import org.tb.bdom.Employeecontract;
import org.tb.bdom.Timereport;
import org.tb.bdom.Workingday;
import org.tb.helper.TimereportHelper;
import org.tb.persistence.CustomerorderDAO;
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
public class EditDailyReportAction extends LoginRequiredAction {
	
	private TimereportDAO timereportDAO;
	private CustomerorderDAO customerorderDAO;
	private SuborderDAO suborderDAO;
	private EmployeecontractDAO employeecontractDAO;	
	private WorkingdayDAO workingdayDAO;
	
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
		
		AddDailyReportForm reportForm = (AddDailyReportForm) form;
		long trId = Long.parseLong(request.getParameter("trId"));
		Timereport tr = timereportDAO.getTimereportById(trId);
		
		// fill the form with properties of the timereport to be edited
		setFormEntries(mapping, request, reportForm, tr);
		
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
		Employee theEmployee = ec.getEmployee();
		
		request.getSession().setAttribute("trId", tr.getId());
		request.getSession().setAttribute("orders", customerorderDAO.getCustomerordersByEmployeeContractId(ec.getId()));
		request.getSession().setAttribute("suborders", suborderDAO.getSubordersByEmployeeContractId(ec.getId()));
		
		reportForm.reset(mapping, request);
		reportForm.setEmployeename(theEmployee.getFirstname() + theEmployee.getLastname());
		Date utilDate = new Date(tr.getReferenceday().getRefdate().getTime()); // convert to java.util.Date
		
		reportForm.setReferenceday(DateUtils.getSqlDateString(utilDate));
		java.sql.Date reportDate = tr.getReferenceday().getRefdate();
		Workingday workingday = workingdayDAO.getWorkingdayByDateAndEmployeeContractId(reportDate, ec.getId());
		
		boolean workingDayIsAvailable = false;
		if (workingday != null) {
			workingDayIsAvailable = true;
		} 
		request.getSession().setAttribute("workingDayIsAvailable", workingDayIsAvailable);
		TimereportHelper th = new TimereportHelper();
		int[] displayTime = th.determineTimesToDisplay(ec.getId(), timereportDAO, reportDate, workingday, tr);
			
		reportForm.setSelectedHourBegin(displayTime[0]);
		reportForm.setSelectedMinuteBegin(displayTime[1]);
		reportForm.setSelectedHourEnd(displayTime[2]);
		reportForm.setSelectedMinuteEnd(displayTime[3]);
		reportForm.setComment(tr.getTaskdescription());
		
		TimereportHelper.refreshHours(reportForm);
				
		reportForm.setSortOfReport(tr.getSortofreport());
		request.getSession().setAttribute("report", tr.getSortofreport());
		if (tr.getSortofreport().equals("W")) {
			if ((tr.getSuborder() != null) && (tr.getSuborder().getCustomerorder() != null)) { 
				reportForm.setSuborder(tr.getSuborder().getSign());
				reportForm.setSuborderSignId(tr.getSuborder().getId());
				reportForm.setSuborderDescriptionId(tr.getSuborder().getId());
				reportForm.setOrder(tr.getSuborder().getCustomerorder().getSign());
				reportForm.setOrderId(tr.getSuborder().getCustomerorder().getId());	
				request.getSession().setAttribute("currentSuborderId", tr.getSuborder().getId());
				request.getSession().setAttribute("suborders", tr.getSuborder().getCustomerorder().getSuborders());
			}
			reportForm.setCosts(tr.getCosts());		
			reportForm.setStatus(tr.getStatus());
		}
	}
	
}
