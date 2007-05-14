package org.tb.web.form;

import javax.servlet.http.HttpServletRequest;

import org.apache.struts.action.ActionErrors;
import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionMapping;
import org.tb.GlobalConstants;

public class AddEmployeeOrderContentForm extends ActionForm {

	static final long serialVersionUID = 1L;
	
	private String description;
	private String task;
	private String boundary;
	private String procedure;
	private Integer qm_process_id;
	private String contact_contract_customer;
	private String contact_tech_customer;
	private Long contact_contract_hbt_emp_id;
	private Long contact_tech_hbt_emp_id;
	private String additional_risks;
	private String arrangement;
	

	
	/**
	 * @return the additional_risks
	 */
	public String getAdditional_risks() {
		return additional_risks;
	}

	/**
	 * @param additional_risks the additional_risks to set
	 */
	public void setAdditional_risks(String additional_risks) {
		this.additional_risks = additional_risks;
	}

	/**
	 * @return the arrangement
	 */
	public String getArrangement() {
		return arrangement;
	}

	/**
	 * @param arrangement the arrangement to set
	 */
	public void setArrangement(String arrangement) {
		this.arrangement = arrangement;
	}

	/**
	 * @return the boundary
	 */
	public String getBoundary() {
		return boundary;
	}

	/**
	 * @param boundary the boundary to set
	 */
	public void setBoundary(String boundary) {
		this.boundary = boundary;
	}

	/**
	 * @return the contact_contract_customer
	 */
	public String getContact_contract_customer() {
		return contact_contract_customer;
	}

	/**
	 * @param contact_contract_customer the contact_contract_customer to set
	 */
	public void setContact_contract_customer(String contact_contract_customer) {
		this.contact_contract_customer = contact_contract_customer;
	}

	/**
	 * @return the contact_contract_hbt_emp_id
	 */
	public Long getContact_contract_hbt_emp_id() {
		return contact_contract_hbt_emp_id;
	}

	/**
	 * @param contact_contract_hbt_emp_id the contact_contract_hbt_emp_id to set
	 */
	public void setContact_contract_hbt_emp_id(Long contact_contract_hbt_emp_id) {
		this.contact_contract_hbt_emp_id = contact_contract_hbt_emp_id;
	}

	/**
	 * @return the contact_tech_customer
	 */
	public String getContact_tech_customer() {
		return contact_tech_customer;
	}

	/**
	 * @param contact_tech_customer the contact_tech_customer to set
	 */
	public void setContact_tech_customer(String contact_tech_customer) {
		this.contact_tech_customer = contact_tech_customer;
	}

	/**
	 * @return the contact_tech_hbt_emp_id
	 */
	public Long getContact_tech_hbt_emp_id() {
		return contact_tech_hbt_emp_id;
	}

	/**
	 * @param contact_tech_hbt_emp_id the contact_tech_hbt_emp_id to set
	 */
	public void setContact_tech_hbt_emp_id(Long contact_tech_hbt_emp_id) {
		this.contact_tech_hbt_emp_id = contact_tech_hbt_emp_id;
	}

	/**
	 * @return the description
	 */
	public String getDescription() {
		return description;
	}

	/**
	 * @param description the description to set
	 */
	public void setDescription(String description) {
		this.description = description;
	}

	/**
	 * @return the procedure
	 */
	public String getProcedure() {
		return procedure;
	}

	/**
	 * @param procedure the procedure to set
	 */
	public void setProcedure(String procedure) {
		this.procedure = procedure;
	}

	/**
	 * @return the qm_process_id
	 */
	public Integer getQm_process_id() {
		return qm_process_id;
	}

	/**
	 * @param qm_process_id the qm_process_id to set
	 */
	public void setQm_process_id(Integer qm_process_id) {
		this.qm_process_id = qm_process_id;
	}

	/**
	 * @return the task
	 */
	public String getTask() {
		return task;
	}

	/**
	 * @param task the task to set
	 */
	public void setTask(String task) {
		this.task = task;
	}

	/* (non-Javadoc)
	 * @see org.apache.struts.action.ActionForm#reset(org.apache.struts.action.ActionMapping, javax.servlet.http.HttpServletRequest)
	 */
	@Override
	public void reset(ActionMapping arg0, HttpServletRequest arg1) {
		description = "";
		task = "";
		boundary = "";
		procedure = "";
		qm_process_id = GlobalConstants.QM_PROCESS_ID_OTHER;
		contact_contract_customer = "";
		contact_tech_customer = "";
		contact_contract_hbt_emp_id = null;
		contact_tech_hbt_emp_id = null;
		additional_risks = "";
		arrangement = "";
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
