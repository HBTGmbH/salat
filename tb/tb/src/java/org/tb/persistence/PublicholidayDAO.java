package org.tb.persistence;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.Iterator;
import java.util.List;

import org.hibernate.Session;
import org.springframework.orm.hibernate3.support.HibernateDaoSupport;
import org.tb.bdom.Publicholiday;
import org.tb.bdom.Referenceday;
import org.tb.util.DateUtils;

/**
 * DAO class for 'Publicholiday'
 * 
 * @author oda
 *
 */
public class PublicholidayDAO extends HibernateDaoSupport {

	
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
		List<Publicholiday> holidays = 
			getSession().createQuery("from Publicholiday p").list();

		Date currentYearStart = null;
		try {
			currentYearStart = new SimpleDateFormat("yyyy-MM-dd").parse(DateUtils.getCurrentYearString() + "-01-01");
		} catch (ParseException e) {
			e.printStackTrace();
		}
		boolean currentYearEntriesAvailable = false;
		for (Iterator iter = holidays.iterator(); iter.hasNext();) {
			Publicholiday ph = (Publicholiday) iter.next();
			
			if(ph.getRefdate().after(currentYearStart)) {
				currentYearEntriesAvailable = true;
				break;
			}
		}
		
		if (!currentYearEntriesAvailable) {
			// add public holidays to DB
			
			Calendar cal = new GregorianCalendar();
			int[] easter = DateUtils.getEaster(currentYearStart); // year, month, day
			
			Publicholiday ph = null;
			
			// Neujahr
			cal.set(DateUtils.getCurrentYear(),0,1);
			ph = new Publicholiday(new java.sql.Date(cal.getTimeInMillis()), "Neujahr");
			save(ph);
			
			// Karfreitag			
			cal.set(DateUtils.getCurrentYear(),easter[1]-1,easter[2]-2);
			ph = new Publicholiday(new java.sql.Date(cal.getTimeInMillis()), "Karfreitag");
			save(ph);
			
			// Ostersonntag			
			cal.set(DateUtils.getCurrentYear(),easter[1]-1,easter[2]);
			ph = new Publicholiday(new java.sql.Date(cal.getTimeInMillis()), "Ostersonntag");
			save(ph);
			
			// Ostermontag			
			cal.set(DateUtils.getCurrentYear(),easter[1]-1,easter[2]+1);
			ph = new Publicholiday(new java.sql.Date(cal.getTimeInMillis()), "Ostermontag");
			save(ph);
			
			// Maifeiertag
			cal.set(DateUtils.getCurrentYear(),4,1);
			ph = new Publicholiday(new java.sql.Date(cal.getTimeInMillis()), "Maifeiertag");
			save(ph);
			
			// Christi Himmelfahrt			
			cal.set(DateUtils.getCurrentYear(),easter[1]-1,easter[2]+39);
			ph = new Publicholiday(new java.sql.Date(cal.getTimeInMillis()), "Christi Himmelfahrt");
			save(ph);

			// Pfingstsonntag			
			cal.set(DateUtils.getCurrentYear(),easter[1]-1,easter[2]+49);
			ph = new Publicholiday(new java.sql.Date(cal.getTimeInMillis()), "Pfingstsonntag");
			save(ph);
			
			// Pfingstsonntag			
			cal.set(DateUtils.getCurrentYear(),easter[1]-1,easter[2]+50);
			ph = new Publicholiday(new java.sql.Date(cal.getTimeInMillis()), "Pfingstsonntag");
			save(ph);
			
			// Heiligabend			
			cal.set(DateUtils.getCurrentYear(),11,24);
			ph = new Publicholiday(new java.sql.Date(cal.getTimeInMillis()), "Heiligabend");
			save(ph);
			
			// 1. Weihnachtstag			
			cal.set(DateUtils.getCurrentYear(),11,25);
			ph = new Publicholiday(new java.sql.Date(cal.getTimeInMillis()), "1. Weihnachtstag");
			save(ph);
			
			// 2. Weihnachtstag			
			cal.set(DateUtils.getCurrentYear(),11,26);
			ph = new Publicholiday(new java.sql.Date(cal.getTimeInMillis()), "2. Weihnachtstag");
			save(ph);

			// Heiligabend			
			cal.set(DateUtils.getCurrentYear(),11,31);
			ph = new Publicholiday(new java.sql.Date(cal.getTimeInMillis()), "Silvester");
			save(ph);

		}
	}
	
	/**
	 * 
	 * @param start
	 * @param end
	 * @return Returns the number of holidays between the two given dates.
	 */
	public int getNumberOfHolidaysBetween(Date start, Date end) {
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
	public List<Publicholiday> getPublicHolidaysBetween(Date start, Date end) {
		return getSession().createQuery("from Publicholiday ph where ph.refdate >= ? and ph.refdate <= ? ").setDate(0, start).setDate(1, end).list();
	}

}
