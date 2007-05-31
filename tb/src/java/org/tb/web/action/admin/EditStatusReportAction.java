package org.tb.web.action.admin;

import java.text.SimpleDateFormat;
import java.util.List;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;
import org.tb.bdom.Employee;
import org.tb.bdom.Statusreport;
import org.tb.persistence.CustomerorderDAO;
import org.tb.persistence.EmployeeDAO;
import org.tb.persistence.StatusReportDAO;
import org.tb.web.form.AddStatusReportForm;

public class EditStatusReportAction extends StatusReportAction {

	private EmployeeDAO employeeDAO;
	private StatusReportDAO statusReportDAO;
	private CustomerorderDAO customerorderDAO;
	
	public void setEmployeeDAO(EmployeeDAO employeeDAO) {
		this.employeeDAO = employeeDAO;
	}
	public void setStatusReportDAO(StatusReportDAO statusReportDAO) {
		this.statusReportDAO = statusReportDAO;
	}
	public void setCustomerorderDAO(CustomerorderDAO customerorderDAO) {
		this.customerorderDAO = customerorderDAO;
	}
	
	
	/* (non-Javadoc)
	 * @see org.tb.web.action.LoginRequiredAction#executeAuthenticated(org.apache.struts.action.ActionMapping, org.apache.struts.action.ActionForm, javax.servlet.http.HttpServletRequest, javax.servlet.http.HttpServletResponse)
	 */
	@Override
	protected ActionForward executeAuthenticated(ActionMapping mapping, ActionForm form, HttpServletRequest request, HttpServletResponse response) throws Exception {

		AddStatusReportForm reportForm = (AddStatusReportForm) form;
		
		// get status report to be edited
		Statusreport statusreport = null;
		
		try {
			Long srId = Long.parseLong(request.getParameter("srId"));
			statusreport = statusReportDAO.getStatusReportById(srId);
		} catch (Exception e) {
			// do nothing here
		}
		// go to error page, if statusreport is null
		if (statusreport == null) {
			return mapping.findForward("error");
		}
		
		// remove action info
		request.getSession().removeAttribute("actionInfo");
				
		// set collection of phases
		request.getSession().setAttribute("phases", getPhaseOptionList(request));
		
		// set collection of report sorts
		request.getSession().setAttribute("sorts", getSortOptionList(request));
		
		// set collection of employees for jsp
		List<Employee> employees = employeeDAO.getEmployeesWithContracts();
		request.getSession().setAttribute("employees", employees);
		
		// set collection of customers
		request.getSession().setAttribute("visibleCustomerOrders", customerorderDAO.getVisibleCustomerorders());

		
		// set selected customer order
		request.getSession().setAttribute("selectedCustomerOrder", statusreport.getCustomerorder());
		
		// set report status
		request.getSession().setAttribute("reportStatus", "id "+statusreport.getId());
		
		// set null as current statusreport
		request.getSession().setAttribute("currentStatusReport", statusreport);
		
		// report editable
		request.getSession().setAttribute("isReportEditable", isReportEditable(statusreport, request));
		
		// is report ready for release
		request.getSession().setAttribute("isReportReadyForRelease", isReportReadyForRelease(statusreport.getId(), statusReportDAO, request));
		
		// is report ready for acceptance
		request.getSession().setAttribute("isReportReadyForAcceptance", isReportReadyForAcceptance(statusreport.getId(), statusReportDAO, request));

		
		
		SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd");
		
		// set form entries
		reportForm.setAim_action(statusreport.getAim_action());
		reportForm.setAim_source(statusreport.getAim_source());
		reportForm.setAim_status(statusreport.getAim_status());
		reportForm.setAim_text(statusreport.getAim_text());
		
		reportForm.setAllocator(statusreport.getAllocator());
		
		reportForm.setBudget_resources_date_action(statusreport.getBudget_resources_date_action());
		reportForm.setBudget_resources_date_source(statusreport.getBudget_resources_date_source());
		reportForm.setBudget_resources_date_status(statusreport.getBudget_resources_date_status());
		reportForm.setBudget_resources_date_text(statusreport.getBudget_resources_date_text());
		
		reportForm.setChangedirective_action(statusreport.getChangedirective_action());
		reportForm.setChangedirective_source(statusreport.getChangedirective_source());
		reportForm.setChangedirective_status(statusreport.getChangedirective_status());
		reportForm.setChangedirective_text(statusreport.getChangedirective_text());
		
		reportForm.setCommunication_action(statusreport.getCommunication_action());
		reportForm.setCommunication_source(statusreport.getCommunication_source());
		reportForm.setCommunication_status(statusreport.getCommunication_status());
		reportForm.setCommunication_text(statusreport.getCommunication_text());
		
		reportForm.setCustomerOrderId(statusreport.getCustomerorder().getId());
		
		reportForm.setFromDateString(simpleDateFormat.format(statusreport.getFromdate()));
		
		reportForm.setImprovement_action(statusreport.getImprovement_action());
		reportForm.setImprovement_source(statusreport.getImprovement_source());
		reportForm.setImprovement_status(statusreport.getImprovement_status());
		reportForm.setImprovement_text(statusreport.getImprovement_text());
		
		reportForm.setMiscellaneous_action(statusreport.getMiscellaneous_action());
		reportForm.setMiscellaneous_source(statusreport.getMiscellaneous_source());
		reportForm.setMiscellaneous_status(statusreport.getMiscellaneous_status());
		reportForm.setMiscellaneous_text(statusreport.getMiscellaneous_text());
		
		reportForm.setNeedforaction_source(statusreport.getNeedforaction_source());
		reportForm.setNeedforaction_status(statusreport.getNeedforaction_status());
		reportForm.setNeedforaction_text(statusreport.getNeedforaction_text());
		
		reportForm.setNotes(statusreport.getNotes());
		
		reportForm.setOverallStatus(statusreport.getOverallStatus());
		
		reportForm.setPhase(statusreport.getPhase());
		
		reportForm.setRecipientId(statusreport.getRecipient().getId());
		
		reportForm.setRiskmonitoring_action(statusreport.getRiskmonitoring_action());
		reportForm.setRiskmonitoring_source(statusreport.getRiskmonitoring_source());
		reportForm.setRiskmonitoring_status(statusreport.getRiskmonitoring_status());
		reportForm.setRiskmonitoring_text(statusreport.getRiskmonitoring_text());
		
		reportForm.setSenderId(statusreport.getSender().getId());
		
		reportForm.setSort(statusreport.getSort());
		
		reportForm.setTrend(statusreport.getTrend());
		reportForm.setTrendstatus(statusreport.getTrendstatus());
		
		reportForm.setUntilDateString(simpleDateFormat.format(statusreport.getUntildate()));
				
		
		return mapping.findForward("success");
	}
	
	
}
