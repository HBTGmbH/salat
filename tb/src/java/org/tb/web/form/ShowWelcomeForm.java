package org.tb.web.form;

import org.apache.struts.action.ActionForm;

public class ShowWelcomeForm extends ActionForm {
	
	/**
	 * 
	 */
	private static final long serialVersionUID = -1868543616734155005L;
	private Long employeeContractId;

	public Long getEmployeeContractId() {
		return employeeContractId;
	}

	public void setEmployeeContractId(Long employeeContractId) {
		this.employeeContractId = employeeContractId;
	}
	
	

}
