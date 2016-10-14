package org.tb.persistence;

import java.sql.Date;
import java.time.LocalDate;
import java.util.ArrayList;
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
import org.tb.bdom.Employeecontract;
import org.tb.bdom.Employeeorder;
import org.tb.bdom.Suborder;
import org.tb.bdom.Timereport;
import org.tb.helper.TimereportHelper;

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
	private EmployeecontractDAO ecDAO;
	
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
	
	@Test
	public void testGetTimereportsByMonthAndYear() {
		String[] months = new String[]{ "Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec" };
		String[] years = new String[]{ "2015", "2016" };
		List<Timereport> allTimereports = trDAO.getTimereports();
		for(int i = 0; i < months.length; i++) {
			String month = months[i];
			for(int j = 0; j < years.length; j++) {
				String year = years[j];
				
		        List<Timereport> specificTimereports = new ArrayList<Timereport>();
		        for (Timereport timereport : allTimereports) {
		            // if timereport belongs to reference month/year, add it to result list...
		            if (TimereportHelper.getMonthStringFromTimereport(timereport).equalsIgnoreCase(month) &&
		                    TimereportHelper.getYearStringFromTimereport(timereport).equalsIgnoreCase(year)) {
		                specificTimereports.add(timereport);
		            }
		        }
		        

		        LocalDate date = LocalDate.of(j+2015, i+1, 10);
				
				List<Timereport> newTrs = new ArrayList<>(trDAO.getTimereportsByMonthAndYear(Date.valueOf(date)));
				
				Assert.assertEquals(specificTimereports.size(), newTrs.size());
				outer: for(Timereport tr : specificTimereports) {
					for(int k = 0; k < newTrs.size(); k++) {
						if(newTrs.get(k).getId() == tr.getId()) {
							continue outer; 
						}
					}
					Assert.assertTrue("list contain different elements", false);
				}
			}
		}
	}
	
	@Test
	public void testGetTimereportsByMonthAndYearAndEmployeeContractId() {
		String[] months = new String[]{ "Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec" };
		String[] years = new String[]{ "2015", "2016" };
		List<Employeecontract> allContracts = ecDAO.getEmployeeContracts();
		for(int i = 0; i < months.length; i++) {
			String month = months[i];
			for(int j = 0; j < years.length; j++) {
				String year = years[j];
				
				for(int k = 0; k < allContracts.size(); k= k+10) {
					Employeecontract ec = allContracts.get(k);
					
			        List<Timereport> allTimereports = new ArrayList<Timereport>();
			        List<Timereport> specificTimereports = getSession().createQuery("from Timereport t where t.employeecontract.id = ? " +
			                "order by employeecontract.employee.sign asc, referenceday.refdate desc, sequencenumber asc")
			                .setLong(0, ec.getId()).setCacheable(true).list();
			        for (Timereport specificTimereport : specificTimereports) {
			            // if timereport belongs to reference month/year, add it to result list...
			            // month has format EEE, e.g., 'Jan'
			            if (TimereportHelper.getMonthStringFromTimereport(specificTimereport).equalsIgnoreCase(month)
			                    && TimereportHelper.getYearStringFromTimereport(specificTimereport).equalsIgnoreCase(year)) {
			                allTimereports.add(specificTimereport);
			            }
			        }
			        
	
			        LocalDate date = LocalDate.of(j+2015, i+1, 10);
					
					List<Timereport> newTrs = new ArrayList<>(trDAO.getTimereportsByMonthAndYearAndEmployeeContractId(ec.getId(), Date.valueOf(date)));
					
					Assert.assertEquals(allTimereports.size(), newTrs.size());
					outer: for(Timereport tr : allTimereports) {
						for(int l = 0; l < newTrs.size(); l++) {
							if(newTrs.get(l).getId() == tr.getId()) {
								continue outer; 
							}
						}
						Assert.assertTrue("list contain different elements", false);
					}
				}
			}
		}
	}
}
