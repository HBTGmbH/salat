package org.tb.persistence;

import java.util.Date;
import java.util.List;

import org.hibernate.Session;
import org.tb.bdom.Referenceday;
import org.tb.util.DateUtils;

/**
 * DAO class for 'Referenceday'
 * 
 * @author oda
 *
 */
public class ReferencedayDAO extends AbstractDAO {

	private PublicholidayDAO publicholidayDAO;
	
	public void setPublicholidayDAO(PublicholidayDAO publicholidayDAO) {
		this.publicholidayDAO = publicholidayDAO;
	}
	
	/**
	 * Saves the given referenceday.
	 * 
	 * @param Referenceday ref
	 */
	public void save(Referenceday ref) {
		Session session = getSession();
		session.saveOrUpdate(ref);
		session.flush();
	}

	/**
	 * Gets the referenceday for the given id.
	 * 
	 * @param long id
	 * 
	 * @return Referenceday
	 */
	public Referenceday getReferencedayById(long id) {
		return (Referenceday) getSession().createQuery("from Referenceday rd where rd.id = ?").setLong(0, id).uniqueResult();
	}
	
	/**
	 * Gets the referenceday for the given date.
	 * 
	 * @param Date dt
	 * 
	 * @return Referenceday
	 */
	public Referenceday getReferencedayByDate(Date dt) {		

		Referenceday ref = (Referenceday) 
			getSession().createQuery("from Referenceday r where r.refdate = ?").setDate(0, dt).uniqueResult();

		return ref;
	}
	
	/**
	 * Get a list of all Referencedays.
	 * 
	 * @return List<Referenceday>
	 */
	@SuppressWarnings("unchecked")
	public List<Referenceday> getReferencedays() {
		return getSession().createQuery("from Referenceday").list();
	}
	
	/**
	 * Adds a referenceday to database at the time when it is first referenced in a new timereport.
	 * 
	 * @param java.sql.Date dt
	 * 
	 */
	public void addReferenceday(java.sql.Date dt) {
		Referenceday rd = new Referenceday();
		rd.setRefdate(dt);
		
		// set day of week
		String dow = DateUtils.getDoW(dt);	
		rd.setDow(dow);
				
		// checks for public holidays		
		String publicHoliday = publicholidayDAO.getPublicHoliday(dt);
		if ((publicHoliday != null) && (publicHoliday.length() > 0)) {
			rd.setHoliday(Boolean.TRUE);
			rd.setName(publicHoliday);
		} else if (dow.equals("Sun")) {
			rd.setHoliday(Boolean.TRUE);
			rd.setName("");
		} else {
			rd.setHoliday(Boolean.FALSE);
			rd.setName("");
		}
		
		// check workingday
		if ((rd.getHoliday()) || (dow.equals("Sat")) || (dow.equals("Sun"))) {
			rd.setWorkingday(Boolean.FALSE);
		} else {
			rd.setWorkingday(Boolean.TRUE);
		}
		
		save(rd);
	}
	
}
