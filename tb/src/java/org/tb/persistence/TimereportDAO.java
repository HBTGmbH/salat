package org.tb.persistence;

import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;

import org.hibernate.Session;
import org.springframework.orm.hibernate3.support.HibernateDaoSupport;
import org.tb.GlobalConstants;
import org.tb.bdom.Customerorder;
import org.tb.bdom.Employee;
import org.tb.bdom.Employeecontract;
import org.tb.bdom.Suborder;
import org.tb.bdom.Timereport;
import org.tb.helper.TimereportHelper;

/**
 * DAO class for 'Timereport'
 * 
 * @author oda
 *
 */
public class TimereportDAO extends HibernateDaoSupport {
	
	private SuborderDAO suborderDAO;
	
	
	public void setSuborderDAO(SuborderDAO suborderDAO) {
		this.suborderDAO = suborderDAO;
	}
	
	/**
	 * Gets the timereport for the given id.
	 * 
	 * @param long id
	 * 
	 * @return Timereport
	 */
	public Timereport getTimereportById(long id) {
		return (Timereport) getSession().createQuery("from Timereport t where t.id = ?").setLong(0, id).uniqueResult();
	}
	
	/**
	 * Get a list of all Timereports.
	 * 
	 * @return List<Timereport>
	 */
	public List<Timereport> getTimereports() {
		return getSession().createQuery("from Timereport order by employeecontract.employee.sign asc, referenceday.refdate desc, sequencenumber asc").list();
	}
	
	/**
	 * 
	 * @param suborderId
	 * @return
	 */
	public List<Timereport> getTimereportsBySuborderId(long suborderId) {
		return getSession().createQuery("from Timereport tr where tr.suborder.id = ? order by employeecontract.employee.sign asc, referenceday.refdate desc, sequencenumber asc").setLong(0, suborderId).list();
	}

	/**
	 * 
	 * @param ecId
	 * @param suborderId
	 * @return
	 */
	public List<Timereport> getTimereportsBySuborderIdAndEmployeeContractId(long suborderId, long ecId) {
		return getSession().createQuery("from Timereport tr where tr.suborder.id = ? and tr.employeecontract.id = ? order by employeecontract.employee.sign asc, referenceday.refdate desc, sequencenumber asc").setLong(0, suborderId).setLong(1, ecId).list();
	}
	
	/**
	 * 
	 * @param employeeContractId
	 * @return Returns a list of all {@link Timereport}s associated to the given {@link Employeecontract#getId()}.
	 */
	public List<Timereport> getTimereportsByEmployeeContractId(long employeeContractId) {
		return getSession().createQuery("from Timereport where employeecontract.id = ? order by employeecontract.employee.sign asc, referenceday.refdate desc, sequencenumber asc").setLong(0, employeeContractId).list();
	}
	
	/**
	 * Gets a list of Timereports by month/year.
	 * month must have format EEE here, e.g. 'Jan' !
	 * 
	 * @param String month
	 * @param String year
	 * 
	 * @return List<Timereport>
	 */
	public List<Timereport> getTimereportsByMonthAndYear(String month, String year) {
		
		List<Timereport> specificTimereports = new ArrayList<Timereport>();
		List<Timereport> allTimereports = getTimereports();
			
		for (Iterator iter = allTimereports.iterator(); iter.hasNext();) {
			// if timereport belongs to reference month/year, add it to result list...
			Timereport tr = (Timereport) iter.next();	

			if ((TimereportHelper.getMonthStringFromTimereport(tr).equalsIgnoreCase(month)) &&
				(TimereportHelper.getYearStringFromTimereport(tr).equalsIgnoreCase(year)))	{
					specificTimereports.add(tr);
			}
		}

		return specificTimereports;
	}
	
