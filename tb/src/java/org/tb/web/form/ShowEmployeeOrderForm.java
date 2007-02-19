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

	@Override
	public void reset(ActionMapping mapping, HttpServletRequest request) {
		Employeecontract currentEmployeeContract = (Employeecontract) request.getSession().getAttribute("currentEmployeeContract");
		if (currentEmployeeContract != null) {
			employeeContractId = currentEmployeeContract.getId();
		} else {
			Employeecontract loginEmployeeContract = (Employeecontract) request.getSession().getAttribute("loginEmployeeContract");
			employeeContractId = loginEmployeeContract.getId();
		}
	}

	@Override
	public ActionErrors validate(ActionMapping mapping, HttpServletRequest request) {
		ActionErrors errors = new ActionErrors();
		
		// actually, no checks here
		return errors;
	}

}
