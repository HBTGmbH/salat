package org.tb.persistence;

import java.sql.Date;
import java.util.Calendar;
import java.util.List;

import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.AbstractTransactionalJUnit4SpringContextTests;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.test.context.transaction.TransactionConfiguration;
import org.springframework.transaction.annotation.Transactional;
import org.tb.bdom.Employeeorder;
import org.tb.bdom.Suborder;
import org.tb.bdom.Timereport;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations={"classpath:spring-beans-cfg-test.xml"})
@TransactionConfiguration
@Transactional
public class TimereportDAOTest extends AbstractTransactionalJUnit4SpringContextTests {

	@Autowired
	private TimereportDAO trDAO;
	
	@Autowired
	private SuborderDAO soDAO;
	
	@Autowired
	private EmployeeorderDAO eoDAO;
	
	@Autowired
	private SessionFactory sessionFactory;
	
	private Session getSession() {
		return this.sessionFactory.getCurrentSession();
	}
	
	
	private static Date getBrokenDate(int plusDays) {
		Calendar cal = Calendar.getInstance();
		cal.set(2016, Calendar.OCTOBER, 14, 0, 0, 0);
		cal.set(Calendar.MILLISECOND, 0);
		cal.add(Calendar.DATE, plusDays);
		return new Date(cal.getTimeInMillis());
	}
	
	@Test
	public void testGetTotalDurationMinutesForSuborder() {
		Date fromDate = getBrokenDate(0);
		Date untilDate = getBrokenDate(10); 
		
		for(Suborder so : soDAO.getSuborders(true)) {

			List<Employeeorder> employeeorders = eoDAO.getEmployeeOrdersBySuborderId(so.getId());
	        long minutes = 0l;
	        for (Employeeorder employeeorder : employeeorders) {
	            List<Timereport> timereports = employeeorder.getSuborder().getTimereports();
	            for (Timereport timereport : timereports) {
	                java.sql.Date refDate = timereport.getReferenceday().getRefdate();
	                if (timereport.getEmployeeorder().getId() == employeeorder.getId()
	                        && !refDate.before(fromDate) && !refDate.after(untilDate)) {
	                    minutes += timereport.getDurationminutes()+60*timereport.getDurationhours();
	                }
	            }
	        }
	        
	        Long newMinutes = trDAO.getTotalDurationMinutesForSuborder(so.getId(), fromDate, untilDate);
			
			Assert.assertEquals(minutes, newMinutes.longValue());
		}
	}
	
	@Test
	public void	testGetTotalDurationMinutesForEmployeeOrder() {
		List<Employeeorder> eos = eoDAO.getEmployeeorders();
		int counter = 0;
		for(Employeeorder eo : eos) {
			counter++;
			if(counter % 100 != 0) continue;

	        List<Timereport> timereports = getSession().createQuery("FROM Timereport tr WHERE tr.employeeorder.id = ?").setLong(0, eo.getId()).setCacheable(true).list();
	        long minutes = 0l;
	        for (Timereport timereport : timereports) {
	            minutes += timereport.getDurationminutes();
	            minutes += 60*timereport.getDurationhours();
	        }
	        
	        Long newMinutes = trDAO.getTotalDurationMinutesForEmployeeOrder(eo.getId());
			
			Assert.assertEquals(minutes, newMinutes.longValue());
		}
	}
}
