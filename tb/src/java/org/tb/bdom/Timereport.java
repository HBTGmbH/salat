package org.tb.bdom;

import java.io.Serializable;
import java.text.SimpleDateFormat;

import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;

import org.hibernate.annotations.Cascade;
import org.hibernate.annotations.CascadeType;

/**
 * Bean for table 'timereport'.
 * 
 * @author oda
 */
@Entity
public class Timereport implements Serializable {

	private static final long serialVersionUID = 1L; // 1L;

	/**
	 * Autogenerated technical object id.
	 */	
	@Id
	@GeneratedValue(strategy = GenerationType.SEQUENCE)
	private long id;

	/** Referenceday */
	@ManyToOne
	@JoinColumn(name="REFERENCEDAY_ID")
	@Cascade(value = { CascadeType.SAVE_UPDATE })
	private Referenceday referenceday;
	
	/** Employeecontract */
	@ManyToOne
	@JoinColumn(name="EMPLOYEECONTRACT_ID")
	@Cascade(value = { CascadeType.SAVE_UPDATE })
	private Employeecontract employeecontract;
	
	/** Suborder */
	@ManyToOne
	@JoinColumn(name="SUBORDER_ID")
	@Cascade(value = { CascadeType.SAVE_UPDATE })
	private Suborder suborder;
	
	
	/** Employeeorder */
	@ManyToOne
	@JoinColumn(name="EMPLOYEEORDER_ID")
	@Cascade(value = { CascadeType.SAVE_UPDATE })
	private Employeeorder employeeorder;
	
	/** Duration */
	private Integer durationhours;
	private Integer durationminutes;
	
	/** Sort of Report */
	private String sortofreport;
	
	/** Task Description */
	private String taskdescription;
	
	/** Status */
	private String status;
	
	/** Costs */
	private Double costs;
	
	/** Sequencial number */
	private int sequencenumber;
	
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
	
	/** Date of Release */
	private java.util.Date released;
	
	/** Sign of the releasing person */
	private String releasedby;
	
	/** Date of Acceptance */
	private java.util.Date accepted;
	
	/** Sign of the accepting person */
	private String acceptedby;
	
	
	public long getId() {
		return id;
	}

	public void setId(long id) {
		this.id = id;
	}	
	
	public Employeecontract getEmployeecontract() {
		return employeecontract;
	}

	public void setEmployeecontract(Employeecontract employeecontract) {
		this.employeecontract = employeecontract;
	}

	public Employeeorder getEmployeeorder() {
		return employeeorder;
	}

	public void setEmployeeorder(Employeeorder employeeorder) {
		this.employeeorder = employeeorder;
	}

	public Integer getDurationhours() {
		return durationhours;
	}

	public void setDurationhours(Integer durationhours) {
		this.durationhours = durationhours;
	}
	
	public Integer getDurationminutes() {
		return durationminutes;
	}

	public void setDurationminutes(Integer durationminutes) {
		this.durationminutes = durationminutes;
	}

	public String getSortofreport() {
		return sortofreport;
	}

	public void setSortofreport(String sortofreport) {
		this.sortofreport = sortofreport;
	}

	public Referenceday getReferenceday() {
		return referenceday;
	}

	public void setReferenceday(Referenceday referenceday) {
		this.referenceday = referenceday;
	}

	public Suborder getSuborder() {
		return suborder;
	}

	public void setSuborder(Suborder order) {
		this.suborder = order;
	}
	
	public String getTaskdescription() {
		return taskdescription;
	}

	public void setTaskdescription(String taskdescription) {
		this.taskdescription = taskdescription;
	}

	public Double getCosts() {
		return costs;
	}

	public void setCosts(Double costs) {
		this.costs = costs;
	}

	public String getStatus() {
		return status;
	}

	public void setStatus(String status) {
		this.status = status;
	}

	/**
	 * @return the sequencenumber
	 */
	public int getSequencenumber() {
		return sequencenumber;
	}

	/**
	 * @param sequencenumber the sequencenumber to set
	 */
	public void setSequencenumber(int sequencenumber) {
		this.sequencenumber = sequencenumber;
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

	public java.util.Date getAccepted() {
		return accepted;
	}

	public void setAccepted(java.util.Date accepted) {
		this.accepted = accepted;
	}

	public String getAcceptedby() {
		return acceptedby;
	}

	public void setAcceptedby(String acceptedby) {
		this.acceptedby = acceptedby;
	}

	public java.util.Date getReleased() {
		return released;
	}

	public void setReleased(java.util.Date released) {
		this.released = released;
	}

	public String getReleasedby() {
		return releasedby;
	}

	public void setReleasedby(String releasedby) {
		this.releasedby = releasedby;
	}
	
	public Timereport getTwin() {
		Timereport timereport = new Timereport();
		timereport.setCosts(costs);
		timereport.setDurationhours(durationhours);
		timereport.setDurationminutes(durationminutes);
		timereport.setEmployeecontract(employeecontract);
		timereport.setSortofreport(sortofreport);
		timereport.setStatus(status);
		timereport.setSuborder(suborder);
		timereport.setTaskdescription(taskdescription);
		timereport.setSequencenumber(0);
		timereport.setEmployeeorder(employeeorder);
		
		return timereport;
	}
	
	public boolean getFitsToContract() {
		if (referenceday.getRefdate().before(employeecontract.getValidFrom()) || (employeecontract.getValidUntil() != null && referenceday.getRefdate().after(employeecontract.getValidUntil()))) {
			return false;
		}
		return true;
	}
	
	public String getTimeReportAsString() {
		SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd");
		return "TR["+getEmployeecontract().getEmployee().getSign()+" | "
			+simpleDateFormat.format(getReferenceday().getRefdate()) + " | " 
			+ getSuborder().getCustomerorder().getSign() + " / " 
			+ getSuborder().getSign() + " | " + getDurationhours() + ":" 
			+ getDurationminutes() + " | " + getTaskdescription() + "]";
	}
	
	
}
