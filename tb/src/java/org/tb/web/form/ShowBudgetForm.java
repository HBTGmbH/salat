package org.tb.web.form;

import org.apache.struts.action.ActionForm;

public class ShowBudgetForm extends ActionForm{
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L; // 1194028167113461984L;
	private int text;
	private Long customerorderId = new Long(-1);
	private Long customerOrSuborderId = new Long(-1);
	

	public Long getCustomerorderId() {
		return customerorderId;
	}

	public void setCustomerorderId(Long customerorderId) {
		this.customerorderId = customerorderId;
	}

	public Long getCustomerOrSuborderId() {
		return customerOrSuborderId;
	}

	public void setCustomerOrSuborderId(Long customerOrSuborderId) {
		this.customerOrSuborderId = customerOrSuborderId;
	}

	public Long getCustomerOrderId() {
		return customerorderId;
	}

	public void setCustomerOrderId(Long customerOrderId) {
		this.customerorderId = customerOrderId;
	}

	public int getText() {
		return text;
	}

	public void setText(int text) {
		this.text = text;
	}
	
	
}
