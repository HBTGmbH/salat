package org.tb.web.action;

import java.sql.Date;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Iterator;
import java.util.List;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.validator.GenericValidator;
import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;
import org.apache.struts.action.ActionMessage;
import org.apache.struts.action.ActionMessages;
import org.tb.GlobalConstants;
import org.tb.bdom.Customerorder;
import org.tb.bdom.Employee;
import org.tb.bdom.Employeecontract;
import org.tb.bdom.Monthlyreport;
import org.tb.bdom.Referenceday;
import org.tb.bdom.Timereport;
import org.tb.bdom.Vacation;
import org.tb.helper.CustomerorderHelper;
import org.tb.helper.EmployeeHelper;
import org.tb.helper.SuborderHelper;
import org.tb.helper.TimereportHelper;
import org.tb.persistence.CustomerorderDAO;
import org.tb.persistence.EmployeeDAO;
import org.tb.persistence.EmployeecontractDAO;
import org.tb.persistence.MonthlyreportDAO;
import org.tb.persistence.PublicholidayDAO;
import org.tb.persistence.ReferencedayDAO;
import org.tb.persistence.SuborderDAO;
import org.tb.persistence.TimereportDAO;
import org.tb.persistence.VacationDAO;
import org.tb.util.DateUtils;
import org.tb.web.form.AddDailyReportForm;

/**
 * Action class for a timereport to be stored permanently.
 * 
 * @author oda
 *
 */
public class StoreDailyReportAction extends LoginRequiredAction {
	
	private EmployeeDAO employeeDAO;
	private EmployeecontractDAO employeecontractDAO;
	private SuborderDAO suborderDAO;
	private CustomerorderDAO customerorderDAO;
	private TimereportDAO timereportDAO;
	private ReferencedayDAO referencedayDAO;
	private PublicholidayDAO publicholidayDAO;
	private MonthlyreportDAO monthlyreportDAO;
	private VacationDAO vacationDAO;
	
	public void setEmployeeDAO(EmployeeDAO employeeDAO) {
		this.employeeDAO = employeeDAO;
	}

	public void setEmployeecontractDAO(EmployeecontractDAO employeecontractDAO) {
		this.employeecontractDAO = employeecontractDAO;
	}
	
	public void setSuborderDAO(SuborderDAO suborderDAO) {
		this.suborderDAO = suborderDAO;
	}
	
	public void setCustomerorderDAO(CustomerorderDAO customerorderDAO) {
		this.customerorderDAO = customerorderDAO;
	}
	
	public TimereportDAO getTimereportDAO() {
		return timereportDAO;
	}

	public void setTimereportDAO(TimereportDAO timereportDAO) {
		this.timereportDAO = timereportDAO;
	}

	public void setReferencedayDAO(ReferencedayDAO referencedayDAO) {
		this.referencedayDAO = referencedayDAO;
	}
	
	public void setPublicholidayDAO(PublicholidayDAO publicholidayDAO) {
		this.publicholidayDAO = publicholidayDAO;
	}
	
	public void setMonthlyreportDAO(MonthlyreportDAO monthlyreportDAO) {
		this.monthlyreportDAO = monthlyreportDAO;
	}
	
	public void setVacationDAO(VacationDAO vacationDAO) {
		this.vacationDAO = vacationDAO;
	}

