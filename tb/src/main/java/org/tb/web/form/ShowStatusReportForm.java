package org.tb.web.form;

import javax.annotation.Nonnull;
import javax.servlet.http.HttpServletRequest;

import org.apache.struts.action.ActionErrors;
import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionMapping;

public class ShowStatusReportForm extends ActionForm {
	
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L; // -3493035136151698227L;
	private Long customerOrderId;
	private Boolean showReleased = true;
	
	@Nonnull
	public Boolean getShowReleased() {
		return this.showReleased == null ? true : this.showReleased;
	}
	
	public void setShowReleased(Boolean showReleased) {
		this.showReleased = showReleased;
	}
	
	/**
	 * @return the customerOrderId
	 */
	public Long getCustomerOrderId() {
		return customerOrderId;
	}

	/**
	 * @param customerOrderId the customerOrderId to set
	 */
	public void setCustomerOrderId(Long customerOrderId) {
		this.customerOrderId = customerOrderId;
	}

	/* (non-Javadoc)
	 * @see org.apache.struts.action.ActionForm#reset(org.apache.struts.action.ActionMapping, javax.servlet.http.HttpServletRequest)
	 */
	@Override
	public void reset(ActionMapping arg0, HttpServletRequest arg1) {
		// TODO Auto-generated method stub
		this.showReleased = false;
	}

	/* (non-Javadoc)
	 * @see org.apache.struts.action.ActionForm#validate(org.apache.struts.action.ActionMapping, javax.servlet.http.HttpServletRequest)
	 */
	@Override
	public ActionErrors validate(ActionMapping arg0, HttpServletRequest arg1) {
		// TODO Auto-generated method stub
		return super.validate(arg0, arg1);
	}
	
	

}
