package org.tb.bdom;

import java.io.Serializable;
import java.sql.Date;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.List;

import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.OneToMany;
import javax.persistence.OneToOne;

import org.hibernate.annotations.Cascade;
import org.hibernate.annotations.CascadeType;
import org.tb.GlobalConstants;


/**
 * Bean for table 'Employeecontract'.
 * 
 * @author oda
 */
@Entity
public class Employeecontract implements Serializable {

	private static final long serialVersionUID = 1L;

	/**
	 * Autogenerated technical object id.
	 */	
	@Id
	@GeneratedValue(strategy = GenerationType.SEQUENCE)
	private long id;
	
	/** valid from date */
	private Date validFrom;
	
	/** valid until date */
	private Date validUntil;
	
	/** daily working time */
	private Double dailyWorkingTime;
	
	/** freelancer y/n */
	private Boolean freelancer;
	
	/** task description */
	private String taskDescription;
	
	/** fixed until date */
	private Date fixedUntil;
	
	/** report acceptance date */
	private java.sql.Date reportAcceptanceDate;
	
	/** report release date */
	private java.sql.Date reportReleaseDate;
	
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
	
	/** Hide Flag */
	private Boolean hide;
	
	/** Employee */
	@OneToOne
	@JoinColumn(name="EMPLOYEE_ID")
	@Cascade(value = { CascadeType.SAVE_UPDATE })
	private Employee employee;
	
	/** list of timereports, associated to this employeecontract */
	@OneToMany(mappedBy = "employeecontract")
	@Cascade(value = { CascadeType.SAVE_UPDATE })
	private List<Timereport> timereports;

	/** list of employeeorders, associated to this employeecontract */
	@OneToMany(mappedBy = "employeecontract")
	@Cascade(value = { CascadeType.SAVE_UPDATE })
	private List<Employeeorder> employeeorders;
	
	/** list of monthlyreports, associated to this employeecontract */
	@OneToMany(mappedBy = "employeecontract")
	@Cascade(value = { CascadeType.SAVE_UPDATE })
	private List<Monthlyreport> monthlyreports;
	
	/** list of vacations, associated to this employeecontract */
	@OneToMany(mappedBy = "employeecontract")
	@Cascade(value = { CascadeType.SAVE_UPDATE })
	private List<Vacation> vacations;
	
	public long getId() {
		return id;
	}

	public void setId(long id) {
		this.id = id;
	}

	public Double getDailyWorkingTime() {
		return dailyWorkingTime;
	}

	public void setDailyWorkingTime(Double dailyWorkingTime) {
		this.dailyWorkingTime = dailyWorkingTime;
	}
	
	public Boolean getFreelancer() {
		if (freelancer == null) {
			freelancer = false;
		}
		return freelancer;
	}

	public void setFreelancer(Boolean freelancer) {
		this.freelancer = freelancer;
	}

	public String getTaskDescription() {
		return taskDescription;
	}

	public void setTaskDescription(String taskDescription) {
		this.taskDescription = taskDescription;
	}

	public Date getFixedUntil() {
		return fixedUntil;
	}

	public void setFixedUntil(Date fixedUntil) {
		this.fixedUntil = fixedUntil;
	}

	public Date getValidFrom() {
		return validFrom;
	}

	public void setValidFrom(Date validFrom) {
		this.validFrom = validFrom;
	}

	public Date getValidUntil() {
		return validUntil;
	}

	public void setValidUntil(Date validUntil) {
		this.validUntil = validUntil;
	}

	public Employee getEmployee() {
		return employee;
	}

