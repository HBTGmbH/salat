package org.tb.web.action;

import java.util.List;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.validator.GenericValidator;
import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;
import org.tb.GlobalConstants;
import org.tb.bdom.Employeecontract;
import org.tb.bdom.Timereport;
import org.tb.bdom.Workingday;
import org.tb.helper.TimereportHelper;
import org.tb.persistence.CustomerorderDAO;
import org.tb.persistence.EmployeeDAO;
import org.tb.persistence.EmployeecontractDAO;
import org.tb.persistence.EmployeeorderDAO;
import org.tb.persistence.OvertimeDAO;
import org.tb.persistence.PublicholidayDAO;
import org.tb.persistence.SuborderDAO;
import org.tb.persistence.TimereportDAO;
import org.tb.persistence.VacationDAO;
import org.tb.persistence.WorkingdayDAO;
import org.tb.web.form.ShowDailyReportForm;

/**
 * Action class for deletion of a timereport initiated from the daily display
 * 
 * @author oda
 *
 */
public class DeleteTimereportFromDailyDisplayAction extends DailyReportAction {
	
	private OvertimeDAO overtimeDAO;
	private CustomerorderDAO customerorderDAO;
	private TimereportDAO timereportDAO;
	private EmployeecontractDAO employeecontractDAO;
	private SuborderDAO suborderDAO;
	private EmployeeorderDAO employeeorderDAO;
	private VacationDAO vacationDAO;
	private PublicholidayDAO publicholidayDAO;
	private WorkingdayDAO workingdayDAO;
	private EmployeeDAO employeeDAO;
	
	public void setEmployeeDAO(EmployeeDAO employeeDAO) {
		this.employeeDAO = employeeDAO;
	}
	
	public void setWorkingdayDAO(WorkingdayDAO workingdayDAO) {
		this.workingdayDAO = workingdayDAO;
	}
	
	public void setPublicholidayDAO(PublicholidayDAO publicholidayDAO) {
		this.publicholidayDAO = publicholidayDAO;
	}
	
	public void setVacationDAO(VacationDAO vacationDAO) {
		this.vacationDAO = vacationDAO;
	}
	
	public void setEmployeeorderDAO(EmployeeorderDAO employeeorderDAO) {
		this.employeeorderDAO = employeeorderDAO;
	}
	
	public void setSuborderDAO(SuborderDAO suborderDAO) {
		this.suborderDAO = suborderDAO;
	}
	
	public void setEmployeecontractDAO(EmployeecontractDAO employeecontractDAO) {
		this.employeecontractDAO = employeecontractDAO;
	}
	
	public void setTimereportDAO(TimereportDAO timereportDAO) {
		this.timereportDAO = timereportDAO;
	}
	
	public void setCustomerorderDAO(CustomerorderDAO customerorderDAO) {
		this.customerorderDAO = customerorderDAO;
	}
	
	public void setOvertimeDAO(OvertimeDAO overtimeDAO) {
		this.overtimeDAO = overtimeDAO;
	}
	
