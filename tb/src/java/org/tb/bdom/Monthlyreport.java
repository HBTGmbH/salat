package org.tb.bdom;

import java.io.Serializable;

import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;

import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;
import org.hibernate.annotations.Cascade;
import org.hibernate.annotations.CascadeType;
import org.hibernate.annotations.Fetch;
import org.hibernate.annotations.FetchMode;

/**
 * Bean for table 'Monthlyreport'
 * 
 * @author oda
 */
@Entity
@Cache(usage=CacheConcurrencyStrategy.READ_WRITE)
public class Monthlyreport implements Serializable {

	private static final long serialVersionUID = 1L; // 1L;

	/**
	 * Autogenerated technical object id.
	 */	
	@Id
	@GeneratedValue(strategy = GenerationType.SEQUENCE)
	private long id;

	/** Employeecontract */
	@ManyToOne
	@Fetch(FetchMode.SELECT)
	@JoinColumn(name="EMPLOYEECONTRACT_ID")
	@Cascade(value = { CascadeType.SAVE_UPDATE })
	private Employeecontract employeecontract;
	
	/** year */
	private Integer year;
	
	/** month */
	private Integer month;
	
	/** hour balance */
	private Double hourbalance;
	
	/** ok by ma */
	private Boolean ok_ma;
	
	/** ok by av */
	private Boolean ok_av;
	

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
	
	public Integer getMonth() {
		return month;
	}

	public void setMonth(Integer month) {
		this.month = month;
	}

	public Integer getYear() {
		return year;
	}

	public void setYear(Integer year) {
		this.year = year;
	}

	public Double getHourbalance() {
		return hourbalance;
	}

	public void setHourbalance(Double hourbalance) {
		this.hourbalance = hourbalance;
	}

	public Boolean getOk_av() {
		return ok_av;
	}

	public void setOk_av(Boolean ok_av) {
		this.ok_av = ok_av;
	}

	public Boolean getOk_ma() {
		return ok_ma;
	}

	public void setOk_ma(Boolean ok_ma) {
		this.ok_ma = ok_ma;
	}

	@Override
	public boolean equals(Object obj) {
		if (obj instanceof Monthlyreport) {
			Monthlyreport other = (Monthlyreport) obj;
			return (other.year.equals(year) && other.employeecontract.equals(employeecontract));
		}
		return false;
	}

}
