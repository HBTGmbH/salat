package org.tb.web.action;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;

/**
 * Action class for a timereport to be shown in the monthly display
 * 
 * @author oda
 *
 */
public class ShowMonthlyReportAction extends LoginRequiredAction {

	@Override
	public ActionForward executeAuthenticated(ActionMapping mapping,
			ActionForm form, HttpServletRequest request,
			HttpServletResponse response) {

//		// check if special tasks initiated from the daily display need to be carried out...
//		ShowMonthlyReportForm reportForm = (ShowMonthlyReportForm) form;
//
//		if ((request.getParameter("task") != null)
//				&& (request.getParameter("task").equals("refreshTimereports"))) {
//			// refresh list of timereports to be displayed
//			if (refreshTimereports(mapping, request, reportForm) != true) {
//				return mapping.findForward("error");
//			} else {
//				return mapping.findForward("success");
//			}
//		}
//
//		if (request.getParameter("task") != null) {
//			// just go back to main menu
//			if (request.getParameter("task").equalsIgnoreCase("back")) {
//				return mapping.findForward("backtomenu");
//			} else {
//				return mapping.findForward("success");
//			}
//		}
//
//		if (request.getParameter("task") == null) {
//			// no special task - prepare everything to show reports...
//			Employee loginEmployee = (Employee) request.getSession().getAttribute("loginEmployee");
//			EmployeeHelper eh = new EmployeeHelper();
//			Employeecontract ec = eh.setCurrentEmployee(loginEmployee, request,
//					employeeDAO, employeecontractDAO);
//			
//			if (ec == null) {
//				request.setAttribute("errorMessage",
//								"No employee contract found for employee - please call system administrator.");
//				return mapping.findForward("error");
//			}
//
//			request.getSession().setAttribute("years", DateUtils.getYearsToDisplay());
//			request.getSession().setAttribute("months", DateUtils.getMonthsToDisplay());
//			request.getSession().setAttribute("hours", DateUtils.getHoursToDisplay());
//			request.getSession().setAttribute("hoursDuration", DateUtils.getHoursDurationToDisplay());
//			request.getSession().setAttribute("minutes", DateUtils.getMinutesToDisplay());
//			if (reportForm.getMonth() != null) {
//				// call from list select change
//				request.getSession().setAttribute("currentMonth", reportForm.getMonth());
//				request.getSession().setAttribute("currentYear", reportForm.getYear());
//				request.getSession().setAttribute("timereports", timereportDAO
//						.getTimereportsByMonthAndYearAndEmployeeContractId(ec
//								.getId(), reportForm.getMonth(), reportForm
//								.getYear()));
//			} else {
//				// call from main menu: set current month, year, timereports,
//				// orders...
//				Date dt = new Date();
//				// get month string (e.g., 'Jan') from java.util.Date
//				String monthString = dt.toString().substring(4, 7);
//				// get year string (e.g., '2006') from java.util.Date
//				int length = dt.toString().length();
//				String yearString = dt.toString().substring(length-4, length);
//
//				request.getSession().setAttribute("currentMonth", monthString);
//				request.getSession().setAttribute("currentYear", yearString);
//
//				String currentEmployeeName = (String) request.getSession()
//						.getAttribute("currentEmployee");
//				if ((currentEmployeeName != null)
//						&& (currentEmployeeName
//								.equalsIgnoreCase("ALL EMPLOYEES"))) {
//					request.getSession().setAttribute("timereports", timereportDAO
//							.getTimereportsByMonthAndYear(monthString,
//									yearString));
//				} else {
//					request.getSession().setAttribute("timereports", timereportDAO
//							.getTimereportsByMonthAndYearAndEmployeeContractId(
//									ec.getId(), monthString, yearString));
//				}
//
//				// orders
//				List<Customerorder> orders = null;
//				if ((currentEmployeeName != null)
//						&& (currentEmployeeName
//								.equalsIgnoreCase("ALL EMPLOYEES"))) {
//					orders = customerorderDAO.getCustomerorders();
//				} else {
//					orders = customerorderDAO
//							.getCustomerordersByEmployeeContractId(ec.getId());
//				}
//				request.getSession().setAttribute("orders", orders);
//				request.getSession().setAttribute("currentOrder", "ALL ORDERS");
//
//				// hour balance
//				Monthlyreport mr = monthlyreportDAO
//						.getMonthlyreportByYearAndMonthAndEmployeecontract(ec
//								.getId(), Integer.parseInt(yearString),
//								DateUtils
//										.getMonthMMFromShortstring(monthString));
//				if (mr == null) {
//					// add new monthly report
//					mr = monthlyreportDAO.setNewReport(ec, Integer
//							.parseInt(yearString), DateUtils
//							.getMonthMMFromShortstring(monthString));
//				}
//				request.getSession().setAttribute("hourbalance",
//						mr.getHourbalance());
//
//				// vacation balance
//				Vacation va = vacationDAO.getVacationByYearAndEmployeecontract(ec.getId(), Integer.parseInt(yearString));
//				if (va == null) {
//					// should not be the case!
//					va = vacationDAO.setNewVacation(ec, Integer.parseInt(yearString));
//				} 
//				String vacationBalance = "" + va.getUsed().intValue() + "/" + va.getEntitlement().intValue(); 
//				request.getSession().setAttribute("vacation", vacationBalance);
//			}
//		}
//		return mapping.findForward("success");
//	}
//
//	/**
//	 * refreshes timereports to be displayed after changes of relevant criteria
//	 * (e.g., employee or order)
//	 * 
//	 * @param mapping
//	 * @param request
//	 * @param reportForm
//	 * @return
//	 */
//	private boolean refreshTimereports(ActionMapping mapping,
//			HttpServletRequest request, ShowMonthlyReportForm reportForm) {
//
//		if (reportForm.getEmployeename().equalsIgnoreCase("ALL EMPLOYEES")) {
//			// consider timereports for all employees
//			List<Customerorder> orders = customerorderDAO.getCustomerorders();
//			request.getSession().setAttribute("orders", orders);
//
//			if ((reportForm.getOrder() == null)
//					|| (reportForm.getOrder().equals("ALL ORDERS"))) {
//				// get the timereports for specific date, all employees, all orders
//				request.getSession().setAttribute("timereports", timereportDAO
//						.getTimereportsByMonthAndYear(reportForm.getMonth(),
//								reportForm.getYear()));
//			} else {
//				// get the timereports for specific date, all employees, specific order
//				Customerorder co = customerorderDAO
//						.getCustomerorderBySign(reportForm.getOrder());
//				long orderId = co.getId();
//				request.getSession().setAttribute("timereports", timereportDAO
//						.getTimereportsByMonthAndYearAndCustomerorder(orderId,
//								reportForm.getMonth(), reportForm.getYear(), "W"));
//			}
//
//		} else {
//			// consider timereports for specific employee
//			EmployeeHelper eh = new EmployeeHelper();
//			String[] firstAndLast = eh.splitEmployeename(reportForm.getEmployeename());
//			Employeecontract ec = employeecontractDAO
//					.getEmployeeContractByEmployeeName(firstAndLast[0],
//							firstAndLast[1]);
//
//			if (ec == null) {
//				request
//						.setAttribute("errorMessage",
//								"No employee contract found for employee - please call system administrator.");
//				return false;
//			}
//
//			// also refresh orders/suborders to be displayed for specific employee 
//			List<Customerorder> orders = customerorderDAO
//					.getCustomerordersByEmployeeContractId(ec.getId());
//			request.getSession().setAttribute("orders", orders);
//			if (orders.size() > 0) {
//				request.getSession().setAttribute
//					("suborders", suborderDAO.getSubordersByEmployeeContractIdAndCustomerorderId(
//												ec.getId(), orders.get(0).getId()));
//			}
//
//
//			if ((reportForm.getOrder() == null)
//					|| (reportForm.getOrder().equals("ALL ORDERS"))) {
//				// get the timereports for specific date, specific employee, all orders
//				request.getSession().setAttribute("timereports", timereportDAO
//						.getTimereportsByMonthAndYearAndEmployeeContractId(ec
//								.getId(), reportForm.getMonth(), reportForm
//								.getYear()));
//			} else {
//				Customerorder co = customerorderDAO
//						.getCustomerorderBySign(reportForm.getOrder());
//				long orderId = co.getId();
//				// get the timereports for specific date, specific employee, specific order
//				// fill up order-specific list with 'working' reports only...
//				request.
//				getSession().setAttribute(
//								"timereports",
//								timereportDAO
//										.getTimereportsByMonthAndYearAndEmployeeContractIdAndCustomerorderId(
//												ec.getId(), orderId, reportForm
//														.getMonth(), reportForm
//														.getYear(), "W"));
//			}
//			// refresh hour balance
//			Monthlyreport mr = monthlyreportDAO
//					.getMonthlyreportByYearAndMonthAndEmployeecontract(ec
//							.getId(), Integer.parseInt(reportForm.getYear()),
//							DateUtils.getMonthMMFromShortstring(reportForm
//									.getMonth()));
//			if (mr == null) {
//				// add new monthly report
//				mr = monthlyreportDAO.setNewReport(ec, Integer
//						.parseInt(reportForm.getYear()), DateUtils
//						.getMonthMMFromShortstring(reportForm.getMonth()));
//			}
//			request.getSession().setAttribute("hourbalance", mr.getHourbalance());
//			
//			//	refresh vacation balance
//			Vacation va = vacationDAO.getVacationByYearAndEmployeecontract(ec.getId(), Integer.parseInt(reportForm.getYear()));
//			if (va == null) {
//				// should not be the case!
//				va = vacationDAO.setNewVacation(ec, Integer.parseInt(reportForm.getYear()));
//			} 
//			String vacationBalance = "" + va.getUsed().intValue() + "/" + va.getEntitlement().intValue(); 
//			request.getSession().setAttribute("vacation", vacationBalance);
//		}
//
//		// refresh all relevant attributes
//		request.getSession().setAttribute("currentEmployee",
//				reportForm.getEmployeename());
//		if ((reportForm.getOrder() == null)
//				|| (reportForm.getOrder().equals("ALL ORDERS"))) {
//			request.getSession().setAttribute("currentOrder", "ALL ORDERS");
//		} else {
//			request.getSession().setAttribute("currentOrder",
//					reportForm.getOrder());
//		}
//		request.getSession().setAttribute("currentMonth", reportForm.getMonth());
//		request.getSession().setAttribute("currentYear", reportForm.getYear());
//
//		return true;
		return mapping.findForward("");

	}
}
