package org.tb.bdom;

import java.util.Date;

import org.tb.GlobalConstants;
import org.tb.persistence.TimereportDAO;

public class EmployeeOrderViewDecorator extends Employeeorder {
	
	private static final long serialVersionUID = 1L; // 789L;
	
	private TimereportDAO timereportDAO;
	private Employeeorder employeeOrder;
	
	public double getDuration() {
		Long durationHours = timereportDAO.getTotalDurationHoursForEmployeeOrder(employeeOrder.getId());
		Long durationMinutes = timereportDAO.getTotalDurationMinutesForEmployeeOrder(employeeOrder.getId());	
		
		double totalTime = durationHours.doubleValue() + (durationMinutes.doubleValue() / GlobalConstants.MINUTES_PER_HOUR);
		
		/* round totalTime */
		totalTime *= 100.0;
		long roundedTime = Math.round(totalTime);
		totalTime = roundedTime / 100.0;
		
		/* return result */
		return totalTime;
    }
	
	public Double getDifference() {
		
		if((this.employeeOrder.getDebithours() != null && this.employeeOrder.getDebithours() > 0.0) 
				&& (this.employeeOrder.getDebithoursunit() == null || this.employeeOrder.getDebithoursunit() == GlobalConstants.DEBITHOURS_UNIT_TOTALTIME)){
			
			Double rounded,notRounded;
			notRounded = ( this.employeeOrder.getDebithours() - getDuration() );		
			rounded = Math.round( notRounded * 100 ) / 100.0   ;

			return rounded;
		} else{
			return null;
		}
	}
	
	/**
	 * @param timereportDAO
	 * @param employeeOrder
	 */
	public EmployeeOrderViewDecorator(TimereportDAO timereportDAO, Employeeorder employeeOrder) {
		this.timereportDAO = timereportDAO;
		this.employeeOrder = employeeOrder;
	}


	/* (non-Javadoc)
	 * @see org.tb.bdom.Employeeorder#getCreated()
	 */
	@Override
	public Date getCreated() {
		return employeeOrder.getCreated();
	}


	/* (non-Javadoc)
	 * @see org.tb.bdom.Employeeorder#getCreatedby()
	 */
	@Override
	public String getCreatedby() {
		return employeeOrder.getCreatedby();
	}


	/* (non-Javadoc)
	 * @see org.tb.bdom.Employeeorder#getCurrentlyValid()
	 */
	@Override
	public boolean getCurrentlyValid() {
		return employeeOrder.getCurrentlyValid();
	}


	/* (non-Javadoc)
	 * @see org.tb.bdom.Employeeorder#getDebithours()
	 */
	@Override
	public Double getDebithours() {
		return employeeOrder.getDebithours();
	}


	/* (non-Javadoc)
	 * @see org.tb.bdom.Employeeorder#getDebithoursunit()
	 */
	@Override
	public Byte getDebithoursunit() {
		return employeeOrder.getDebithoursunit();
	}


	/* (non-Javadoc)
	 * @see org.tb.bdom.Employeeorder#getEmployeecontract()
	 */
	@Override
	public Employeecontract getEmployeecontract() {
		return employeeOrder.getEmployeecontract();
	}


	/* (non-Javadoc)
	 * @see org.tb.bdom.Employeeorder#getEmployeeOrderAsString()
	 */
	@Override
	public String getEmployeeOrderAsString() {
		return employeeOrder.getEmployeeOrderAsString();
	}


	/* (non-Javadoc)
	 * @see org.tb.bdom.Employeeorder#getEmployeeordercontent()
	 */
	@Override
	public Employeeordercontent getEmployeeordercontent() {
		return employeeOrder.getEmployeeordercontent();
	}


	/* (non-Javadoc)
	 * @see org.tb.bdom.Employeeorder#getFitsToSuperiorObjects()
	 */
	@Override
	public boolean getFitsToSuperiorObjects() {
		return employeeOrder.getFitsToSuperiorObjects();
	}


	/* (non-Javadoc)
	 * @see org.tb.bdom.Employeeorder#getFromDate()
	 */
	@Override
	public java.sql.Date getFromDate() {
		return employeeOrder.getFromDate();
	}


	/* (non-Javadoc)
	 * @see org.tb.bdom.Employeeorder#getId()
	 */
	@Override
	public long getId() {
		return employeeOrder.getId();
	}


	/* (non-Javadoc)
	 * @see org.tb.bdom.Employeeorder#getLastupdate()
	 */
	@Override
	public Date getLastupdate() {
		return employeeOrder.getLastupdate();
	}


