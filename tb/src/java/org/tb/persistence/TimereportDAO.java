package org.tb.persistence;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.hibernate.Session;
import org.springframework.orm.hibernate3.support.HibernateDaoSupport;
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
		return getSession().createQuery("from Timereport order by employeecontract.employee.lastname asc, referenceday.refdate desc, sequencenumber asc").list();
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
				getSession().createQuery("from Timereport t where t.suborder.id = ? and t.suborder.customerorder.id = ? order by employeecontract.employee.lastname asc, referenceday.refdate desc, sequencenumber asc").setLong(0, so.getId()).setLong(1, coId).list();
			
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
				getSession().createQuery("from Timereport t where t.referenceday.refdate = ? and t.suborder.id = ? and t.suborder.customerorder.id = ? order by employeecontract.employee.lastname asc, referenceday.refdate desc, sequencenumber asc").setDate(0, dt).setLong(1, so.getId()).setLong(2, coId).list();
			
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
				getSession().createQuery("from Timereport t where t.employeecontract.id = ? order by employeecontract.employee.lastname asc, referenceday.refdate desc, sequencenumber asc").setLong(0, contractId).list();
			
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
				getSession().createQuery("from Timereport t where t.employeecontract.id = ? and t.referenceday.refdate = ? order by employeecontract.employee.lastname asc, sequencenumber asc").setLong(0, contractId).setDate(1, dt).list();

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
				getSession().createQuery("from Timereport t where t.referenceday.refdate = ? order by employeecontract.employee.lastname asc, sequencenumber asc").setDate(0, dt).list();

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
				getSession().createQuery("from Timereport t where t.employeecontract.id = ? and t.suborder.id = ? and t.suborder.customerorder.id = ? order by employeecontract.employee.lastname asc, referenceday.refdate desc, sequencenumber asc").setLong(0, contractId).setLong(1, so.getId()).setLong(2, coId).list();

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
				getSession().createQuery("from Timereport t where t.referenceday.refdate = ? and t.suborder.id = ? and t.suborder.customerorder.id = ? order by employeecontract.employee.lastname asc, referenceday.refdate desc, sequencenumber asc").setDate(0, dt).setLong(1, so.getId()).setLong(2, coId).list();

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
	 * Saves the given timereport.
	 * 
	 * @param Timereport tr
	 * 
	 */
	public void save(Timereport tr) {
		Session session = getSession();
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
