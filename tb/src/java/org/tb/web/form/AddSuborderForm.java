package org.tb.web.form;

import javax.servlet.http.HttpServletRequest;

import org.apache.struts.action.ActionErrors;
import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionMapping;
import org.tb.GlobalConstants;

/**
 * Form for adding a suborder
 * 
 * @author oda
 *
 */
public class AddSuborderForm extends ActionForm {

	private long id;
	private String sign;
	private String description;
	private String shortdescription;
	private String invoice;
	private String currency;
	private Double hourlyRate;
	
	private long customerorderId;
	
	
	private String action;
	private Boolean standard;
	private Boolean commentnecessary;

	
	public long getId() {
		return id;
	}

	public void setId(long id) {
		this.id = id;
	}

	public String getAction() {
		return action;
	}

	public void setAction(String action) {
		this.action = action;
	}

	public String getCurrency() {
		return currency;
	}

	public void setCurrency(String currency) {
		this.currency = currency;
	}

	public long getCustomerorderId() {
		return customerorderId;
	}

	public void setCustomerorderId(long customerorderId) {
		this.customerorderId = customerorderId;
	}

	public Double getHourlyRate() {
		return hourlyRate;
	}

	public void setHourlyRate(Double hourlyRate) {
		this.hourlyRate = hourlyRate;
	}
	
	public String getSign() {
		return sign;
	}

	public void setSign(String sign) {
		this.sign = sign;
	}
	
	public String getDescription() {
		return description;
	}

	public void setDescription(String description) {
		this.description = description;
	}

	public String getInvoice() {
		return invoice;
	}

	public void setInvoice(String invoice) {
		this.invoice = invoice;
	}

	
	
	/**
	 * @return the standard
	 */
	public Boolean getStandard() {
		return standard;
	}

	/**
	 * @param standard the standard to set
	 */
	public void setStandard(Boolean standard) {
		this.standard = standard;
	}
	
	public String getShortdescription() {
		return shortdescription;
	}

	public void setShortdescription(String shortdescription) {
		this.shortdescription = shortdescription;
	}

	public Boolean getCommentnecessary() {
		return commentnecessary;
	}

	public void setCommentnecessary(Boolean commentnecessary) {
		this.commentnecessary = commentnecessary;
	}

	
	
	@Override
	public void reset(ActionMapping mapping, HttpServletRequest request) {	
		sign = "";
		description = "";
		shortdescription = "";
		invoice = "J";
		currency = GlobalConstants.DEFAULT_CURRENCY;
		standard = false;
		commentnecessary = false;
//		hourlyRate = 0.0;
//		request.getSession().setAttribute("hourlyRate", new Double(0.0));
	}

	@Override
	public ActionErrors validate(ActionMapping mapping, HttpServletRequest request) {
		ActionErrors errors = new ActionErrors();

		// actually, no checks here
		return errors;
	}

}
