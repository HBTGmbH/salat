/*
 * File:          $RCSfile$
 * Version:       $Revision$
 * 
 * Created:       29.11.2006 by cb
 * Last changed:  $Date$ by $Author$
 * 
 * Copyright (C) 2006 by HBT GmbH, www.hbt.de
 *
 */
package org.tb.web.action;

import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;
import org.tb.GlobalConstants;
import org.tb.bdom.Customerorder;
import org.tb.bdom.Employee;
import org.tb.bdom.Employeecontract;
import org.tb.helper.EmployeeHelper;
import org.tb.helper.TimereportHelper;
import org.tb.helper.matrix.MatrixHelper;
import org.tb.helper.matrix.ReportWrapper;
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
import org.tb.util.DateUtils;
import org.tb.web.form.ShowMatrixForm;

/**
 * @author cb
 * @since 29.11.2006
 */
public class ShowMatrixAction extends DailyReportAction {

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
	public ActionForward executeAuthenticated(ActionMapping mapping,
			ActionForm form, HttpServletRequest request,
			HttpServletResponse response) {

		// conversion and localization of day values
		Map<String, String> monthMap = new HashMap<String, String>();
		monthMap.put("Jan", "main.timereport.select.month.jan.text");
		monthMap.put("Feb", "main.timereport.select.month.feb.text");
		monthMap.put("Mar", "main.timereport.select.month.mar.text");
		monthMap.put("Apr", "main.timereport.select.month.apr.text");
		monthMap.put("May", "main.timereport.select.month.mai.text");
		monthMap.put("Jun", "main.timereport.select.month.jun.text");
		monthMap.put("Jul", "main.timereport.select.month.jul.text");
		monthMap.put("Aug", "main.timereport.select.month.aug.text");
		monthMap.put("Sep", "main.timereport.select.month.sep.text");
		monthMap.put("Oct", "main.timereport.select.month.oct.text");
		monthMap.put("Nov", "main.timereport.select.month.nov.text");
		monthMap.put("Dec", "main.timereport.select.month.dec.text");

		// check if special tasks initiated from the daily display need to be
		// carried out...
		ShowMatrixForm reportForm = (ShowMatrixForm) form;
		TimereportHelper th = new TimereportHelper();

		// call on MatrixView with parameter refreshMergedreports to update
		// request
		if ((request.getParameter("task") != null)
				&& (request.getParameter("task").equals("refreshMergedreports"))) {
			//
			// if(reportForm.getInvoice()==true){
			// reportForm.setInvoice(false);
			// }else{
			// reportForm.setInvoice(true);
			// }
			//            
			// selected view and selected dates
			String selectedView = reportForm.getMatrixview();
			Date dateFirst;
			Date dateLast;
			try {
				if (selectedView.equals(GlobalConstants.VIEW_MONTHLY)) {
					request.getSession().setAttribute("matrixview",
							GlobalConstants.VIEW_MONTHLY);
					dateFirst = th.getDateFormStrings("1", reportForm
							.getFromMonth(), reportForm.getFromYear(), false);
					GregorianCalendar gc = new GregorianCalendar();
					gc.setTime(dateFirst);
					int maxday = gc.getActualMaximum(Calendar.DAY_OF_MONTH);
					String maxDayString = "";
					if (maxday < 10) {
						maxDayString += "0";
					}
					maxDayString += maxday;
					dateLast = th.getDateFormStrings(maxDayString, reportForm
							.getFromMonth(), reportForm.getFromYear(), false);
				} else if (selectedView.equals(GlobalConstants.VIEW_CUSTOM)) {
					request.getSession().setAttribute("matrixview",
							GlobalConstants.VIEW_CUSTOM);
					dateFirst = th.getDateFormStrings(reportForm.getFromDay(),
							reportForm.getFromMonth(),
							reportForm.getFromYear(), false);
					if (reportForm.getUntilDay() == null
							|| reportForm.getUntilMonth() == null
							|| reportForm.getUntilYear() == null) {
						GregorianCalendar gc = new GregorianCalendar();
						gc.setTime(dateFirst);
						int maxday = gc.getActualMaximum(Calendar.DAY_OF_MONTH);
						String maxDayString = "";
						if (maxday < 10) {
							maxDayString += "0";
						}
						maxDayString += maxday;
						reportForm.setUntilDay(maxDayString);
						reportForm.setUntilMonth(reportForm.getFromMonth());
						reportForm.setUntilYear(reportForm.getFromYear());
					}
					dateLast = th.getDateFormStrings(reportForm.getUntilDay(),
							reportForm.getUntilMonth(), reportForm
									.getUntilYear(), false);
				} else {
					throw new RuntimeException("no view type selected");
				}
			} catch (Exception e) {
				throw new RuntimeException("date cannot be parsed for form");
			}

			MatrixHelper mh = new MatrixHelper();
			Customerorder order = customerorderDAO
					.getCustomerorderBySign(reportForm.getOrder());
			if (reportForm.getEmployeeContractId() == -1) {
				// consider timereports for all employees
				List<Customerorder> orders = customerorderDAO
						.getCustomerorders();
				request.getSession().setAttribute("orders", orders);

				if ((reportForm.getOrder() == null)
						|| (reportForm.getOrder().equals("ALL ORDERS"))) {
					// get the timereports for specific date, all employees, all
					// orders
					ReportWrapper tempReportWrapper = mh
							.getEmployeeMatrix(
									dateFirst,
									dateLast,
									reportForm.getEmployeeContractId(),
									timereportDAO,
									employeecontractDAO,
									publicholidayDAO,
									GlobalConstants.MATRIX_SPECIFICDATE_ALLORDERS_ALLEMPLOYEES,
									-1, reportForm.isInvoice());
					request.getSession().setAttribute("mergedreports",
							tempReportWrapper.getMergedReportList());
					request.getSession().setAttribute("dayhourcounts",
							tempReportWrapper.getDayAndWorkingHourCountList());
					request.getSession().setAttribute("dayhourssum",
							tempReportWrapper.getDayHoursSum());
					request.getSession().setAttribute("dayhourstarget",
							tempReportWrapper.getDayHoursTarget());
					request.getSession().setAttribute("dayhoursdiff",
							tempReportWrapper.getDayHoursDiff());
				} else {
					// get the timereports for specific date, all employees,
					// specific order
					ReportWrapper tempReportWrapper = mh
							.getEmployeeMatrix(
									dateFirst,
									dateLast,
									reportForm.getEmployeeContractId(),
									timereportDAO,
									employeecontractDAO,
									publicholidayDAO,
									GlobalConstants.MATRIX_SPECIFICDATE_SPECIFICORDERS_ALLEMPLOYEES,
									order.getId(), reportForm.isInvoice());
					request.getSession().setAttribute("mergedreports",
							tempReportWrapper.getMergedReportList());
					request.getSession().setAttribute("dayhourcounts",
							tempReportWrapper.getDayAndWorkingHourCountList());
					request.getSession().setAttribute("dayhourssum",
							tempReportWrapper.getDayHoursSum());
					request.getSession().setAttribute("dayhourstarget",
							tempReportWrapper.getDayHoursTarget());
					request.getSession().setAttribute("dayhoursdiff",
							tempReportWrapper.getDayHoursDiff());
				}

			} else {
				// consider timereports for specific employee
				Employeecontract employeeContract = employeecontractDAO
						.getEmployeeContractById(reportForm
								.getEmployeeContractId());
				if (employeeContract == null) {
					request
							.setAttribute("errorMessage",
									"No employee contract found for employee - please call system administrator.");
					return mapping.findForward("error");
				}

				// also refresh orders/suborders to be displayed for specific
				// employee
				List<Customerorder> orders = customerorderDAO
						.getCustomerordersByEmployeeContractId(employeeContract
								.getId());
				request.getSession().setAttribute("orders", orders);
				if (orders.size() > 0) {
					request
							.getSession()
							.setAttribute(
									"suborders",
									suborderDAO
											.getSubordersByEmployeeContractId(employeeContract
													.getId()));
				}

				if ((reportForm.getOrder() == null)
						|| (reportForm.getOrder().equals("ALL ORDERS"))) {
					// get the timereports for specific date, specific employee,
					// all orders
					ReportWrapper tempReportWrapper = mh
							.getEmployeeMatrix(
									dateFirst,
									dateLast,
									reportForm.getEmployeeContractId(),
									timereportDAO,
									employeecontractDAO,
									publicholidayDAO,
									GlobalConstants.MATRIX_SPECIFICDATE_ALLORDERS_SPECIFICEMPLOYEES,
									-1, reportForm.isInvoice());
					request.getSession().setAttribute("mergedreports",
							tempReportWrapper.getMergedReportList());
					request.getSession().setAttribute("dayhourcounts",
							tempReportWrapper.getDayAndWorkingHourCountList());
					request.getSession().setAttribute("dayhourssum",
							tempReportWrapper.getDayHoursSum());
					request.getSession().setAttribute("dayhourstarget",
							tempReportWrapper.getDayHoursTarget());
					request.getSession().setAttribute("dayhoursdiff",
							tempReportWrapper.getDayHoursDiff());
				} else {
					// get the timereports for specific date, specific employee,
					// specific order
					Employeecontract tempEmployeeContract = employeecontractDAO
							.getEmployeeContractById(reportForm
									.getEmployeeContractId());
					List<Customerorder> tempCustomerOrder = customerorderDAO
							.getCustomerordersByEmployeeContractId(tempEmployeeContract
									.getId());
					ReportWrapper tempReportWrapper;
					if (tempCustomerOrder.contains(order)) {
						tempReportWrapper = mh
								.getEmployeeMatrix(
										dateFirst,
										dateLast,
										reportForm.getEmployeeContractId(),
										timereportDAO,
										employeecontractDAO,
										publicholidayDAO,
										GlobalConstants.MATRIX_SPECIFICDATE_SPECIFICORDERS_SPECIFICEMPLOYEES,
										order.getId(), reportForm.isInvoice());
					} else {
						tempReportWrapper = mh
								.getEmployeeMatrix(
										dateFirst,
										dateLast,
										reportForm.getEmployeeContractId(),
										timereportDAO,
										employeecontractDAO,
										publicholidayDAO,
										GlobalConstants.MATRIX_SPECIFICDATE_ALLORDERS_SPECIFICEMPLOYEES,
										-1, reportForm.isInvoice());
					}
					request.getSession().setAttribute("mergedreports",
							tempReportWrapper.getMergedReportList());
					request.getSession().setAttribute("dayhourcounts",
							tempReportWrapper.getDayAndWorkingHourCountList());
					request.getSession().setAttribute("dayhourssum",
							tempReportWrapper.getDayHoursSum());
					request.getSession().setAttribute("dayhourstarget",
							tempReportWrapper.getDayHoursTarget());
					request.getSession().setAttribute("dayhoursdiff",
							tempReportWrapper.getDayHoursDiff());
				}
			}

			// refresh all relevant attributes
			if (reportForm.getEmployeeContractId() == -1) {
				request.getSession().setAttribute("currentEmployee",
						"ALL EMPLOYEES");
				request.getSession().setAttribute("currentEmployeeContract",
						null);
				request.getSession().setAttribute("currentEmployeeId", -1);
				List<Employeecontract> ecList = employeecontractDAO
						.getEmployeeContracts();
				Employeecontract tempEmployeeContract;
				Date acceptanceDate;
				boolean isAcceptanceWarning = false;
				for (Iterator iter = ecList.iterator(); iter.hasNext();) {
					tempEmployeeContract = (Employeecontract) iter.next();
					if (!tempEmployeeContract.getEmployee().getSign().equals("adm")) {
						if (!tempEmployeeContract
								.getAcceptanceWarningByDate(dateLast)) {
							acceptanceDate = tempEmployeeContract
								.getReportAcceptanceDate();
							if(acceptanceDate == null){
								isAcceptanceWarning = false;
								break;
							}else{
								if (!dateLast.after(acceptanceDate)) {
									isAcceptanceWarning = true;
								} else {
									isAcceptanceWarning = false;
									break;
								}							
							}
							
						} else {
							isAcceptanceWarning = false;
							break;
						}
					}

				}
				request.getSession().setAttribute("acceptance",
						isAcceptanceWarning);
				if(isAcceptanceWarning){
					request.getSession().setAttribute("acceptedby", "");
				}
			} else {
				Employeecontract employeecontract = employeecontractDAO
						.getEmployeeContractById(reportForm
								.getEmployeeContractId());
				request.getSession().setAttribute("currentEmployee",
						employeecontract.getEmployee().getName());
				request.getSession().setAttribute("currentEmployeeContract",
						employeecontract);
				request.getSession().setAttribute("currentEmployeeId",
						employeecontract.getEmployee().getId());
				if (!employeecontract.getAcceptanceWarningByDate(dateLast)) {
					Date acceptanceDate = employeecontract
							.getReportAcceptanceDate();
					if (!dateLast.after(acceptanceDate)) {
						request.getSession().setAttribute("acceptance", true);
						Employee tempEmployee = employeeDAO.getEmployeeBySign(timereportDAO.getLastAcceptedTimereportByDateAndEmployeeContractId(new java.sql.Date(dateLast.getTime()), employeecontract.getId()).getAcceptedby());
						request.getSession().setAttribute("acceptedby", tempEmployee.getFirstname() + " " + tempEmployee.getLastname() + " (" + tempEmployee.getStatus() + ")");
					} else {
						request.getSession().setAttribute("acceptance", false);
					}
				} else {
					request.getSession().setAttribute("acceptance", false);
				}
			}
			// request.getSession().setAttribute("currentEmployeeId",
			// reportForm.getEmployeeCId());
			if ((reportForm.getOrder() == null)
					|| (reportForm.getOrder().equals("ALL ORDERS"))) {
				request.getSession().setAttribute("currentOrder", "ALL ORDERS");
			} else {
				request.getSession().setAttribute("currentOrder",
						reportForm.getOrder());
			}
			request.getSession().setAttribute("currentDay",
					reportForm.getFromDay());
			request.getSession().setAttribute("currentMonth",
					reportForm.getFromMonth());
			request.getSession().setAttribute("MonthKey",
					monthMap.get(reportForm.getFromMonth()));
			request.getSession().setAttribute("currentYear",
					reportForm.getFromYear());
			request.getSession().setAttribute("lastDay",
					reportForm.getUntilDay());
			request.getSession().setAttribute("lastMonth",
					reportForm.getUntilMonth());
			request.getSession().setAttribute("lastYear",
					reportForm.getUntilYear());
			GregorianCalendar gc = new GregorianCalendar();
			gc.setTime(dateFirst);
			request.getSession().setAttribute("daysofmonth",
					gc.getActualMaximum(GregorianCalendar.DAY_OF_MONTH));

			return mapping.findForward("success");

		}

		// call on MatrixView with parameter print
		if ((request.getParameter("task") != null)
				&& (request.getParameter("task").equals("print"))) {
			return mapping.findForward("print");
		}

		// call on MatrixView with any parameter to forward or go back
		if (request.getParameter("task") != null) {
			// just go back to main menu
			if (request.getParameter("task").equalsIgnoreCase("back")) {
				return mapping.findForward("backtomenu");
			} else {
				return mapping.findForward("success");
			}
		}

		// call on MatrixView without a parameter
		if (request.getParameter("task") == null) {

			// set daily view as standard
			reportForm.setMatrixview(GlobalConstants.VIEW_MONTHLY);
			request.getSession().setAttribute("matrixview",
					GlobalConstants.VIEW_MONTHLY);

			// no special task - prepare everything to show reports
			Employee loginEmployee = (Employee) request.getSession()
					.getAttribute("loginEmployee");
			EmployeeHelper eh = new EmployeeHelper();
			Employeecontract ec = eh.setCurrentEmployee(loginEmployee, request,
					employeeDAO, employeecontractDAO);

			if (ec == null) {
				request
						.setAttribute("errorMessage",
								"No employee contract found for employee - please call system administrator.");
				return mapping.findForward("error");
			}

			// List<Employee> employees = employeeDAO.getEmployees();
			// List<Employee> employeesWithContract =
			// employeeDAO.getEmployeesWithContracts();
			List<Employeecontract> employeeContracts = employeecontractDAO
					.getVisibleEmployeeContractsOrderedByEmployeeSign();

			// make sure, that admin is in list
			// if (loginEmployee.getSign().equalsIgnoreCase("adm") &&
			// loginEmployee.getStatus().equalsIgnoreCase(GlobalConstants.EMPLOYEE_STATUS_ADM))
			// {
			// if (!employeesWithContract.contains(loginEmployee)) {
			// employeesWithContract.add(loginEmployee);
			// }
			// }

			if ((employeeContracts == null) || (employeeContracts.size() <= 0)) {
				request
						.setAttribute("errorMessage",
								"No employees with valid contracts found - please call system administrator.");
				return mapping.findForward("error");
			}
			request.getSession().setAttribute("employeecontracts",
					employeeContracts);
			request.getSession().setAttribute("days",
					DateUtils.getDaysToDisplay());
			request.getSession().setAttribute("years",
					DateUtils.getYearsToDisplay());
			if (reportForm.getFromMonth() != null) {
				// call from list select change
				request.getSession().setAttribute("currentDay",
						reportForm.getFromDay());
				request.getSession().setAttribute("currentMonth",
						reportForm.getFromMonth());
				request.getSession().setAttribute("MonthKey",
						monthMap.get(reportForm.getFromMonth()));
				request.getSession().setAttribute("currentYear",
						reportForm.getFromYear());

				Date dateFirst = new Date();
				Date dateLast = new Date();
				try {
					dateFirst = th.getDateFormStrings("01", reportForm
							.getFromMonth(), reportForm.getFromYear(), false);
				} catch (Exception e) {
					System.out.println("this should not happen");
				}
				GregorianCalendar gc = new GregorianCalendar();
				gc.setTime(dateFirst);
				int maxday = gc.getActualMaximum(Calendar.DAY_OF_MONTH);
				String maxDayString = "";
				if (maxday < 10) {
					maxDayString += "0";
				}
				maxDayString += maxday;

				try {
					dateLast = th.getDateFormStrings(maxDayString, reportForm
							.getFromMonth(), reportForm.getFromYear(), false);
				} catch (Exception e) {
					System.out.println("this should not happen");
				}

				MatrixHelper mh = new MatrixHelper();

				Employeecontract employeecontract = (Employeecontract) request
						.getSession().getAttribute("currentEmployeeContract");
				Long ecId = -1l;
				boolean isAcceptanceWarning = false;
				if (employeecontract != null) {
					ecId = employeecontract.getId();
					if (!employeecontract.getAcceptanceWarningByDate(dateLast)) {
						if (!dateLast.after(employeecontract
								.getReportAcceptanceDate())) {
							isAcceptanceWarning = true;
						} else {
							isAcceptanceWarning = false;
						}
					} else {
						isAcceptanceWarning = false;
					}
				} else {
					List<Employeecontract> ecList = employeecontractDAO
							.getEmployeeContracts();
					Employeecontract tempEmployeeContract;
					Date acceptanceDate;
					for (Iterator iter = ecList.iterator(); iter.hasNext();) {
						tempEmployeeContract = (Employeecontract) iter.next();
						if (!tempEmployeeContract.getEmployee().getSign().equals("adm")) {
							if (!tempEmployeeContract
									.getAcceptanceWarningByDate(dateLast)) {
								acceptanceDate = tempEmployeeContract
										.getReportAcceptanceDate();
								if (!dateLast.after(acceptanceDate)) {
									isAcceptanceWarning = true;
								} else {
									isAcceptanceWarning = false;
									break;
								}
							} else {
								isAcceptanceWarning = false;
								break;
							}
						}
					}
				}
				request.getSession().setAttribute("acceptance",
						isAcceptanceWarning);
				if(isAcceptanceWarning){
					Employee tempEmployee = employeeDAO.getEmployeeBySign(timereportDAO.getLastAcceptedTimereportByDateAndEmployeeContractId(new java.sql.Date(dateLast.getTime()), employeecontract.getId()).getAcceptedby());
					request.getSession().setAttribute("acceptedby", tempEmployee.getFirstname() + " " + tempEmployee.getLastname() +  " (" + tempEmployee.getStatus() + ")");
				}
				
				ReportWrapper tempReportWrapper = mh
						.getEmployeeMatrix(
								dateFirst,
								dateLast,
								ecId,
								timereportDAO,
								employeecontractDAO,
								publicholidayDAO,
								GlobalConstants.MATRIX_SPECIFICDATE_ALLORDERS_SPECIFICEMPLOYEES,
								reportForm.getOrderId(), reportForm.isInvoice());
				request.getSession().setAttribute("mergedreports",
						tempReportWrapper.getMergedReportList());
				request.getSession().setAttribute("dayhourcounts",
						tempReportWrapper.getDayAndWorkingHourCountList());
				request.getSession().setAttribute("dayhourssum",
						tempReportWrapper.getDayHoursSum());
				request.getSession().setAttribute("dayhourstarget",
						tempReportWrapper.getDayHoursTarget());
				request.getSession().setAttribute("dayhoursdiff",
						tempReportWrapper.getDayHoursDiff());

				request.getSession().setAttribute("daysofmonth",
						gc.getActualMaximum(GregorianCalendar.DAY_OF_MONTH));
				// refreshVacationAndOvertime(request,
				// employeecontractDAO.getEmployeeContractByEmployeeId(loginEmployee.getId()),
				// employeeorderDAO, publicholidayDAO, timereportDAO,
				// overtimeDAO);
				// String currentMonth = (String)
				// request.getSession().getAttribute("currentMonth");
			} else {

				// call from main menu: set current month, year,
				// orders, suborders...
				Date dt = new Date();
				// get day string (e.g., '31') from java.util.Date
				String dayString = dt.toString().substring(8, 10);
				// get month string (e.g., 'Jan') from java.util.Date
				String monthString = dt.toString().substring(4, 7);
				// get year string (e.g., '2006') from java.util.Date
				int length = dt.toString().length();
				String yearString = dt.toString().substring(length - 4, length);

				// set Month for first call
				if (reportForm.getFromMonth() == null
						|| reportForm.getFromMonth().trim()
								.equalsIgnoreCase("")) {
					String month = (String) request.getSession().getAttribute(
							"currentMonth");
					if (month == null || month.trim().equals("")) {
						Date date = new Date();
						String[] dateArray = th.getDateAsStringArray(date);
						month = dateArray[1];
					}
					reportForm.setFromMonth(month);
				}

				request.getSession().setAttribute("currentDay", dayString);
				request.getSession().setAttribute("currentMonth",
						reportForm.getFromMonth());
				request.getSession().setAttribute("MonthKey",
						monthMap.get(reportForm.getFromMonth()));
				request.getSession().setAttribute("currentYear", yearString);

				reportForm.setFromDay("01");
				reportForm.setFromMonth(monthString);
				reportForm.setFromYear(yearString);
				reportForm.setOrderId(-1);
				// reportForm.setInvoice(false);

				request.getSession().setAttribute("lastDay", dayString);
				request.getSession().setAttribute("lastMonth", monthString);
				request.getSession().setAttribute("lastYear", yearString);

				// test
				Date dateFirst = new Date();
				Date dateLast = new Date();
				try {
					dateFirst = th.getDateFormStrings("01", monthString,
							yearString, false);
				} catch (Exception e) {
					System.out.println("this should not happen");
				}
				GregorianCalendar gc = new GregorianCalendar();
				gc.setTime(dateFirst);
				int maxday = gc.getActualMaximum(Calendar.DAY_OF_MONTH);
				String maxDayString = "";
				if (maxday < 10) {
					maxDayString += "0";
				}
				maxDayString += maxday;

				try {
					dateLast = th.getDateFormStrings(maxDayString, monthString,
							yearString, false);
				} catch (Exception e) {
					System.out.println("this should not happen");
				}

				MatrixHelper mh = new MatrixHelper();

				Employeecontract employeecontract = getEmployeeContractFromRequest(request);
				Long ecId = -1l;
				if (employeecontract != null) {
					ecId = employeecontract.getId();
				}
				if (!employeecontract.getAcceptanceWarningByDate(dateLast)) {
					if (!dateLast.after(employeecontract
							.getReportAcceptanceDate())) {
						request.getSession().setAttribute("acceptance", true);
						Employee tempEmployee = employeeDAO.getEmployeeBySign(timereportDAO.getLastAcceptedTimereportByDateAndEmployeeContractId(new java.sql.Date(dateLast.getTime()), employeecontract.getId()).getAcceptedby());
						request.getSession().setAttribute("acceptedby", tempEmployee.getFirstname() + " " + tempEmployee.getLastname() +  " (" + tempEmployee.getStatus() + ")");
					} else {
						request.getSession().setAttribute("acceptance", false);
					}
				} else {
					request.getSession().setAttribute("acceptance", false);
				}

				ReportWrapper tempReportWrapper = mh
						.getEmployeeMatrix(
								dateFirst,
								dateLast,
								ecId,
								timereportDAO,
								employeecontractDAO,
								publicholidayDAO,
								GlobalConstants.MATRIX_SPECIFICDATE_ALLORDERS_SPECIFICEMPLOYEES,
								0, reportForm.isInvoice());
				request.getSession().setAttribute("mergedreports",
						tempReportWrapper.getMergedReportList());
				request.getSession().setAttribute("dayhourcounts",
						tempReportWrapper.getDayAndWorkingHourCountList());
				request.getSession().setAttribute("dayhourssum",
						tempReportWrapper.getDayHoursSum());
				request.getSession().setAttribute("dayhourstarget",
						tempReportWrapper.getDayHoursTarget());
				request.getSession().setAttribute("dayhoursdiff",
						tempReportWrapper.getDayHoursDiff());
				request.getSession().setAttribute("daysofmonth",
						gc.getActualMaximum(GregorianCalendar.DAY_OF_MONTH));
				// refreshVacationAndOvertime(request,
				// employeecontractDAO.getEmployeeContractByEmployeeId(loginEmployee.getId()),
				// employeeorderDAO, publicholidayDAO, timereportDAO,
				// overtimeDAO);

				// orders
				List<Customerorder> orders = null;
				Long employeeId = (Long) request.getSession().getAttribute(
						"currentEmployeeId");
				if ((employeeId != null) && (employeeId == -1)) {
					orders = customerorderDAO.getCustomerorders();
				} else {
					orders = customerorderDAO
							.getCustomerordersByEmployeeContractId(ec.getId());
				}
				request.getSession().setAttribute("orders", orders);
				request.getSession().setAttribute("currentOrder", "ALL ORDERS");
				if (employeeId == -1) {
					request.getSession().setAttribute("currentEmployee",
							"ALL EMPLOYEES");
				} else {
					request.getSession().setAttribute("currentEmployee",
							employeeDAO.getEmployeeById(employeeId).getName());
				}
				if (orders.size() > 0) {
					// List<List> suborderlists = new ArrayList<List>();
					// for (Customerorder customerorder : orders) {
					// suborderlists.add(customerorder.getSuborders());
					// }
					request.getSession().setAttribute(
							"suborders",
							suborderDAO.getSubordersByEmployeeContractId(ec
									.getId()));
				}

			}

		}
		return mapping.findForward("success");
	}

	/**
	 * @param request
	 * @return
	 * @author cb
	 * @since 08.02.2007
	 */
	private Employeecontract getEmployeeContractFromRequest(
			HttpServletRequest request) {
		Employeecontract loginEmployeeContract = (Employeecontract) request
				.getSession().getAttribute("loginEmployeeContract");
		Employeecontract ec = (Employeecontract) request.getSession()
				.getAttribute("currentEmployeeContract");

		if (ec == null) {
			ec = loginEmployeeContract;
		}
		return ec;
	}
}

/*
 * $Log$
 */