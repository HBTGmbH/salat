package org.tb.web.action.admin;

import java.util.List;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;
import org.tb.bdom.Customerorder;
import org.tb.bdom.Employee;
import org.tb.logging.TbLogger;
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
		List<Customerorder> visibleCustomerOrders = customerorderDAO.getVisibleCustomerorders();
		request.getSession().setAttribute("visibleCustomerOrders", visibleCustomerOrders);
				
		String filter = null;
		Boolean show = null;
		Long customerOrderId = null; 
		
		TbLogger.getLogger().debug("suborderForm.getShowStructure()"  + suborderForm.getShowstructure());
		TbLogger.getLogger().debug("suborderForm.getShow();" + suborderForm.getShow());
		TbLogger.getLogger().debug("suborderForm.getFilter-()"  + suborderForm.getFilter());
		TbLogger.getLogger().debug("suborderForm.getCustomerOrderId-();" + suborderForm.getCustomerOrderId());
		TbLogger.getLogger().debug("suborderFilter"  + request.getSession().getAttribute("suborderFilter"));
		TbLogger.getLogger().debug("suborderShow;" + request.getSession().getAttribute("suborderShow"));
		TbLogger.getLogger().debug("suborderCustomerOrderId"  + request.getSession().getAttribute("suborderCustomerOrderId"));
		TbLogger.getLogger().debug("showStructure" + request.getSession().getAttribute("showStructure"));
		
		
		if ((request.getParameter("task") != null) && 
				(request.getParameter("task").equals("refresh"))) {
			
			Boolean showStructure = suborderForm.getShowstructure();
			request.getSession().setAttribute("showStructure", showStructure);
			
			filter = suborderForm.getFilter();

			if (filter != null && !filter.trim().equals("")) {
				filter = filter.toUpperCase();
				filter = "%" + filter + "%";
			}			
			request.getSession().setAttribute("suborderFilter", filter);
			
			show = suborderForm.getShow();
			request.getSession().setAttribute("suborderShow", show);
			
			customerOrderId = suborderForm.getCustomerOrderId();
			request.getSession().setAttribute("suborderCustomerOrderId", customerOrderId);
			
			Customerorder co = customerorderDAO.getCustomerorderById(suborderForm.getCustomerOrderId());
			TbLogger.getLogger().debug("ShowSuborderAction.executeAuthenticated - suborderForm.getCustomerOrderId()" + suborderForm.getCustomerOrderId());
			request.getSession().setAttribute("currentOrder", co);
			if (customerOrderId == -1){
				request.getSession().setAttribute("showStructure", false);
				suborderForm.setShowstructure(false);
			}
			
		} else {
			if (request.getSession().getAttribute("suborderFilter") != null) {
				filter = (String) request.getSession().getAttribute("suborderFilter");
				suborderForm.setFilter(filter);
			}
			if (request.getSession().getAttribute("suborderShow") != null) {
				show = (Boolean) request.getSession().getAttribute("suborderShow");
				suborderForm.setShow(show);
			}
			if (request.getSession().getAttribute("suborderCustomerOrderId") != null) {
				customerOrderId = (Long) request.getSession().getAttribute("suborderCustomerOrderId");
				suborderForm.setCustomerOrderId(customerOrderId);
				Customerorder co = customerorderDAO.getCustomerorderById(suborderForm.getCustomerOrderId());
				TbLogger.getLogger().debug("ShowSuborderAction.executeAuthenticated - suborderForm.getCustomerOrderId()" + suborderForm.getCustomerOrderId());
				request.getSession().setAttribute("currentOrder", co);
			} else{
				request.getSession().setAttribute("suborderCustomerOrderId", new Long(-1));
				suborderForm.setCustomerOrderId(customerOrderId);
			}
			if (request.getSession().getAttribute("showStructure") != null) {
				Boolean showStructure = (Boolean) request.getSession().getAttribute("showStructure");
				suborderForm.setShowstructure(showStructure);
			}else{
				request.getSession().setAttribute("showStructure", false);
				suborderForm.setShowstructure(false);
			}
			
		}
		
		request.getSession().setAttribute("suborders", suborderDAO.getSubordersByFilters(show, filter, customerOrderId));			

		// check if loginEmployee has responsibility for some orders
		Employee loginEmployee = (Employee) request.getSession().getAttribute("loginEmployee");
		List<Customerorder> orders = customerorderDAO.getVisibleCustomerOrdersByResponsibleEmployeeId(loginEmployee.getId());
		boolean employeeIsResponsible = false;
		
		if (orders != null && orders.size() > 0) {
			employeeIsResponsible =  true;
		}
		request.getSession().setAttribute("employeeIsResponsible", employeeIsResponsible);
		
		// check if there are visible customer orders
		orders = customerorderDAO.getVisibleCustomerorders();
		boolean visibleOrdersPresent = false;
		if (orders != null && !orders.isEmpty()) {
			visibleOrdersPresent = true;
		} 
		request.getSession().setAttribute("visibleOrdersPresent", visibleOrdersPresent);
		
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
