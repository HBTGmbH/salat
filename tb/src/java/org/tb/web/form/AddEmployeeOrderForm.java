package org.tb.web.form;

import java.sql.Date;
import java.text.SimpleDateFormat;

import javax.servlet.http.HttpServletRequest;

import org.apache.struts.action.ActionErrors;
import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionMapping;
import org.tb.bdom.Customerorder;
import org.tb.bdom.Employeecontract;
import org.tb.util.DateUtils;

/**
 * Form for adding an employee order
 * 
 * @author oda
 *
 */
public class AddEmployeeOrderForm extends ActionForm {

	/**
	 * 
	 */
	private static final long serialVersionUID = -2418121509861779749L;
	private long id;
	private String sign;
	private String validFrom;
	private String validUntil;
	private Double debithours;
	private Byte debithoursunit;
	private String status;
	private String order;
	private String suborder;
	

	private long orderId;
	private long suborderId;
	private Long employeeContractId;
	
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

	
//	public String getEmployeename() {
//		return employeename;
//	}
//
//	public void setEmployeename(String employeename) {
//		this.employeename = employeename;
//	}
	
	public String getOrder() {
		return order;
	}

	public void setOrder(String order) {
		this.order = order;
	}

	public String getSuborder() {
		return suborder;
	}

	public void setSuborder(String suborder) {
		this.suborder = suborder;
	}

	public Double getDebithours() {
		return debithours;
	}

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

	public String getSign() {
		return sign;
	}

	public void setSign(String sign) {
		this.sign = sign;
	}

	public String getStatus() {
		return status;
	}

	public void setStatus(String status) {
		this.status = status;
	}
	
	public long getOrderId() {
		return orderId;
	}

	public void setOrderId(long orderId) {
		this.orderId = orderId;
	}

	public long getSuborderId() {
		return suborderId;
	}

	public void setSuborderId(long suborderId) {
		this.suborderId = suborderId;
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
	 * @return the employeeContractId
	 */
	public Long getEmployeeContractId() {
		return employeeContractId;
	}

	/**
	 * @param employeeContractId the employeeContractId to set
	 */
	public void setEmployeeContractId(Long employeeContractId) {
		this.employeeContractId = employeeContractId;
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
//		employeename = loginEmployee.getFirstname() + " " + loginEmployee.getLastname();
		
		sign = "";
		status = "";
		validFrom = DateUtils.getSqlDateString(new java.util.Date()); // 'yyyy-mm-dd'
		validUntil = DateUtils.getSqlDateString(new java.util.Date()); // 'yyyy-mm-dd'
		debithours = null;
		debithoursunit = null;
	}

	
	/**
	 * 
	 * @param customerorder
	 */
	public void useDatesFromCustomerOrder(Customerorder customerorder) {
		if (customerorder == null) {
			return;
		}
		Date coFromDate = customerorder.getFromDate();
		Date coUntilDate = customerorder.getUntilDate();
		SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd");
		String coFromDateString = simpleDateFormat.format(coFromDate);
		String coUntilDateString;
		if (coUntilDate != null) {
			coUntilDateString = simpleDateFormat.format(coUntilDate);
		} else {
			coUntilDateString = "";
		}
		setValidFrom(coFromDateString);
		setValidUntil(coUntilDateString);
	}
	
	
	@Override
	public ActionErrors validate(ActionMapping mapping, HttpServletRequest request) {
		ActionErrors errors = new ActionErrors();

		// actually, no checks here
		return errors;
	}

}
