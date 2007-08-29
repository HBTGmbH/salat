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
@Entity
public class Overtime implements Serializable {
	
	private static final long serialVersionUID = 1L; // 1L;
	
	/**
	 * Autogenerated technical object id.
	 */	
	@Id
	@GeneratedValue(strategy = GenerationType.SEQUENCE)
	private long id;
	
	/** Employeecontract */
	@ManyToOne
	@JoinColumn(name="EMPLOYEECONTRACT_ID")
	@Cascade(value = { CascadeType.SAVE_UPDATE })
	private Employeecontract employeecontract;
	
	/** Comment */
	private String comment;
	
	/** Creation Date */
	private java.util.Date created;
	
	/** Created By */
	private String createdby;
	
	/** Time */
	private Double time;

	/**
	 * @return the employeecontract
	 */
	public Employeecontract getEmployeecontract() {
		return employeecontract;
	}

	/**
	 * @param employeecontract the employeecontract to set
	 */
	public void setEmployeecontract(Employeecontract employeecontract) {
		this.employeecontract = employeecontract;
	}

	/**
	 * @return the id
	 */
	public long getId() {
		return id;
	}

	/**
	 * @param id the id to set
	 */
	public void setId(long id) {
		this.id = id;
	}

	/**
	 * @return the comment
	 */
	public String getComment() {
		return comment;
	}

	/**
	 * @param comment the comment to set
	 */
	public void setComment(String comment) {
		this.comment = comment;
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
	 * @return the time
	 */
	public Double getTime() {
		return time;
	}

	/**
	 * @param time the time to set
	 */
	public void setTime(Double time) {
		this.time = time;
	}
	
	public String getCreatedString() {
		SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd");
		return simpleDateFormat.format(created);
	}

	

	

}
