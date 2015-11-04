package org.tb.web.action.admin;


import java.util.ArrayList;
import java.util.List;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tb.bdom.Customerorder;
import org.tb.bdom.Employee;
import org.tb.bdom.Suborder;
import org.tb.persistence.CustomerorderDAO;
import org.tb.persistence.SuborderDAO;
import org.tb.web.action.LoginRequiredAction;
import org.tb.web.form.ShowBudgetForm;

public class ShowBudgetAction extends LoginRequiredAction{
	private static final Logger LOG = LoggerFactory.getLogger(ShowBudgetAction.class);

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
		
		
		
		/*
		Customerorder co = customerorderDAO.getCustomerorderById(budgetForm.getCustomerOrderId());
		request.getSession().setAttribute("currentOrder", co);
		
		Long currentOrderId = new Long(0);
		try{
			currentOrderId = (Long) request.getSession().getAttribute("customerorderId");
			co = customerorderDAO.getCustomerorderById(currentOrderId);
			request.getSession().setAttribute("currentOrder", co);
		} catch (Throwable th){}

		
		
		

		TbLogger.getLogger().debug("ShowBudgetAction.executeAuthenticated - budgetForm.getCustomerOrderId():  " + currentOrderId);
		
		
		*/
		request.getSession().setAttribute("showResult", false);
		@SuppressWarnings("unused")
		Employee loginEmployee = (Employee) request.getSession().getAttribute("loginEmployee");
		
		List<Customerorder> visibleCustomerOrders = customerorderDAO.getVisibleCustomerorders();
		request.getSession().setAttribute("visibleCustomerOrders", visibleCustomerOrders);
		request.getSession().setAttribute("suborders", suborderDAO.getSuborders(false));
		ShowBudgetForm budgetForm = (ShowBudgetForm) form;
		LOG.debug("ShowBudgetAction.executeAuthenticated -request.getParameter(task) : " +request.getParameter("task"));
		Long orderOrSuborderId = new Long(-1);
		if (budgetForm.getCustomerOrderId() != null)
			orderOrSuborderId = budgetForm.getCustomerOrderId();
		if ((request.getParameter("id") != null)) {
			if (request.getParameter("id").equals("-1")){
				orderOrSuborderId = budgetForm.getCustomerOrderId();
				request.getSession().setAttribute("orderOrSuborderId", orderOrSuborderId);
				LOG.debug("ShowBudgetAction.executeAuthenticated - orderOrSuborderId : " + orderOrSuborderId);
			} else{
				orderOrSuborderId = Long.valueOf(request.getParameter("id"));
				request.getSession().setAttribute("orderOrSuborderId", orderOrSuborderId);
				LOG.debug("ShowBudgetAction.executeAuthenticated - orderOrSuborderId : " + orderOrSuborderId);
			}
			Suborder so = null; 
			so = suborderDAO.getSuborderById(orderOrSuborderId);
			request.getSession().setAttribute("orderOrSuborder", so);
			if (so == null) {
				Customerorder co = this.customerorderDAO.getCustomerorderById(orderOrSuborderId);
				request.getSession().setAttribute("orderOrSuborder", co);
				if (co!=null)
					request.getSession().setAttribute("orderOrSuborderSignAndDescription", co.getSignAndDescription());
			} else {
				request.getSession().setAttribute("orderOrSuborderSignAndDescription", so.getSignAndDescription());
			}
		}
		