	@Override
	public ActionForward executeAuthenticated(ActionMapping mapping, ActionForm form, HttpServletRequest request, HttpServletResponse response) {

		if ((GenericValidator.isBlankOrNull(request.getParameter("trId"))) ||
			(!GenericValidator.isLong(request.getParameter("trId")))) 
				return mapping.getInputForward();
		
		
		
		long trId = Long.parseLong(request.getParameter("trId"));
		Timereport tr = timereportDAO.getTimereportById(trId);
		if (tr == null) 
			return mapping.getInputForward();
		
		TimereportHelper th = new TimereportHelper();
		
//		String trDay = TimereportHelper.getDayStringFromTimereport(tr);
//		String trMonth = TimereportHelper.getMonthStringFromTimereport(tr);
//		String trYear = TimereportHelper.getYearStringFromTimereport(tr);
		
		boolean deleted = timereportDAO.deleteTimereportById(trId);	
		
		//neu
		
		ShowDailyReportForm reportForm = (ShowDailyReportForm) request.getSession().getAttribute("reportForm");
		
		if (refreshTimereports(mapping, request, reportForm, customerorderDAO, timereportDAO, employeecontractDAO, 
				suborderDAO, employeeorderDAO, publicholidayDAO, overtimeDAO, vacationDAO, employeeDAO) != true) {
			return mapping.findForward("error");
		} else {
							
			List<Timereport> timereports = (List<Timereport>) request.getSession().getAttribute("timereports");
			request.getSession().setAttribute("labortime", th.calculateLaborTime(timereports));
			request.getSession().setAttribute("maxlabortime", th.checkLaborTimeMaximum(timereports, GlobalConstants.MAX_HOURS_PER_DAY));
			request.getSession().setAttribute("dailycosts", th.calculateDailyCosts(timereports));
			//refresh workingday
			Workingday workingday;
			try {
				workingday = refreshWorkingday(mapping, reportForm, request, employeecontractDAO, workingdayDAO);
			} catch (Exception e) {
				return mapping.findForward("error");
			}
			Employeecontract employeecontract = employeecontractDAO.getEmployeeContractById(reportForm.getEmployeeContractId());
			request.getSession().setAttribute("quittingtime",th.calculateQuittingTime(workingday, request));
			request.getSession().setAttribute("currentEmployeeId", employeecontract.getEmployee().getId());
			request.getSession().setAttribute("currentEmployeeContract", employeecontract);
			return mapping.findForward("success");
		}
		
		/*
		
//		if (tr.getSortofreport().equals("W")) {
			// update monthly hour balance...
//			th.updateMonthlyHourBalance(tr, -1, timereportDAO, monthlyreportDAO);
//		}
//		if (tr.getSortofreport().equals("V")) {
//			// update vacation...
//			th.updateVacation(tr, -1, vacationDAO);
//		}
		
		// set attributes to be analyzed by target jsp
		String currentEmployeeName = (String) request.getSession().getAttribute("currentEmployee");
		Employeecontract ec = tr.getEmployeecontract();
		List<Timereport> timereports;
		if (currentEmployeeName.equalsIgnoreCase("ALL EMPLOYEES")) {
			timereports = timereportDAO.getTimereportsByDate(tr.getReferenceday().getRefdate());
			request.getSession().setAttribute("timereports", timereports);
		} else {
			timereports = timereportDAO.getTimereportsByDateAndEmployeeContractId(ec.getId(), tr.getReferenceday().getRefdate());
			request.getSession().setAttribute("timereports", timereports);
		}
		
		
		request.getSession().setAttribute("labortime", th.calculateLaborTime(timereports));
		request.getSession().setAttribute("maxlabortime", th.checkLaborTimeMaximum(timereports, GlobalConstants.MAX_HOURS_PER_DAY));
		request.getSession().setAttribute("dailycosts", th.calculateDailyCosts(timereports));
		
		Workingday workingday = workingdayDAO.getWorkingdayByDateAndEmployeeContractId(tr.getReferenceday().getRefdate(), ec.getId());
		request.getSession().setAttribute("quittingtime",th.calculateQuittingTime(workingday, request));
		
		
		
		request.getSession().setAttribute("currentDay", trDay);
		request.getSession().setAttribute("currentMonth", trMonth);
		request.getSession().setAttribute("currentYear", trYear);			
		
//		refresh overtime
		refreshVacationAndOvertime(request, ec, employeeorderDAO, publicholidayDAO, timereportDAO, overtimeDAO);
		
		
		
//		trMonth = DateUtils.getMonthMMStringFromShortstring(trMonth); // e.g., convert from 'Aug' to '08'
//		Monthlyreport mr = monthlyreportDAO.getMonthlyreportByYearAndMonthAndEmployeecontract(ec.getId(),
//										Integer.parseInt(trYear), Integer.parseInt(trMonth));		
//		request.getSession().setAttribute("hourbalance", mr.getHourbalance());
//		
//		Vacation va = vacationDAO.getVacationByYearAndEmployeecontract(ec.getId(), Integer.parseInt(trYear));
//		String vacationBalance = "" + va.getUsed().intValue() + "/" + va.getEntitlement().intValue(); 
//		request.getSession().setAttribute("vacation", vacationBalance);
		
		return mapping.getInputForward();
		
		*/
	}
	
}
