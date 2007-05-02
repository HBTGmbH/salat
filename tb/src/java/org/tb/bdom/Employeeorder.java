package org.tb.bdom;

import java.io.Serializable;
import java.sql.Date;
import java.text.SimpleDateFormat;

import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;

import org.apache.naming.java.javaURLContextFactory;
import org.hibernate.annotations.Cascade;
import org.hibernate.annotations.CascadeType;

/**
 * Bean for table 'Employeeorder'.
 * 
 * @author oda
 */
@Entity
public class Employeeorder implements Serializable {

	private static final long serialVersionUID = 1L;

	/**
	 * Autogenerated technical object id.
	 */	
	@Id
	@GeneratedValue(strategy = GenerationType.SEQUENCE)
	private long id;

	/** Suborder */
	@ManyToOne
	@JoinColumn(name="SUBORDER_ID")
	@Cascade(value = { CascadeType.SAVE_UPDATE })
	private Suborder suborder;
	
	/** EmployeeContract */
	@ManyToOne
	@JoinColumn(name="EMPLOYEECONTRACT_ID")
	@Cascade(value = { CascadeType.SAVE_UPDATE })
	private Employeecontract employeecontract;
	
	/** sign of the employee order */
	private String sign;
	
	/** Debit Hours */
	private Double debithours;
	
	/** Unit of the debit hours */
	private Byte debithoursunit;
	
	/** valid from date */
	private Date fromDate;
	
	/** valid until date */
	private Date untilDate;
	
	/** Creation Date */
	private java.util.Date created;
	
	/** Last Update */
	private java.util.Date lastupdate;
	
	/** Created By */
	private String createdby;
	
	/** Updated By */
	private String lastupdatedby;
	
	/** Update Counter */
	private Integer updatecounter;
	

	public long getId() {
		return id;
	}

	public void setId(long id) {
		this.id = id;
	}

	public Double getDebithours() {
		return debithours;
	}

	public void setDebithours(Double hours) {
		this.debithours = hours;
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

	public Date getFromDate() {
		return fromDate;
	}

	public void setFromDate(Date fromDate) {
		this.fromDate = fromDate;
	}

	public Date getUntilDate() {
		return untilDate;
	}

	public void setUntilDate(Date untilDate) {
		this.untilDate = untilDate;
	}

	public Employeecontract getEmployeecontract() {
		return employeecontract;
	}

	public void setEmployeecontract(Employeecontract employeecontract) {
		this.employeecontract = employeecontract;
	}

	public Suborder getSuborder() {
		return suborder;
	}

	public void setSuborder(Suborder suborder) {
		this.suborder = suborder;
	}

	/**
	 * @return the created
	 */
	public java.util.Date getCreated() {
		return created;
	}

	/**
	 * @param created the created to set
	 */
	public void setCreated(java.util.Date created) {
		this.created = created;
	}

	/**
	 * @return the createdby
	 */
	public String getCreatedby() {
		return createdby;
	}

	/**
	 * @param createdby the createdby to set
	 */
	public void setCreatedby(String createdby) {
		this.createdby = createdby;
	}

	/**
	 * @return the lastupdate
	 */
	public java.util.Date getLastupdate() {
		return lastupdate;
	}

	/**
	 * @param lastupdate the lastupdate to set
	 */
	public void setLastupdate(java.util.Date lastupdate) {
		this.lastupdate = lastupdate;
	}

	/**
	 * @return the lastupdatedby
	 */
	public String getLastupdatedby() {
		return lastupdatedby;
	}

	/**
	 * @param lastupdatedby the lastupdatedby to set
	 */
	public void setLastupdatedby(String lastupdatedby) {
		this.lastupdatedby = lastupdatedby;
	}

	/**
	 * @return the updatecounter
	 */
	public Integer getUpdatecounter() {
		return updatecounter;
	}

	/**
	 * @param updatecounter the updatecounter to set
	 */
	public void setUpdatecounter(Integer updatecounter) {
		this.updatecounter = updatecounter;
	}

	public String getEmployeeOrderAsString() {
		SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd");
		return "EO["+getEmployeecontract().getEmployee().getSign()+" | "
			+ getSuborder().getCustomerorder().getSign() + " / " 
			+ getSuborder().getSign() + " | " 
			+ simpleDateFormat.format(getFromDate()) + " - " 
			+ simpleDateFormat.format(getUntilDate()) +  "]";
	}
	
	/**
	 * 
	 * @return Returns true, if the {@link Employeeorder} is currently valid, false otherwise.
	 */
	public boolean getCurrentlyValid() {
		java.util.Date now = new java.util.Date();
		java.sql.Date nowSqlDate = new java.sql.Date(now.getTime());
		if (!nowSqlDate.before(getFromDate()) && (getUntilDate() == null || !nowSqlDate.after(getUntilDate()))){
			return true;
		}
		return false;
	}
	
	/**
	 * 
	 * @return Returns true, if {@link Employeeorder} fits to its superior objects, false otherwise
	 */
	public boolean getFitsToSuperiorObjects() {
		// check from date
		if (getFromDate().before(employeecontract.getValidFrom()) || getFromDate().before(suborder.getFromDate())) {
			return false;
		}
		
		// check until date
		if (getUntilDate() == null && (employeecontract.getValidUntil() != null || suborder.getUntilDate() != null)) {
			return false;
		}
		if (getUntilDate() != null && 
				((employeecontract.getValidUntil() != null && 
						getUntilDate().after(employeecontract.getValidUntil())) ||
				 (suborder.getUntilDate() != null &&
						getUntilDate().after(suborder.getUntilDate()))
				)) {
			return false;
		}
		
		
		return true;
	}
	
}