		if ((request.getParameter("task") != null) && (request.getParameter("task").equals("refresh"))) {
			request.getSession().setAttribute("currentOrder", customerorderDAO.getCustomerorderById(budgetForm.getCustomerOrderId()));
		} else if((request.getParameter("task") != null) &&	(request.getParameter("task").equals("calcStructure"))) {
			request.getSession().setAttribute("showResult", true);
			ArrayList<String> changeFrom = new ArrayList<String>();
			ArrayList<String> changeTo = new ArrayList<String>();
			ArrayList<Long> changeId = new ArrayList<Long>();

			createListWithChanges(request, this.suborderDAO.getSuborders(false), changeFrom, changeTo, changeId);
			request.getSession().setAttribute("changeFrom", changeFrom);
			request.getSession().setAttribute("changeTo", changeTo);
			request.getSession().setAttribute("changeId", changeId);
			
		} else if((request.getParameter("task") != null) && (request.getParameter("task").equals("calcBudget"))) {
			
			//request.getSession().setAttribute("showResult", true);
			//ArrayList changes = getListWithChanges(request, this.suborderDAO.getSuborders());
			request.getSession().setAttribute("toChange",null);
			
		} else if((request.getParameter("task") != null) && (request.getParameter("task").equals("calcDebit"))) {
			
			//request.getSession().setAttribute("showResult", true);
			//ArrayList changes = getListWithChanges(request, this.suborderDAO.getSuborders());
			request.getSession().setAttribute("toChange",null);
			
		} else if((request.getParameter("task") != null) && 
				(request.getParameter("task").equals("editStructure"))) {
			List<Suborder> subs = this.suborderDAO.getSuborders(false);
			@SuppressWarnings("unchecked")
			ArrayList<Long> changeId = (ArrayList<Long>) request.getSession().getAttribute("changeId");
			@SuppressWarnings("unchecked")
			ArrayList<String> changeTo = (ArrayList<String>) request.getSession().getAttribute("changeTo");
			for (int i=0; i<subs.size();i++){
				for (int j=0; j < changeId.size(); j++){
				
					Suborder tempSuborder = subs.get(i);
					if (tempSuborder.getId() == changeId.get(j)){
						this.suborderDAO.getSuborders(false).get(i).setSign(changeTo.get(j));
					}
				}
			}
			//request.getSession().setAttribute("showResult", true);
			//ArrayList changes = getListWithChanges(request, this.suborderDAO.getSuborders());
			request.getSession().setAttribute("toChange", null);
			
		} else {
			LOG.debug("ShowBudgetAction.executeAuthenticated - budgetForm.getCustomerOrderId():  " + budgetForm.getCustomerOrderId());
			request.getSession().setAttribute("suborders", suborderDAO.getSuborders(false));
			request.getSession().setAttribute("currentOrder", null);
		}
		/*Customerorder current = ((Customerorder)request.getSession().getAttribute("currentOrder"));
		if (current != null)
			request.getSession().setAttribute("currentOrderId", current.getId());
		else
			request.getSession().setAttribute("currentOrderId", -1l);
		*/
		return mapping.findForward("success");
	}
	
	/**
	 * returns a list with all the changes that must be done for the clientrequest
	 * the content of the list is a list of strings
	 * @param suborders 
	 * @return
	 */
	private void createListWithChanges(HttpServletRequest request, List<Suborder> suborders, List<String> changeFrom, List<String> changeTo, List<Long> changeId){
		Long orderId;
		String orderSign;
		
		if (request.getSession().getAttribute("orderOrSuborder") instanceof Customerorder){
			Customerorder co = (Customerorder) request.getSession().getAttribute("orderOrSuborder");
			orderSign = co.getSign();
			orderId = co.getId();
		} else if (request.getSession().getAttribute("orderOrSuborder") instanceof Suborder){
			Suborder so = (Suborder) request.getSession().getAttribute("orderOrSuborder");
			orderSign = so.getSign();
			orderId = so.getId();
		} else{
			return;
		}

		int counter = 1;
		for (int i = 0; i<suborders.size();i++){
			if (suborders.get(i).getCustomerorder().getId() == orderId
					&& suborders.get(i).getParentorder() == null){
				fillRecursivly(changeFrom, changeTo, changeId,  suborders.get(i), suborders, orderSign + "." + counter );
				counter++;
			}
		}
		
		return;
	}

	/**
	 * helps to generate the signs of the following nodes of one parent node recursivly
	 * 
	 * @param changeFrom
	 * @param changeTo
	 * @param changeId
	 * @param suborder
	 * @param suborders
	 * @param parentSign
	 */
	private void fillRecursivly(List<String> changeFrom, List<String> changeTo, List<Long> changeId, Suborder suborder, List<Suborder> suborders, String parentSign) {
		int counter = 1;
		for (int i = 0; i<suborders.size();i++){
			if (suborders.get(i).getParentorder() == suborder){
				fillRecursivly(changeFrom, changeTo, changeId, suborders.get(i), suborders, parentSign + "." + counter);
				counter++;
			}
		}
		changeFrom.add(suborder.getSign());
		changeTo.add(parentSign);
		changeId.add(suborder.getId());
	}
	
}


