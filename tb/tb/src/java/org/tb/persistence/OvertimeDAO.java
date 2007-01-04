package org.tb.persistence;

import java.util.List;

import org.hibernate.Session;
import org.springframework.orm.hibernate3.support.HibernateDaoSupport;
import org.tb.bdom.Employee;
import org.tb.bdom.Overtime;

public class OvertimeDAO extends HibernateDaoSupport {
	
	
	/**
	 * 
	 * @return Returns a list with all {@link Overtime}s.
	 */
	public List<Overtime> getOvertimes() {
		return (List<Overtime>) getSession().createQuery("from Overtime order by employeecontract.id asc, created asc").list();
	}
	
	/**
	 * 
	 * @param employeeContractId
	 * @return Returns a list with all {@link Overtime}s associated to the given employeeContractId.
	 */
	public List<Overtime> getOvertimesByEmployeeContractId(long employeeContractId) {
		return (List<Overtime>) getSession().createQuery("from Overtime where employeecontract.id = ? order by employeecontract.id asc, created asc").setLong(0, employeeContractId).list();
	}
	
	/**
	 * Calls {@link OvertimeDAO#save(Overtime, Employee)} with the given {@link Overtime} and null for the {@link Employee}.
	 * 
	 * @param overtime
	 */
	public void save(Overtime overtime) {
		save(overtime, null);
	}
	
	/**
	 * Saves the given overtime and sets creation-user and creation-date.
	 * 
	 * @param Suborder so
	 */
	public void save(Overtime overtime, Employee loginEmployee) {
		if (loginEmployee == null) {
			throw new RuntimeException("the login-user must be passed to the db");
		}
		Session session = getSession();
		
		overtime.setCreated(new java.util.Date());
		overtime.setCreatedby(loginEmployee.getSign());
		
		session.saveOrUpdate(overtime);
		session.flush();
		session.clear();
	}
	

}
