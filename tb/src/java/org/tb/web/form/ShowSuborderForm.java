package org.tb.web.form;

import javax.servlet.http.HttpServletRequest;

import org.apache.struts.action.ActionErrors;
import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionMapping;

/**
 * Form for showing all suborders.
 * Actually not used - will be needed if we want to have an editable suborders display
 * (like the timereport daily display)
 * 
 * @author oda
 *
 */
public class ShowSuborderForm extends ActionForm {

	private Boolean show;
	private String filter;
	private Long customerOrderId;
	
	
	
	
	public String getFilter() {
		return filter;
	}

	public void setFilter(String filter) {
		this.filter = filter;
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

	@Override
	public void reset(ActionMapping mapping, HttpServletRequest request) {
		filter = "";
		show = false;
		customerOrderId = -1L;
	}

	@Override
	public ActionErrors validate(ActionMapping mapping, HttpServletRequest request) {
		ActionErrors errors = new ActionErrors();
		
		// actually, no checks here
		return errors;
	}

}