	/**
	 * Gets a list of 'W' Timereports by month/year and customerorder.
	 * 
	 * @param long coId
	 * @param String month
	 * @param String year
	 * @param String sortOfReport
	 * 
	 * @return List<Timereport>
	 */
	public List<Timereport> getTimereportsByMonthAndYearAndCustomerorder(long coId, String month, String year, String sortOfReport) {
		
		List<Suborder> suborders = suborderDAO.getSuborders();
		
		List<Timereport> allTimereports = new ArrayList<Timereport>();
		for (Iterator iter = suborders.iterator(); iter.hasNext();) {
			Suborder so = (Suborder) iter.next();		
			// get all timereports for this suborder...
			List<Timereport> specificTimereports = 
				getSession().createQuery("from Timereport t where t.suborder.id = ? and t.suborder.customerorder.id = ? order by employeecontract.employee.sign asc, referenceday.refdate desc, sequencenumber asc").setLong(0, so.getId()).setLong(1, coId).list();
			
			for (Iterator iter2 = specificTimereports.iterator(); iter2.hasNext();) {
				// if timereport belongs to reference month/year, add it to result list...
				Timereport tr = (Timereport) iter2.next();	

				if ((sortOfReport != null) && (tr.getSortofreport().equals("W"))) {
					if ((TimereportHelper.getMonthStringFromTimereport(tr).equalsIgnoreCase(month)) &&
							(TimereportHelper.getYearStringFromTimereport(tr).equalsIgnoreCase(year)))	{
						allTimereports.add(tr);
					}
				}
			}
		}
		return allTimereports;
	}
	
	/**
	 * Gets a list of Timereports by month/year and customerorder.
	 * 
	 * @param long coId
	 * @param java.sql.Date dt
	 * @param String sortOfReport
	 * 
	 * @return List<Timereport>
	 */
	public List<Timereport> getTimereportsByDateAndCustomerorder(long coId, java.sql.Date dt, String sortOfReport) {
		
		List<Suborder> suborders = suborderDAO.getSuborders();
		
		List<Timereport> allTimereports = new ArrayList<Timereport>();
		for (Iterator iter = suborders.iterator(); iter.hasNext();) {
			Suborder so = (Suborder) iter.next();		
			// get all timereports for this suborder...
			List<Timereport> specificTimereports = 
				getSession().createQuery("from Timereport t where t.referenceday.refdate = ? and t.suborder.id = ? and t.suborder.customerorder.id = ? order by employeecontract.employee.sign asc, referenceday.refdate desc, sequencenumber asc").setDate(0, dt).setLong(1, so.getId()).setLong(2, coId).list();
			
			for (Iterator iter2 = specificTimereports.iterator(); iter2.hasNext();) {
				// if timereport belongs to reference month/year, add it to result list...
				Timereport tr = (Timereport) iter2.next();	

				if ((sortOfReport != null) && (tr.getSortofreport().equals("W"))) {
					allTimereports.add(tr);
				}
			}
		}
		return allTimereports;
	}
	
	/**
	 * Gets a list of Timereports by employee contract id and month/year.
	 * 
	 * @param long contractId
	 * @param String month
	 * @param String year
	 * 
	 * @return List<Timereport>
	 */
	public List<Timereport> getTimereportsByMonthAndYearAndEmployeeContractId(long contractId, String month, String year) {
		
		List<Timereport> allTimereports = new ArrayList<Timereport>();
		List<Timereport> specificTimereports = 
				getSession().createQuery("from Timereport t where t.employeecontract.id = ? order by employeecontract.employee.sign asc, referenceday.refdate desc, sequencenumber asc").setLong(0, contractId).list();
			
		for (Iterator iter2 = specificTimereports.iterator(); iter2.hasNext();) {
			// if timereport belongs to reference month/year, add it to result list...
			Timereport tr = (Timereport) iter2.next();	
			
			// month has format EEE, e.g., 'Jan'
			if ((TimereportHelper.getMonthStringFromTimereport(tr).equalsIgnoreCase(month)) &&
				(TimereportHelper.getYearStringFromTimereport(tr).equalsIgnoreCase(year)))	{
				allTimereports.add(tr);
			}
		}

		return allTimereports;
	}
	
	/**
	 * Gets a list of Timereports by employee contract id and date.
	 * 
	 * @param long contractId
	 * @param java.sql.Date dt
	 * 
	 * @return List<Timereport>
	 */
	public List<Timereport> getTimereportsByDateAndEmployeeContractId(long contractId, java.sql.Date dt) {
		
		List<Timereport> allTimereports = 
				getSession().createQuery("from Timereport t where t.employeecontract.id = ? and t.referenceday.refdate = ? order by employeecontract.employee.sign asc, sequencenumber asc").setLong(0, contractId).setDate(1, dt).list();

		return allTimereports;
	}
	
