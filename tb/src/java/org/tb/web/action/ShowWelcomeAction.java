package org.tb.web.action;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;
import org.tb.GlobalConstants;
import org.tb.bdom.Employeecontract;
import org.tb.bdom.Timereport;
import org.tb.bdom.Warning;
import org.tb.persistence.EmployeeDAO;
import org.tb.persistence.EmployeecontractDAO;
import org.tb.persistence.EmployeeorderDAO;
import org.tb.persistence.OvertimeDAO;
import org.tb.persistence.PublicholidayDAO;
import org.tb.persistence.TimereportDAO;
import org.tb.web.form.ShowWelcomeForm;

public class ShowWelcomeAction extends DailyReportAction {

	private OvertimeDAO overtimeDAO;
	private TimereportDAO timereportDAO;
	private EmployeecontractDAO employeecontractDAO;
	private EmployeeorderDAO employeeorderDAO;
	private PublicholidayDAO publicholidayDAO;
	private EmployeeDAO employeeDAO;
	
	
	public void setEmployeeDAO(EmployeeDAO employeeDAO) {
		this.employeeDAO = employeeDAO;
	}
	
	public void setPublicholidayDAO(PublicholidayDAO publicholidayDAO) {
		this.publicholidayDAO = publicholidayDAO;
	}
	
	public void setEmployeeorderDAO(EmployeeorderDAO employeeorderDAO) {
		this.employeeorderDAO = employeeorderDAO;
	}
		
	public void setEmployeecontractDAO(EmployeecontractDAO employeecontractDAO) {
		this.employeecontractDAO = employeecontractDAO;
	}
	
	public void setTimereportDAO(TimereportDAO timereportDAO) {
		this.timereportDAO = timereportDAO;
	}
	
	
	public void setOvertimeDAO(OvertimeDAO overtimeDAO) {
		this.overtimeDAO = overtimeDAO;
	}
	
	@Override
	protected ActionForward executeAuthenticated(ActionMapping mapping,
			ActionForm form, HttpServletRequest request,
			HttpServletResponse response) throws Exception {
		
		ShowWelcomeForm welcomeForm = (ShowWelcomeForm) form;
		Employeecontract employeecontract;
//		Date date = new Date();
		
		// create collection of employeecontracts
		List<Employeecontract> employeecontracts = employeecontractDAO.getEmployeeContractsOrderedByEmployeeSign();
		request.getSession().setAttribute("employeecontracts", employeecontracts);
		
		if ((request.getParameter("task") != null) &&
                (request.getParameter("task").equals("refresh"))){
			
			long employeeContractId = welcomeForm.getEmployeeContractId();
			
			employeecontract = employeecontractDAO.getEmployeeContractById(employeeContractId);
			request.getSession().setAttribute("currentEmployeeId", employeecontract.getEmployee().getId());
			request.getSession().setAttribute("currentEmployeeContract", employeecontract);
		} else {
			employeecontract = (Employeecontract) request.getSession().getAttribute("currentEmployeeContract");
			if (employeecontract == null) {
				employeecontract = (Employeecontract) request.getSession().getAttribute("loginEmployeeContract");
			}
			welcomeForm.setEmployeeContractId(employeecontract.getId());
			request.getSession().setAttribute("currentEmployeeId", employeecontract.getEmployee().getId());
			request.getSession().setAttribute("currentEmployeeContract", employeecontract);
		}
		
		
//		String releaseDate = employeecontract.getReportReleaseDateString();
//		String acceptanceDate = employeecontract.getReportAcceptanceDateString();
//		
//		request.getSession().setAttribute("releasedUntil", releaseDate);
//		request.getSession().setAttribute("acceptedUntil", acceptanceDate);
		
		refreshVacationAndOvertime(request, employeecontract, employeeorderDAO, publicholidayDAO, timereportDAO, overtimeDAO);
		
		List<Timereport> timereports = timereportDAO.getTimereportsOutOfRangeForEmployeeContract(employeecontract);
		List<Warning> warnings = new ArrayList<Warning>();
		for (Timereport timereport : timereports) {
			Warning warning = new Warning();
			warning.setSort(GlobalConstants.WARNING_SORT_TIMEREPORT_NOT_IN_RANGE);
			warning.setText(timereport.getTimeReportAsString());
			warnings.add(warning);
		}
		if (warnings != null && !warnings.isEmpty()) {
			request.getSession().setAttribute("warnings", warnings);
			request.getSession().setAttribute("warningsPresent", true);
		} else {
			request.getSession().setAttribute("warningsPresent", false);
		}
		
		return mapping.findForward("success");
	}

}
