package org.tb.web.form;

import javax.servlet.http.HttpServletRequest;

import org.apache.struts.action.ActionErrors;
import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionMapping;
import org.tb.bdom.Employeecontract;

/**
 * Form for showing all employee orders.
 * Actually not used - will be needed if we want to have an editable employee orders display
 * (like the timereport daily display)
 * 
 * @author oda
 *
 */
public class ShowEmployeeOrderForm extends ActionForm {


	private long employeeContractId;
	private long orderId;
	private String filter;
	private Boolean show;
	
	private Boolean showActualHours = false;
	
	
	/**
	 * @return the employeeContractId
	 */
	public long getEmployeeContractId() {
		return employeeContractId;
	}

	/**
	 * @param employeeContractId the employeeContractId to set
	 */
	public void setEmployeeContractId(long employeeContractId) {
		this.employeeContractId = employeeContractId;
	}
	
	

	/**
	 * @return the orderId
	 */
	public long getOrderId() {
		return orderId;
	}

	/**
	 * @param orderId the orderId to set
	 */
	public void setOrderId(long orderId) {
		this.orderId = orderId;
	}

	/**
	 * @return the filter
	 */
	public String getFilter() {
		return filter;
	}

	/**
	 * @param filter the filter to set
	 */
	public void setFilter(String filter) {
		this.filter = filter;
	}

	/**
	 * @return the show
	 */
	public Boolean getShow() {
		return show;
	}

	/**
	 * @param show the show to set
	 */
	public void setShow(Boolean show) {
		this.show = show;
	}

	/**
	 * @return the showActualHours
	 */
	public Boolean getShowActualHours() {
		return showActualHours;
	}

	/**
	 * @param showActualHours the showActualHours to set
	 */
	public void setShowActualHours(Boolean showActualHours) {
		this.showActualHours = showActualHours;
	}

	@Override
	public void reset(ActionMapping mapping, HttpServletRequest request) {
		Employeecontract currentEmployeeContract = (Employeecontract) request.getSession().getAttribute("currentEmployeeContract");
		if (currentEmployeeContract != null) {
			employeeContractId = currentEmployeeContract.getId();
		} else {
			Employeecontract loginEmployeeContract = (Employeecontract) request.getSession().getAttribute("loginEmployeeContract");
			employeeContractId = loginEmployeeContract.getId();
		}
		
		filter = "";
		show = false;
		showActualHours = false;
		
		
	}

	@Override
	public ActionErrors validate(ActionMapping mapping, HttpServletRequest request) {
		ActionErrors errors = new ActionErrors();
		
		// actually, no checks here
		return errors;
	}

}
