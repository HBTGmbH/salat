package org.tb.persistence;

import java.util.List;

import org.hibernate.Session;
import org.springframework.orm.hibernate3.support.HibernateDaoSupport;
import org.tb.GlobalConstants;
import org.tb.bdom.Employeecontract;
import org.tb.bdom.Vacation;

/**
 * DAO class for 'Vacation'
 * 
 * @author oda
 *
 */
public class VacationDAO extends HibernateDaoSupport {

	
	/**
	 * Saves the given Vacation
	 * 
	 * @param Vacation va
	 * .
	 */
	public void save(Vacation va) {
		Session session = getSession();
		session.saveOrUpdate(va);
		session.flush();
	}

	/**
	 * Gets the Vacation for the given id.
	 * 
	 * @param long id
	 * 
	 * @return Vacation
	 * 
	 */
	public Vacation getVacationById(long id) {
		return (Vacation) getSession().createQuery("from Vacation va where va.id = ?").setLong(0, id).uniqueResult();
	}
	
	/**
	 * Gets the Vacations for the given year.
	 * 
	 * @param int year
	 * 
	 * @return List<Vacation>
	 */
	public List<Vacation> getVacationsByYear(int year) {		

		@SuppressWarnings("unchecked")
		List<Vacation> specificVacations = getSession().createQuery("from Vacation va where va.year = ?").setInteger(0, year).list();

		return specificVacations;
	}
	
	/**
	 * Gets the Vacation for the given year and employeecontract id.
	 * 
	 * @param long ecId
	 * @param int year
	 * 
	 */
	public Vacation getVacationByYearAndEmployeecontract(long ecId, int year) {		

		Vacation va = (Vacation)getSession().createQuery("from Vacation va where va.year = ? and va.employeecontract.id = ?").setInteger(0, year).setLong(1, ecId).uniqueResult();

		return va;
	}
	
	/**
	 * Get a list of all Vacations.
	 * 
	 * 
	 * @return List<Vacation>
	 */
	@SuppressWarnings("unchecked")
	public List<Vacation> getVacations() {
		return getSession().createQuery("from Vacation").list();
	}	
	
	/**
	 * Sets up a new Vacation for given year/ec.
	 * 
	 * @param Employeecontract ec
	 * @param int year
	 * 
	 * @return Vacation
	 */
	public Vacation setNewVacation(Employeecontract ec, int year) {
		Vacation va = new Vacation();
		va.setEmployeecontract(ec);
		va.setYear(new Integer(year));
		va.setEntitlement(new Integer(GlobalConstants.VACATION_PER_YEAR));
		va.setUsed(new Integer(0)); 
		
		save(va);
		
		return va;
	}
	
	/**
	 * Deletes the given Vacation.
	 * 
	 * @param long vaId
	 */
	public boolean deleteVacationById(long vaId) {
		List<Vacation> allVacations = getVacations();
		Vacation vaToDelete = getVacationById(vaId);
		boolean vaDeleted = false;
		
		for (Vacation va : allVacations) {
			if(va.getId() == vaToDelete.getId()) {				
				Session session = getSession();
				session.delete(vaToDelete);
				session.flush();
				vaDeleted = true;
				
				break;
			}
		}
		
		return vaDeleted;
	}

}
