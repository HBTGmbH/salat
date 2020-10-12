package org.tb.persistence;

import java.time.LocalDate;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

import org.hibernate.Session;
import org.tb.bdom.Publicholiday;
import org.tb.bdom.Referenceday;
import org.tb.util.HolidaysUtil;

/**
 * DAO class for 'Publicholiday'
 * 
 * @author oda
 *
 */
public class PublicholidayDAO extends AbstractDAO {
	
	/**
	 * Saves the given public holiday.
	 * 
	 * @param Publicholiday ph
	 */
	public void save(Publicholiday ph) {
		Session session = getSession();
		session.saveOrUpdate(ph);
		session.flush();
	}
	
	/**
	 * checks if given date is a German public holiday
	 * An algorithm proposed by Gauss is used.
	 * 
	 * @link: http://www.phpforum.de/archiv_23333_Feiertage@berechnen_anzeigen.html
	 * 
	 * @param java.sql.Date dt
	 * 
	 * @return String publicHoliday
	 */
	public String getPublicHoliday(java.sql.Date dt) {
		String publicHoliday = "";
		Publicholiday ph = (Publicholiday) 
			getSession().createQuery("from Publicholiday p where p.refdate = ?").setDate(0, dt).uniqueResult();

		if (ph != null) {
			publicHoliday = ph.getName();
		}
		
		return publicHoliday;
	}
	
	/**
	 * Sets the German public holidays of current year if not yet done.
	 * This method will be carried out once at the first login of an employee in a new year.
	 * 
	 * 
	 */
	public void checkPublicHolidaysForCurrentYear() {
		@SuppressWarnings("unchecked")
		List<Publicholiday> holidays = getSession().createQuery("from Publicholiday p").list();
		
		int maxYear = 0;
		for(Publicholiday holiday : holidays) {
			Calendar cal = Calendar.getInstance();
			cal.setTime(holiday.getRefdate());
			
			maxYear = Math.max(maxYear, cal.get(Calendar.YEAR));
		}
		
		for(LocalDate easterSunday : HolidaysUtil.loadEasterSundayDates()) {
			if(maxYear < easterSunday.getYear()) {
				for(Publicholiday newHoliday : HolidaysUtil.generateHolidays(easterSunday)) {
					save(newHoliday);
				}
			}
		}
	}
	
	/**
	 * 
	 * @param start
	 * @param end
	 * @return Returns the number of holidays between the two given dates.
	 */
	public int getNumberOfHolidaysBetween(Date start, Date end) {
		@SuppressWarnings("unchecked")
		List<Publicholiday> holidays = getSession().createQuery("from Publicholiday ph where ph.refdate >= ? and ph.refdate <= ? ").setDate(0, start).setDate(1, end).list();
		return (holidays == null ? 0 : holidays.size());
	}
	
	/**
	 * Returns a List of all {@link Publicholiday}s with a {@link Referenceday#getRefdate()} between the two given dates.
	 * 
	 * @param start
	 * @param end
	 * @return
	 */
	@SuppressWarnings("unchecked")
	public List<Publicholiday> getPublicHolidaysBetween(Date start, Date end) {
		return getSession().createQuery("from Publicholiday ph where ph.refdate >= ? and ph.refdate <= ? ").setDate(0, start).setDate(1, end).list();
	}

}
