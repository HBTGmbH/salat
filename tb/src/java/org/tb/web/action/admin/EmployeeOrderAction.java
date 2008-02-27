package org.tb.web.action.admin;

import java.text.SimpleDateFormat;
import java.util.LinkedList;
import java.util.List;

import javax.servlet.http.HttpServletRequest;

import org.tb.bdom.EmployeeOrderViewDecorator;
import org.tb.bdom.Employeecontract;
import org.tb.bdom.Employeeorder;
import org.tb.bdom.Suborder;
import org.tb.persistence.CustomerorderDAO;
import org.tb.persistence.EmployeecontractDAO;
import org.tb.persistence.EmployeeorderDAO;
import org.tb.persistence.SuborderDAO;
import org.tb.persistence.TimereportDAO;
import org.tb.web.action.LoginRequiredAction;
import org.tb.web.form.AddEmployeeOrderForm;
import org.tb.web.form.ShowEmployeeOrderForm;

public abstract class EmployeeOrderAction extends LoginRequiredAction {

	// /**
	// * Checks, if the employeeorder exists in the database. If it exists, the
	// form is filled with the data and the session attribute
	// "employeeorderalreadyexists" is set to true.
	// * @param request
	// * @param eoForm
	// */
	// protected void checkDatabaseForEmployeeOrder(HttpServletRequest request,
	// AddEmployeeOrderForm eoForm, EmployeecontractDAO employeecontractDAO,
	// EmployeeorderDAO employeeorderDAO) {
	// Employeecontract employeecontract =
	// employeecontractDAO.getEmployeeContractById(eoForm.getEmployeeContractId());
	// long employeecontractId = employeecontract.getId();
	// long suborderId = eoForm.getSuborderId();
	//		
	// Employeeorder employeeorder =
	// employeeorderDAO.getEmployeeorderByEmployeeContractIdAndSuborderId(employeecontractId,
	// suborderId);
	// if (employeeorder != null) {
	// request.getSession().setAttribute("employeeorderalreadyexists", true);
	// //fill form with data from existing employeeorder
	// SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd");
	// eoForm.setValidFrom(simpleDateFormat.format(employeeorder.getFromDate()));
	// eoForm.setValidUntil(simpleDateFormat.format(employeeorder.getUntilDate()));
	// eoForm.setStandingorder(employeeorder.getStandingorder());
	// eoForm.setDebithours(employeeorder.getDebithours());
	// eoForm.setStatus(employeeorder.getStatus());
	// eoForm.setStatusreport(employeeorder.getStatusreport());
	// } else
	// request.getSession().setAttribute("employeeorderalreadyexists", false);
	// }
	// }

	/**
	 * Refreshes the list of employee orders and stores it in the session.
	 * 
	 * @param request
	 * @param orderForm
	 * @param customerorderDAO 
	 * @param employeeorderDAO
	 */

	protected void refreshEmployeeSubOrders(HttpServletRequest request,
			ShowEmployeeOrderForm orderForm, SuborderDAO suborderDAO, CustomerorderDAO customerorderDAO) {
		
		Long suborderId = orderForm.getSuborderId();
		
//		if(orderForm.getEmployeeContractId() == -1 && orderForm.getOrderId()== -1){
//			orderForm.setSuborderId(-1);
//		}
		
		
		if((orderForm.getEmployeeContractId() > -1) && ((Long)request.getSession().getAttribute("currentOrderId")!= -1L)){
		request.getSession().setAttribute("suborders", suborderDAO.getSubordersByEmployeeContractIdAndCustomerorderId( orderForm.getEmployeeContractId(),customerorderDAO
				.getCustomerorderById(orderForm.getOrderId())
				.getId()));
		}
		else request.getSession().setAttribute("suborders", suborderDAO.getSubordersByCustomerorderId(orderForm.getOrderId()));
				
		request.getSession().setAttribute("curretntSuborder", suborderId);

		
        System.out.println("Ausgewähler Unterauftrag:"+ suborderId);
	}

