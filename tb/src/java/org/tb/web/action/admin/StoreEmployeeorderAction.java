package org.tb.web.action.admin;

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
import org.tb.bdom.Employeeorder;
import org.tb.bdom.Suborder;
import org.tb.bdom.Timereport;
import org.tb.persistence.CustomerorderDAO;
import org.tb.persistence.EmployeeDAO;
import org.tb.persistence.EmployeecontractDAO;
import org.tb.persistence.EmployeeorderDAO;
import org.tb.persistence.SuborderDAO;
import org.tb.persistence.TimereportDAO;
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
	
	private TimereportDAO timereportDAO;
	
	public void setTimereportDAO(TimereportDAO timereportDAO) {
		this.timereportDAO = timereportDAO;
	}

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

		// remove list with timereports out of range
		request.getSession().removeAttribute("timereportsOutOfRange");
		
		if ((request.getParameter("task") != null)
				&& (request.getParameter("task").equals("refreshEmployee"))) {
			Employeecontract employeecontract = employeecontractDAO
				.getEmployeeContractById(eoForm.getEmployeeContractId());
			if (employeecontract == null) {
				return mapping.findForward("error");
			} else {
				request.getSession().setAttribute("currentEmployeeContract", employeecontract);
				
				setFormDates(request, eoForm);
				return mapping.getInputForward();
			}
		}
		
		
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
				List<Suborder> suborders = co.getSuborders();
				// remove hidden suborders
				Iterator<Suborder> suborderIterator = suborders.iterator();
				while (suborderIterator.hasNext()) {
					Suborder suborder = suborderIterator.next();
					if (suborder.getHide() != null && suborder.getHide()) {
						suborderIterator.remove();
					}
				}
				request.getSession().setAttribute("suborders", suborders);
				
				request.getSession().setAttribute("selectedcustomerorder", co);
				eoForm.useDatesFromCustomerOrder(co);
				eoForm.setOrderId(co.getId());
				if (suborders != null && !suborders.isEmpty()) {
					eoForm.setHourlyRate(suborders.get(0).getHourly_rate());
					eoForm.setCurrency(suborders.get(0).getCurrency());
				} else {
					eoForm.setHourlyRate(co.getHourly_rate());
					eoForm.setCurrency(co.getCurrency());
				}
				// checkDatabaseForEmployeeOrder(request, eoForm,
				// employeecontractDAO, employeeorderDAO);
				request.getSession().setAttribute("currentOrderId", co.getId());
				setFormDates(request, eoForm);
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
				eoForm.setHourlyRate(so.getHourly_rate());
				eoForm.setCurrency(so.getCurrency());
			} else {
				eoForm.setHourlyRate(null);
				eoForm.setCurrency(GlobalConstants.DEFAULT_CURRENCY);
			}
			// checkDatabaseForEmployeeOrder(request, eoForm,
			// employeecontractDAO, employeeorderDAO);
			setFormDates(request, eoForm);
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
			long employeeContractId = employeecontract.getId();
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
					employeeorderDAO, employeecontractDAO, suborderDAO, eo.getId());
			if (errorMessages.size() > 0) {
				return mapping.getInputForward();
			}

			request.getSession().setAttribute("currentEmployeeContract", employeecontract);
			
			eo.setEmployeecontract(employeecontract);
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
				eo.setDebithoursunit(GlobalConstants.DEBITHOURS_UNIT_YEAR);
			} else if (eo.getSuborder().getCustomerorder().getSign().equals(
					GlobalConstants.CUSTOMERORDER_SIGN_ILL)) {
				eo.setDebithours(0.0);
				eo.setDebithoursunit(GlobalConstants.DEBITHOURS_UNIT_YEAR);
			} else {				
				if (eoForm.getDebithours() == null || eoForm.getDebithours() == 0.0) {
					eo.setDebithours(null);
					eo.setDebithoursunit(null);
				} else {
					eo.setDebithours(eoForm.getDebithours());
					eo.setDebithoursunit(eoForm.getDebithoursunit());
				}
			}
			eo.setStatusreport(eoForm.getStatusreport());
			
			if (eoForm.getHourlyRate() != null) {
				eo.setHourly_rate(eoForm.getHourlyRate());
				eo.setCurrency(eoForm.getCurrency());
			} else {
				eo.setHourly_rate(null);
				eo.setCurrency(null);
			}

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
				employeeContractId = -1;
			} else {
				employeeContractId = employeecontract.getId();
			}
				
			String filter = null;
			Boolean show = null;
			
			if (request.getSession().getAttribute("employeeOrderFilter") != null) {
				filter = (String) request.getSession().getAttribute("employeeOrderFilter");
			}
			if (request.getSession().getAttribute("employeeOrderShow") != null) {
				show = (Boolean) request.getSession().getAttribute("employeeOrderShow");
			}
		
			request.getSession().setAttribute("employeeorders", employeeorderDAO.getEmployeeordersByFilters(show, filter, employeeContractId, orderId));


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
			AddEmployeeOrderForm eoForm, EmployeeorderDAO employeeorderDAO, EmployeecontractDAO employeecontractDAO, 
			SuborderDAO suborderDAO, long eoId) {

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
			errors.add("debithours", new ActionMessage("form.employeeorder.error.debithours.wrongformat"));
		} else if (eoForm.getDebithours() != null && eoForm.getDebithours() != 0.0) {
			Double debithours = eoForm.getDebithours() * 100000;
			debithours += 0.5;
			
			int debithours2 = debithours.intValue();
			int modulo = debithours2%5000;
			eoForm.setDebithours(debithours2/100000.0);
			
			if (modulo != 0) {
				errors.add("debithours", new ActionMessage("form.customerorder.error.debithours.wrongformat2"));
			}
		} 
		
		if (eoForm.getDebithours() != 0.0) {
			if (eoForm.getDebithoursunit() == null || !(eoForm.getDebithoursunit() == GlobalConstants.DEBITHOURS_UNIT_MONTH ||
					eoForm.getDebithoursunit() == GlobalConstants.DEBITHOURS_UNIT_YEAR || eoForm.getDebithoursunit() == GlobalConstants.DEBITHOURS_UNIT_TOTALTIME)) {
				errors.add("debithours", new ActionMessage("form.customerorder.error.debithours.nounit"));
			}
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
		// check if dates fit to employee contract and suborder
		//TODO
		if (validFromDate != null) {
			Employeecontract ec = employeecontractDAO.getEmployeeContractById(eoForm.getEmployeeContractId());
			Suborder suborder = suborderDAO.getSuborderById(eoForm.getSuborderId());
			if (validFromDate.before(ec.getValidFrom())) {
				errors.add("validFrom", new ActionMessage("form.employeeorder.error.date.outofrange.employeecontract"));
			}
			if (validFromDate.before(suborder.getFromDate())) {
				errors.add("validFrom", new ActionMessage("form.employeeorder.error.date.outofrange.suborder"));
			}
			if ((validUntilDate == null && ec.getValidUntil() != null) || 
					(validUntilDate != null && ec.getValidUntil() != null &&  validUntilDate.after(ec.getValidUntil()))) {
				errors.add("validUntil", new ActionMessage("form.employeeorder.error.date.outofrange.employeecontract"));
			}
			if ((validUntilDate == null && suborder.getUntilDate() != null) || 
					(validUntilDate != null && suborder.getUntilDate() != null &&  validUntilDate.after(suborder.getUntilDate()))) {
				errors.add("validUntil", new ActionMessage("form.employeeorder.error.date.outofrange.suborder"));
			}
		}

		// check hourly rate format
		if (!GenericValidator.isDouble(eoForm.getHourlyRate().toString())
				|| (!GenericValidator.isInRange(eoForm.getHourlyRate(), 0.0,
						GlobalConstants.MAX_HOURLY_RATE))) {
			errors.add("hourlyRate", new ActionMessage(
					"form.suborder.error.hourlyrate.wrongformat"));
		}
		
		// check, if dates fit to existing timereports
		List<Timereport> timereportsInvalidForDates = timereportDAO.
			getTimereportsByEmployeeorderIdInvalidForDates(new java.sql.Date(validFromDate.getTime()), new java.sql.Date(validUntilDate.getTime()), eoId);
		if (timereportsInvalidForDates != null && !timereportsInvalidForDates.isEmpty()) {
			request.getSession().setAttribute("timereportsOutOfRange", timereportsInvalidForDates);
			errors.add("timereportOutOfRange", new ActionMessage("form.general.error.timereportoutofrange"));
			
		}
		
		saveErrors(request, errors);

		return errors;
	}
	
	

}