	/**
	 * Gets a list of all {@link Timereport}s that fulfill following criteria: 
	 * 1) associated to the given employee contract
	 * 2) refdate out of range of the employee contract
	 * 
	 * @param employeecontract
	 * @return Returns a {@link List} with all {@link Timereport}s, that fulfill the criteria.
	 */
	public List<Timereport> getTimereportsOutOfRangeForEmployeeContract(Employeecontract employeecontract) {
		Long employeeContractId = employeecontract.getId();
		Date contractBegin = employeecontract.getValidFrom();
		Date contractEnd = employeecontract.getValidUntil();
		List<Timereport> allTimereports = 
				getSession().createQuery("from Timereport t where t.employeecontract.id = ? and (t.referenceday.refdate < ? or  t.referenceday.refdate > ?) order by t.referenceday.refdate asc, t.suborder.customerorder.sign asc, t.suborder.sign asc").setLong(0, employeeContractId).setDate(1, contractBegin).setDate(2, contractEnd).list();

		return allTimereports;
	}
	
//	public List<Timereport> getTimereportsUnassignedToAnEmployeeorder() {
//		
//	}
	
	/**
	 * Gets a list of all {@link Timereport}s that fulfill following criteria: 
	 * 1) associated to the given employee contract id
	 * 2) valid before and at the given date
	 * 3) status is open 
	 * 
	 * @param contractId
	 * @param dt
	 * @return
	 */
	public List<Timereport> getOpenTimereportsByEmployeeContractIdBeforeDate(long contractId, java.sql.Date dt) {
		
		List<Timereport> allTimereports = 
				getSession().createQuery("from Timereport t where t.employeecontract.id = ? and t.referenceday.refdate <= ? and status = ?").setLong(0, contractId).setDate(1, dt).setString(2, GlobalConstants.TIMEREPORT_STATUS_OPEN).list();

		return allTimereports;
	}
	
	/**
	 * Gets a list of all {@link Timereport}s that fulfill following criteria: 
	 * 1) associated to the given employee contract id
	 * 2) valid before and at the given date
	 * 3) status is commited 
	 * 
	 * @param contractId
	 * @param dt
	 * @return
	 */
	public List<Timereport> getCommitedTimereportsByEmployeeContractIdBeforeDate(long contractId, java.sql.Date dt) {
		
		List<Timereport> allTimereports = 
				getSession().createQuery("from Timereport t where t.employeecontract.id = ? and t.referenceday.refdate <= ? and status = ?").setLong(0, contractId).setDate(1, dt).setString(2, GlobalConstants.TIMEREPORT_STATUS_COMMITED).list();

		return allTimereports;
	}
	
	/**
	 * Gets a list of all {@link Timereport}s that fulfill following criteria: 
	 * 1) associated to the given employee contract id
	 * 2) valid after and at the given date
	 * 
	 * 
	 * @param contractId
	 * @param dt
	 * @return
	 */
	public List<Timereport> getTimereportsByEmployeeContractIdAfterDate(long contractId, java.sql.Date dt) {
		
		List<Timereport> allTimereports = 
				getSession().createQuery("from Timereport t where t.employeecontract.id = ? and t.referenceday.refdate >= ? ").setLong(0, contractId).setDate(1, dt).list();

		return allTimereports;
	}
	
	
	/**
	 * Gets a list of Timereports by employee contract id and two dates.
	 * 
	 * @param long contractId
	 * @param java.sql.Date begin
	 * @param java.sql.Date end
	 * 
	 * @return List<Timereport>
	 */
	public List<Timereport> getTimereportsByDatesAndEmployeeContractId(long contractId, java.sql.Date begin, java.sql.Date end) {	
		List<Timereport> allTimereports;
		if (begin.compareTo(end) == 0) {
			allTimereports = getSession().createQuery("from Timereport t where t.employeecontract.id = ? and t.referenceday.refdate >= ? and t.referenceday.refdate <= ? order by employeecontract.employee.sign asc, referenceday.refdate asc, sequencenumber asc").setLong(0, contractId).setDate(1, begin).setDate(2, end).list();
		} else {
			allTimereports = getSession().createQuery("from Timereport t where t.employeecontract.id = ? and t.referenceday.refdate >= ? and t.referenceday.refdate <= ? order by employeecontract.employee.sign asc, referenceday.refdate asc, suborder.customerorder.sign asc, suborder.sign asc").setLong(0, contractId).setDate(1, begin).setDate(2, end).list();
		}
		return allTimereports;
	}
	