	protected void refreshEmployeeOrders(HttpServletRequest request,
			ShowEmployeeOrderForm orderForm, EmployeeorderDAO employeeorderDAO,
			EmployeecontractDAO employeecontractDAO, TimereportDAO timereportDAO) {
		Employeecontract loginEmployeeContract = (Employeecontract) request
				.getSession().getAttribute("loginEmployeeContract");
		Employeecontract currentEmployeeContract = (Employeecontract) request
				.getSession().getAttribute("currentEmployeeContract");
		Long employeeContractId = 0L;
		Long orderId = 0L;
		if (orderForm != null) {
			employeeContractId = orderForm.getEmployeeContractId();
			orderId = orderForm.getOrderId();
		}

		if (employeeContractId == null || employeeContractId == 0) {
			if (currentEmployeeContract != null) {
				employeeContractId = currentEmployeeContract.getId();
			} else {
				employeeContractId = loginEmployeeContract.getId();
			}

		}

		if (orderId == null || orderId == 0) {
			if (request.getSession().getAttribute("currentOrderId") != null) {
				orderId = (Long) request.getSession().getAttribute(
						"currentOrderId");
			}
		}
		if (orderId == null || orderId == 0) {
			orderId = -1l;
		}

		if (orderForm != null) {
			orderForm.setEmployeeContractId(employeeContractId);
			orderForm.setOrderId(orderId);
		}

		request.getSession().setAttribute("currentOrderId", orderId);

		String filter = null;
		Boolean show = null;

		if ((request.getParameter("task") != null)
				&& (request.getParameter("task").equals("refresh"))) {
			filter = orderForm.getFilter();

			if (filter != null && !filter.trim().equals("")) {
				filter = filter.toUpperCase();
				filter = "%" + filter + "%";
			}
			request.getSession().setAttribute("employeeOrderFilter", filter);

			show = orderForm.getShow();
			request.getSession().setAttribute("employeeOrderShow", show);

		} else {
			if (request.getSession().getAttribute("employeeOrderFilter") != null) {
				filter = (String) request.getSession().getAttribute(
						"employeeOrderFilter");
				orderForm.setFilter(filter);
			}
			if (request.getSession().getAttribute("employeeOrderShow") != null) {
				show = (Boolean) request.getSession().getAttribute(
						"employeeOrderShow");
				orderForm.setShow(show);
			}
		}

		boolean showActualHours = orderForm.getShowActualHours();
		request.getSession().setAttribute("showActualHours", showActualHours);

		orderForm.setFilter(filter);
		orderForm.setOrderId(orderId);
		orderForm.setShow(show);
		orderForm.setShowActualHours(showActualHours);

		if (showActualHours) {
			/* show actual hours */
			List<Employeeorder> employeeOrders = employeeorderDAO
					.getEmployeeordersByFilters(show, filter,
							employeeContractId, orderId);
			List<EmployeeOrderViewDecorator> decorators = new LinkedList<EmployeeOrderViewDecorator>();
			for (Employeeorder employeeorder : employeeOrders) {
				EmployeeOrderViewDecorator decorator = new EmployeeOrderViewDecorator(
						timereportDAO, employeeorder);
				decorators.add(decorator);
			}
			request.getSession().setAttribute("employeeorders", decorators);
		} else {
			request.getSession().setAttribute(
					"employeeorders",
					employeeorderDAO.getEmployeeordersByFilters(show, filter,
							employeeContractId, orderId));
		}

		if (employeeContractId == -1) {
			request.getSession().setAttribute("currentEmployeeId",
					loginEmployeeContract.getEmployee().getId());
			request.getSession().setAttribute("currentEmployeeContract", null);
		} else {
			currentEmployeeContract = employeecontractDAO
					.getEmployeeContractById(employeeContractId);
			request.getSession().setAttribute("currentEmployeeId",
					currentEmployeeContract.getEmployee().getId());
			request.getSession().setAttribute("currentEmployeeContract",
					currentEmployeeContract);
		}
	}
	
	
	protected void refreshEmployeeOrdersAndSuborders(HttpServletRequest request,
			ShowEmployeeOrderForm orderForm, EmployeeorderDAO employeeorderDAO,
			EmployeecontractDAO employeecontractDAO, TimereportDAO timereportDAO, SuborderDAO suborderDAO, CustomerorderDAO customerorderDAO) {
		Employeecontract loginEmployeeContract = (Employeecontract) request
				.getSession().getAttribute("loginEmployeeContract");
		Employeecontract currentEmployeeContract = (Employeecontract) request
				.getSession().getAttribute("currentEmployeeContract");
		Long employeeContractId = 0L;
		Long orderId = 0L;
		Long suborderId = 0L;
		
		
		if (orderForm != null) {
			employeeContractId = orderForm.getEmployeeContractId();
			orderId = orderForm.getOrderId();
		}

		if (employeeContractId == null || employeeContractId == 0) {
			if (currentEmployeeContract != null) {
				employeeContractId = currentEmployeeContract.getId();
			} else {
				employeeContractId = loginEmployeeContract.getId();
			}

		}

		if (orderId == null || orderId == 0) {
			if (request.getSession().getAttribute("currentOrderId") != null) {
				orderId = (Long) request.getSession().getAttribute(
						"currentOrderId");
			}
		}
		
		
		if (orderId == null || orderId == 0) {
			orderId = -1l;
		}
			
		if (orderForm != null) {
			orderForm.setEmployeeContractId(employeeContractId);
			orderForm.setOrderId(orderId);
			
		}
		
		request.getSession().setAttribute("currentOrderId", orderId);
		
		if ((Long)request.getSession().getAttribute("currentOrderId")== -1L){
			orderForm.setSuborderId(-1);
			
		}

		
        suborderId = orderForm.getSuborderId();
		
		if(request.getSession().getAttribute("currentOrderId")!= null) {	
			if((orderForm.getEmployeeContractId() > -1) && ((Long)request.getSession().getAttribute("currentOrderId")!= -1L)){
			request.getSession().setAttribute("suborders", suborderDAO.getSubordersByEmployeeContractIdAndCustomerorderId( orderForm.getEmployeeContractId(),customerorderDAO
					.getCustomerorderById(orderForm.getOrderId())
					.getId()));
			}
			else
				request.getSession().setAttribute("suborders", suborderDAO.getSubordersByCustomerorderId(orderForm.getOrderId()));
		// actual suborder 			
			request.getSession().setAttribute("currentSub", suborderId);

			}
			
		

		String filter = null;
		Boolean show = null;

		if ((request.getParameter("task") != null)
				&& (request.getParameter("task").equals("refresh"))) {
			filter = orderForm.getFilter();

			if (filter != null && !filter.trim().equals("")) {
				filter = filter.toUpperCase();
				filter = "%" + filter + "%";
			}
			request.getSession().setAttribute("employeeOrderFilter", filter);

			show = orderForm.getShow();
			request.getSession().setAttribute("employeeOrderShow", show);

		} else {
			if (request.getSession().getAttribute("employeeOrderFilter") != null) {
				filter = (String) request.getSession().getAttribute(
						"employeeOrderFilter");
				orderForm.setFilter(filter);
			}
			if (request.getSession().getAttribute("employeeOrderShow") != null) {
				show = (Boolean) request.getSession().getAttribute(
						"employeeOrderShow");
				orderForm.setShow(show);
			}
		}

		boolean showActualHours = orderForm.getShowActualHours();
		request.getSession().setAttribute("showActualHours", showActualHours);

		orderForm.setFilter(filter);
		orderForm.setOrderId(orderId);
		orderForm.setSuborderId(suborderId);
		orderForm.setShow(show);
		orderForm.setShowActualHours(showActualHours);

		if (showActualHours) {
			/* show actual hours */
			List<Employeeorder> employeeOrders = employeeorderDAO
					.getEmployeeordersByFilters(show, filter,
							employeeContractId, orderId, suborderId);
			List<EmployeeOrderViewDecorator> decorators = new LinkedList<EmployeeOrderViewDecorator>();
			for (Employeeorder employeeorder : employeeOrders) {
				EmployeeOrderViewDecorator decorator = new EmployeeOrderViewDecorator(
						timereportDAO, employeeorder);
				decorators.add(decorator);
			}
			request.getSession().setAttribute("employeeorders", decorators);
		} else {
			request.getSession().setAttribute(
					"employeeorders",
					employeeorderDAO.getEmployeeordersByFilters(show, filter,
							employeeContractId, orderId, suborderId));
		}

		if (employeeContractId == -1) {
			request.getSession().setAttribute("currentEmployeeId",
					loginEmployeeContract.getEmployee().getId());
			request.getSession().setAttribute("currentEmployeeContract", null);
		} else {
			currentEmployeeContract = employeecontractDAO
					.getEmployeeContractById(employeeContractId);
			request.getSession().setAttribute("currentEmployeeId",
					currentEmployeeContract.getEmployee().getId());
			request.getSession().setAttribute("currentEmployeeContract",
					currentEmployeeContract);

		}
	}	

