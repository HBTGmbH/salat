package org.tb.web.action.admin;

import java.sql.Date;
import java.text.ParseException;
import java.text.SimpleDateFormat;
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
import org.tb.bdom.Employeeorder;
import org.tb.bdom.Suborder;
import org.tb.persistence.CustomerorderDAO;
import org.tb.persistence.EmployeeDAO;
import org.tb.persistence.EmployeecontractDAO;
import org.tb.persistence.EmployeeorderDAO;
import org.tb.persistence.SuborderDAO;
import org.tb.util.DateUtils;
import org.tb.web.form.AddEmployeeOrderForm;

/**
 * action class for storing an employee order permanently
 * 
 * @author oda
 * 
 */
public class StoreEmployeeorderAction extends EmployeeOrderAction {

	private EmployeeDAO employeeDAO;

	private EmployeecontractDAO employeecontractDAO;

	private EmployeeorderDAO employeeorderDAO;

	private CustomerorderDAO customerorderDAO;

	private SuborderDAO suborderDAO;

	public void setEmployeeorderDAO(EmployeeorderDAO employeeorderDAO) {
		this.employeeorderDAO = employeeorderDAO;
	}

	public void setEmployeecontractDAO(EmployeecontractDAO employeecontractDAO) {
		this.employeecontractDAO = employeecontractDAO;
	}

	public void setEmployeeDAO(EmployeeDAO employeeDAO) {
		this.employeeDAO = employeeDAO;
	}

	public void setCustomerorderDAO(CustomerorderDAO customerorderDAO) {
		this.customerorderDAO = customerorderDAO;
	}

	public void setSuborderDAO(SuborderDAO suborderDAO) {
		this.suborderDAO = suborderDAO;
	}