	/**
	 * Gets a list of Timereports by associated to the given employee contract id, customer order id and the time period between the two given dates.
	 * 
	 * @param long contractId
	 * @param java.sql.Date begin
	 * @param java.sql.Date end
	 * @param customerOrderId
	 * 
	 * @return List<Timereport>
	 */
	public List<Timereport> getTimereportsByDatesAndEmployeeContractIdAndCustomerOrderId(long contractId, java.sql.Date begin, java.sql.Date end, long customerOrderId) {	
		List<Timereport> allTimereports;
		if (begin.compareTo(end) == 0) {
			allTimereports = getSession().createQuery("from Timereport t where t.employeecontract.id = ? and t.referenceday.refdate >= ? and t.referenceday.refdate <= ? and t.suborder.customerorder.id = ? order by employeecontract.employee.sign asc, referenceday.refdate asc, sequencenumber asc").setLong(0, contractId).setDate(1, begin).setDate(2, end).setLong(3, customerOrderId).list();
		} else {
			allTimereports = getSession().createQuery("from Timereport t where t.employeecontract.id = ? and t.referenceday.refdate >= ? and t.referenceday.refdate <= ? and t.suborder.customerorder.id = ? order by employeecontract.employee.sign asc, referenceday.refdate asc, suborder.customerorder.sign asc, suborder.sign asc").setLong(0, contractId).setDate(1, begin).setDate(2, end).setLong(3, customerOrderId).list();
		}
		return allTimereports;
	}
	
	/**
	 * Gets a list of Timereports by associated to the given employee contract id, suborder id and the time period between the two given dates.
	 * 
	 * @param long suborderId
	 * @param java.sql.Date begin
	 * @param java.sql.Date end
	 * @param customerOrderId
	 * 
	 * @return List<Timereport>
	 */
	public List<Timereport> getTimereportsByDatesAndEmployeeContractIdAndSuborderId(long contractId, java.sql.Date begin, java.sql.Date end, long suborderId) {	
		List<Timereport> allTimereports;
		if (end == null) {
			allTimereports = getSession().createQuery(
					"from Timereport t where t.employeecontract.id = ? and t.referenceday.refdate >= ? and t.suborder.id = ? order by employeecontract.employee.sign asc, referenceday.refdate asc, sequencenumber asc")
			.setLong(0, contractId).setDate(1, begin).setLong(3, suborderId).list();
		} else {
			if (begin.compareTo(end) == 0) {
				allTimereports = getSession()
						.createQuery(
								"from Timereport t where t.employeecontract.id = ? and t.referenceday.refdate >= ? and t.referenceday.refdate <= ? and t.suborder.id = ? order by employeecontract.employee.sign asc, referenceday.refdate asc, sequencenumber asc")
						.setLong(0, contractId).setDate(1, begin).setDate(2,
								end).setLong(3, suborderId).list();
			} else {
				allTimereports = getSession()
						.createQuery(
								"from Timereport t where t.employeecontract.id = ? and t.referenceday.refdate >= ? and t.referenceday.refdate <= ? and t.suborder.id = ? order by employeecontract.employee.sign asc, referenceday.refdate asc, suborder.customerorder.sign asc, suborder.sign asc")
						.setLong(0, contractId).setDate(1, begin).setDate(2,
								end).setLong(3, suborderId).list();
			}
		}		
		return allTimereports;
	}
	
	
	/**
	 * Gets a list of Timereports by date.
	 * 
	 * @param java.sql.Date dt
	 * 
	 * @return List<Timereport>
	 */
	public List<Timereport> getTimereportsByDate(java.sql.Date dt) {
		
		List<Timereport> allTimereports = 
				getSession().createQuery("from Timereport t where t.referenceday.refdate = ? order by employeecontract.employee.sign asc, sequencenumber asc").setDate(0, dt).list();

		return allTimereports;
	}
	
	/**
	 * Gets a list of timereports, which lay between two dates.
	 * 
	 * @param java.sql.Date begin
	 * @param java.sql.Date end
	 * 
	 * @return List<Timereport>
	 */
	public List<Timereport> getTimereportsByDates(java.sql.Date begin, java.sql.Date end) {
		List<Timereport> allTimereports;
		if (begin.compareTo(end) == 0) {
			allTimereports = getSession().createQuery("from Timereport t where t.referenceday.refdate >= ? and t.referenceday.refdate <= ? order by employeecontract.employee.sign asc, referenceday.refdate asc, sequencenumber asc").setDate(0, begin).setDate(1, end).list();
		} else {
			allTimereports = getSession().createQuery("from Timereport t where t.referenceday.refdate >= ? and t.referenceday.refdate <= ? order by employeecontract.employee.sign asc, referenceday.refdate asc, suborder.customerorder.sign asc, suborder.sign asc").setDate(0, begin).setDate(1, end).list();
		}
		return allTimereports;
	}
	