	public void setEmployee(Employee employee) {
		this.employee = employee;
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

	public List<Monthlyreport> getMonthlyreports() {
		return monthlyreports;
	}

	public void setMonthlyreports(List<Monthlyreport> monthlyreports) {
		this.monthlyreports = monthlyreports;
	}

	public List<Vacation> getVacations() {
		return vacations;
	}

	public void setVacations(List<Vacation> vacations) {
		this.vacations = vacations;
	}
		
	public java.sql.Date getReportAcceptanceDate() {
		return reportAcceptanceDate;
	}
	
	public String getReportAcceptanceDateString() {
		SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd");
		if (reportAcceptanceDate != null) {
			return simpleDateFormat.format(reportAcceptanceDate); 
		} else {
			return simpleDateFormat.format(validFrom); 
		}
	}

	public void setReportAcceptanceDate(java.sql.Date reportAcceptanceDate) {
		this.reportAcceptanceDate = reportAcceptanceDate;
	}

	public java.sql.Date getReportReleaseDate() {
		return reportReleaseDate;
	}
	
	public String getReportReleaseDateString() {
		SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd");
		if (reportReleaseDate != null) {
			return simpleDateFormat.format(reportReleaseDate);
		} else {
			return simpleDateFormat.format(validFrom);
		}
	}

	public void setReportReleaseDate(java.sql.Date reportReleaseDate) {
		this.reportReleaseDate = reportReleaseDate;
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
	 * @return the hide
	 */
	public Boolean getHide() {
		return hide;
	}

	/**
	 * @param hide the hide to set
	 */
	public void setHide(Boolean hide) {
		this.hide = hide;
	}

	public String getTimeString() {
		SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd");
		if (validUntil == null) {
			return simpleDateFormat.format(validFrom) + " - ";
		}
		return simpleDateFormat.format(validFrom) + " - " + simpleDateFormat.format(validUntil);
	}
	
	public Boolean getOpenEnd() {
		if (validUntil == null) {
			return true;
		}
		return false;
	}

	public Integer getVacationEntitlement() {
		if ((vacations != null) && (vacations.size() > 0)) {
			// actually, vacation entitlement is a constant value
			// for an employee (not year-dependent), so just take the
			// first vacation entry to get the value
			return vacations.get(0).getEntitlement();
		}
		else {
			return GlobalConstants.VACATION_PER_YEAR;
		}
	}
	
	/**
	 * Checks, if the employeecontract is released until the last day of the preceding month.
	 * @return Returns true, if the contract is not released until the last day of the preceding month, false otherwise.
	 */
	public boolean getReleaseWarning() {
		boolean releaseWarning = false;
		GregorianCalendar calendar = new GregorianCalendar();
		Date release = getReportReleaseDate();
		
		if (release == null) {
			// new contract without initial login
			return false;
		}
		
		java.util.Date now = new java.util.Date();
		int month = calendar.get(Calendar.MONTH);
		int year = calendar.get(Calendar.YEAR);

		if (month == 0) {
			year--;
			month = 11;
		} else {
			month--;
		}
		calendar.clear();
		calendar.set(Calendar.MONTH, month);
		calendar.set(Calendar.YEAR, year);
		int date = calendar.getActualMaximum(Calendar.DAY_OF_MONTH);
		calendar.set(Calendar.DATE, date);
		now = calendar.getTime();
		
		if (release.before(now)) {
			releaseWarning = true;
		}
		return releaseWarning;
	}
	
	/**
	 * Checks, if the employeecontract is accepted until the last day of the preceding month.
	 * @return Returns true, if the contract is not accepted until the last day of the preceding month, false otherwise.
	 */
	public boolean getAcceptanceWarning() {
		boolean acceptanceWarning = false;
		GregorianCalendar calendar = new GregorianCalendar();
		Date acceptance = getReportAcceptanceDate();
		
		if (acceptance == null) {
			// new contract without initial login
			return false;
		}
		
		java.util.Date now = new java.util.Date();
		int month = calendar.get(Calendar.MONTH);
		int year = calendar.get(Calendar.YEAR);

		if (month == 0) {
			year--;
			month = 11;
		} else {
			month--;
		}
		calendar.clear();
		calendar.set(Calendar.MONTH, month);
		calendar.set(Calendar.YEAR, year);
		int date = calendar.getActualMaximum(Calendar.DAY_OF_MONTH);
		calendar.set(Calendar.DATE, date);
		now = calendar.getTime();
		
		if (acceptance.before(now)) {
			acceptanceWarning = true;
		}
		return acceptanceWarning;
	}
	
}
