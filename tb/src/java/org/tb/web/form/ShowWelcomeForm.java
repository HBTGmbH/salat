package org.tb.web.form;

import org.apache.struts.action.ActionForm;

public class ShowWelcomeForm extends ActionForm {
	
	private Long employeeContractId;

	public Long getEmployeeContractId() {
		return employeeContractId;
	}

	public void setEmployeeContractId(Long employeeContractId) {
		this.employeeContractId = employeeContractId;
	}
	
	

}
