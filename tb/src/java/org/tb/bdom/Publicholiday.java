package org.tb.bdom;

import java.io.Serializable;
import java.sql.Date;

import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;

/**
 * Bean for table 'publicholiday'
 * 
 * @author oda
 */
@Entity
public class Publicholiday implements Serializable {

	private static final long serialVersionUID = 1L; // 1L;

	/**
	 * Autogenerated technical object id.
	 */	
	@Id
	@GeneratedValue(strategy = GenerationType.SEQUENCE)
	private long id;

	/** Date */
	private java.sql.Date refdate;
	
	/** name */
	private String name;
	
	
	public Publicholiday() {
	}
	
	public Publicholiday(Date refdate, String name) {
		this.refdate = refdate;
		this.name = name;
	}

	public long getId() {
		return id;
	}

	public void setId(long id) {
		this.id = id;
	}
	
	public java.sql.Date getRefdate() {
		return refdate;
	}

	public void setRefdate(java.sql.Date refdate) {
		this.refdate = refdate;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	@Override
	public boolean equals(Object obj) {
		if (obj instanceof Publicholiday) {
			Publicholiday other = (Publicholiday) obj;
			return other.refdate.equals(refdate);
		}
		return false;
	}

	@Override
	public int hashCode() {
		return refdate.hashCode();
	}

}
