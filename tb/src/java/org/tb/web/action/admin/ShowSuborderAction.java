package org.tb.web.action.admin;

import java.util.List;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;
import org.tb.bdom.Customerorder;
import org.tb.bdom.Employee;
import org.tb.persistence.CustomerorderDAO;
import org.tb.persistence.SuborderDAO;
import org.tb.web.action.LoginRequiredAction;
import org.tb.web.form.ShowSuborderForm;

/**
 * action class for showing all suborders
 * 
 * @author oda
 *
 */
public class ShowSuborderAction extends LoginRequiredAction {

	
	private SuborderDAO suborderDAO;
	private CustomerorderDAO customerorderDAO;
	
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
		
		
		ShowSuborderForm suborderForm = (ShowSuborderForm) form;
		
		String filter = suborderForm.getFilter();
		
		filter = "%"+filter+"%";
		
		if (filter != null && !filter.equalsIgnoreCase("")) {
			request.getSession().setAttribute("suborders", suborderDAO.getSubordersByFilter(filter));
		} else {
			request.getSession().setAttribute("suborders", suborderDAO.getSubordersOrderedByCustomerorder());
		}
		
		// check if loginEmployee has responsibility for some orders
		Employee loginEmployee = (Employee) request.getSession().getAttribute("loginEmployee");
		List<Customerorder> orders = customerorderDAO.getCustomerOrdersByResponsibleEmployeeId(loginEmployee.getId());
		boolean employeeIsResponsible = false;
		
		if (orders != null && orders.size() > 0) {
			employeeIsResponsible =  true;
		}
		request.getSession().setAttribute("employeeIsResponsible", employeeIsResponsible);
		
		if (request.getParameter("task") != null) {
			if (request.getParameter("task").equalsIgnoreCase("back")) {
				// back to main menu
				return mapping.findForward("backtomenu");
			} else {
				// forward to show suborders jsp
				return mapping.findForward("success");
			}
		} else {		
			// forward to show suborders jsp
			return mapping.findForward("success");
		}
	}

}
