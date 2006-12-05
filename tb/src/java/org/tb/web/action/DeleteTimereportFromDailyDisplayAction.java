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
import org.tb.bdom.Monthlyreport;
import org.tb.bdom.Timereport;
import org.tb.bdom.Vacation;
import org.tb.bdom.Workingday;
import org.tb.helper.TimereportHelper;
import org.tb.persistence.EmployeeorderDAO;
import org.tb.persistence.MonthlyreportDAO;
import org.tb.persistence.OvertimeDAO;
import org.tb.persistence.PublicholidayDAO;
import org.tb.persistence.TimereportDAO;
import org.tb.persistence.VacationDAO;
import org.tb.persistence.WorkingdayDAO;
import org.tb.util.DateUtils;

/**
 * Action class for deletion of a timereport initiated from the daily display
 * 
 * @author oda
 *
 */
public class DeleteTimereportFromDailyDisplayAction extends DailyReportAction {
	
	private TimereportDAO timereportDAO;
	private MonthlyreportDAO monthlyreportDAO;
	private VacationDAO vacationDAO;
	private WorkingdayDAO workingdayDAO;
	private EmployeeorderDAO employeeorderDAO;
	private PublicholidayDAO publicholidayDAO;
	private OvertimeDAO overtimeDAO;
	
	public void setOvertimeDAO(OvertimeDAO overtimeDAO) {
		this.overtimeDAO = overtimeDAO;
	}
	
	public void setPublicholidayDAO(PublicholidayDAO publicholidayDAO) {
		this.publicholidayDAO = publicholidayDAO;
	}
	
	public void setEmployeeorderDAO(EmployeeorderDAO employeeorderDAO) {
		this.employeeorderDAO = employeeorderDAO;
	}
	
		
	public TimereportDAO getTimereportDAO() {
		return timereportDAO;
	}

	public void setTimereportDAO(TimereportDAO timereportDAO) {
		this.timereportDAO = timereportDAO;
	}

	public void setMonthlyreportDAO(MonthlyreportDAO monthlyreportDAO) {
		this.monthlyreportDAO = monthlyreportDAO;
	}
	
	public void setVacationDAO(VacationDAO vacationDAO) {
		this.vacationDAO = vacationDAO;
	}
	
	public void setWorkingdayDAO(WorkingdayDAO workingdayDAO) {
		this.workingdayDAO = workingdayDAO;
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
		
		String trDay = TimereportHelper.getDayStringFromTimereport(tr);
		String trMonth = TimereportHelper.getMonthStringFromTimereport(tr);
		String trYear = TimereportHelper.getYearStringFromTimereport(tr);
		
		boolean deleted = timereportDAO.deleteTimereportById(trId);	
		
		TimereportHelper th = new TimereportHelper();
		if (tr.getSortofreport().equals("W")) {
			// update monthly hour balance...
//			th.updateMonthlyHourBalance(tr, -1, timereportDAO, monthlyreportDAO);
		}
		if (tr.getSortofreport().equals("V")) {
			// update vacation...
			th.updateVacation(tr, -1, vacationDAO);
		}
		
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
		String year = (String) request.getSession().getAttribute("currentYear");
		refreshVacationAndOvertime(request, new Integer(year), ec, employeeorderDAO, publicholidayDAO, timereportDAO, overtimeDAO, vacationDAO);
		
		
		
//		trMonth = DateUtils.getMonthMMStringFromShortstring(trMonth); // e.g., convert from 'Aug' to '08'
//		Monthlyreport mr = monthlyreportDAO.getMonthlyreportByYearAndMonthAndEmployeecontract(ec.getId(),
//										Integer.parseInt(trYear), Integer.parseInt(trMonth));		
//		request.getSession().setAttribute("hourbalance", mr.getHourbalance());
//		
//		Vacation va = vacationDAO.getVacationByYearAndEmployeecontract(ec.getId(), Integer.parseInt(trYear));
//		String vacationBalance = "" + va.getUsed().intValue() + "/" + va.getEntitlement().intValue(); 
//		request.getSession().setAttribute("vacation", vacationBalance);
		
		return mapping.getInputForward();
	}
	
}
