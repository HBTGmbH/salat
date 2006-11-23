package org.tb.web.form;

import javax.servlet.http.HttpServletRequest;

import org.apache.struts.action.ActionErrors;
import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionMapping;

/**
 * Form for showing all employee orders.
 * Actually not used - will be needed if we want to have an editable employee orders display
 * (like the timereport daily display)
 * 
 * @author oda
 *
 */
public class ShowEmployeeOrderForm extends ActionForm {


	private long employeeId;
	private long orderId;
	
	
	/**
	 * @return the employeeName
	 */
	public long getEmployeeId() {
		return employeeId;
	}

	/**
	 * @param employeeName the employeeName to set
	 */
	public void setEmployeeId(long employeeId) {
		this.employeeId = employeeId;
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

	@Override
	public void reset(ActionMapping mapping, HttpServletRequest request) {
		// actually, nothing to reset
	}

	@Override
	public ActionErrors validate(ActionMapping mapping, HttpServletRequest request) {
		ActionErrors errors = new ActionErrors();
		
		// actually, no checks here
		return errors;
	}

}