	@Override
	public ActionForward executeAuthenticated(ActionMapping mapping, ActionForm form, HttpServletRequest request, HttpServletResponse response) {
			
		// check if special tasks initiated from the form or the daily display need to be carried out...
		AddDailyReportForm reportForm = (AddDailyReportForm) form;
		
		boolean refreshOrders = false;

			if ((request.getParameter("task") != null) && 
				(request.getParameter("task").equals("refreshOrders"))) {
				// refresh orders to be displayed in the select menu
				CustomerorderHelper ch = new CustomerorderHelper();
				if (ch.refreshOrders(mapping, request, reportForm,
						customerorderDAO, employeeDAO, employeecontractDAO, suborderDAO) != true) {
					return mapping.findForward("error");
				} else {
					//return mapping.findForward("success");
					refreshOrders = true;
				}
			}
			
			if ((request.getParameter("task") != null) && 
					(request.getParameter("task").equals("refreshSuborders"))) {
				// refresh suborders to be displayed in the select menu
				SuborderHelper sh = new SuborderHelper();
				if (sh.refreshSuborders(mapping, request, reportForm,
						suborderDAO, employeecontractDAO) != true) {
					return mapping.findForward("error");
				} else {
					return mapping.findForward("success");
				}
			}
			
			if (((request.getParameter("task") != null) && 
					(request.getParameter("task").equals("adjustBeginTime"))) || refreshOrders) {
				// refresh begin time to be displayed
				refreshOrders = false;
				Employee loginEmployee = (Employee) request.getSession().getAttribute("loginEmployee"); 	
				Employeecontract ec = null;	
				
				EmployeeHelper eh = new EmployeeHelper();
				if (request.getSession().getAttribute("currentEmployee") != null) {
					String currentEmployeeName = (String) request.getSession().getAttribute("currentEmployee");
					if (currentEmployeeName.equalsIgnoreCase("ALL EMPLOYEES")) {
						ec = employeecontractDAO.getEmployeeContractByEmployeeId(loginEmployee.getId());
						request.getSession().setAttribute("currentEmployee", loginEmployee.getName());
					} else {
						String[] firstAndLast = eh.splitEmployeename(currentEmployeeName);		
						ec = employeecontractDAO.getEmployeeContractByEmployeeName(firstAndLast[0], firstAndLast[1]);
						request.getSession().setAttribute("currentEmployee", currentEmployeeName);
					}
				} else {
					ec = employeecontractDAO.getEmployeeContractByEmployeeId(loginEmployee.getId());
					request.getSession().setAttribute("currentEmployee", loginEmployee.getName());
				}
				
				if (ec == null) {
					request.setAttribute("errorMessage",
									"No employee contract found for employee - please call system administrator.");
					return mapping.findForward("error");
				}
				
				TimereportHelper th = new TimereportHelper();
				
				String refDateString = reportForm.getReferenceday();
								
				SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd");
				java.util.Date selectedDate;
				try {
					selectedDate = simpleDateFormat.parse(refDateString);
				} catch (ParseException e) {
					// error occured while parsing date - use current date instead
					selectedDate = new java.util.Date();
				} 
								
				int[] beginTime = th.determineBeginTimeToDisplay(ec.getId(), timereportDAO, selectedDate);
				reportForm.setSelectedHourBegin(beginTime[0]);
				reportForm.setSelectedMinuteBegin(beginTime[1]);
				TimereportHelper.refreshHours(reportForm);			
				
				return mapping.findForward("success");			
			}
			
			if ((request.getParameter("task") != null) && 
					(request.getParameter("task").equals("adjustSuborderSignChanged"))) {
				// refresh suborder sign/description select menus
				SuborderHelper sh = new SuborderHelper();
				sh.adjustSuborderSignChanged(request, reportForm, suborderDAO);
				return mapping.findForward("success");
			}
			
			if ((request.getParameter("task") != null) && 
					(request.getParameter("task").equals("adjustSuborderDescriptionChanged"))) {
				// refresh suborder sign/description select menus
				SuborderHelper sh = new SuborderHelper();
				sh.adjustSuborderDescriptionChanged(request, reportForm, suborderDAO);
				return mapping.findForward("success");
			}
			
			if ((request.getParameter("task") != null) && 
					(request.getParameter("task").equalsIgnoreCase("updateSortOfReport"))) {
				// updates the sort of report
				request.getSession().setAttribute("report", reportForm.getSortOfReport());
				return mapping.findForward("success");
			}
			
			if ((request.getParameter("task") != null) && 
				(request.getParameter("task").equals("refreshHours"))) {
					// refreshes the hours displayed after a change of duration period
					TimereportHelper.refreshHours(reportForm);
					return mapping.findForward("success");
			}
			
			if ((request.getParameter("task") != null) && 
					(request.getParameter("task").equals("refreshPeriod"))) {
					// refreshes the duration period after a change of begin/end times
					ActionMessages periodErrors =  new ActionMessages();
					if (TimereportHelper.refreshPeriod(request, periodErrors, reportForm) != true) {
						saveErrors(request, periodErrors);
					}
					return mapping.findForward("success");
			}
			
			if ((request.getParameter("task") != null) && 
					(request.getParameter("task").equals("save")) ||
					(request.getParameter("trId") != null)) {
			
				// 'main' task - prepare everything to store the report.
				// I.e., copy properties from the form into the timereport before saving.
				
				EmployeeHelper eh = new EmployeeHelper();
				String[] firstAndLast = eh.splitEmployeename(reportForm.getEmployeename());
				Employeecontract ec = employeecontractDAO.getEmployeeContractByEmployeeName(firstAndLast[0], firstAndLast[1]);
				double hours = TimereportHelper.calculateTime(reportForm);
				
				long trId = -1;
				Timereport tr = null;
				if (request.getSession().getAttribute("trId") != null) {
					// edited report from monthly overview
					// TODO: this should be refactured some time when monthly overview is adjusted
					// to the layout of the daily overview!
					trId = Long.parseLong(request.getSession().getAttribute("trId").toString());
					tr = timereportDAO.getTimereportById(trId);
				} else if (request.getParameter("trId") != null) {
					// edited report from daily overview
					trId = Long.parseLong(request.getParameter("trId"));
					tr = timereportDAO.getTimereportById(trId);
				} else {
					// new report
					tr = new Timereport();
				}
				
				ActionMessages errorMessages = validateFormData(request, reportForm, trId, ec.getId(), hours);
				if (errorMessages.size() > 0) {
					return mapping.getInputForward();
				}
				
				Date theDate = Date.valueOf(reportForm.getReferenceday());
				
				tr.setTaskdescription(reportForm.getComment());	
				tr.setEmployeecontract(ec);
	
				if (!reportForm.getSortOfReport().equals("W")) {
					tr.setBeginhour(new Integer(GlobalConstants.BEGINHOUR));
					tr.setBeginminute(new Integer(GlobalConstants.BEGINMINUTE));
					tr.setEndhour(new Integer(GlobalConstants.ENDHOUR));
					tr.setEndminute(new Integer(GlobalConstants.ENDMINUTE));
					
					tr.setHours(ec.getDailyWorkingTime());
				} else {
					tr.setBeginhour(new Integer(reportForm.getSelectedHourBegin()));
					tr.setBeginminute(new Integer(reportForm.getSelectedMinuteBegin()));
					tr.setEndhour(new Integer(reportForm.getSelectedHourEnd()));
					tr.setEndminute(new Integer(reportForm.getSelectedMinuteEnd()));
					
					tr.setHours(hours);	
				}
				
				tr.setSortofreport(reportForm.getSortOfReport());
						
				if ((tr.getReferenceday() == null) || 
						(tr.getReferenceday().getRefdate() == null) || 
						(!tr.getReferenceday().getRefdate().equals(theDate))) {
					// if timereport is new
					Referenceday rd = referencedayDAO.getReferencedayByDate(theDate);					
					if (rd == null) {
						// new referenceday to be added in database
						referencedayDAO.addReferenceday(theDate);	
						rd = referencedayDAO.getReferencedayByDate(theDate);
					} 
					tr.setReferenceday(rd);
				}
				
				if (reportForm.getSortOfReport().equals("W")) {
					tr.setCosts(reportForm.getCosts());
					tr.setSuborder(suborderDAO.getSuborderById(reportForm.getSuborderSignId()));
					tr.setStatus(reportForm.getStatus());
				} else {
					// 'special' reports: set suborder in timereport to null.				
					tr.setSuborder(null);				
					tr.setStatus("");
					tr.setCosts(0.0);
				}
				
				timereportDAO.save(tr);
				
				String year = DateUtils.getYearString(tr.getReferenceday().getRefdate());	// yyyy
				String month = DateUtils.getMonthString(tr.getReferenceday().getRefdate()); // MM	
				TimereportHelper th = new TimereportHelper();
				if (reportForm.getSortOfReport().equals("W")) {
					// update monthly hour balance...
					th.updateMonthlyHourBalance(tr, 1, timereportDAO, monthlyreportDAO);				
					Monthlyreport mr = 
						monthlyreportDAO.getMonthlyreportByYearAndMonthAndEmployeecontract
						(ec.getId(), Integer.parseInt(year), Integer.parseInt(month));
					request.getSession().setAttribute("hourbalance", mr.getHourbalance());
				}
				if (reportForm.getSortOfReport().equals("V")) {
					// update vacation balance
					if (request.getSession().getAttribute("trId") == null) {
						// new report
						th.updateVacation(tr, 1, vacationDAO);
						Vacation va = vacationDAO.getVacationByYearAndEmployeecontract
							(ec.getId(), Integer.parseInt(year));
						if (va == null) {
							// should not be the case!
							va = vacationDAO.setNewVacation(ec, Integer.parseInt(year));
						} 
						String vacationBalance = "" + va.getUsed().intValue() + "/" + va.getEntitlement().intValue(); 
						request.getSession().setAttribute("vacation", vacationBalance);
					}				
				}
															
				request.getSession().setAttribute("currentDay", DateUtils.getDayString(theDate));
				request.getSession().setAttribute("currentMonth", DateUtils.getMonthShortString(theDate));
				request.getSession().setAttribute("currentYear", DateUtils.getYearString(theDate));
				
				if (request.getSession().getAttribute("trId") != null) {
					// edited report from monthly overview
					// get updated list of timereports from DB					
					request.getSession().setAttribute("timereports", timereportDAO
							.getTimereportsByMonthAndYearAndEmployeeContractId(ec.getId(),
									DateUtils.getMonthShortString(theDate), year));
					request.getSession().removeAttribute("trId");
					return mapping.findForward("showMonthly");
				} else {
					// get updated list of timereports from DB
					request.getSession().setAttribute("timereports", timereportDAO
							.getTimereportsByDateAndEmployeeContractId(
									ec.getId(), theDate));
					return mapping.findForward("showDaily");
				}
				
			} 
			if ((request.getParameter("task") != null) && 
					(request.getParameter("task").equals("back"))) {
				// go back
				request.getSession().removeAttribute("trId");
				reportForm.reset(mapping, request);
				return mapping.findForward("cancel");
			} 
			if ((request.getParameter("task") != null) && 
					(request.getParameter("task").equals("reset"))) {
				// reset form
				doResetActions(mapping, request, reportForm);
				return mapping.getInputForward();				
			}	
						
			return mapping.findForward("error");
			
	}
	
