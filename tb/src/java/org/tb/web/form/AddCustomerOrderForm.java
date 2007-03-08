package org.tb.web.form;

import javax.servlet.http.HttpServletRequest;

import org.apache.struts.action.ActionErrors;
import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionMapping;
import org.tb.GlobalConstants;
import org.tb.bdom.Employee;
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
	private String shortdescription;
	private String validFrom;
	private String validUntil;
	private String responsibleCustomerTechnical;
	private String responsibleCustomerContractually;
	private String orderCustomer;
	private String currency;
	private Double hourlyRate;
	
	private Double debithours;
	private Byte debithoursunit;
	
	private long customerId;
	private long employeeId;
	
	
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

	
	
	/**
	 * @return the employeeId
	 */
	public long getEmployeeId() {
		return employeeId;
	}

	/**
	 * @param employeeId the employeeId to set
	 */
	public void setEmployeeId(long employeeId) {
		this.employeeId = employeeId;
	}

	/**
	 * @return the responsibleCustomerContractually
	 */
	public String getResponsibleCustomerContractually() {
		return responsibleCustomerContractually;
	}

	/**
	 * @param responsibleCustomerContractually the responsibleCustomerContractually to set
	 */
	public void setResponsibleCustomerContractually(
			String responsibleCustomerContractually) {
		this.responsibleCustomerContractually = responsibleCustomerContractually;
	}

	/**
	 * @return the responsibleCustomerTechnical
	 */
	public String getResponsibleCustomerTechnical() {
		return responsibleCustomerTechnical;
	}

	/**
	 * @param responsibleCustomerTechnical the responsibleCustomerTechnical to set
	 */
	public void setResponsibleCustomerTechnical(String responsibleCustomerTechnical) {
		this.responsibleCustomerTechnical = responsibleCustomerTechnical;
	}
	
	

	public String getShortdescription() {
		return shortdescription;
	}

	public void setShortdescription(String shortdescription) {
		this.shortdescription = shortdescription;
	}
	
	/**
	 * @return the debithours
	 */
	public Double getDebithours() {
		return debithours;
	}

	/**
	 * @param debithours the debithours to set
	 */
	public void setDebithours(Double debithours) {
		this.debithours = debithours;
	}

	/**
	 * @return the debithoursunit
	 */
	public Byte getDebithoursunit() {
		return debithoursunit;
	}

	/**
	 * @param debithoursunit the debithoursunit to set
	 */
	public void setDebithoursunit(Byte debithoursunit) {
		this.debithoursunit = debithoursunit;
	}

	@Override
	public void reset(ActionMapping mapping, HttpServletRequest request) {	
		sign = "";
		description = "";
		shortdescription = "";
		validFrom = DateUtils.getSqlDateString(new java.util.Date()); // 'yyyy-mm-dd'
		validUntil = DateUtils.getSqlDateString(new java.util.Date()); // 'yyyy-mm-dd'
		responsibleCustomerTechnical = "";
		responsibleCustomerContractually = "";
		if (null != request.getSession().getAttribute("currentEmployeeId") && (Long)request.getSession().getAttribute("currentEmployeeId") != -1 && (Long)request.getSession().getAttribute("currentEmployeeId") != 0) {
			employeeId = (Long)request.getSession().getAttribute("currentEmployeeId");
		} else {
			Employee loginEmployee = (Employee)request.getSession().getAttribute("loginEmployee");
			employeeId = loginEmployee.getId();
		}
		orderCustomer = "";
		currency = GlobalConstants.DEFAULT_CURRENCY;
		hourlyRate = 0.0;
		debithours = null;
		debithoursunit = null;
	}

	@Override
	public ActionErrors validate(ActionMapping mapping, HttpServletRequest request) {
		ActionErrors errors = new ActionErrors();

		// actually, no checks here
		return errors;
	}

}
