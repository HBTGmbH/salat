package org.tb.bdom;

import java.io.Serializable;

import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;

import org.tb.util.MD5Util;

/**
 * Bean for table 'Employee'.
 * 
 * @author oda
 */
@Entity
public class Employee implements Serializable {

	private static final long serialVersionUID = 1L; // 1L;

	/**
	 * Autogenerated technical object id.
	 */
	@Id
	@GeneratedValue(strategy = GenerationType.SEQUENCE)
	private long id;

	/** registration first and last name of the employee */
	private String loginname;

	/** registration password of the employee */
	private String password;

	/** first name of the employee */
	private String firstname;

	/** last name of the employee */
	private String lastname;

	/** sign of the employee (2 or 3 letters) */
	private String sign;

	/** gender of the employee */
	private char gender;

	/** status of the employee (e.g., admin, ma, bl */
	private String status;

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

	/** Password change required */
	private Boolean passwordchange;

	public void changePassword(final String newPassword) {
		passwordchange = false;
		password = MD5Util.makeMD5(newPassword);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.lang.Object#equals(java.lang.Object)
	 */
	@Override
	public boolean equals(final Object obj) {
		if (this == obj) {
			return true;
		}
		if (obj == null) {
			return false;
		}
		if (getClass() != obj.getClass()) {
			return false;
		}
		final Employee other = (Employee) obj;
		if (id != other.id) {
			return false;
		}
		if (sign == null) {
			if (other.sign != null) {
				return false;
			}
		} else if (!sign.equals(other.sign)) {
			return false;
		}
		return true;
	}

	/**
	 * @return the created
	 */
	public java.util.Date getCreated() {
		return created;
	}

	/**
	 * @return the createdby
	 */
	public String getCreatedby() {
		return createdby;
	}

	public String getFirstname() {
		return firstname;
	}

	public char getGender() {
		return gender;
	}

	public long getId() {
		return id;
	}

	public String getLastname() {
		return lastname;
	}

	/**
	 * @return the lastupdate
	 */
	public java.util.Date getLastupdate() {
		return lastupdate;
	}

	/**
	 * @return the lastupdatedby
	 */
	public String getLastupdatedby() {
		return lastupdatedby;
	}

	public String getLoginname() {
		return loginname;
	}

	public String getName() {
		return (this.firstname + " " + this.lastname);
	}

	public String getPassword() {
		return password;
	}

	/**
	 * @return the passwordchange
	 */
	public Boolean getPasswordchange() {
		return passwordchange == null ? false : passwordchange;
	}

	public String getSign() {
		return sign;
	}

	public String getStatus() {
		return status;
	}

	/**
	 * @return the updatecounter
	 */
	public Integer getUpdatecounter() {
		return updatecounter;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.lang.Object#hashCode()
	 */
	@Override
	public int hashCode() {
		final int PRIME = 31;
		int result = 1;
		result = PRIME * result + (int) (id ^ (id >>> 32));
		result = PRIME * result + ((sign == null) ? 0 : sign.hashCode());
		return result;
	}

	public void resetPassword() {
		password = MD5Util.makeMD5(sign);
		passwordchange = true;
	}

	/**
	 * @param created
	 *            the created to set
	 */
	public void setCreated(final java.util.Date created) {
		this.created = created;
	}

	/**
	 * @param createdby
	 *            the createdby to set
	 */
	public void setCreatedby(final String createdby) {
		this.createdby = createdby;
	}

	public void setFirstname(final String firstname) {
		this.firstname = firstname;
	}

	public void setGender(final char gender) {
		this.gender = gender;
	}

	public void setId(final long id) {
		this.id = id;
	}

	public void setLastname(final String lastname) {
		this.lastname = lastname;
	}

	/**
	 * @param lastupdate
	 *            the lastupdate to set
	 */
	public void setLastupdate(final java.util.Date lastupdate) {
		this.lastupdate = lastupdate;
	}

	/**
	 * @param lastupdatedby
	 *            the lastupdatedby to set
	 */
	public void setLastupdatedby(final String lastupdatedby) {
		this.lastupdatedby = lastupdatedby;
	}

	public void setLoginname(final String loginname) {
		this.loginname = loginname;
	}

	public void setPassword(final String password) {
		this.password = password;
	}

	/**
	 * @param passwordchange
	 *            the passwordchange to set
	 */
	public void setPasswordchange(final Boolean passwordchange) {
		this.passwordchange = passwordchange;
	}

	public void setSign(final String sign) {
		this.sign = sign;
	}

	public void setStatus(final String status) {
		this.status = status;
	}

	/**
	 * @param updatecounter
	 *            the updatecounter to set
	 */
	public void setUpdatecounter(final Integer updatecounter) {
		this.updatecounter = updatecounter;
	}

}
