package org.tb.web.action.admin;

import java.util.ArrayList;
import java.util.List;

import javax.servlet.http.HttpServletRequest;

import org.tb.GlobalConstants;
import org.tb.bdom.Employee;
import org.tb.bdom.Statusreport;
import org.tb.persistence.StatusReportDAO;
import org.tb.util.OptionItem;
import org.tb.web.action.LoginRequiredAction;

public abstract class StatusReportAction extends LoginRequiredAction {

	
	protected List<OptionItem> getPhaseOptionList(HttpServletRequest request) {
		List<OptionItem> phaseList = new ArrayList<OptionItem>();
		phaseList.add(new OptionItem(GlobalConstants.PHASE_ID_ORGANISATION, getResources(request).getMessage(getLocale(request), "statusreport.phase.organisation.text")));
		phaseList.add(new OptionItem(GlobalConstants.PHASE_ID_SPECIFICATIO, getResources(request).getMessage(getLocale(request), "statusreport.phase.specification.text")));
		phaseList.add(new OptionItem(GlobalConstants.PHASE_ID_ANALYSIS, getResources(request).getMessage(getLocale(request), "statusreport.phase.analysis.text")));
		phaseList.add(new OptionItem(GlobalConstants.PHASE_ID_REALIZATION, getResources(request).getMessage(getLocale(request), "statusreport.phase.realization.text")));
		phaseList.add(new OptionItem(GlobalConstants.PHASE_ID_ACCEPTANCE, getResources(request).getMessage(getLocale(request), "statusreport.phase.acceptance.text")));
		phaseList.add(new OptionItem(GlobalConstants.PHASE_ID_DELIVERY, getResources(request).getMessage(getLocale(request), "statusreport.phase.delivery.text")));
		phaseList.add(new OptionItem(GlobalConstants.PHASE_ID_ROLLOUT, getResources(request).getMessage(getLocale(request), "statusreport.phase.rollout.text")));
		phaseList.add(new OptionItem(GlobalConstants.PHASE_ID_FINISH, getResources(request).getMessage(getLocale(request), "statusreport.phase.finish.text")));
		return phaseList;
	}
	
	protected List<OptionItem> getSortOptionList(HttpServletRequest request) {
		List<OptionItem> sortList = new ArrayList<OptionItem>();
		sortList.add(new OptionItem(GlobalConstants.STATUSREPORT_SORT_PERIODICAL, getResources(request).getMessage(getLocale(request), "statusreport.sort.periodical")));
		sortList.add(new OptionItem(GlobalConstants.STATUSREPORT_SORT_EXTRA, getResources(request).getMessage(getLocale(request), "statusreport.sort.extra")));
		sortList.add(new OptionItem(GlobalConstants.STATUSREPORT_SORT_FINAL, getResources(request).getMessage(getLocale(request), "statusreport.sort.final")));
		return sortList;
	}
	
	protected boolean isReportEditable(Statusreport statusreport, HttpServletRequest request) {
		// admin may allways edit
		Employee loginEmployee = (Employee) request.getSession().getAttribute("loginEmployee");
		if (loginEmployee.getStatus().equals(GlobalConstants.EMPLOYEE_STATUS_ADM)) {
			return true;
		}
		// released reports can't be edited
		if (statusreport != null && statusreport.getReleased() != null) {
			return false;
		}		
		// otherwise the report can be edited
		return true;
	}
	
	protected boolean isReportReadyForRelease(Long statusReportId, StatusReportDAO statusReportDAO, HttpServletRequest request) {
		if (statusReportId == null) {
			return false;
		}
		Statusreport statusreport = statusReportDAO.getStatusReportById(statusReportId);
		if (statusreport == null) {
			return false;
		}
		if (statusreport.getReleased() != null) {
			return false;
		}
		Employee loginEmployee = (Employee) request.getSession().getAttribute("loginEmployee");
		if (loginEmployee.getStatus().equals(GlobalConstants.EMPLOYEE_STATUS_ADM)) {
			return true;
		}
		if (loginEmployee.equals(statusreport.getSender())) {
			return true;
		}		
		return false;
	}
	
	protected boolean isReportReadyForAcceptance(Long statusReportId, StatusReportDAO statusReportDAO, HttpServletRequest request) {
		if (statusReportId == null) {
			return false;
		}
		Statusreport statusreport = statusReportDAO.getStatusReportById(statusReportId);
		if (statusreport == null) {
			return false;
		}
		if (statusreport.getReleased() == null) {
			return false;
		}
		if (statusreport.getAccepted() != null) {
			return false;
		}
		Employee loginEmployee = (Employee) request.getSession().getAttribute("loginEmployee");
		if (loginEmployee.getStatus().equals(GlobalConstants.EMPLOYEE_STATUS_ADM)) {
			return true;
		}
		if (loginEmployee.equals(statusreport.getRecipient())) {
			return true;
		}		
		return false;
	}
	
	protected Byte getWorstStatus(Statusreport statusreport) {
		Byte status = 0;
		if (statusreport.getTrendstatus() > status) status = statusreport.getTrendstatus();
		if (statusreport.getNeedforaction_status() > status) status = statusreport.getNeedforaction_status();
		if (statusreport.getAim_status() > status) status = statusreport.getAim_status();
		if (statusreport.getBudget_resources_date_status() > status) status = statusreport.getBudget_resources_date_status();
		if (statusreport.getRiskmonitoring_status() > status) status = statusreport.getRiskmonitoring_status();
		if (statusreport.getChangedirective_status() > status) status = statusreport.getChangedirective_status();
		if (statusreport.getCommunication_status() > status) status = statusreport.getCommunication_status();
		if (statusreport.getImprovement_status() > status) status = statusreport.getImprovement_status();
		if (statusreport.getMiscellaneous_status() > status) status = statusreport.getMiscellaneous_status();
		
		return status;
	}

}
