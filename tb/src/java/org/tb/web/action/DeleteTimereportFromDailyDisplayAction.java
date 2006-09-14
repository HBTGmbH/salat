package org.tb.web.action;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.validator.GenericValidator;
import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;
import org.tb.bdom.Employeecontract;
import org.tb.bdom.Monthlyreport;
import org.tb.bdom.Timereport;
import org.tb.bdom.Vacation;
import org.tb.helper.TimereportHelper;
import org.tb.persistence.MonthlyreportDAO;
import org.tb.persistence.TimereportDAO;
import org.tb.persistence.VacationDAO;
import org.tb.util.DateUtils;

/**
 * Action class for deletion of a timereport initiated from the daily display
 * 
 * @author oda
 *
 */
public class DeleteTimereportFromDailyDisplayAction extends LoginRequiredAction {
	
	private TimereportDAO timereportDAO;
	private MonthlyreportDAO monthlyreportDAO;
	private VacationDAO vacationDAO;
	
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
			th.updateMonthlyHourBalance(tr, -1, timereportDAO, monthlyreportDAO);
		}
		if (tr.getSortofreport().equals("V")) {
			// update vacation...
			th.updateVacation(tr, -1, vacationDAO);
		}
		
		// set attributes to be analyzed by target jsp
		String currentEmployeeName = (String) request.getSession().getAttribute("currentEmployee");
		Employeecontract ec = tr.getEmployeecontract();
		if (currentEmployeeName.equalsIgnoreCase("ALL EMPLOYEES")) {
			request.getSession().setAttribute("timereports", timereportDAO.getTimereportsByDate(tr.getReferenceday().getRefdate()));
		} else {
			request.getSession().setAttribute("timereports", timereportDAO.getTimereportsByDateAndEmployeeContractId(ec.getId(), tr.getReferenceday().getRefdate()));
		}
		
		request.getSession().setAttribute("currentDay", trDay);
		request.getSession().setAttribute("currentMonth", trMonth);
		request.getSession().setAttribute("currentYear", trYear);			
			
		trMonth = DateUtils.getMonthMMStringFromShortstring(trMonth); // e.g., convert from 'Aug' to '08'
		Monthlyreport mr = monthlyreportDAO.getMonthlyreportByYearAndMonthAndEmployeecontract(ec.getId(),
										Integer.parseInt(trYear), Integer.parseInt(trMonth));		
		request.getSession().setAttribute("hourbalance", mr.getHourbalance());
		
		Vacation va = vacationDAO.getVacationByYearAndEmployeecontract(ec.getId(), Integer.parseInt(trYear));
		String vacationBalance = "" + va.getUsed().intValue() + "/" + va.getEntitlement().intValue(); 
		request.getSession().setAttribute("vacation", vacationBalance);
		
		return mapping.getInputForward();
	}
	
}