	@Override
	public ActionForward executeAuthenticated(ActionMapping mapping,
			ActionForm form, HttpServletRequest request,
			HttpServletResponse response) {
		AddEmployeeOrderForm eoForm = (AddEmployeeOrderForm) form;

		if ((request.getParameter("task") != null)
				&& (request.getParameter("task").equals("refreshSuborders"))) {
			// refresh suborders to be displayed in the select menu:
			// get suborders related to selected customer order...
			// remove selection - displayed info would be false, if an error
			// occurs
			request.getSession().removeAttribute("selectedcustomerorder");
			request.getSession().removeAttribute("selectedsuborder");
			long coId = eoForm.getOrderId();
			Customerorder co = customerorderDAO.getCustomerorderById(coId);
			if (co == null) {
				return mapping.findForward("error");
			} else {
				Suborder so = co.getSuborders().get(0);
				if (so != null) {
					eoForm.setSuborderId(so.getId());
					request.getSession().setAttribute("selectedsuborder", so);
				}
				request.getSession().setAttribute("suborders",
						co.getSuborders());
				request.getSession().setAttribute("selectedcustomerorder", co);
				eoForm.useDatesFromCustomerOrder(co);
				eoForm.setOrderId(co.getId());
				// checkDatabaseForEmployeeOrder(request, eoForm,
				// employeecontractDAO, employeeorderDAO);
				request.getSession().setAttribute("currentOrderId", co.getId());
				return mapping.getInputForward();
			}
		}

		if ((request.getParameter("task") != null)
				&& (request.getParameter("task")
						.equals("refreshSuborderDescription"))) {
			// remove selection - displayed info would be false, if an error
			// occurs
			request.getSession().removeAttribute("selectedsuborder");
			long soId = eoForm.getSuborderId();
			Suborder so = suborderDAO.getSuborderById(soId);
			if (so != null) {
				request.getSession().setAttribute("selectedsuborder", so);
				eoForm.setSuborderId(so.getId());
			}
			// checkDatabaseForEmployeeOrder(request, eoForm,
			// employeecontractDAO, employeeorderDAO);
			return mapping.getInputForward();
		}

		// if ((request.getParameter("task") != null) &&
		// (request.getParameter("task").equals("refreshEmployees"))) {
		// // check if employeeorder for this employee, order, suborder already
		// exists
		// Employeecontract employeecontract =
		// employeecontractDAO.getEmployeeContractById(eoForm.getEmployeeContractId());
		// request.getSession().setAttribute("currentEmployeeId",
		// employeecontract.getEmployee().getId());
		// request.getSession().setAttribute("currentEmployeeContract",
		// employeecontract);
		// checkDatabaseForEmployeeOrder(request, eoForm, employeecontractDAO,
		// employeeorderDAO);
		// return mapping.getInputForward();
		// }

		if ((request.getParameter("task") != null)
				&& (request.getParameter("task").equals("save"))
				|| (request.getParameter("eoId") != null)) {

			// 'main' task - prepare everything to store the employee order.
			// I.e., copy properties from the form into the employee order
			// before saving.
			long eoId = -1;

			Employeeorder eo = null;

			Employeecontract employeecontract = employeecontractDAO
					.getEmployeeContractById(eoForm.getEmployeeContractId());
			long employeecontractId = employeecontract.getId();
			long suborderId = eoForm.getSuborderId();

			// Employeeorder employeeorderFromForm =
			// employeeorderDAO.getEmployeeorderByEmployeeContractIdAndSuborderId(employeecontractId,
			// suborderId);

			if (request.getSession().getAttribute("eoId") != null) {
				// edited employeeorder
				eoId = Long.parseLong(request.getSession().getAttribute("eoId")
						.toString());
				eo = employeeorderDAO.getEmployeeorderById(eoId);
				// if (employeeorderFromForm != null) {
				// if (eo.getId() != employeeorderFromForm.getId()) {
				// employeeorderDAO.deleteEmployeeorderById(eo.getId());
				// }
				// eo = employeeorderFromForm;
				//						
				// }
			} else {
				// if (employeeorderFromForm != null) {
				// eo = employeeorderFromForm;
				// } else {
				// new report
				eo = new Employeeorder();
				// }
			}

			ActionMessages errorMessages = validateFormData(request, eoForm,
					employeeorderDAO, eo.getId());
			if (errorMessages.size() > 0) {
				return mapping.getInputForward();
			}

			Employeecontract ec = employeecontractDAO
					.getEmployeeContractById(eoForm.getEmployeeContractId());
			eo.setEmployeecontract(ec);
			eo.setSuborder(suborderDAO.getSuborderById(eoForm.getSuborderId()));

			Date fromDate = Date.valueOf(eoForm.getValidFrom());

			if (eoForm.getValidUntil() == null
					|| eoForm.getValidUntil() == "".trim()) {
				eo.setUntilDate(null);
			} else {
				Date untilDate = Date.valueOf(eoForm.getValidUntil());
				eo.setUntilDate(untilDate);
			}
			eo.setFromDate(fromDate);

			eo.setSign(eoForm.getSign());
			// eo.setStatus(eoForm.getStatus());
			eo.setStandingorder(eoForm.getStandingorder());
			if (eo.getSuborder().getCustomerorder().getSign().equals(
					GlobalConstants.CUSTOMERORDER_SIGN_VACATION)) {
				eo.setDebithours(eo.getEmployeecontract()
						.getVacationEntitlement()
						* eo.getEmployeecontract().getDailyWorkingTime());
			} else if (eo.getSuborder().getCustomerorder().getSign().equals(
					GlobalConstants.CUSTOMERORDER_SIGN_ILL)) {
				eo.setDebithours(0.0);
			} else {
				eo.setDebithours(eoForm.getDebithours());
			}
			eo.setStatusreport(eoForm.getStatusreport());

			Employee loginEmployee = (Employee) request.getSession()
					.getAttribute("loginEmployee");
			employeeorderDAO.save(eo, loginEmployee);

			// not necessary
			// List<Employee> employeeOptionList = employeeDAO.getEmployees();
			// request.getSession().setAttribute("employees",
			// employeeOptionList);

			// refresh list of employee orders for overview
			// long employeeId = (Long)
			// request.getSession().getAttribute("currentEmployeeId");
			employeecontract = (Employeecontract) request.getSession()
					.getAttribute("currentEmployeeContract");
			long orderId = (Long) request.getSession().getAttribute(
					"currentOrderId");
			if (employeecontract == null) {
				if (orderId == -1) {
					request.getSession().setAttribute("employeeorders",
							employeeorderDAO.getSortedEmployeeorders());
				} else {
					request.getSession().setAttribute(
							"employeeorders",
							employeeorderDAO
									.getEmployeeordersByOrderId(orderId));
				}
			} else {
				if (orderId == -1) {
					request
							.getSession()
							.setAttribute(
									"employeeorders",
									employeeorderDAO
											.getEmployeeOrdersByEmployeeContractId(employeecontract
													.getId()));
				} else {
					request
							.getSession()
							.setAttribute(
									"employeeorders",
									employeeorderDAO
											.getEmployeeordersByOrderIdAndEmployeeContractId(
													orderId, employeecontract
															.getId()));
				}
			}

			// request.getSession().setAttribute("employeeorders",
			// employeeorderDAO.getSortedEmployeeorders());
			request.getSession().removeAttribute("eoId");

			boolean addMoreOrders = Boolean.parseBoolean((String) request
					.getParameter("continue"));
			if (!addMoreOrders) {
				return mapping.findForward("success");
			} else {
				// reuse current input of the form and show add-page
				return mapping.findForward("reset");
			}
		}
		if ((request.getParameter("task") != null)
				&& (request.getParameter("task").equals("back"))) {
			// go back
			request.getSession().removeAttribute("eoId");
			doResetActions(mapping, request, eoForm);
			// eoForm.reset(mapping, request);
			return mapping.findForward("cancel");
		}
		if ((request.getParameter("task") != null)
				&& (request.getParameter("task").equals("reset"))) {
			// reset form
			doResetActions(mapping, request, eoForm);
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
	private void doResetActions(ActionMapping mapping,
			HttpServletRequest request, AddEmployeeOrderForm eoForm) {
		eoForm.reset(mapping, request);
		long coId = eoForm.getOrderId();
		Customerorder co = customerorderDAO.getCustomerorderById(coId);
		eoForm.useDatesFromCustomerOrder(co);
	}

	/**
	 * validates the form data (syntax and logic)
	 * 
	 * @param request
	 * @param cuForm
	 * @return
	 */
	private ActionMessages validateFormData(HttpServletRequest request,
			AddEmployeeOrderForm eoForm, EmployeeorderDAO employeeorderDAO,
			long eoId) {

		ActionMessages errors = getErrors(request);
		if (errors == null)
			errors = new ActionMessages();

		// check date formats (must now be 'yyyy-MM-dd')
		// String dateFromString = eoForm.getValidFrom().trim();
		// boolean dateError = DateUtils.validateDate(dateFromString);
		// if (dateError) {
		// errors.add("validFrom", new
		// ActionMessage("form.timereport.error.date.wrongformat"));
		// }
		//		
		// String dateUntilString = eoForm.getValidUntil().trim();
		// dateError = DateUtils.validateDate(dateUntilString);
		// if (dateError) {
		// errors.add("validUntil", new
		// ActionMessage("form.timereport.error.date.wrongformat"));
		// }

		SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd");
		java.util.Date validFromDate = null;
		java.util.Date validUntilDate = null;
		try {
			validFromDate = simpleDateFormat.parse(eoForm.getValidFrom());
		} catch (ParseException e) {
			errors.add("validFrom", new ActionMessage(
					"form.timereport.error.date.wrongformat"));
		}
		if (eoForm.getValidUntil() != null
				&& eoForm.getValidUntil() != "".trim()) {
			try {
				validUntilDate = simpleDateFormat.parse(eoForm.getValidUntil());
			} catch (ParseException e) {
				errors.add("validUntil", new ActionMessage(
						"form.timereport.error.date.wrongformat"));
			}
		}
		
		// check if begin is before end
		if (validFromDate != null && validUntilDate != null) {
			if (validUntilDate.before(validFromDate)) {
				errors.add("validUntil", new ActionMessage(
						"form.timereport.error.date.endbeforebegin"));
			}
		}
		
		// check if valid suborder exists - otherwise, no save possible
		if (eoForm.getSuborderId() <= 0) {
			errors.add("suborderId", new ActionMessage(
					"form.employeeorder.suborder.invalid"));
		}

		// check length of text fields and if they are filled

		// actually, sign is not used
		// if (eoForm.getSign().length() >
		// GlobalConstants.EMPLOYEEORDER_SIGN_MAX_LENGTH) {
		// errors.add("sign", new
		// ActionMessage("form.employeeorder.error.sign.toolong"));
		// }
		// if (eoForm.getSign().length() <= 0) {
		// errors.add("sign", new
		// ActionMessage("form.employeeorder.error.sign.required"));
		// }

		// if (eoForm.getStatus().length() >
		// GlobalConstants.EMPLOYEEORDER_STATUS_MAX_LENGTH) {
		// errors.add("status", new
		// ActionMessage("form.employeeorder.error.status.toolong"));
		// }

		// actually, status is not required
		// if (eoForm.getStatus().length() <= 0) {
		// errors.add("status", new
		// ActionMessage("form.employeeorder.error.status.required"));
		// }

		// check debit hours format
		if (!GenericValidator.isDouble(eoForm.getDebithours().toString())
				|| (!GenericValidator.isInRange(eoForm.getDebithours(), 0.0,
						GlobalConstants.MAX_DEBITHOURS))) {
			errors.add("debithours", new ActionMessage(
					"form.employeeorder.error.debithours.wrongformat"));
		}

		// check for overleap with another employee order for the same employee
		// contract and suborder
		List<Employeeorder> employeeOrders = employeeorderDAO
				.getEmployeeOrdersByEmployeeContractIdAndSuborderId(eoForm
						.getEmployeeContractId(), eoForm.getSuborderId());
		if (employeeOrders != null && !employeeOrders.isEmpty()) {
			// SimpleDateFormat simpleDateFormat = new
			// SimpleDateFormat("yyyy-MM-dd");
			// java.util.Date validFromDate;
			// java.util.Date validUntilDate;
			if (validFromDate != null) {
				for (Employeeorder employeeorder : employeeOrders) {
					if (eoId != employeeorder.getId()) {
						if (validUntilDate != null && employeeorder.getUntilDate() != null) {
							if (!validFromDate.before(employeeorder
									.getFromDate())
									&& !validFromDate.after(employeeorder
											.getUntilDate())) {
								// validFrom overleaps!
								errors.add("overleap", new ActionMessage(
										"form.employeeorder.error.overleap"));
								break;
							}
							if (!validUntilDate.before(employeeorder
									.getFromDate())
									&& !validUntilDate.after(employeeorder
											.getUntilDate())) {
								// validUntil overleaps!
								errors.add("overleap", new ActionMessage(
										"form.employeeorder.error.overleap"));
								break;
							}
							if (validFromDate.before(employeeorder
									.getFromDate())
									&& validUntilDate.after(employeeorder
											.getUntilDate())) {
								// new Employee order enclosures an existing one
								errors.add("overleap", new ActionMessage(
										"form.employeeorder.error.overleap"));
								break;
							}
						} else if (validUntilDate == null && employeeorder.getUntilDate() != null) {
							if (!validFromDate.after(employeeorder.getUntilDate())) {
								errors.add("overleap", new ActionMessage(
										"form.employeeorder.error.overleap"));
								break;
							}	
						} else if (validUntilDate != null && employeeorder.getUntilDate() == null) {
							if (!validUntilDate.before(employeeorder.getFromDate())) {
								errors.add("overleap", new ActionMessage(
										"form.employeeorder.error.overleap"));
								break;
							}
						} else {
							// two employee orders with open end MUST overleap
							errors.add("overleap", new ActionMessage(
								"form.employeeorder.error.overleap"));
							break;
						}
					} 
				}
			} 
		}

		saveErrors(request, errors);

		return errors;
	}

}
