package org.tb.web.action.admin;

import java.util.Date;
import java.util.List;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;
import org.tb.bdom.Customer;
import org.tb.bdom.Customerorder;
import org.tb.bdom.Employee;
import org.tb.persistence.CustomerDAO;
import org.tb.persistence.CustomerorderDAO;
import org.tb.persistence.EmployeeDAO;
import org.tb.util.DateUtils;
import org.tb.web.action.LoginRequiredAction;
import org.tb.web.form.AddCustomerOrderForm;

/**
 * action class for editing a customer order
 * 
 * @author oda
 *
 */
public class EditCustomerorderAction extends LoginRequiredAction {
	
	private CustomerorderDAO customerorderDAO;
	private CustomerDAO customerDAO;
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
		
//		 remove list with timereports out of range
		request.getSession().removeAttribute("timereportsOutOfRange");
		
		AddCustomerOrderForm coForm = (AddCustomerOrderForm) form;
		long coId = Long.parseLong(request.getParameter("coId"));
		Customerorder co = customerorderDAO.getCustomerorderById(coId);
		request.getSession().setAttribute("coId", co.getId());
		
		List<Customer> customers = customerDAO.getCustomers();
		List<Customerorder> customerorders = customerorderDAO.getCustomerorders();
		
		if ((customers == null) || (customers.size() <= 0)) {
			request.setAttribute("errorMessage", 
					"No customers found - please call system administrator.");
			return mapping.findForward("error");
		}
	
		request.getSession().setAttribute("customerorders", customerorders);			
		request.getSession().setAttribute("customers", customers);
		
		// get list of employees with employee contract
		List<Employee> employeesWithContracts = employeeDAO.getEmployeesWithContracts();
		request.getSession().setAttribute("employeeswithcontract", employeesWithContracts);
		
		// fill the form with properties of cústomer order to be edited
		setFormEntries(mapping, request, coForm, co);
		
		// forward to customer order add/edit form
		return mapping.findForward("success");	
	}
	
	/**
	 * fills customer order form with properties of given cústomer
	 * 
	 * @param mapping
	 * @param request
	 * @param coForm
	 * @param co - the customer order
	 */
	private void setFormEntries(ActionMapping mapping, HttpServletRequest request, 
									AddCustomerOrderForm coForm, Customerorder co) {
		
		coForm.setCurrency(co.getCurrency());
		coForm.setCustomerId(co.getCustomer().getId());
		coForm.setHourlyRate(co.getHourly_rate());
		coForm.setOrderCustomer(co.getOrder_customer());
		
		coForm.setResponsibleCustomerContractually(co.getResponsible_customer_contractually());
		coForm.setResponsibleCustomerTechnical(co.getResponsible_customer_technical());
		if (co.getResponsible_hbt() != null) {
			coForm.setEmployeeId(co.getResponsible_hbt().getId());
		}
		coForm.setSign(co.getSign());
		coForm.setDescription(co.getDescription());
		coForm.setShortdescription(co.getShortdescription());
		
		Date fromDate = new Date(co.getFromDate().getTime()); // convert to java.util.Date
		//coForm.setValidFrom(DateUtils.getDateString(fromDate));
		coForm.setValidFrom(DateUtils.getSqlDateString(fromDate));
		if (co.getUntilDate() != null) {
			Date untilDate = new Date(co.getUntilDate().getTime()); // convert to java.util.Date
			//coForm.setValidUntil(DateUtils.getDateString(untilDate));
			coForm.setValidUntil(DateUtils.getSqlDateString(untilDate));
		} else {
			coForm.setValidUntil("");
		}
		
		if (co.getDebithours() != null) {
			coForm.setDebithours(co.getDebithours());
			coForm.setDebithoursunit(co.getDebithoursunit());
		} else {
			coForm.setDebithours(null);
			coForm.setDebithoursunit(null);
		}
		coForm.setHide(co.getHide());
		
	}
	
}