	/**
	 * Gets a list of timereports, which lay between two dates and belong to the given {@link Customerorder} id.
	 * 
	 * @param java.sql.Date begin
	 * @param java.sql.Date end
	 * @param coId
	 * 
	 * @return List<Timereport>
	 */
	public List<Timereport> getTimereportsByDatesAndCustomerOrderId(java.sql.Date begin, java.sql.Date end, long coId) {		
		List<Timereport> allTimereports; 
		if (begin.compareTo(end) == 0) {
			allTimereports = getSession().createQuery("from Timereport t where t.referenceday.refdate >= ? and t.referenceday.refdate <= ? and t.suborder.customerorder.id = ? order by employeecontract.employee.sign asc, referenceday.refdate asc, sequencenumber asc").setDate(0, begin).setDate(1, end).setLong(2, coId).list();
		} else {
			allTimereports = getSession().createQuery("from Timereport t where t.referenceday.refdate >= ? and t.referenceday.refdate <= ? and t.suborder.customerorder.id = ? order by employeecontract.employee.sign asc, referenceday.refdate asc, suborder.customerorder.sign asc, suborder.sign asc").setDate(0, begin).setDate(1, end).setLong(2, coId).list();
		}
		return allTimereports;
	}
	
	/**
	 * Gets a list of timereports, which lay between two dates and belong to the given {@link Suborder} id.
	 * 
	 * @param java.sql.Date begin
	 * @param java.sql.Date end
	 * @param suborderId
	 * 
	 * @return List<Timereport>
	 */
	public List<Timereport> getTimereportsByDatesAndSuborderId(java.sql.Date begin, java.sql.Date end, long suborderId) {		
		List<Timereport> allTimereports; 
		if (begin.compareTo(end) == 0) {
			allTimereports = getSession().createQuery("from Timereport t where t.referenceday.refdate >= ? and t.referenceday.refdate <= ? and t.suborder.id = ? order by employeecontract.employee.sign asc, referenceday.refdate asc, sequencenumber asc").setDate(0, begin).setDate(1, end).setLong(2, suborderId).list();
		} else {
			allTimereports = getSession().createQuery("from Timereport t where t.referenceday.refdate >= ? and t.referenceday.refdate <= ? and t.suborder.id = ? order by employeecontract.employee.sign asc, referenceday.refdate asc, suborder.customerorder.sign asc, suborder.sign asc").setDate(0, begin).setDate(1, end).setLong(2, suborderId).list();
		}
		return allTimereports;
	}
	
	
	/**
	 * Gets a list of 'W' Timereports by employee contract id and customer id and month/year.
	 * 
	 * @param long contractId
	 * @param long coId
	 * @param String month
	 * @param String year
	 * @param String sortOfReport
	 * 
	 * @return List<Timereport>
	 */
	public List<Timereport> getTimereportsByMonthAndYearAndEmployeeContractIdAndCustomerorderId(long contractId, long coId, String month, String year, String sortOfReport) {
		
		List<Suborder> suborders = suborderDAO.getSubordersByEmployeeContractId(contractId);
		
		List<Timereport> allTimereports = new ArrayList<Timereport>();
		for (Iterator iter = suborders.iterator(); iter.hasNext();) {
			Suborder so = (Suborder) iter.next();		
			// get all timereports for this suborder AND employee contract...
			List<Timereport> specificTimereports = 
				getSession().createQuery("from Timereport t where t.employeecontract.id = ? and t.suborder.id = ? and t.suborder.customerorder.id = ? order by employeecontract.employee.sign asc, referenceday.refdate desc, sequencenumber asc").setLong(0, contractId).setLong(1, so.getId()).setLong(2, coId).list();

			for (Iterator iter2 = specificTimereports.iterator(); iter2.hasNext();) {
				// if timereport belongs to reference month/year, add it to result list...
				Timereport tr = (Timereport) iter2.next();	

				if ((sortOfReport != null) && (tr.getSortofreport().equals("W"))) {
					if ((TimereportHelper.getMonthStringFromTimereport(tr).equalsIgnoreCase(month)) &&
							(TimereportHelper.getYearStringFromTimereport(tr).equalsIgnoreCase(year)))	{
						allTimereports.add(tr);
					}
				}
			}
		}
		return allTimereports;
	}
	
