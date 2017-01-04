package org.tb.persistence;

import java.util.List;

import org.hibernate.Session;
import org.tb.bdom.Employeecontract;
import org.tb.bdom.Monthlyreport;

/**
 * DAO class for 'Monthlyreport'
 * 
 * @author oda
 *
 */
public class MonthlyreportDAO extends AbstractDAO {

	
	
	/**
	 * Gets the Monthlyreport for the given id.
	 * 
	 * @param long id
	 * 
	 * @return Monthlyreport
	 */
	public Monthlyreport getMonthlyreportById(long id) {
		return (Monthlyreport) getSession().createQuery("from Monthlyreport mr where mr.id = ?").setLong(0, id).uniqueResult();
	}
	
	/**
	 * Gets the Monthlyreports for the given year and month.
	 * 
	 * @param int year
	 * @param int month
	 * 
	 * @return List<Monthlyreport>
	 */
	public List<Monthlyreport> getMonthlyreportsByYearAndMonth(int year, int month) {		

		@SuppressWarnings("unchecked")
		List<Monthlyreport> specificMonthlyreports = 
			getSession().createQuery("from Monthlyreport mr where mr.year = ? and mr.month = ?").setInteger(0, year).setInteger(1, month).list();

		return specificMonthlyreports;
	}
	
	/**
	 * Gets the Monthlyreport for the given year and month and employeecontract id.
	 * 
	 * @param long ecId
	 * @param int year
	 * @param int month
	 * 
	 * @return Monthlyreport
	 */
	public Monthlyreport getMonthlyreportByYearAndMonthAndEmployeecontract(long ecId, int year, int month) {		

		Monthlyreport mr = (Monthlyreport)  
			getSession().createQuery("from Monthlyreport mr where mr.year = ? and mr.month = ? and mr.employeecontract.id = ?").setInteger(0, year).setInteger(1, month).setLong(2, ecId).uniqueResult();

		return mr;
	}
	
	/**
	 * Get a list of all Monthlyreports.
	 * 
	 * @return List<Monthlyreport>
	 */
	@SuppressWarnings("unchecked")
	public List<Monthlyreport> getMonthlyreports() {
		return getSession().createQuery("from Monthlyreport").list();
	}	
	
	/**
	 * Sets up a new Monthlyreport for given year/month/ec.
	 * 
	 * @param Employeecontract ec
	 * @param int year
	 * @param int month
	 * 
	 * @return Monthlyreport
	 */
	public Monthlyreport setNewReport(Employeecontract ec, int year, int month) {
		Monthlyreport mr = new Monthlyreport();
		mr.setEmployeecontract(ec);
		mr.setHourbalance(new Double(0.0));
		mr.setYear(new Integer(year));
		mr.setMonth(new Integer(month));
		mr.setOk_av(new Boolean(Boolean.FALSE));
		mr.setOk_ma(new Boolean(Boolean.FALSE));
		
		save(mr);
		
		return mr;
	}
	
	/**
	 * Saves the given Monthlyreport.
	 * 
	 * @param Monthlyreport mr
	 * 
	 */
	public void save(Monthlyreport mr) {
		Session session = getSession();
		session.saveOrUpdate(mr);
		session.flush();
	}

	
	/**
	 * Deletes the given Monthlyreport .
	 * 
	 * @param long mrId
	 * 
	 * @return boolean
	 */
	public boolean deleteMonthlyreportById(long mrId) {
		List<Monthlyreport> allMonthlyreports = getMonthlyreports();
		Monthlyreport mrToDelete = getMonthlyreportById(mrId);
		boolean mrDeleted = false;
		
		for (Monthlyreport mr : allMonthlyreports) {
			if(mr.getId() == mrToDelete.getId()) {				
				Session session = getSession();
				session.delete(mrToDelete);
				session.flush();
				mrDeleted = true;
				
				break;
			}
		}
		
		return mrDeleted;
	}

}
