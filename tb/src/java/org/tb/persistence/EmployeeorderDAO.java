package org.tb.persistence;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import org.hibernate.Session;
import org.springframework.orm.hibernate3.support.HibernateDaoSupport;
import org.tb.bdom.Employee;
import org.tb.bdom.Employeecontract;
import org.tb.bdom.Employeeorder;
import org.tb.bdom.comparators.EmployeeOrderComparator;

/**
 * DAO class for 'Employeeorder'
 * 
 * @author oda
 *
 */
public class EmployeeorderDAO extends HibernateDaoSupport {

	private EmployeeOrderComparator employeeOrderComparator = new EmployeeOrderComparator();
	private EmployeecontractDAO employeecontractDAO;
	
	public void setEmployeecontractDAO(EmployeecontractDAO employeecontractDAO) {
		this.employeecontractDAO = employeecontractDAO;
	}
	
	
	/**
	 * Gets the employeeorder for the given id.
	 * 
	 * @param long id
	 * 
	 * @return Employeeorder
	 */
	public Employeeorder getEmployeeorderById(long id) {
		return (Employeeorder) getSession().createQuery("from Employeeorder eo where eo.id = ?").setLong(0, id).uniqueResult();
	}
	
	
	/**
	 * 
	 * @param employeecontractId
	 * @param suborderId
	 * @return
	 */
	public Employeeorder getEmployeeorderByEmployeeContractIdAndSuborderId(long employeecontractId, long suborderId) {
		return (Employeeorder) getSession().createQuery("from Employeeorder eo where eo.employeecontract.id = ? and eo.suborder.id = ?").setLong(0, employeecontractId).setLong(1, suborderId).uniqueResult();
	}
	
	/**
	 * Gets the employeeorder for the given sign.
	 * 
	 * @param String sign
	 * 
	 * @return Employeeorder
	 */
	public Employeeorder getEmployeeorderBySign(String sign) {
		Employeeorder co = (Employeeorder) getSession().createQuery("from Employeeorder c where c.sign = ?").setString(0, sign).uniqueResult();
		return co;
	}
	
	
	/**
	 * Gets the list of employeeorders for the given employee contract id.
	 * 
	 * @param employeeContractId
	 * @return
	 */
	public List<Employeeorder> getEmployeeOrdersByEmployeeContractId(long employeeContractId) {
		return getSession().createQuery("from Employeeorder where EMPLOYEECONTRACT_ID = ? order by suborder.customerorder.sign asc, suborder.sign asc, fromdate asc").setLong(0, employeeContractId).list();
	}
	
	/**
	 * Gets the list of employeeorders for the given employee id.
	 * 
	 * @param employeeId
	 * @return
	 */
	public List<Employeeorder> getEmployeeOrdersByEmployeeId(long employeeId) {
		Employeecontract employeecontract = employeecontractDAO.getEmployeeContractByEmployeeId(employeeId);
		long employeeContractId = employeecontract.getId();
		return getEmployeeOrdersByEmployeeContractId(employeeContractId);
	}
	
	
	
	/**
	 * Get a list of all Employeeorders ordered by their sign.
	 * 
	 * @return List<Employeeorder> 
	 */
	public List<Employeeorder> getEmployeeorders() {
		return getSession().createQuery("from Employeeorder order by employeecontract.employee.firstname asc, suborder.customerorder.sign asc, suborder.sign asc, fromdate asc").list();
	}

	/**
	 * Get a list of all Employeeorders ordered by employee, customer order, suborder.
	 * 
	 * @return List<Employeeorder> 
	 */
	public List<Employeeorder> getSortedEmployeeorders() {
		List<Employeeorder> employeeorders = getSession().createQuery("from Employeeorder").list();
		Collections.sort(employeeorders, employeeOrderComparator);
		return employeeorders;
	}
	
	/**
	 * Calls {@link EmployeeorderDAO#save(Employeeorder, Employee)} with {@link Employee} = null.
	 * @param eo
	 */
	public void save(Employeeorder eo) {
		save(eo, null);
	}
	
	/**
	 * Saves the given Employeeorder and sets creation-/update-user and creation-/update-date.
	 * 
	 * @param Employeeorder eo
	 */
	public void save(Employeeorder eo, Employee loginEmployee) {
		if (loginEmployee == null) {
			throw new RuntimeException("the login-user must be passed to the db");
		}
		Session session = getSession();
		java.util.Date creationDate = eo.getCreated();
		if (creationDate == null) {
			eo.setCreated(new java.util.Date());
			eo.setCreatedby(loginEmployee.getSign());
		} else {
			eo.setLastupdate(new java.util.Date());
			eo.setLastupdatedby(loginEmployee.getSign());
			Integer updateCounter = eo.getUpdatecounter();
			updateCounter = (updateCounter == null) ? 1 : updateCounter +1;
			eo.setUpdatecounter(updateCounter);
		}
		session.saveOrUpdate(eo);
		session.flush();
	}

	/**
	 * Deletes the given employee order.
	 * 
	 * @param long eoId
	 * 
	 * @return boolean
	 */
	public boolean deleteEmployeeorderById(long eoId) {
		List<Employeeorder> allEmployeeorders = getEmployeeorders();
		Employeeorder eoToDelete = getEmployeeorderById(eoId);
		boolean eoDeleted = false;
		
		for (Iterator iter = allEmployeeorders.iterator(); iter.hasNext();) {
			Employeeorder eo = (Employeeorder) iter.next();
			if(eo.getId() == eoToDelete.getId()) {
				// check if related status reports exist - if so, no deletion possible				
				// TODO as soon as table STATUSREPORT is available...
				boolean deleteOk = true;
				
				if (deleteOk) {
					Session session = getSession();
					session.delete(eoToDelete);
					session.flush();
					eoDeleted = true;
				}
				break;
			}
		}
		
		return eoDeleted;
	}
}
