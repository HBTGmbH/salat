package org.tb.web.form;

import javax.servlet.http.HttpServletRequest;

import org.apache.struts.action.ActionErrors;
import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionMapping;
import org.tb.GlobalConstants;
import org.tb.util.DateUtils;

/**
 * Form for adding a customer order
 * 
 * @author oda
 *
 */
public class AddCustomerOrderForm extends ActionForm {

	private long id;
	private String sign;
	private String description;
	private String validFrom;
	private String validUntil;
	private String responsibleCustomer;
	private String responsibleHbt;
	private String orderCustomer;
	private String currency;
	private Double hourlyRate;
	
	private long customerId;
	
	
	private String action;

	
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

	public long getCustomerId() {
		return customerId;
	}

	public void setCustomerId(long customerId) {
		this.customerId = customerId;
	}

	public Double getHourlyRate() {
		return hourlyRate;
	}

	public void setHourlyRate(Double hourlyRate) {
		this.hourlyRate = hourlyRate;
	}

	public String getOrderCustomer() {
		return orderCustomer;
	}

	public void setOrderCustomer(String orderCustomer) {
		this.orderCustomer = orderCustomer;
	}

	public String getResponsibleCustomer() {
		return responsibleCustomer;
	}

	public void setResponsibleCustomer(String responsibleCustomer) {
		this.responsibleCustomer = responsibleCustomer;
	}

	public String getResponsibleHbt() {
		return responsibleHbt;
	}

	public void setResponsibleHbt(String responsibleHbt) {
		this.responsibleHbt = responsibleHbt;
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

	public String getValidFrom() {
		return validFrom;
	}

	public void setValidFrom(String validFrom) {
		this.validFrom = validFrom;
	}

	public String getValidUntil() {
		return validUntil;
	}

	public void setValidUntil(String validUntil) {
		this.validUntil = validUntil;
	}

	@Override
	public void reset(ActionMapping mapping, HttpServletRequest request) {	
		sign = "";
		description = "";
		validFrom = DateUtils.getSqlDateString(new java.util.Date()); // 'yyyy-mm-dd'
		validUntil = DateUtils.getSqlDateString(new java.util.Date()); // 'yyyy-mm-dd'
		responsibleCustomer = "";
		responsibleHbt = "";
		orderCustomer = "";
		currency = GlobalConstants.DEFAULT_CURRENCY;
		hourlyRate = 0.0;
	}

	@Override
	public ActionErrors validate(ActionMapping mapping, HttpServletRequest request) {
		ActionErrors errors = new ActionErrors();

		// actually, no checks here
		return errors;
	}

}