	/**
	 * resets the 'add report' form to default values
	 * 
	 * @param mapping
	 * @param request
	 * @param reportForm
	 */
	private void doResetActions(ActionMapping mapping, HttpServletRequest request, AddDailyReportForm reportForm) {
		reportForm.reset(mapping, request);

		Employeecontract ec = null;	
		Employee loginEmployee = (Employee) request.getSession().getAttribute("loginEmployee"); 

		ec = employeecontractDAO.getEmployeeContractByEmployeeId(loginEmployee.getId());
		EmployeeHelper eh = new EmployeeHelper();
		List<Employee> employeeOptionList = eh.getEmployeeOptions(loginEmployee, employeeDAO);
		request.getSession().setAttribute("employees", employeeOptionList);
		request.getSession().setAttribute("currentEmployee", loginEmployee.getName());
		
		List<Customerorder> orders = customerorderDAO.getCustomerordersByEmployeeContractId(ec.getId());
		request.getSession().setAttribute("orders", customerorderDAO.getCustomerordersByEmployeeContractId(ec.getId()));
		
		request.getSession().setAttribute("report", "W");
		
		// init form with first order and corresponding suborders
		if ((orders != null) && (orders.size() > 0)) {
			reportForm.setOrder(orders.get(0).getSign());
			reportForm.setOrderId(orders.get(0).getId());
			request.getSession().setAttribute("suborders", 
				suborderDAO.getSubordersByEmployeeContractIdAndCustomerorderId(ec.getId(), orders.get(0).getId()));
		}
		request.getSession().removeAttribute("trId");
	}
	
