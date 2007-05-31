package org.tb.web.action.admin;

import java.sql.Date;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.List;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;
import org.apache.struts.action.ActionMessage;
import org.apache.struts.action.ActionMessages;
import org.tb.GlobalConstants;
import org.tb.bdom.Customerorder;
import org.tb.bdom.Employee;
import org.tb.bdom.Statusreport;
import org.tb.persistence.CustomerorderDAO;
import org.tb.persistence.EmployeeDAO;
import org.tb.persistence.StatusReportDAO;
import org.tb.web.form.AddStatusReportForm;

public class StoreStatusReportAction extends StatusReportAction {

	private StatusReportDAO statusReportDAO;
	private CustomerorderDAO customerorderDAO;
	private EmployeeDAO employeeDAO;
	
	public void setStatusReportDAO(StatusReportDAO statusReportDAO) {
		this.statusReportDAO = statusReportDAO;
	}
	public void setCustomerorderDAO(CustomerorderDAO customerorderDAO) {
		this.customerorderDAO = customerorderDAO;
	}
	public void setEmployeeDAO(EmployeeDAO employeeDAO) {
		this.employeeDAO = employeeDAO;
	}
	
	
	/* (non-Javadoc)
	 * @see org.tb.web.action.LoginRequiredAction#executeAuthenticated(org.apache.struts.action.ActionMapping, org.apache.struts.action.ActionForm, javax.servlet.http.HttpServletRequest, javax.servlet.http.HttpServletResponse)
	 */
	@Override
	protected ActionForward executeAuthenticated(ActionMapping mapping, ActionForm form, HttpServletRequest request, HttpServletResponse response) throws Exception {
		
		AddStatusReportForm reportForm = (AddStatusReportForm) form;
		boolean backAction = false;
		
		// action release
		if ((request.getParameter("action") != null)
				&& (request.getParameter("action").equals("release"))) {
			
			Statusreport currentReport = (Statusreport) request.getSession().getAttribute("currentStatusReport");

			if (currentReport == null) {
				return mapping.findForward("error");
			} 
			
			if (isReportReadyForRelease(currentReport.getId(), statusReportDAO, request) && formEntriesEqualDB(currentReport.getId(), reportForm)) {						
				
				// get it fresh from db
				currentReport = statusReportDAO.getStatusReportById(currentReport.getId());
				
				Employee loginEmployee = (Employee) request.getSession().getAttribute("loginEmployee");
				
				currentReport.setReleasedby(employeeDAO.getEmployeeById(loginEmployee.getId()));
				currentReport.setReleased(new Date(new java.util.Date().getTime()));
				
				statusReportDAO.save(currentReport, loginEmployee);
				
				// is report ready for release
				request.getSession().setAttribute("isReportReadyForRelease", isReportReadyForRelease(currentReport.getId(), statusReportDAO, request));
				
				// is report ready for acceptance
				request.getSession().setAttribute("isReportReadyForAcceptance", isReportReadyForAcceptance(currentReport.getId(), statusReportDAO, request));

				// is report editable
				request.getSession().setAttribute("isReportEditable", isReportEditable(currentReport, request));
				
				// set action info
				request.getSession().setAttribute("actionInfo", getResources(request).getMessage(getLocale(request), "statusreport.actioninfo.released.text"));
				
				// set current report
				request.getSession().setAttribute("currentStatusReport", currentReport);
				
			} else {
				// set action info
				request.getSession().setAttribute("actionInfo", getResources(request).getMessage(getLocale(request), "statusreport.actioninfo.notreleased.text"));
			}
			return mapping.findForward("success");
		} // end action release
		
		
		// action accept
		if ((request.getParameter("action") != null)
				&& (request.getParameter("action").equals("accept"))) {
			
			Statusreport currentReport = (Statusreport) request.getSession().getAttribute("currentStatusReport");

			if (currentReport == null) {
				return mapping.findForward("error");
			} 
			
			if (isReportReadyForAcceptance(currentReport.getId(), statusReportDAO, request)) {						
				
				// get it fresh from db
				currentReport = statusReportDAO.getStatusReportById(currentReport.getId());
				
				Employee loginEmployee = (Employee) request.getSession().getAttribute("loginEmployee");
				
				currentReport.setAcceptedby(employeeDAO.getEmployeeById(loginEmployee.getId()));
				currentReport.setAccepted(new Date(new java.util.Date().getTime()));
				
				statusReportDAO.save(currentReport, loginEmployee);
				
				// is report ready for release
				request.getSession().setAttribute("isReportReadyForRelease", isReportReadyForRelease(currentReport.getId(), statusReportDAO, request));
				
				// is report ready for acceptance
				request.getSession().setAttribute("isReportReadyForAcceptance", isReportReadyForAcceptance(currentReport.getId(), statusReportDAO, request));

				// is report editable
				request.getSession().setAttribute("isReportEditable", isReportEditable(currentReport, request));
				
				// set action info
				request.getSession().setAttribute("actionInfo", getResources(request).getMessage(getLocale(request), "statusreport.actioninfo.accepted.text"));
				
				// set current report
				request.getSession().setAttribute("currentStatusReport", currentReport);
				
			} else {
				// set action info
				request.getSession().setAttribute("actionInfo", getResources(request).getMessage(getLocale(request), "statusreport.actioninfo.notaccepted.text"));
			}
			return mapping.findForward("success");
		} // end action accept

		
		// action remove release
		if ((request.getParameter("action") != null)
				&& (request.getParameter("action").equals("removeRelease"))) {
			
			Employee loginEmployee = (Employee) request.getSession().getAttribute("loginEmployee");
		
			// remove cation info
			request.getSession().removeAttribute("actionInfo");
			
			if (loginEmployee.getStatus().equals(GlobalConstants.EMPLOYEE_STATUS_ADM) && request.getSession().getAttribute("currentStatusReport") != null) {
				Statusreport currentReport = (Statusreport) request.getSession().getAttribute("currentStatusReport");
				Statusreport statusreport = statusReportDAO.getStatusReportById(currentReport.getId());
				statusreport.setReleased(null);
				statusreport.setReleasedby(null);
				statusreport.setAccepted(null);
				statusreport.setAcceptedby(null);
				statusReportDAO.save(statusreport, loginEmployee);	
				
				// set action info
				request.getSession().setAttribute("actionInfo", getResources(request).getMessage(getLocale(request), "statusreport.actioninfo.releaseremoved.text"));
				
				// set current report
				request.getSession().setAttribute("currentStatusReport", statusreport);
				
				// is report ready for release
				request.getSession().setAttribute("isReportReadyForRelease", isReportReadyForRelease(statusreport.getId(), statusReportDAO, request));
				
				// is report ready for acceptance
				request.getSession().setAttribute("isReportReadyForAcceptance", isReportReadyForAcceptance(statusreport.getId(), statusReportDAO, request));

				// is report editable
				request.getSession().setAttribute("isReportEditable", isReportEditable(statusreport, request));
			}			
			
			return mapping.findForward("success");
		} // end action remove release
		
		
		// action refresh
		if ((request.getParameter("action") != null)
				&& (request.getParameter("action").equals("refresh"))) {
			
			// refresh sender, recipient and number
			
			Customerorder selectedCustomerOrder = customerorderDAO.getCustomerorderById(reportForm.getCustomerOrderId());
			if (selectedCustomerOrder == null) {
				return mapping.findForward("error");
			}
			if (selectedCustomerOrder.getResponsible_hbt() != null) {
				// set sender & recipient in form
				reportForm.setSenderId(selectedCustomerOrder.getResponsible_hbt().getId());
			}			
			if (selectedCustomerOrder.getRespEmpHbtContract() != null) {
				reportForm.setRecipientId(selectedCustomerOrder.getRespEmpHbtContract().getId());
			}	
			
			// refresh fromdate
			List<Statusreport> existingReports = statusReportDAO.getStatusReportsByCustomerOrderId(selectedCustomerOrder.getId());
			Date fromDate = selectedCustomerOrder.getFromDate();
			SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd");
			if (existingReports != null && !existingReports.isEmpty()) {
				Statusreport lastKnownReport = existingReports.get(existingReports.size() -1);
				
				if (request.getSession().getAttribute("currentStatusReport") != null &&
						((Statusreport) request.getSession().getAttribute("currentStatusReport")).getId() == lastKnownReport.getId()) {
					fromDate = lastKnownReport.getFromdate();
					reportForm.setUntilDateString(simpleDateFormat.format(lastKnownReport.getUntildate()));
				} else {				
					fromDate = lastKnownReport.getUntildate();
					GregorianCalendar calendar = new GregorianCalendar();
					calendar.setTime(fromDate);
					calendar.add(Calendar.DATE, 1);
					fromDate.setTime(calendar.getTimeInMillis());
				}
			}		
			reportForm.setFromDateString(simpleDateFormat.format(fromDate));
						
			// remove actionInfo
			request.getSession().removeAttribute("actionInfo");
			
			// set selected customer order
			request.getSession().setAttribute("selectedCustomerOrder", selectedCustomerOrder);
			
			return mapping.findForward("success");
			
		} // end action refresh
		
		// action save
		if ((request.getParameter("action") != null)
				&& (request.getParameter("action").equals("save"))) {
			
			// existing or new one?
			Statusreport currentReport = (Statusreport) request.getSession().getAttribute("currentStatusReport");

			if (currentReport == null) {
				currentReport = new Statusreport();
			} else {
				// get it fresh from db
				currentReport = statusReportDAO.getStatusReportById(currentReport.getId());
			}
			
			// belongs to which customerorder?
			Customerorder customerorder = customerorderDAO.getCustomerorderById(reportForm.getCustomerOrderId());
			if (customerorder == null) {
				return mapping.findForward("error");
			}
			
			// validate
			ActionMessages errorMessages = validateFormData(request, reportForm);
			if (errorMessages.size() > 0) {
				// set action info
				request.getSession().setAttribute("actionInfo", getResources(request).getMessage(getLocale(request), "statusreport.actioninfo.notsaved.text"));
				return mapping.getInputForward();
			}
			
			// set attributes:			
			currentReport.setCustomerorder(customerorder);
			
			currentReport.setSort(reportForm.getSort());
			
			currentReport.setSender(employeeDAO.getEmployeeById(reportForm.getSenderId()));
			currentReport.setRecipient(employeeDAO.getEmployeeById(reportForm.getRecipientId()));
			
			// get dates from validate later
			SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd");
			Date reportFromDate = new Date(simpleDateFormat.parse(reportForm.getFromDateString()).getTime());
			Date reportUntilDate = new Date(simpleDateFormat.parse(reportForm.getUntilDateString()).getTime());
			currentReport.setFromdate(reportFromDate);
			currentReport.setUntildate(reportUntilDate);
			
			currentReport.setPhase(reportForm.getPhase());
			
			currentReport.setAllocator(reportForm.getAllocator());
			
			currentReport.setTrend(reportForm.getTrend());
			currentReport.setTrendstatus(reportForm.getTrendstatus());
			
			currentReport.setNeedforaction_source(reportForm.getNeedforaction_source());
			currentReport.setNeedforaction_status(reportForm.getNeedforaction_status());
			currentReport.setNeedforaction_text(reportForm.getNeedforaction_text());
			
			currentReport.setAim_action(reportForm.getAim_action());
			currentReport.setAim_source(reportForm.getAim_source());
			currentReport.setAim_status(reportForm.getAim_status());
			currentReport.setAim_text(reportForm.getAim_text());
			
			currentReport.setBudget_resources_date_action(reportForm.getBudget_resources_date_action());
			currentReport.setBudget_resources_date_source(reportForm.getBudget_resources_date_source());
			currentReport.setBudget_resources_date_status(reportForm.getBudget_resources_date_status());
			currentReport.setBudget_resources_date_text(reportForm.getBudget_resources_date_text());
			
			currentReport.setRiskmonitoring_action(reportForm.getRiskmonitoring_action());
			currentReport.setRiskmonitoring_source(reportForm.getRiskmonitoring_source());
			currentReport.setRiskmonitoring_status(reportForm.getRiskmonitoring_status());
			currentReport.setRiskmonitoring_text(reportForm.getRiskmonitoring_text());
			
			currentReport.setChangedirective_action(reportForm.getChangedirective_action());
			currentReport.setChangedirective_source(reportForm.getChangedirective_source());
			currentReport.setChangedirective_status(reportForm.getChangedirective_status());
			currentReport.setChangedirective_text(reportForm.getChangedirective_text());
			
			currentReport.setCommunication_action(reportForm.getCommunication_action());
			currentReport.setCommunication_source(reportForm.getCommunication_source());
			currentReport.setCommunication_status(reportForm.getCommunication_status());
			currentReport.setCommunication_text(reportForm.getCommunication_text());
			
			currentReport.setImprovement_action(reportForm.getImprovement_action());
			currentReport.setImprovement_source(reportForm.getImprovement_source());
			currentReport.setImprovement_status(reportForm.getImprovement_status());
			currentReport.setImprovement_text(reportForm.getImprovement_text());
			
			currentReport.setMiscellaneous_action(reportForm.getMiscellaneous_action());
			currentReport.setMiscellaneous_source(reportForm.getMiscellaneous_source());
			currentReport.setMiscellaneous_status(reportForm.getMiscellaneous_status());
			currentReport.setMiscellaneous_text(reportForm.getMiscellaneous_text());
			
			currentReport.setNotes(reportForm.getNotes());
			
			//save
			Employee loginEmployee = (Employee) request.getSession().getAttribute("loginEmployee");
			statusReportDAO.save(currentReport, loginEmployee);
			
			// set action info
			request.getSession().setAttribute("actionInfo", getResources(request).getMessage(getLocale(request), "statusreport.actioninfo.saved.text"));
			
			// set current report
			request.getSession().setAttribute("currentStatusReport", currentReport);
			
			// set selected customer order
			request.getSession().setAttribute("selectedCustomerOrder", customerorder);
			
			// set report status
			request.getSession().setAttribute("reportStatus", "id "+currentReport.getId());
			
			// set overall status
			reportForm.setOverallStatus(currentReport.getOverallStatus());
			
			// is report ready for release
			request.getSession().setAttribute("isReportReadyForRelease", isReportReadyForRelease(currentReport.getId(), statusReportDAO, request));
			
			// is report ready for acceptance
			request.getSession().setAttribute("isReportReadyForAcceptance", isReportReadyForAcceptance(currentReport.getId(), statusReportDAO, request));

			
			return mapping.findForward("success");
			
		} // end action save
		
		
		// action back
		if (((request.getParameter("action") != null)
				&& (request.getParameter("action").equals("back"))) || backAction) {
			
			// refresh list of reports for overview
			Long customerOrderId = (Long) request.getSession().getAttribute("customerOrderId");
			
			List<Statusreport> statusReports;
			if (customerOrderId != null && customerOrderId != 0 && customerOrderId != -1) {
				statusReports = statusReportDAO.getStatusReportsByCustomerOrderId(customerOrderId);
			} else {
				statusReports = statusReportDAO.getVisibleStatusReports();
			}
			
			request.getSession().setAttribute("statusReports", statusReports);
			
			
			return mapping.findForward("back");
		} // end action back
		
		return mapping.findForward("success");
		
	}
	
	
	private boolean formEntriesEqualDB(Long srId, AddStatusReportForm reportForm) {
		Statusreport statusreport = statusReportDAO.getStatusReportById(srId);
		SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd");
				
		try {
			return (reportForm.getAim_action().equals(statusreport.getAim_action()) &&
					reportForm.getAim_source().equals(statusreport.getAim_source()) &&
					reportForm.getAim_status().equals(statusreport.getAim_status()) &&
					reportForm.getAim_text().equals(statusreport.getAim_text()) &&
					reportForm.getAllocator().equals(statusreport.getAllocator()) &&
					reportForm.getBudget_resources_date_action().equals(statusreport.getBudget_resources_date_action()) &&
					reportForm.getBudget_resources_date_source().equals(statusreport.getBudget_resources_date_source()) &&
					reportForm.getBudget_resources_date_status().equals(statusreport.getBudget_resources_date_status()) &&
					reportForm.getBudget_resources_date_text().equals(statusreport.getBudget_resources_date_text()) &&
					reportForm.getChangedirective_action().equals(statusreport.getChangedirective_action()) &&
					reportForm.getChangedirective_source().equals(statusreport.getChangedirective_source()) &&
					reportForm.getChangedirective_status().equals(statusreport.getChangedirective_status()) &&
					reportForm.getChangedirective_text().equals(statusreport.getChangedirective_text()) &&
					reportForm.getCommunication_action().equals(statusreport.getCommunication_action()) &&
					reportForm.getCommunication_source().equals(statusreport.getCommunication_source()) &&
					reportForm.getCommunication_status().equals(statusreport.getCommunication_status()) &&
					reportForm.getCommunication_text().equals(statusreport.getCommunication_text()) &&
					reportForm.getCustomerOrderId() == statusreport.getCustomerorder().getId() &&
					reportForm.getFromDateString().equals(simpleDateFormat.format(statusreport.getFromdate())) &&
					reportForm.getImprovement_action().equals(statusreport.getImprovement_action()) &&
					reportForm.getImprovement_source().equals(statusreport.getImprovement_source()) &&
					reportForm.getImprovement_status().equals(statusreport.getImprovement_status()) &&
					reportForm.getImprovement_text().equals(statusreport.getImprovement_text()) &&
					reportForm.getMiscellaneous_action().equals(statusreport.getMiscellaneous_action()) &&
					reportForm.getMiscellaneous_source().equals(statusreport.getMiscellaneous_source()) &&
					reportForm.getMiscellaneous_status().equals(statusreport.getMiscellaneous_status()) &&
					reportForm.getMiscellaneous_text().equals(statusreport.getMiscellaneous_text()) &&
					reportForm.getNeedforaction_source().equals(statusreport.getNeedforaction_source()) &&
					reportForm.getNeedforaction_status().equals(statusreport.getNeedforaction_status()) &&
					reportForm.getNeedforaction_text().equals(statusreport.getNeedforaction_text()) &&
					reportForm.getPhase().equals(statusreport.getPhase()) &&
					reportForm.getRecipientId() == statusreport.getRecipient().getId() &&
					reportForm.getRiskmonitoring_action().equals(statusreport.getRiskmonitoring_action()) &&
					reportForm.getRiskmonitoring_source().equals(statusreport.getRiskmonitoring_source()) &&
					reportForm.getRiskmonitoring_status().equals(statusreport.getRiskmonitoring_status()) &&
					reportForm.getRiskmonitoring_text().equals(statusreport.getRiskmonitoring_text()) &&
					reportForm.getSenderId() == statusreport.getSender().getId() &&
					reportForm.getSort().equals(statusreport.getSort()) &&
					reportForm.getTrend().equals(statusreport.getTrend()) &&
					reportForm.getTrendstatus().equals(statusreport.getTrendstatus()) &&
					reportForm.getUntilDateString().equals(simpleDateFormat.format(statusreport.getUntildate()))
			);			
		} catch (NullPointerException e) {
			return false;
		}

	}
	
	
	/**
	 * Validates the form data.
	 * 
	 * @param request
	 * @param reportForm
	 * @return Returns the errors as {@link ActionMessages}.
	 */
	private ActionMessages validateFormData(HttpServletRequest request,
			AddStatusReportForm reportForm) {

		ActionMessages errors = getErrors(request);
		if (errors == null)
			errors = new ActionMessages();
		
		SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd");
		
		// check dates
		String fromDateString = reportForm.getFromDateString();
		java.util.Date fromDate = null;
		try {
			fromDate = simpleDateFormat.parse(fromDateString);
		} catch (java.text.ParseException exception) {
			errors.add("fromdate", new ActionMessage("form.statusreport.error.fromdate.invalid.text"));
		}
		String untilDateString = reportForm.getUntilDateString();
		java.util.Date untilDate = null;
		try {
			untilDate = simpleDateFormat.parse(untilDateString);
		} catch (java.text.ParseException exception) {
			errors.add("untildate", new ActionMessage("form.statusreport.error.untildate.invalid.text"));
		}
		if (fromDate != null && untilDate != null && !fromDate.before(untilDate)) {
			errors.add("fromdate", new ActionMessage("form.statusreport.error.fromdate.notbefore.untildate.text"));
		}
		
		// check allocator
		String allocator = reportForm.getAllocator();
		if (allocator.length() > GlobalConstants.FORM_MAX_CHAR_TEXTFIELD) {
			errors.add("allocator", new ActionMessage("form.error.toomanychars.64.text"));
		}
	
		// check trend
		if (reportForm.getTrend() == null || reportForm.getTrend() == (byte)0) {
			errors.add("trend", new ActionMessage("form.statusreport.error.trend.notselected.text"));
		}
		
		String text;
		String source;
		String action;
		
		// check need for action
		text = reportForm.getNeedforaction_text();
		source = reportForm.getNeedforaction_source();
		if (text.length() > GlobalConstants.FORM_MAX_CHAR_BIG_TEXTAREA) {
			errors.add("needforaction_text", new ActionMessage("form.error.toomanychars.2048.text"));
		}
		if (text.trim().equals("")) {
			errors.add("needforaction_text", new ActionMessage("form.error.mandatoryfield.text"));
		}
		if (source.length() > GlobalConstants.FORM_MAX_CHAR_TEXTAREA) {
			errors.add("needforaction_source", new ActionMessage("form.error.toomanychars.256.text"));
		}
		
		// check aim
		text = reportForm.getAim_text();
		source = reportForm.getAim_source();
		action = reportForm.getAim_action();
		if (text.length() > GlobalConstants.FORM_MAX_CHAR_BIG_TEXTAREA) {
			errors.add("aim_text", new ActionMessage("form.error.toomanychars.2048.text"));
		}
		if (text.trim().equals("")) {
			errors.add("aim_text", new ActionMessage("form.error.mandatoryfield.text"));
		}
		if (source.length() > GlobalConstants.FORM_MAX_CHAR_TEXTAREA) {
			errors.add("aim_source", new ActionMessage("form.error.toomanychars.256.text"));
		}
		if (action.length() > GlobalConstants.FORM_MAX_CHAR_TEXTAREA) {
			errors.add("aim_action", new ActionMessage("form.error.toomanychars.256.text"));
		}
		
		// check budget resources date
		text = reportForm.getBudget_resources_date_text();
		source = reportForm.getBudget_resources_date_source();
		action = reportForm.getBudget_resources_date_action();
		if (text.length() > GlobalConstants.FORM_MAX_CHAR_BIG_TEXTAREA) {
			errors.add("budget_resources_date_text", new ActionMessage("form.error.toomanychars.2048.text"));
		}
		if (text.trim().equals("")) {
			errors.add("budget_resources_date_text", new ActionMessage("form.error.mandatoryfield.text"));
		}
		if (source.length() > GlobalConstants.FORM_MAX_CHAR_TEXTAREA) {
			errors.add("budget_resources_date_source", new ActionMessage("form.error.toomanychars.256.text"));
		}
		if (action.length() > GlobalConstants.FORM_MAX_CHAR_TEXTAREA) {
			errors.add("budget_resources_date_action", new ActionMessage("form.error.toomanychars.256.text"));
		}
		
		// check risk monitoring
		text = reportForm.getRiskmonitoring_text();
		source = reportForm.getRiskmonitoring_source();
		action = reportForm.getRiskmonitoring_action();
		if (text.length() > GlobalConstants.FORM_MAX_CHAR_BIG_TEXTAREA) {
			errors.add("riskmonitoring_text", new ActionMessage("form.error.toomanychars.2048.text"));
		}
		if (text.trim().equals("")) {
			errors.add("riskmonitoring_text", new ActionMessage("form.error.mandatoryfield.text"));
		}
		if (source.length() > GlobalConstants.FORM_MAX_CHAR_TEXTAREA) {
			errors.add("riskmonitoring_source", new ActionMessage("form.error.toomanychars.256.text"));
		}
		if (action.length() > GlobalConstants.FORM_MAX_CHAR_TEXTAREA) {
			errors.add("riskmonitoring_action", new ActionMessage("form.error.toomanychars.256.text"));
		}
		
		// check change directive
		text = reportForm.getChangedirective_text();
		source = reportForm.getChangedirective_source();
		action = reportForm.getChangedirective_action();
		if (text.length() > GlobalConstants.FORM_MAX_CHAR_BIG_TEXTAREA) {
			errors.add("changedirective_text", new ActionMessage("form.error.toomanychars.2048.text"));
		}
		if (text.trim().equals("")) {
			errors.add("changedirective_text", new ActionMessage("form.error.mandatoryfield.text"));
		}
		if (source.length() > GlobalConstants.FORM_MAX_CHAR_TEXTAREA) {
			errors.add("changedirective_source", new ActionMessage("form.error.toomanychars.256.text"));
		}
		if (action.length() > GlobalConstants.FORM_MAX_CHAR_TEXTAREA) {
			errors.add("changedirective_action", new ActionMessage("form.error.toomanychars.256.text"));
		}
		
		// check communication
		text = reportForm.getCommunication_text();
		source = reportForm.getCommunication_source();
		action = reportForm.getCommunication_action();
		if (text.length() > GlobalConstants.FORM_MAX_CHAR_BIG_TEXTAREA) {
			errors.add("communication_text", new ActionMessage("form.error.toomanychars.2048.text"));
		}
		if (text.trim().equals("")) {
			errors.add("communication_text", new ActionMessage("form.error.mandatoryfield.text"));
		}
		if (source.length() > GlobalConstants.FORM_MAX_CHAR_TEXTAREA) {
			errors.add("communication_source", new ActionMessage("form.error.toomanychars.256.text"));
		}
		if (action.length() > GlobalConstants.FORM_MAX_CHAR_TEXTAREA) {
			errors.add("communication_action", new ActionMessage("form.error.toomanychars.256.text"));
		}
		
		// check improvement
		text = reportForm.getImprovement_text();
		source = reportForm.getImprovement_source();
		action = reportForm.getImprovement_action();
		if (text.length() > GlobalConstants.FORM_MAX_CHAR_BIG_TEXTAREA) {
			errors.add("improvement_text", new ActionMessage("form.error.toomanychars.2048.text"));
		}
		if (text.trim().equals("")) {
			errors.add("improvement_text", new ActionMessage("form.error.mandatoryfield.text"));
		}
		if (source.length() > GlobalConstants.FORM_MAX_CHAR_TEXTAREA) {
			errors.add("improvement_source", new ActionMessage("form.error.toomanychars.256.text"));
		}
		if (action.length() > GlobalConstants.FORM_MAX_CHAR_TEXTAREA) {
			errors.add("improvement_action", new ActionMessage("form.error.toomanychars.256.text"));
		}
				
		// check miscellaneous
		text = reportForm.getMiscellaneous_text();
		source = reportForm.getMiscellaneous_source();
		action = reportForm.getMiscellaneous_action();
		if (text.length() > GlobalConstants.FORM_MAX_CHAR_BIG_TEXTAREA) {
			errors.add("miscellaneous_text", new ActionMessage("form.error.toomanychars.2048.text"));
		}
		if (source.length() > GlobalConstants.FORM_MAX_CHAR_TEXTAREA) {
			errors.add("miscellaneous_source", new ActionMessage("form.error.toomanychars.256.text"));
		}
		if (action.length() > GlobalConstants.FORM_MAX_CHAR_TEXTAREA) {
			errors.add("miscellaneous_action", new ActionMessage("form.error.toomanychars.256.text"));
		}
		
		// check notes
		text = reportForm.getNotes();
		if (text.length() > GlobalConstants.FORM_MAX_CHAR_BIG_TEXTAREA) {
			errors.add("notes", new ActionMessage("form.error.toomanychars.2048.text"));
		}
		
				
		saveErrors(request, errors);

		return errors;
	}

}
