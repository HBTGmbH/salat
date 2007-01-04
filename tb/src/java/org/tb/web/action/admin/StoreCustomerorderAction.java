package org.tb.web.action.admin;

import java.sql.Date;
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
import org.tb.persistence.CustomerDAO;
import org.tb.persistence.CustomerorderDAO;
import org.tb.persistence.EmployeeDAO;
import org.tb.util.DateUtils;
import org.tb.web.action.LoginRequiredAction;
import org.tb.web.form.AddCustomerOrderForm;

/**
 * action class for storing a customer order permanently
 * 
 * @author oda
 *
 */
public class StoreCustomerorderAction extends LoginRequiredAction {
	
	
	private CustomerDAO customerDAO;
	private CustomerorderDAO customerorderDAO;
	private EmployeeDAO employeeDAO;
	
	public void setEmployeeDAO(EmployeeDAO employeeDAO) {
		this.employeeDAO = employeeDAO;
	}
	
	public void setCustomerorderDAO(CustomerorderDAO customerorderDAO) {
		this.customerorderDAO = customerorderDAO;
	}

	public void setCustomerDAO(CustomerDAO customerDAO) {
		this.customerDAO = customerDAO;
	}


	@Override
	public ActionForward executeAuthenticated(ActionMapping mapping, ActionForm form, HttpServletRequest request, HttpServletResponse response) {
			AddCustomerOrderForm coForm = (AddCustomerOrderForm) form;
	
			if ((request.getParameter("task") != null) && 
					(request.getParameter("task").equals("save")) ||
					(request.getParameter("coId") != null)) {
						
				// 'main' task - prepare everything to store the customer.
				// I.e., copy properties from the form into the customer before saving.
				long coId = -1;
				Customerorder co = null;
				if (request.getSession().getAttribute("coId") != null) {
					// edited customerorder
					coId = Long.parseLong(request.getSession().getAttribute("coId").toString());
					co = customerorderDAO.getCustomerorderById(coId);
				} else {
					// new report
					co = new Customerorder();
				}
				
				ActionMessages errorMessages = validateFormData(request, coForm);
				if (errorMessages.size() > 0) {
					return mapping.getInputForward();
				}
				
				co.setCurrency(coForm.getCurrency());
				co.setCustomer(customerDAO.getCustomerById(coForm.getCustomerId()));
				
				Date fromDate = Date.valueOf(coForm.getValidFrom());
				Date untilDate = Date.valueOf(coForm.getValidUntil());
				co.setFromDate(fromDate);
				co.setUntilDate(untilDate);
				co.setSign(coForm.getSign());
				co.setDescription(coForm.getDescription());
				co.setHourly_rate(coForm.getHourlyRate());
				co.setOrder_customer(coForm.getOrderCustomer());
				
				co.setResponsible_customer_contractually(coForm.getResponsibleCustomerContractually());
				co.setResponsible_customer_technical(coForm.getResponsibleCustomerTechnical());
				co.setResponsible_hbt(employeeDAO.getEmployeeById(coForm.getEmployeeId()));
				
				
				Employee loginEmployee = (Employee)request.getSession().getAttribute("loginEmployee");
				customerorderDAO.save(co, loginEmployee);
				
				request.getSession().setAttribute("customerorders", customerorderDAO.getCustomerorders());
				request.getSession().removeAttribute("coId");
				
				boolean addMoreOrders = Boolean.parseBoolean((String)request.getParameter("continue"));
				if (!addMoreOrders) {
					return mapping.findForward("success");
				} else {
					// reuse form entries and show add-page
					return mapping.findForward("reset");
				}
			} 
			if ((request.getParameter("task") != null) && 
				(request.getParameter("task").equals("back"))) {	
				// go back
				request.getSession().removeAttribute("coId");
				coForm.reset(mapping, request);
				return mapping.findForward("cancel");
			} 
			if ((request.getParameter("task") != null) && 
				(request.getParameter("task").equals("reset"))) {	
				// reset form
				doResetActions(mapping, request, coForm);
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
	private void doResetActions(ActionMapping mapping, HttpServletRequest request, AddCustomerOrderForm coForm) {
		coForm.reset(mapping, request);
	}
	
	/**
	 * validates the form data (syntax and logic)
	 * 
	 * @param request
	 * @param cuForm
	 * @return
	 */
	private ActionMessages validateFormData(HttpServletRequest request, AddCustomerOrderForm coForm) {

		ActionMessages errors = getErrors(request);
		if(errors == null) errors = new ActionMessages();
		
		//	check date formats (must now be 'yyyy-MM-dd')
		String dateFromString = coForm.getValidFrom().trim();
		boolean dateError = DateUtils.validateDate(dateFromString);
		if (dateError) {
			errors.add("validFrom", new ActionMessage("form.timereport.error.date.wrongformat"));
		} 
		
		String dateUntilString = coForm.getValidUntil().trim();
		dateError = DateUtils.validateDate(dateUntilString);
		if (dateError) {
			errors.add("validUntil", new ActionMessage("form.timereport.error.date.wrongformat"));
		} 
		
		// for a new customerorder, check if the sign already exists
		if (request.getSession().getAttribute("coId") == null) {
			List<Customerorder> allCustomerorders = customerorderDAO.getCustomerorders();
			for (Iterator iter = allCustomerorders.iterator(); iter.hasNext();) {
				Customerorder co = (Customerorder) iter.next();
				if (co.getSign().equalsIgnoreCase(coForm.getSign())) {
					errors.add("sign", new ActionMessage("form.customerorder.error.sign.alreadyexists"));		
					break;
				}
			}
		}
		
		// check length of text fields and if they are filled
		if (coForm.getSign().length() > GlobalConstants.CUSTOMERORDER_SIGN_MAX_LENGTH) {
			errors.add("sign", new ActionMessage("form.customerorder.error.sign.toolong"));
		}
		if (coForm.getSign().length() <= 0) {
			errors.add("sign", new ActionMessage("form.customerorder.error.sign.required"));
		}
		if (coForm.getDescription().length() > GlobalConstants.CUSTOMERORDER_DESCRIPTION_MAX_LENGTH) {
			errors.add("description", new ActionMessage("form.customerorder.error.description.toolong"));
		}
		if (coForm.getCurrency().length() > GlobalConstants.CUSTOMERORDER_CURRENCY_MAX_LENGTH) {
			errors.add("currency", new ActionMessage("form.customerorder.error.currency.toolong"));
		}
		if (coForm.getCurrency().length() <= 0) {
			errors.add("currency", new ActionMessage("form.customerorder.error.currency.required"));
		}
		if (coForm.getOrderCustomer().length() > GlobalConstants.CUSTOMERORDER_ORDER_CUSTOMER_MAX_LENGTH) {
			errors.add("orderCustomer", new ActionMessage("form.customerorder.error.ordercustomer.toolong"));
		}
		if (coForm.getOrderCustomer().length() <= 0) {
			coForm.setOrderCustomer("-");
		}
		if (coForm.getResponsibleCustomerContractually().length() > GlobalConstants.CUSTOMERORDER_RESP_CUSTOMER_MAX_LENGTH) {
			errors.add("responsibleCustomerContractually", new ActionMessage("form.customerorder.error.responsiblecustomer.toolong"));
		}
		if (coForm.getResponsibleCustomerContractually().length() <= 0) {
			errors.add("responsibleCustomerContractually", new ActionMessage("form.customerorder.error.responsiblecustomer.required"));
		}
		if (coForm.getResponsibleCustomerTechnical().length() > GlobalConstants.CUSTOMERORDER_RESP_CUSTOMER_MAX_LENGTH) {
			errors.add("responsibleCustomerTechnical", new ActionMessage("form.customerorder.error.responsiblecustomer.toolong"));
		}
		if (coForm.getResponsibleCustomerTechnical().length() <= 0) {
			errors.add("responsibleCustomerTechnical", new ActionMessage("form.customerorder.error.responsiblecustomer.required"));
		}
				
		
		// check hourly rate format		
		if (!GenericValidator.isDouble(coForm.getHourlyRate().toString()) ||
				(!GenericValidator.isInRange(coForm.getHourlyRate(), 
						0.0, GlobalConstants.MAX_HOURLY_RATE))) {
			errors.add("hourlyRate", new ActionMessage("form.customerorder.error.hourlyrate.wrongformat"));
		}
		
		saveErrors(request, errors);
		
		return errors;
	}
}