	/* (non-Javadoc)
	 * @see org.tb.bdom.Employeeorder#getLastupdatedby()
	 */
	@Override
	public String getLastupdatedby() {
		return employeeOrder.getLastupdatedby();
	}


	/* (non-Javadoc)
	 * @see org.tb.bdom.Employeeorder#getSign()
	 */
	@Override
	public String getSign() {
		return employeeOrder.getSign();
	}


	/* (non-Javadoc)
	 * @see org.tb.bdom.Employeeorder#getSuborder()
	 */
	@Override
	public Suborder getSuborder() {
		return employeeOrder.getSuborder();
	}


	/* (non-Javadoc)
	 * @see org.tb.bdom.Employeeorder#getUntilDate()
	 */
	@Override
	public java.sql.Date getUntilDate() {
		return employeeOrder.getUntilDate();
	}


	/* (non-Javadoc)
	 * @see org.tb.bdom.Employeeorder#getUpdatecounter()
	 */
	@Override
	public Integer getUpdatecounter() {
		return employeeOrder.getUpdatecounter();
	}


	/* (non-Javadoc)
	 * @see org.tb.bdom.Employeeorder#setCreated(java.util.Date)
	 */
	@Override
	public void setCreated(Date created) {
		employeeOrder.setCreated(created);
	}


	/* (non-Javadoc)
	 * @see org.tb.bdom.Employeeorder#setCreatedby(java.lang.String)
	 */
	@Override
	public void setCreatedby(String createdby) {
		employeeOrder.setCreatedby(createdby);
	}


	/* (non-Javadoc)
	 * @see org.tb.bdom.Employeeorder#setDebithours(java.lang.Double)
	 */
	@Override
	public void setDebithours(Double hours) {
		employeeOrder.setDebithours(hours);
	}


	/* (non-Javadoc)
	 * @see org.tb.bdom.Employeeorder#setDebithoursunit(java.lang.Byte)
	 */
	@Override
	public void setDebithoursunit(Byte debithoursunit) {
		employeeOrder.setDebithoursunit(debithoursunit);
	}


	/* (non-Javadoc)
	 * @see org.tb.bdom.Employeeorder#setEmployeecontract(org.tb.bdom.Employeecontract)
	 */
	@Override
	public void setEmployeecontract(Employeecontract employeecontract) {
		employeeOrder.setEmployeecontract(employeecontract);
	}


	/* (non-Javadoc)
	 * @see org.tb.bdom.Employeeorder#setEmployeeordercontent(org.tb.bdom.Employeeordercontent)
	 */
	@Override
	public void setEmployeeordercontent(Employeeordercontent employeeOrderContent) {
		employeeOrder.setEmployeeordercontent(employeeOrderContent);
	}


	/* (non-Javadoc)
	 * @see org.tb.bdom.Employeeorder#setFromDate(java.sql.Date)
	 */
	@Override
	public void setFromDate(java.sql.Date fromDate) {
		employeeOrder.setFromDate(fromDate);
	}


	/* (non-Javadoc)
	 * @see org.tb.bdom.Employeeorder#setId(long)
	 */
	@Override
	public void setId(long id) {
		employeeOrder.setId(id);
	}


	/* (non-Javadoc)
	 * @see org.tb.bdom.Employeeorder#setLastupdate(java.util.Date)
	 */
	@Override
	public void setLastupdate(Date lastupdate) {
		employeeOrder.setLastupdate(lastupdate);
	}


	/* (non-Javadoc)
	 * @see org.tb.bdom.Employeeorder#setLastupdatedby(java.lang.String)
	 */
	@Override
	public void setLastupdatedby(String lastupdatedby) {
		employeeOrder.setLastupdatedby(lastupdatedby);
	}


	/* (non-Javadoc)
	 * @see org.tb.bdom.Employeeorder#setSign(java.lang.String)
	 */
	@Override
	public void setSign(String sign) {
		employeeOrder.setSign(sign);
	}


	/* (non-Javadoc)
	 * @see org.tb.bdom.Employeeorder#setSuborder(org.tb.bdom.Suborder)
	 */
	@Override
	public void setSuborder(Suborder suborder) {
		employeeOrder.setSuborder(suborder);
	}


	/* (non-Javadoc)
	 * @see org.tb.bdom.Employeeorder#setUntilDate(java.sql.Date)
	 */
	@Override
	public void setUntilDate(java.sql.Date untilDate) {
		employeeOrder.setUntilDate(untilDate);
	}


	/* (non-Javadoc)
	 * @see org.tb.bdom.Employeeorder#setUpdatecounter(java.lang.Integer)
	 */
	@Override
	public void setUpdatecounter(Integer updatecounter) {
		employeeOrder.setUpdatecounter(updatecounter);
	}
	
	

}