	/**
	 * validates the form data (syntax and logic)
	 * 
	 * @param request
	 * @param reportForm
	 * @param theDate
	 * @param trId: > 0 for edited report, -1 for new report
	 * @param ecId
	 * @param hours
	 * @return
	 */
	private ActionMessages validateFormData(HttpServletRequest request, 
											AddDailyReportForm reportForm,
											long trId,
											long ecId,
											double hours) {

		ActionMessages errors = getErrors(request);
		if(errors == null) errors = new ActionMessages();
		
		// check date format (must now be 'yyyy-MM-dd')
		String dateString = reportForm.getReferenceday().trim();
		
		boolean dateError = DateUtils.validateDate(dateString);
		if (dateError) {
			errors.add("referenceday", new ActionMessage("form.timereport.error.date.wrongformat"));
			// return here - further validations do not make sense with wrong date format
			saveErrors(request, errors);		
			return errors;
		} 
			
		Date theDate = Date.valueOf(reportForm.getReferenceday());
		
		// check date range (must be in current or previous year)
		if (DateUtils.getCurrentYear() - DateUtils.getYear(dateString.substring(0,4)) >= 2) {
			errors.add("referenceday", new ActionMessage("form.timereport.error.date.invalidyear"));
		}
		
		// end time must be later than begin time
		int begin = reportForm.getSelectedHourBegin()*100 + reportForm.getSelectedMinuteBegin();
		int end = reportForm.getSelectedHourEnd()*100 + reportForm.getSelectedMinuteEnd();
		if (reportForm.getSortOfReport().equals("W")) {		
			if (begin >= end) {
				errors.add("selectedHourBegin", new ActionMessage("form.timereport.error.endbeforebegin"));
			}
		}
		
		// check if report types for one day are unique and if there is no time overlap with other work reports
		boolean timeOverlap = false;
		List<Timereport> dailyReports = 
				timereportDAO.getTimereportsByDateAndEmployeeContractId(ecId, theDate);
		if ((dailyReports != null) && (dailyReports.size() > 0)) {
			for (Iterator iter = dailyReports.iterator(); iter.hasNext();) {
				Timereport tr = (Timereport) iter.next();
				if (tr.getId() != trId) { // do not check report against itself in case of edit
					// uniqueness of types
					// actually not checked - e.g., combination of sickness and work on ONE day should be valid
					// but: vacation or sickness MUST occur only once per day
					if ((!reportForm.getSortOfReport().equals("W")) && (!tr.getSortofreport().equals("W"))) {
						errors.add("sortOfReport", new ActionMessage("form.timereport.error.sortofreport.special.alreadyexisting"));		
						break;
					}
					// time overlap
					if (tr.getSortofreport().equals("W")) {
						if (TimereportHelper.checkTimeOverlap(tr, reportForm) == true) {
							timeOverlap = true;
							if (!(begin > end))
								errors.add("selectedHourBegin", new ActionMessage("form.timereport.error.timeoverlap"));		
							break;
						}
					}
				}
			}
		}
		
		// check if orders/suborders are filled in case of 'W' report
		if (reportForm.getSortOfReport().equals("W")) {
			if (reportForm.getOrderId() <= 0) {
				errors.add("orderId", new ActionMessage("form.timereport.error.orderid.empty"));
			}
			if (reportForm.getSuborderSignId() <= 0) {
				errors.add("suborderIdDescription", new ActionMessage("form.timereport.error.suborderid.empty"));
			}
		}
		
		// if sort of report is not 'W' reports are only allowed for workdays
		// e.g., vacation cannot be set on a Sunday
		if (!reportForm.getSortOfReport().equals("W")) {
			boolean valid = true;
			String dow = DateUtils.getDow(theDate);
			if ((dow.equalsIgnoreCase("Sat")) || (dow.equalsIgnoreCase("Sun"))) {
				valid = false;
			}
			
			// checks for public holidays
			if (valid) {			
				String publicHoliday = publicholidayDAO.getPublicHoliday(theDate);
				if ((publicHoliday != null) && (publicHoliday.length() > 0)) {
					valid = false;
				}
			}
			
			if (!valid) {
				errors.add("sortOfReport", new ActionMessage("form.timereport.error.sortofreport.invalidday"));		
			} else {
				// for new report, check if other reports already exist for selected day
				if (trId == -1) {
					List<Timereport> allReports = 
						timereportDAO.getTimereportsByDateAndEmployeeContractId(ecId, theDate);
					if (allReports.size() > 0) {
						valid = false;
						errors.add("sortOfReport", new ActionMessage("form.timereport.error.sortofreport.othersexisting"));
					}
				}
			}
					
			
		}
		
		// check hour sum (must be less than 10.0)
		if ((!timeOverlap) && (reportForm.getSortOfReport().equals("W"))) {
			List<Timereport> allReports = 
				timereportDAO.getTimereportsByDateAndEmployeeContractId(ecId, theDate);
			double dailyHourSum = TimereportHelper.calculateDailyHourSum(allReports);
			if (trId == -1) dailyHourSum += hours; // new report!
			if (dailyHourSum > GlobalConstants.MAX_HOURS_PER_DAY) {
				errors.add("selectedHourEnd", new ActionMessage("form.timereport.error.hours.exceeded"));
			}
		}
			
		// check costs format
		if (reportForm.getSortOfReport().equals("W")) {
			if (!GenericValidator.isDouble(reportForm.getCosts().toString()) ||
				(!GenericValidator.isInRange(reportForm.getCosts(), 
						0.0, GlobalConstants.MAX_COSTS))) {
				errors.add("costs", new ActionMessage("form.timereport.error.costs.wrongformat"));
			}
		}

		// check comment length
		if (!GenericValidator.maxLength(reportForm.getComment(),GlobalConstants.COMMENT_MAX_LENGTH)) {
			errors.add("comment", new ActionMessage("form.timereport.error.comment.toolarge"));
		}
		
		saveErrors(request, errors);		
		return errors;
	}
}