	/**
	 * Sets the from and until date in the form
	 * 
	 * @param request
	 * @param employeeOrderForm
	 */
	protected void setFormDates(HttpServletRequest request,
			AddEmployeeOrderForm employeeOrderForm) {
		Employeecontract employeecontract = (Employeecontract) request
				.getSession().getAttribute("currentEmployeeContract");
		Suborder suborder = (Suborder) request.getSession().getAttribute(
				"selectedsuborder");

		java.util.Date ecFromDate = null;
		java.util.Date ecUntilDate = null;
		java.util.Date soFromDate = null;
		java.util.Date soUntilDate = null;

		SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd");

		if (employeecontract != null) {
			ecFromDate = employeecontract.getValidFrom();
			ecUntilDate = employeecontract.getValidUntil();
		}
		if (suborder != null) {
			soFromDate = suborder.getFromDate();
			soUntilDate = suborder.getUntilDate();
		}

		// set from date
		if (ecFromDate != null && soFromDate != null) {
			if (ecFromDate.before(soFromDate)) {
				employeeOrderForm.setValidFrom(simpleDateFormat
						.format(soFromDate));
			} else {
				employeeOrderForm.setValidFrom(simpleDateFormat
						.format(ecFromDate));
			}
		} else if (ecFromDate != null && soFromDate == null) {
			employeeOrderForm.setValidFrom(simpleDateFormat.format(ecFromDate));
		} else if (ecFromDate == null && soFromDate != null) {
			employeeOrderForm.setValidFrom(simpleDateFormat.format(soFromDate));
		}

		// set until date
		if (ecUntilDate != null && soUntilDate != null) {
			if (ecUntilDate.after(soUntilDate)) {
				employeeOrderForm.setValidUntil(simpleDateFormat
						.format(soUntilDate));
			} else {
				employeeOrderForm.setValidUntil(simpleDateFormat
						.format(ecUntilDate));
			}
		} else if (ecUntilDate != null && soUntilDate == null) {
			employeeOrderForm.setValidUntil(simpleDateFormat
					.format(ecUntilDate));
		} else if (ecUntilDate == null && soUntilDate != null) {
			employeeOrderForm.setValidUntil(simpleDateFormat
					.format(soUntilDate));
		} else {
			employeeOrderForm.setValidUntil("");
		}

	}

}
