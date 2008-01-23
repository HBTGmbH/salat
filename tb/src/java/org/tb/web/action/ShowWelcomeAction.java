package org.tb.web.action;

import java.sql.Date;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.List;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;
import org.tb.GlobalConstants;
import org.tb.bdom.Customerorder;
import org.tb.bdom.Employeecontract;
import org.tb.bdom.Employeeorder;
import org.tb.bdom.Statusreport;
import org.tb.bdom.Timereport;
import org.tb.bdom.Warning;
import org.tb.persistence.CustomerorderDAO;
import org.tb.persistence.EmployeeDAO;
import org.tb.persistence.EmployeecontractDAO;
import org.tb.persistence.EmployeeorderDAO;
import org.tb.persistence.OvertimeDAO;
import org.tb.persistence.PublicholidayDAO;
import org.tb.persistence.StatusReportDAO;
import org.tb.persistence.TimereportDAO;
import org.tb.web.form.ShowWelcomeForm;

public class ShowWelcomeAction extends DailyReportAction {

	private OvertimeDAO overtimeDAO;
	private TimereportDAO timereportDAO;
	private EmployeecontractDAO employeecontractDAO;
	private EmployeeorderDAO employeeorderDAO;
	private PublicholidayDAO publicholidayDAO;
	private EmployeeDAO employeeDAO;
	private CustomerorderDAO customerorderDAO;
	private StatusReportDAO statusReportDAO;
	
	public void setStatusReportDAO(StatusReportDAO statusReportDAO) {
		this.statusReportDAO = statusReportDAO;
	}
	
	public void setCustomerorderDAO(CustomerorderDAO customerorderDAO) {
		this.customerorderDAO = customerorderDAO;
	}
	
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
		List<Employeecontract> employeecontracts = employeecontractDAO.getVisibleEmployeeContractsOrderedByEmployeeSign();
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
		
		// warnings
		List<Warning> warnings = new ArrayList<Warning>();
		SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd");
		
		// eoc warning
		List<Employeeorder> employeeorders = new ArrayList<Employeeorder>();
		employeeorders.addAll(employeeorderDAO.getEmployeeordersForEmployeeordercontentWarning(employeecontract));			
		
		for (Employeeorder employeeorder: employeeorders) {
			if (!employeecontract.getFreelancer() && !employeeorder.getSuborder().getNoEmployeeOrderContent()) {
				try {
					if (employeeorder.getEmployeeordercontent() == null) {
						throw new RuntimeException("null content");
					} else if (employeeorder.getEmployeeordercontent() != null && employeeorder.getEmployeeordercontent().getCommitted_emp() != true && employeeorder.getEmployeecontract().getEmployee().equals(employeecontract.getEmployee())) {
						Warning warning = new Warning();
						warning.setSort(getResources(request).getMessage(getLocale(request), "employeeordercontent.thumbdown.text"));
						warning.setText(employeeorder.getEmployeeOrderAsString());
						warning.setLink("/tb/do/ShowEmployeeorder?employeeContractId=" + employeeorder.getEmployeecontract().getId());
						warnings.add(warning);
					} else if (employeeorder.getEmployeeordercontent() != null && employeeorder.getEmployeeordercontent().getCommitted_mgmt() != true && employeeorder.getEmployeeordercontent().getContactTechHbt().equals(employeecontract.getEmployee())) {
						Warning warning = new Warning();
						warning.setSort(getResources(request).getMessage(getLocale(request), "employeeordercontent.thumbdown.text"));
						warning.setText(employeeorder.getEmployeeOrderAsString());
						warning.setLink("/tb/do/ShowEmployeeorder?employeeContractId=" + employeeorder.getEmployeecontract().getId());
						warnings.add(warning);
					} else {
						throw new RuntimeException("query suboptimal");
					}
				}
				catch (Exception e) {
					System.out.println(e);
				}
			}
		}
		
		// timereport warning
		List<Timereport> timereports = timereportDAO.getTimereportsOutOfRangeForEmployeeContract(employeecontract);
		for (Timereport timereport : timereports) {
			Warning warning = new Warning();
			warning.setSort(getResources(request).getMessage(getLocale(request), "main.info.warning.timereportnotinrange"));
			warning.setText(timereport.getTimeReportAsString());
			warnings.add(warning);
		}
		
		// timereport warning 2
		timereports = timereportDAO.getTimereportsOutOfRangeForEmployeeOrder(employeecontract);
		for (Timereport timereport : timereports) {
			Warning warning = new Warning();
			warning.setSort(getResources(request).getMessage(getLocale(request), "main.info.warning.timereportnotinrangeforeo"));
			warning.setText(timereport.getTimeReportAsString()+" "+timereport.getEmployeeorder().getEmployeeOrderAsString());
			warnings.add(warning);
		}
		
