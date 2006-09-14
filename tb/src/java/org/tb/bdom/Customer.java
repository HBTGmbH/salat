package org.tb.bdom;

import java.io.Serializable;
import java.util.List;

import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.OneToMany;

import org.hibernate.annotations.Cascade;
import org.hibernate.annotations.CascadeType;


/**
 * Bean for table 'customer'.
 * 
 * @author oda
 */
@Entity
public class Customer implements Serializable {

	private static final long serialVersionUID = 1L;

	/**
	 * Autogenerated technical object id.
	 */	
	@Id
	@GeneratedValue(strategy = GenerationType.SEQUENCE)
	private long id;

	/** Name */
	private String name;
	
	/** Address */
	private String address;
	
	/** list of customerorders, associated to this customer */
	@OneToMany(mappedBy = "customer")
	@Cascade(value = { CascadeType.SAVE_UPDATE })
	private List<Customerorder> customerorders;

	/** list of invoices, associated to this customer */
	@OneToMany(mappedBy = "customer")
	@Cascade(value = { CascadeType.SAVE_UPDATE })
	private List<Invoice> invoices;
	
	
	public long getId() {
		return id;
	}

	public void setId(long id) {
		this.id = id;
	}

	public String getAddress() {
		return address;
	}

	public void setAddress(String address) {
		this.address = address;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public List<Customerorder> getCustomerorders() {
		return customerorders;
	}

	public void setCustomerorders(List<Customerorder> customerorders) {
		this.customerorders = customerorders;
	}

	public List<Invoice> getInvoices() {
		return invoices;
	}

	public void setInvoices(List<Invoice> invoices) {
		this.invoices = invoices;
	}

	@Override
	public boolean equals(Object obj) {
		if (obj instanceof Customer) {
			Customer other = (Customer) obj;
			return other.name.equals(name);
		}
		return false;
	}

	@Override
	public int hashCode() {
		return name.hashCode();
	}

}
