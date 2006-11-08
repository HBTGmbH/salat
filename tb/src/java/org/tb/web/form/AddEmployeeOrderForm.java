package org.tb.web.form;

import java.sql.Date;
import java.text.SimpleDateFormat;

import javax.servlet.http.HttpServletRequest;

import org.apache.struts.action.ActionErrors;
import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionMapping;
import org.tb.bdom.Customerorder;
import org.tb.bdom.Employee;
import org.tb.util.DateUtils;

/**
 * Form for adding an employee order
 * 
 * @author oda
 *
 */
public class AddEmployeeOrderForm extends ActionForm {

	private long id;
	private String sign;
	private String validFrom;
	private String validUntil;
	private Boolean standingorder;
	private Double debithours;
	private String status;
	private Boolean statusreport;
	private String employeename;
	private String order;
	private String suborder;
	private long employeeId;
	
	private long employeecontractId;
	private long orderId;
	private long suborderId;
	
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

	
	public String getEmployeename() {
		return employeename;
	}

	public void setEmployeename(String employeename) {
		this.employeename = employeename;
	}
	
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

	public long getEmployeecontractId() {
		return employeecontractId;
	}

	public void setEmployeecontractId(long employeecontractId) {
		this.employeecontractId = employeecontractId;
	}

	public String getSign() {
		return sign;
	}

	public void setSign(String sign) {
		this.sign = sign;
	}

	public Boolean getStandingorder() {
		return standingorder;
	}

	public void setStandingorder(Boolean standingorder) {
		this.standingorder = standingorder;
	}

	public String getStatus() {
		return status;
	}

	public void setStatus(String status) {
		this.status = status;
	}

	public Boolean getStatusreport() {
		return statusreport;
	}

	public void setStatusreport(Boolean statusreport) {
		this.statusreport = statusreport;
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

	@Override
	public void reset(ActionMapping mapping, HttpServletRequest request) {	
		Employee loginEmployee = (Employee) request.getSession().getAttribute("loginEmployee");
		employeename = loginEmployee.getFirstname() + " " + loginEmployee.getLastname();
		employeeId = loginEmployee.getId();
		sign = "";
		status = "";
		validFrom = DateUtils.getSqlDateString(new java.util.Date()); // 'yyyy-mm-dd'
		validUntil = DateUtils.getSqlDateString(new java.util.Date()); // 'yyyy-mm-dd'
		standingorder = new Boolean(Boolean.FALSE);
		statusreport = new Boolean(Boolean.FALSE);
		debithours = 0.0;
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
		String coUntilDateString = simpleDateFormat.format(coUntilDate);
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
