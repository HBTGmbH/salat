package org.tb.bdom;

import java.io.Serializable;
import java.util.Date;

import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.Table;

import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;
import org.hibernate.annotations.Cascade;
import org.hibernate.annotations.CascadeType;
import org.hibernate.annotations.Fetch;
import org.hibernate.annotations.FetchMode;


/**
 * 
 * Bean for table 'employeeordercontent'
 * 
 * @author th
 *
 */
@Entity
@Table(name="EMPLOYEEORDERCONTENT")
@Cache(usage=CacheConcurrencyStrategy.READ_WRITE)
public class Employeeordercontent implements Serializable {
	
	private static final long serialVersionUID = 1L; // 2L;
	
	/**
	 * Autogenerated technical object id.
	 */	
	@Id
	@GeneratedValue(strategy = GenerationType.SEQUENCE)
	private long id;
	
	/** Responsible Contract HBT */
	@ManyToOne
	@Fetch(FetchMode.SELECT)
	@JoinColumn(name="CONTACT_CONTRACT_HBT")
	@Cascade(value = { CascadeType.SAVE_UPDATE })
	private Employee contactContractHbt;
	
	/** Responsible Technical HBT */
	@ManyToOne
	@Fetch(FetchMode.SELECT)
	@JoinColumn(name="CONTACT_TECH_HBT")
	@Cascade(value = { CascadeType.SAVE_UPDATE })
	private Employee contactTechHbt;
	
	/** Responsible Technical HBT */
	@ManyToOne
	@Fetch(FetchMode.SELECT)
	@JoinColumn(name="COMMITTEDBY_MGMT")
	@Cascade(value = { CascadeType.SAVE_UPDATE })
	private Employee committedby_mgmt;
	
	/** Responsible Technical HBT */
	@ManyToOne
	@Fetch(FetchMode.SELECT)
	@JoinColumn(name="COMMITTEDBY_EMP")
	@Cascade(value = { CascadeType.SAVE_UPDATE })
	private Employee committedby_emp;
	
	private String description;
	private String task;
	private String boundary;
	private String procedure;
	private Integer qm_process_id;
	private String contact_contract_customer;
	private String contact_tech_customer;
	private String additional_risks;
	private String arrangement;
	private Boolean committed_mgmt;
	private Boolean committed_emp;
	private Date created;
	private String createdby;
	private Date lastupdate;
	private String lastupdatedby;
	private Integer updatecounter;
	
	
	
	/**
	 * @return the contactContractHbt
	 */
	public Employee getContactContractHbt() {
		return contactContractHbt;
	}
	/**
	 * @param contactContractHbt the contactContractHbt to set
	 */
	public void setContactContractHbt(Employee contactContractHbt) {
		this.contactContractHbt = contactContractHbt;
	}
	/**
	 * @return the contactTechHbt
	 */
	public Employee getContactTechHbt() {
		return contactTechHbt;
	}
	/**
	 * @param contactTechHbt the contactTechHbt to set
	 */
	public void setContactTechHbt(Employee contactTechHbt) {
		this.contactTechHbt = contactTechHbt;
	}
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
	 * @return the committedby_emp
	 */
	public Employee getCommittedby_emp() {
		return committedby_emp;
	}
	/**
	 * @param committedby_emp the committedby_emp to set
	 */
	public void setCommittedby_emp(Employee committedby_emp) {
		this.committedby_emp = committedby_emp;
	}
	/**
	 * @return the committedby_mgmt
	 */
	public Employee getCommittedby_mgmt() {
		return committedby_mgmt;
	}
	/**
	 * @param committedby_mgmt the committedby_mgmt to set
	 */
	public void setCommittedby_mgmt(Employee committedby_mgmt) {
		this.committedby_mgmt = committedby_mgmt;
	}
	/**
	 * @return the commited_emp
	 */
	public Boolean getCommitted_emp() {
		return committed_emp == null ? false : committed_emp;
	}
	/**
	 * @param commited_emp the commited_emp to set
	 */
	public void setCommitted_emp(Boolean committed_emp) {
		this.committed_emp = committed_emp;
	}
	/**
	 * @return the committed_mgmt
	 */
	public Boolean getCommitted_mgmt() {
		return committed_mgmt == null ? false : committed_mgmt;
	}
	/**
	 * @param committed_mgmt the committed_mgmt to set
	 */
	public void setCommitted_mgmt(Boolean committed_mgmt) {
		this.committed_mgmt = committed_mgmt;
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
	 * @return the created
	 */
	public Date getCreated() {
		return created;
	}
	/**
	 * @param created the created to set
	 */
	public void setCreated(Date created) {
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
	 * @return the lastupdate
	 */
	public Date getLastupdate() {
		return lastupdate;
	}
	/**
	 * @param lastupdate the lastupdate to set
	 */
	public void setLastupdate(Date lastupdate) {
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
	
	
	
	/* (non-Javadoc)
	 * @see java.lang.Object#hashCode()
	 */
	@Override
	public int hashCode() {
		final int PRIME = 31;
		int result = 1;
		result = PRIME * result + (int) (id ^ (id >>> 32));
		return result;
	}
	/* (non-Javadoc)
	 * @see java.lang.Object#equals(java.lang.Object)
	 */
	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		final Employeeordercontent other = (Employeeordercontent) obj;
		if (id != other.id)
			return false;
		return true;
	}
	
	
	
	
	

}
