package org.tb.web.action.admin;

import java.sql.Date;
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
import org.tb.helper.EmployeeHelper;
import org.tb.helper.SuborderHelper;
import org.tb.persistence.CustomerorderDAO;
import org.tb.persistence.EmployeeDAO;
import org.tb.persistence.EmployeecontractDAO;
import org.tb.persistence.EmployeeorderDAO;
import org.tb.persistence.SuborderDAO;
import org.tb.util.DateUtils;
import org.tb.web.action.LoginRequiredAction;
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
	public ActionForward executeAuthenticated(ActionMapping mapping, ActionForm form, HttpServletRequest request, HttpServletResponse response) {
			AddEmployeeOrderForm eoForm = (AddEmployeeOrderForm) form;
	
			if ((request.getParameter("task") != null) && 
					(request.getParameter("task").equals("refreshSuborders"))) {
				// refresh suborders to be displayed in the select menu:
				// get suborders related to selected customer order...
				// remove selection - displayed info would be false, if an error occurs
				request.getSession().removeAttribute("selectedcustomerorder");
				request.getSession().removeAttribute("selectedsuborder");
				long coId = eoForm.getOrderId();
				Customerorder co = customerorderDAO.getCustomerorderById(coId);
				if (co == null) {
					return mapping.findForward("error");
				}
				else {
					Suborder so = co.getSuborders().get(0);
					if (so != null) {
						eoForm.setSuborderId(so.getId());
						request.getSession().setAttribute("selectedsuborder", so);
					}
					request.getSession().setAttribute("suborders", co.getSuborders());
					request.getSession().setAttribute("selectedcustomerorder", co);
					eoForm.useDatesFromCustomerOrder(co);
					eoForm.setOrderId(co.getId());
					checkDatabaseForEmployeeOrder(request, eoForm, employeecontractDAO, employeeorderDAO);
					request.getSession().setAttribute("currentOrderId", co.getId());
					return mapping.getInputForward();
				}
			}
			
			if ((request.getParameter("task") != null) && 
					(request.getParameter("task").equals("refreshSuborderDescription"))) {
				// remove selection - displayed info would be false, if an error occurs
				request.getSession().removeAttribute("selectedsuborder");
				long soId = eoForm.getSuborderId();
				Suborder so = suborderDAO.getSuborderById(soId);
				if (so != null) {
					request.getSession().setAttribute("selectedsuborder", so);
					eoForm.setSuborderId(so.getId());
				}
				checkDatabaseForEmployeeOrder(request, eoForm, employeecontractDAO, employeeorderDAO);
				return mapping.getInputForward();
			}
			
			if ((request.getParameter("task") != null) &&
					(request.getParameter("task").equals("refreshEmployees"))) {
				// check if employeeorder for this employee, order, suborder already exists
				request.getSession().setAttribute("currentEmployeeId", eoForm.getEmployeeId());
				checkDatabaseForEmployeeOrder(request, eoForm, employeecontractDAO, employeeorderDAO);
				return mapping.getInputForward();
			}
			
			if ((request.getParameter("task") != null) && 
					(request.getParameter("task").equals("save")) ||
					(request.getParameter("eoId") != null)) {

				// 'main' task - prepare everything to store the employee order.
				// I.e., copy properties from the form into the employee order before saving.						
				long eoId = -1;
				
				Employeeorder eo = null;
				
				Employeecontract employeecontract = employeecontractDAO.getEmployeeContractByEmployeeId(eoForm.getEmployeeId());
				long employeecontractId = employeecontract.getId();
				long suborderId = eoForm.getSuborderId();
				
				Employeeorder employeeorderFromForm = employeeorderDAO.getEmployeeorderByEmployeeContractIdAndSuborderId(employeecontractId, suborderId);
				
				
				if (request.getSession().getAttribute("eoId") != null) {
					// edited employeeorder
					eoId = Long.parseLong(request.getSession().getAttribute("eoId").toString());
					eo = employeeorderDAO.getEmployeeorderById(eoId);
					if (employeeorderFromForm != null) {
						if (eo.getId() != employeeorderFromForm.getId()) {
							employeeorderDAO.deleteEmployeeorderById(eo.getId());
						}
						eo = employeeorderFromForm;
						
					}
				} else { 
					if (employeeorderFromForm != null) {
						eo = employeeorderFromForm;
					} else {
						// new report
						eo = new Employeeorder();
					}
				}
				
				ActionMessages errorMessages = validateFormData(request, eoForm);
				if (errorMessages.size() > 0) {
					return mapping.getInputForward();
				}
				
				Employeecontract ec = employeecontractDAO.getEmployeeContractByEmployeeId(eoForm.getEmployeeId());
				eo.setEmployeecontract(ec);
				eo.setSuborder(suborderDAO.getSuborderById(eoForm.getSuborderId()));
				
				Date fromDate = Date.valueOf(eoForm.getValidFrom());
				Date untilDate = Date.valueOf(eoForm.getValidUntil());
				eo.setFromDate(fromDate);
				eo.setUntilDate(untilDate);
				eo.setSign(eoForm.getSign());
//				eo.setStatus(eoForm.getStatus());
				eo.setStandingorder(eoForm.getStandingorder());
				if (eo.getSuborder().getCustomerorder().getSign().equals(GlobalConstants.CUSTOMERORDER_SIGN_VACATION)) {
					eo.setDebithours(eo.getEmployeecontract().getVacationEntitlement()*eo.getEmployeecontract().getDailyWorkingTime());
				} else if (eo.getSuborder().getCustomerorder().getSign().equals(GlobalConstants.CUSTOMERORDER_SIGN_ILL)) {
					eo.setDebithours(0.0);
				} else {
					eo.setDebithours(eoForm.getDebithours());
				}
				eo.setStatusreport(eoForm.getStatusreport());

				Employee loginEmployee = (Employee) request.getSession().getAttribute("loginEmployee");
				employeeorderDAO.save(eo, loginEmployee);
				
				// not necessary
//				List<Employee> employeeOptionList = employeeDAO.getEmployees();
//				request.getSession().setAttribute("employees", employeeOptionList);
				
				// refresh list of employee orders for overview
				long employeeId = (Long) request.getSession().getAttribute("currentEmployeeId");
				long orderId = (Long) request.getSession().getAttribute("currentOrderId");
				if (employeeId == -1) {
					if (orderId == -1) {
						request.getSession().setAttribute("employeeorders", employeeorderDAO.getSortedEmployeeorders());
					} else {
						request.getSession().setAttribute("employeeorders", employeeorderDAO.getEmployeeordersByOrderId(orderId));
					}
				} else {
					if (orderId == -1) {
						request.getSession().setAttribute("employeeorders", employeeorderDAO.getEmployeeOrdersByEmployeeId(employeeId));
					} else {
						request.getSession().setAttribute("employeeorders", employeeorderDAO.getEmployeeordersByOrderIdAndEmployeeId(orderId, employeeId));
					}
				}
				
//				request.getSession().setAttribute("employeeorders", employeeorderDAO.getSortedEmployeeorders());
				request.getSession().removeAttribute("eoId");
				
				boolean addMoreOrders = Boolean.parseBoolean((String)request.getParameter("continue"));
				if (!addMoreOrders) {
					return mapping.findForward("success");
				} else {
					// reuse current input of the form and show add-page
					return mapping.findForward("reset");
				}
			} 
			if ((request.getParameter("task") != null) && 
				(request.getParameter("task").equals("back"))) {	
				// go back
				request.getSession().removeAttribute("eoId");
				doResetActions(mapping, request, eoForm);
				// eoForm.reset(mapping, request);
				return mapping.findForward("cancel");
			} 
			if ((request.getParameter("task") != null) && 
				(request.getParameter("task").equals("reset"))) {	
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
	private void doResetActions(ActionMapping mapping, HttpServletRequest request, AddEmployeeOrderForm eoForm) {
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
	private ActionMessages validateFormData(HttpServletRequest request, AddEmployeeOrderForm eoForm) {

		ActionMessages errors = getErrors(request);
		if(errors == null) errors = new ActionMessages();
		
		//	check date formats (must now be 'yyyy-MM-dd')
		String dateFromString = eoForm.getValidFrom().trim();
		boolean dateError = DateUtils.validateDate(dateFromString);
		if (dateError) {
			errors.add("validFrom", new ActionMessage("form.timereport.error.date.wrongformat"));
		} 
		
		String dateUntilString = eoForm.getValidUntil().trim();
		dateError = DateUtils.validateDate(dateUntilString);
		if (dateError) {
			errors.add("validUntil", new ActionMessage("form.timereport.error.date.wrongformat"));
		} 
		
		// for a new employeeorder, check if the sign already exists
		// sign is actually not used any more!
//		if (request.getSession().getAttribute("eoId") == null) {
//			List<Employeeorder> allEmployeeorders = employeeorderDAO.getEmployeeorders();
//			for (Iterator iter = allEmployeeorders.iterator(); iter.hasNext();) {
//				Employeeorder eo = (Employeeorder) iter.next();
//				if (eo.getSign().equalsIgnoreCase(eoForm.getSign())) {
//					errors.add("sign", new ActionMessage("form.employeeorder.error.sign.alreadyexists"));		
//					break;
//				}
//			}
//		}
		
		// check if the order/suborder together with employee already exists

		//if (request.getSession().getAttribute("eoId") == null) {
//			List<Employeeorder> allEmployeeorders = employeeorderDAO.getEmployeeorders();
//			Suborder soInForm = suborderDAO.getSuborderById(eoForm.getSuborderId());
//			
//			if(soInForm != null) {
//				for (Iterator iter = allEmployeeorders.iterator(); iter.hasNext();) {
//					Employeeorder eo = (Employeeorder) iter.next();
//					System.err.println("SUBORDER SIGNS: " + eoForm.getSuborderId() + "/ '" +
//							eo.getSuborder().getSign() + "', '" + soInForm.getSign() + "'");
//					if ((eo.getSuborder().getSign().equalsIgnoreCase(soInForm.getSign())) &&
//						(eo.getSuborder().getCustomerorder().getSign().equalsIgnoreCase(soInForm.getCustomerorder().getSign())) &&
//						(eo.getEmployeecontract().getEmployee().getId() == (eoForm.getEmployeeId()))) {
//						errors.add("suborderId", new ActionMessage("form.employeeorder.error.employeesuborder.alreadyexist"));		
//						break;
//					}
//				}
//			}
		//}
		
		// check if valid suborder exists - otherwise, no save possible
		if (eoForm.getSuborderId() <= 0) {
			errors.add("suborderId", new ActionMessage("form.employeeorder.suborder.invalid"));
		}
		
		// check length of text fields and if they are filled
		
		// actually, sign is not used
//		if (eoForm.getSign().length() > GlobalConstants.EMPLOYEEORDER_SIGN_MAX_LENGTH) {
//			errors.add("sign", new ActionMessage("form.employeeorder.error.sign.toolong"));
//		}
//		if (eoForm.getSign().length() <= 0) {
//			errors.add("sign", new ActionMessage("form.employeeorder.error.sign.required"));
//		}
		
//		if (eoForm.getStatus().length() > GlobalConstants.EMPLOYEEORDER_STATUS_MAX_LENGTH) {
//			errors.add("status", new ActionMessage("form.employeeorder.error.status.toolong"));
//		}
		
		// actually, status is not required
//		if (eoForm.getStatus().length() <= 0) {
//			errors.add("status", new ActionMessage("form.employeeorder.error.status.required"));
//		}
		
		// check debit hours format		
		if (!GenericValidator.isDouble(eoForm.getDebithours().toString()) ||
				(!GenericValidator.isInRange(eoForm.getDebithours(), 
						0.0, GlobalConstants.MAX_DEBITHOURS))) {
			errors.add("debithours", new ActionMessage("form.employeeorder.error.debithours.wrongformat"));
		}
		
		saveErrors(request, errors);
		
		return errors;
	}
	
	
}