		// timereport warning 3: no duration
		Employeecontract loginEmployeeContract = (Employeecontract) request.getSession().getAttribute("loginEmployeeContract");
		timereports = timereportDAO.getTimereportsWithoutDurationForEmployeeContractId(employeecontract.getId());
		for (Timereport timereport : timereports) {
			Warning warning = new Warning();
			warning.setSort(getResources(request).getMessage(getLocale(request), "main.info.warning.timereport.noduration"));
			warning.setText(timereport.getTimeReportAsString());
			if (loginEmployeeContract.equals(employeecontract) 
					|| loginEmployeeContract.getEmployee().getStatus().equals(GlobalConstants.EMPLOYEE_STATUS_BL)
					|| loginEmployeeContract.getEmployee().getStatus().equals(GlobalConstants.EMPLOYEE_STATUS_GF)
					|| loginEmployeeContract.getEmployee().getStatus().equals(GlobalConstants.EMPLOYEE_STATUS_ADM)) {
				warning.setLink("/tb/do/EditDailyReport?trId="+timereport.getId());				
			}
			warnings.add(warning);
		}		
		
		// statusreport due warning
		List<Customerorder> customerOrders = customerorderDAO.getCustomerOrdersByResponsibleEmployeeIdWithStatusReports(employeecontract.getEmployee().getId());
		if (customerOrders != null && !customerOrders.isEmpty()) {
			
			java.util.Date now = new java.util.Date();
						
			for (Customerorder customerorder : customerOrders) {
				Date maxUntilDate = statusReportDAO.getMaxUntilDateForCustomerOrderId(customerorder.getId());
				
				if (maxUntilDate == null) {
					maxUntilDate = customerorder.getFromDate();
				}			
				
				Date checkDate = new Date(maxUntilDate.getTime());
				
				GregorianCalendar calendar = new GregorianCalendar();
				calendar.setTime(checkDate);
				calendar.add(Calendar.MONTH, 12 / customerorder.getStatusreport());
				checkDate.setTime(calendar.getTimeInMillis());
				
				// periodical report due warning
				if (!checkDate.after(now) && (customerorder.getUntilDate() == null || customerorder.getUntilDate().after(checkDate))) {
					// show warning
					Warning warning = new Warning();
					warning.setSort(getResources(request).getMessage(getLocale(request), "main.info.warning.statusreport.due"));
					warning.setText(customerorder.getSign()+" "+customerorder.getShortdescription()+" ("+simpleDateFormat.format(checkDate)+")");
					List<Statusreport> unreleasedReports = statusReportDAO.getUnreleasedPeriodicalStatusReports(customerorder.getId(), employeecontract.getEmployee().getId(), maxUntilDate);
					if (unreleasedReports != null && !unreleasedReports.isEmpty()) {
						if (unreleasedReports.size() == 1) {
							warning.setLink("/tb/do/EditStatusReport?srId="+unreleasedReports.get(0).getId());
						} else {
							warning.setLink("/tb/do/ShowStatusReport?coId="+customerorder.getId());
						}
					} else {
						warning.setLink("/tb/do/CreateStatusReport?coId="+customerorder.getId()+"&final=false");
					}
					warnings.add(warning);
				}
				
				// final report due warning
				List<Statusreport> finalReports = statusReportDAO.getReleasedFinalStatusReportsByCustomerOrderId(customerorder.getId());
				if (customerorder.getStatusreport() > 0 
						&& customerorder.getUntilDate() != null 
						&& !customerorder.getUntilDate().after(now)
						&& (finalReports == null || finalReports.isEmpty())) {
					Warning warning = new Warning();
					warning.setSort(getResources(request).getMessage(getLocale(request), "main.info.warning.statusreport.finalreport"));
					warning.setText(customerorder.getSign()+" "+customerorder.getShortdescription());
					List<Statusreport> unreleasedReports = statusReportDAO.getUnreleasedFinalStatusReports(customerorder.getId(), employeecontract.getEmployee().getId(), maxUntilDate);
					if (unreleasedReports != null && !unreleasedReports.isEmpty()) {
						if (unreleasedReports.size() == 1) {
							warning.setLink("/tb/do/EditStatusReport?srId="+unreleasedReports.get(0).getId());
						} else {
							warning.setLink("/tb/do/ShowStatusReport?coId="+customerorder.getId());
						}
					} else {
						warning.setLink("/tb/do/CreateStatusReport?coId="+customerorder.getId()+"&final=true");
					}
					warnings.add(warning);
				}
			}
		}
		
		// statusreport acceptance warning
		List<Statusreport> reportsToBeAccepted = statusReportDAO.getReleasedStatusReportsByRecipientId(employeecontract.getEmployee().getId());
		if (reportsToBeAccepted != null && !reportsToBeAccepted.isEmpty()) {
			for (Statusreport statusreport : reportsToBeAccepted) {
				Warning warning = new Warning();
				warning.setSort(getResources(request).getMessage(getLocale(request), "main.info.warning.statusreport.acceptance"));
				warning.setText(statusreport.getCustomerorder().getSign()+" "
						+statusreport.getCustomerorder().getShortdescription()
						+" (ID:"+statusreport.getId()+" "
						+getResources(request).getMessage(getLocale(request), "statusreport.from.text")
						+":"+simpleDateFormat.format(statusreport.getFromdate())+" "
						+getResources(request).getMessage(getLocale(request), "statusreport.until.text")
						+":"+simpleDateFormat.format(statusreport.getUntildate())+" "
						+getResources(request).getMessage(getLocale(request), "statusreport.from.text")
						+":"+statusreport.getSender().getName()+" "
						+getResources(request).getMessage(getLocale(request), "statusreport.to.text")
						+":"+statusreport.getRecipient().getName()+")");
				warning.setLink("/tb/do/EditStatusReport?srId="+statusreport.getId());
				warnings.add(warning);
			}
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
