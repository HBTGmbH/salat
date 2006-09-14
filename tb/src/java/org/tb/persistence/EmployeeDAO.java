package org.tb.persistence;

import java.util.Iterator;
import java.util.List;

import org.hibernate.HibernateException;
import org.hibernate.Session;
import org.hibernate.Transaction;
import org.hibernate.criterion.Restrictions;
import org.springframework.orm.hibernate3.support.HibernateDaoSupport;
import org.springframework.util.Assert;
import org.tb.bdom.Employee;
import org.tb.bdom.Employeecontract;

/**
 * DAO class for 'Employee'
 * 
 * @author oda
 *
 */
public class EmployeeDAO extends HibernateDaoSupport {

	private EmployeecontractDAO employeecontractDAO;
	
	private List<String> adminNames;

	
	
	public void setEmployeecontractDAO(EmployeecontractDAO employeecontractDAO) {
		this.employeecontractDAO = employeecontractDAO;
	}

	public void setAdminNames(List<String> adminNames) {
		this.adminNames = adminNames;
	}

	/**
	 * Registers an employee in the system.
	 * 
	 * @param String username
	 * @param String password
	 * 
	 * @return void
	 */
	public void registerEmployee(String username, String password)
			throws EmployeeAlreadyExistsException {
		
		Assert.notNull(username, "loginname");
		Assert.notNull(password, "password");

		Session session = getSession();
		Transaction trx = null;
		try {
			trx = session.beginTransaction();
			Employeecontract employee = new Employeecontract();
			//employee.setName(username);
			//employee.setPassword(password);			
			session.save(employee);			
			trx.commit();
		} catch (HibernateException ex) {
			if (trx != null)
				try {
					trx.rollback();
				} catch (HibernateException exRb) {
				}
			throw new RuntimeException(ex.getMessage());
		} finally {
			try {
				session.flush();
			} catch (Exception exCl) {
			}
		}
	}

	/**
	 * Logs in the employee with the given username and password.
	 * 
	 * @param String username
	 * @param String password
	 * 
	 * @return the LoginEmployee instance or <code>null</code> if no
	 *         employee matches the given username/password combination.
	 */
	public Employee getLoginEmployee(String username, String password) {
		Assert.notNull(username, "loginname");
		Assert.notNull(password, "password");
		Employee employee = (Employee) getSession()
				.createCriteria(Employee.class).add(
						Restrictions.eq("loginname", username)).add(
						Restrictions.eq("password", password)).uniqueResult();
		return employee;
	}

	/**
	 * Checks if the given employee is an administrator.
	 * 
	 * @param Employee employee
	 * 
	 * @return boolean
	 */
	public boolean isAdmin(Employee employee) {
		return adminNames.contains(employee.getSign());
	}

	/**
	 * Gets the employee from the given sign (unique).
	 * 
	 * @param String sign
	 * 
	 * @return Employee
	 */
	public Employee getEmployeeBySign(String sign) {
		return (Employee) getSession().createQuery(
			"from Employee p where p.sign = ?").setString(0, sign).uniqueResult();
	}
	
	/**
	 * Gets the employee from the given name (unique).
	 * 
	 * @param String first
	 * @param String last
	 * 
	 * @return Employee
	 */
	public Employee getEmployeeByName(String first, String last) {
		return (Employee) getSession().createQuery(
			"from Employee p where p.firstname = ? and p.lastname = ?").setString(0, first).setString(1, last).uniqueResult();
	}
	
	/**
	 * Gets the employee with the given id.
	 * 
	 * @param long id
	 * 
	 * @return Employee
	 */
	public Employee getEmployeeById(long id) {
		return (Employee) getSession().createQuery("from Employee em where em.id = ?").setLong(0, id).uniqueResult();
	}
	
	/**
	 * Get a list of all Employees ordered by lastname.
	 * 
	 * @return List<Employee>
	 */
	public List<Employee> getEmployees() {
		return getSession().createQuery(
				"from Employee p order by upper(p.lastname)").list();
	}

	
	/**
	 * Saves the given employee.
	 * 
	 * @param Employee employee
	 */
	public void save(Employee employee) {
		Session session = getSession();
		session.saveOrUpdate(employee);
		session.flush();
	}
	

	/**
	 * Deletes the given employee .
	 * 
	 * @param long emId
	 * 
	 * @return boolean
	 */
	public boolean deleteEmployeeById(long emId) {
		List<Employee> allEmployees = getEmployees();
		Employee emToDelete = getEmployeeById(emId);
		boolean emDeleted = false;
		
		for (Iterator iter = allEmployees.iterator(); iter.hasNext();) {
			Employee em = (Employee) iter.next();
			if(em.getId() == emToDelete.getId()) {	
				// check if related employeecontract exists 
				// if so, no deletion possible				

				boolean deleteOk = true;
				List<Employeecontract> allEmployeecontracts = employeecontractDAO.getEmployeeContracts();
				for (Iterator iter2 = allEmployeecontracts.iterator(); iter2.hasNext();) {
					Employeecontract ec = (Employeecontract) iter2.next();
					if (ec.getEmployee().getId() == emToDelete.getId()) {
						deleteOk = false;
						break;
					}
				}
				
				if (deleteOk) {
					Session session = getSession();
					session.delete(emToDelete);
					session.flush();
					emDeleted = true;
				}
					
				break;
			}
		}
		
		return emDeleted;
	}

}
