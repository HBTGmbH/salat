package org.tb.web.action.admin;

import java.util.List;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;
import org.tb.GlobalConstants;
import org.tb.bdom.Customerorder;
import org.tb.bdom.Employee;
import org.tb.bdom.Suborder;
import org.tb.persistence.CustomerorderDAO;
import org.tb.persistence.SuborderDAO;
import org.tb.web.action.LoginRequiredAction;
import org.tb.web.form.AddSuborderForm;

/**
 * action class for editing a suborder
 * 
 * @author oda
 *
 */
public class EditSuborderAction extends LoginRequiredAction {
	
	private SuborderDAO suborderDAO;
	private CustomerorderDAO customerorderDAO;
	
	public void setSuborderDAO(SuborderDAO suborderDAO) {
		this.suborderDAO = suborderDAO;
	}
	
	public void setCustomerorderDAO(CustomerorderDAO customerorderDAO) {
		this.customerorderDAO = customerorderDAO;
	}

	@Override
	public ActionForward executeAuthenticated(ActionMapping mapping, ActionForm form, HttpServletRequest request, HttpServletResponse response) {
		
		AddSuborderForm soForm = (AddSuborderForm) form;
		long soId = Long.parseLong(request.getParameter("soId"));
		Suborder so = suborderDAO.getSuborderById(soId);
		request.getSession().setAttribute("soId", so.getId());
		
		// fill the form with properties of suborder to be edited
		setFormEntries(mapping, request, soForm, so);
		
		// make sure all customer orders are available in form
		Employee loginEmployee = (Employee) request.getSession().getAttribute("loginEmployee");
		List<Customerorder> customerorders;
		if (loginEmployee.getStatus().equals(GlobalConstants.EMPLOYEE_STATUS_BL)) {
			customerorders = customerorderDAO.getCustomerorders();
		} else {
			customerorders = customerorderDAO.getCustomerOrdersByResponsibleEmployeeId(loginEmployee.getId());
		}
		request.getSession().setAttribute("customerorders", customerorders);
		
		// forward to suborder add/edit form
		return mapping.findForward("success");	
	}
	
	/**
	 * fills suborder form with properties of given suborder
	 * 
	 * @param mapping
	 * @param request
	 * @param soForm
	 * @param so - the suborder
	 */
	private void setFormEntries(ActionMapping mapping, HttpServletRequest request, 
									AddSuborderForm soForm, Suborder so) {
		soForm.setCurrency(so.getCurrency());
		soForm.setCustomerorderId(so.getCustomerorder().getId());
		soForm.setHourlyRate(so.getHourly_rate());
		soForm.setSign(so.getSign());
		soForm.setDescription(so.getDescription());
		soForm.setInvoice(Character.toString(so.getInvoice()));
		
		request.getSession().setAttribute("currentOrderId", new Long(so.getCustomerorder().getId()));
		request.getSession().setAttribute("invoice", Character.toString(so.getInvoice()));
		request.getSession().setAttribute("currency", so.getCurrency());
		request.getSession().setAttribute("hourlyRate", so.getHourly_rate());
	}
	
}
