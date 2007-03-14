package org.tb.bdom;

import java.io.Serializable;
import java.sql.Date;
import java.util.List;

import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.OneToMany;

import org.hibernate.annotations.Cascade;
import org.hibernate.annotations.CascadeType;


/**
 * Bean for table 'suborder'.
 * 
 * @author oda
 */
@Entity
public class Suborder implements Serializable {

	private static final long serialVersionUID = 1L;

	/**
	 * Autogenerated technical object id.
	 */
	@Id
	@GeneratedValue(strategy = GenerationType.SEQUENCE)
	private long id;

	/** Customerorder */
	@ManyToOne
	@JoinColumn(name="CUSTOMERORDER_ID")
	@Cascade(value = { CascadeType.SAVE_UPDATE })
	private Customerorder customerorder;

	/** list of timereports, associated to this suborder */
	@OneToMany(mappedBy = "suborder")
	@Cascade(value = { CascadeType.SAVE_UPDATE })
	private List<Timereport>  timereports;
	
	/** list of employeeorders, associated to this suborder */
	@OneToMany(mappedBy = "suborder")
	@Cascade(value = { CascadeType.SAVE_UPDATE })
	private List<Employeeorder> employeeorders;

	
	/** Invoice */
	private char invoice;

	/** Sign */
	private String sign;
	
	/** Description */
	private String description;
	
	/** Short Description */
	private String shortdescription;
	
	/** Currency */
	private String currency;

	/** Hourly Rate */
	private Double hourly_rate;
	
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
	
	/** STANDARD */
	private Boolean standard;
	
	/** Comment necessary */
	private Boolean commentnecessary;
	
	/** from date */
	private Date fromDate;
	
	/** until date */
	private Date untilDate;
	
	/** Debit hours */
	private Double debithours;
	
	/** Unit of the debit hours */
	private Byte debithoursunit;
	
	/** Hide in select boxes */
	private Boolean hide;
	
	
	public long getId() {
		return id;
	}

	public void setId(long id) {
		this.id = id;
	}

	public char getInvoice() {
		return invoice;
	}

	public void setInvoice(char invoice) {
		this.invoice = invoice;
	}

	public Customerorder getCustomerorder() {
		return customerorder;
	}

	public void setCustomerorder(Customerorder order) {
		this.customerorder = order;
	}

	public String getDescription() {
		return description;
	}

	public void setDescription(String description) {
		this.description = description;
	}

	public String getSign() {
		return sign;
	}

	public void setSign(String sign) {
		this.sign = sign;
	}

	public String getCurrency() {
		return currency;
	}

	public void setCurrency(String currency) {
		this.currency = currency;
	}

	public Double getHourly_rate() {
		return hourly_rate;
	}

	public void setHourly_rate(Double hourly_rate) {
		this.hourly_rate = hourly_rate;
	}
	 
	public List<Timereport> getTimereports() {
		return timereports;
	}

	public void setTimereports(List<Timereport> timereports) {
		this.timereports = timereports;
	}
	
	public List<Employeeorder> getEmployeeorders() {
		return employeeorders;
	}

	public void setEmployeeorders(List<Employeeorder> employeeorders) {
		this.employeeorders = employeeorders;
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
	
	

	/**
	 * @return the standard
	 */
	public Boolean getStandard() {
		return standard;
	}

	/**
	 * @param standard the standard to set
	 */
	public void setStandard(Boolean standard) {
		this.standard = standard;
	}
	
	public Boolean getCommentnecessary() {
		if (commentnecessary == null) {
			return false;
		}
		return commentnecessary;
	}

	public void setCommentnecessary(Boolean commentnecessary) {
		this.commentnecessary = commentnecessary;
	}

	public String getShortdescription() {
		if (shortdescription == null || shortdescription.equals("")) {
			if (description == null) {
				description = "";
			}
			if (description.length() > 20) {
				return description.substring(0, 17) + "...";
			} else {
				return description;
			}
		}
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

	/**
	 * @return the fromDate
	 */
	public Date getFromDate() {
		if (fromDate == null) {
			return getCustomerorder().getFromDate();
		}
		return fromDate;
	}

	/**
	 * @param fromDate the fromDate to set
	 */
	public void setFromDate(Date fromDate) {
		this.fromDate = fromDate;
	}

	/**
	 * @return the hide
	 */
	public Boolean getHide() {
		return (hide == null ? false : hide);
	}

	/**
	 * @param hide the hide to set
	 */
	public void setHide(Boolean hide) {
		this.hide = hide;
	}

	/**
	 * @return the untilDate
	 */
	public Date getUntilDate() {
		if (untilDate == null) {
			return getCustomerorder().getUntilDate();
		}
		return untilDate;
	}

	/**
	 * @param untilDate the untilDate to set
	 */
	public void setUntilDate(Date untilDate) {
		this.untilDate = untilDate;
	}

	public String getSignAndDescription() {
		return sign+" - "+getShortdescription();
	}
	
	/**
	 * 
	 * @return Returns true, if the {@link Suborder} is currently valid, false otherwise.
	 */
	public boolean getCurrentlyValid() {
		java.util.Date now = new java.util.Date();
		if (!now.before(getFromDate()) && (getUntilDate() == null || !now.after(getUntilDate()))){
			return true;
		}
		return false;
	}
	
	/**
	 * 
	 * @return Returns true, if the valitidy period fits to the validity period of the customer order
	 */
	public boolean validityPeriodFitsToCustomerOrder() {
		if (!getFromDate().before(getCustomerorder().getFromDate()) &&
				((getCustomerorder().getUntilDate() == null) ||
				 (getUntilDate() != null && !getUntilDate().after(getCustomerorder().getUntilDate())))) {
					return true;	
		}
		return false;
	}
	
	public String getInvoiceString() {
		String invoiceString = ((Character)invoice).toString();
		return (invoiceString == null || invoiceString == "") ? "U" : invoiceString;
	}
	
	public Character getInvoiceChar() {
		Character invoiceCharacter = (Character)invoice;
		return (invoiceCharacter == null) ? 'U' : invoiceCharacter;
	}
	
	
	@Override
	public boolean equals(Object obj) {
		if (obj instanceof Suborder) {
			Suborder other = (Suborder) obj;
			return other.getSign().equals(getSign());
		}
		return false;
	}

	@Override
	public int hashCode() {
		return getSign().hashCode();
	}

	/* (non-Javadoc)
	 * @see java.lang.Object#toString()
	 */
	@Override
	public String toString() {
		return "Suborder_"+id+": ("+sign+" "+description+")";
	}
	
	

}