	/**
	 * Gets a list of Timereports by employee contract id and customer id and date.
	 * 
	 * @param long contractId
	 * @param long coId
	 * @param java.sql.Date dt
	 * @param String sortOfReport
	 * 
	 * @return List<Timereport>
	 */
	public List<Timereport> getTimereportsByDateAndEmployeeContractIdAndCustomerorderId(long contractId, long coId, java.sql.Date dt, String sortOfReport) {
		
		List<Suborder> suborders = suborderDAO.getSubordersByEmployeeContractId(contractId);
		
		List<Timereport> allTimereports = new ArrayList<Timereport>();
		for (Iterator iter = suborders.iterator(); iter.hasNext();) {
			Suborder so = (Suborder) iter.next();		
			// get all timereports for this suborder...
			List<Timereport> specificTimereports = 
				getSession().createQuery("from Timereport t where t.referenceday.refdate = ? and t.suborder.id = ? and t.suborder.customerorder.id = ? order by employeecontract.employee.sign asc, referenceday.refdate desc, sequencenumber asc").setDate(0, dt).setLong(1, so.getId()).setLong(2, coId).list();

			for (Iterator iter2 = specificTimereports.iterator(); iter2.hasNext();) {
				// if timereport belongs to reference month/year, add it to result list...
				Timereport tr = (Timereport) iter2.next();	

				if ((sortOfReport != null) && (tr.getSortofreport().equals("W"))) {
					allTimereports.add(tr);
				}
			}
		}
		return allTimereports;
	}

	/**
	 * 
	 * @param begin
	 * @param end
	 * @param customerOrderSign
	 * @return Returns a list of all timereports that are associated to the given customer order sign and are valid between the given dates. 
	 */
	public List<Timereport> getTimereportsByDatesAndCustomerOrderSign(java.sql.Date begin, java.sql.Date end, String customerOrderSign) {
		return getSession().createQuery("from Timereport t where t.referenceday.refdate >= ? and t.referenceday.refdate <= ? and t.suborder.customerorder.sign = ? ").setDate(0, begin).setDate(1, end).setString(2, customerOrderSign).list();
	}
	
	/**
	 * Calls {@link TimereportDAO#save(Timereport, Employee)} with {@link Employee} = null.
	 * @param tr
	 */
	public void save(Timereport tr) {
		save(tr, null, true);
	}
	
	/**
	 * Saves the given timereport and sets creation-/update-user and creation-/update-date.
	 * 
	 * @param Timereport tr
	 * 
	 */
	public void save(Timereport tr, Employee loginEmployee, boolean changeUpdateDate) {
		if (loginEmployee == null) {
			throw new RuntimeException("the login-user must be passed to the db");
		}
		Session session = getSession();
		java.util.Date creationDate = tr.getCreated();
		if (creationDate == null) {
			tr.setCreated(new java.util.Date());
			tr.setCreatedby(loginEmployee.getSign());
		} else if (changeUpdateDate){
			tr.setLastupdate(new java.util.Date());
			tr.setLastupdatedby(loginEmployee.getSign());
			Integer updateCounter = tr.getUpdatecounter();
			updateCounter = (updateCounter == null) ? 1 : updateCounter +1;
			tr.setUpdatecounter(updateCounter);
		}
		session.saveOrUpdate(tr);
		session.flush();
	}

	/**
	 * Deletes the given timereport.
	 * 
	 * @param long trId - timereport id
	 * 
	 */
	public boolean deleteTimereportById(long trId) {
		List<Timereport> allTimereports = getTimereports();
		Timereport trToDelete = getTimereportById(trId);
		boolean trDeleted = false;
		
		for (Iterator iter = allTimereports.iterator(); iter.hasNext();) {
			Timereport tr = (Timereport) iter.next();
			if(tr.getId() == trToDelete.getId()) {
				Session session = getSession();
				session.delete(trToDelete);
				session.flush();
				trDeleted = true;
				break;
			}
		}
		
		return trDeleted;
	}
	
}
