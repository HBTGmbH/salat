package org.tb.persistence;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import org.hibernate.Session;
import org.springframework.orm.hibernate3.support.HibernateDaoSupport;
import org.tb.bdom.Customerorder;
import org.tb.bdom.Employee;
import org.tb.bdom.Employeeorder;
import org.tb.bdom.Suborder;
import org.tb.bdom.comparators.CustomerOrderComparator;

public class CustomerorderDAO extends HibernateDaoSupport {

	private SuborderDAO suborderDAO;
	
	/**
	 * DAO class for 'Customerorder'
	 * 
	 * @author oda
	 *
	 */
	public void setSuborderDAO(SuborderDAO suborderDAO) {
		this.suborderDAO = suborderDAO;
	}
	
	/**
	 * Gets the customerorder for the given id.
	 * 
	 * @param long id
	 * 
	 * @return Customerorder
	 */
	public Customerorder getCustomerorderById(long id) {
		return (Customerorder) getSession().createQuery("from Customerorder co where co.id = ?").setLong(0, id).uniqueResult();
	}
	
	/**
	 * Gets the customerorder for the given sign.
	 * 
	 * @param String sign
	 * 
	 * @return Customerorder
	 */
	public Customerorder getCustomerorderBySign(String sign) {
		Customerorder co = (Customerorder) getSession().createQuery("from Customerorder c where c.sign = ?").setString(0, sign).uniqueResult();
		return co;
	}
	
	/**
	 * Get a list of all Customerorders ordered by their sign.
	 * 
	 * 
	 * @return
	 */
	public List<Customerorder> getCustomerorders() {
		return getSession().createQuery("from Customerorder order by sign").list();
	}

	/**
	 * Gets a list of all Customerorders by employee contract id.
	 * 
	 * @param long contractId
	 * 
	 * @return
	 */
	public List<Customerorder> getCustomerordersByEmployeeContractId(long contractId) {

		List<Employeeorder> employeeOrders = 
			getSession().createQuery("from Employeeorder e where e.employeecontract.id = ? order by suborder.customerorder.sign").setLong(0, contractId).list();

		List<Suborder> allSuborders = new ArrayList();
		for (Iterator iter = employeeOrders.iterator(); iter.hasNext();) {
			Employeeorder eo = (Employeeorder) iter.next();			
			Suborder so = (Suborder) getSession().createQuery("from Suborder s where s.id = ? order by customerorder.sign").setLong(0, eo.getSuborder().getId()).uniqueResult();
			allSuborders.add(so);
		}

		List<Suborder> suborders = suborderDAO.getSubordersByEmployeeContractId(contractId);
		List<Customerorder> allCustomerorders = new ArrayList();
		for (Iterator iter = suborders.iterator(); iter.hasNext();) {
			Suborder so = (Suborder) iter.next();			
			Customerorder co = (Customerorder) getSession().createQuery("from Customerorder c where c.id = ?").setLong(0, so.getCustomerorder().getId()).uniqueResult();
			// check if order was already added to list with other suborder
			boolean inList = false;
			for (Iterator iter2 = allCustomerorders.iterator(); iter2.hasNext();) {
				Customerorder coInList = (Customerorder) iter2.next();	
				if (coInList.getId() == co.getId()) {
					inList = true;
					break;
				}
			}
			if (!inList) allCustomerorders.add(co);
			//allCustomerorders.add(co);
		}
		Collections.sort(allCustomerorders, new CustomerOrderComparator());
		return allCustomerorders;
	}
	
	/**
	 * Calls {@link CustomerorderDAO#save(Customerorder, Employee)} with {@link Employee} = null.
	 * @param co
	 */
	public void save(Customerorder co) {
		save(co, null);
	}
	
	/**
	 * Saves the given order and sets creation-/update-user and creation-/update-date.
	 * 
	 * @param Customerorder co
	 * 
	 */
	public void save(Customerorder co, Employee loginEmployee) {
		if (loginEmployee == null) {
			throw new RuntimeException("the login-user must be passed to the db");
		}
		Session session = getSession();
		java.util.Date creationDate = co.getCreated();
		if (creationDate == null) {
			co.setCreated(new java.util.Date());
			co.setCreatedby(loginEmployee.getSign());
		} else {
			co.setLastupdate(new java.util.Date());
			co.setLastupdatedby(loginEmployee.getSign());
			Integer updateCounter = co.getUpdatecounter();
			updateCounter = (updateCounter == null) ? 1 : updateCounter +1;
			co.setUpdatecounter(updateCounter);
		}
		session.saveOrUpdate(co);
		session.flush();
		session.clear();
	}
	
	/**
	 * Deletes the given customer order.
	 * 
	 * @param long coId
	 * 
	 * @return boolean
	 */
	public boolean deleteCustomerorderById(long coId) {
		List<Customerorder> allCustomerorders = getCustomerorders();
		Customerorder coToDelete = getCustomerorderById(coId);
		boolean coDeleted = false;
		
		for (Iterator iter = allCustomerorders.iterator(); iter.hasNext();) {
			Customerorder co = (Customerorder) iter.next();
			if(co.getId() == coToDelete.getId()) {
				// check if related suborders exist - if so, no deletion possible
				boolean deleteOk = true;
				List<Suborder> allSuborders = suborderDAO.getSuborders();
				for (Iterator iter2 = allSuborders.iterator(); iter2.hasNext();) {
					Suborder so = (Suborder) iter2.next();
					if (so.getCustomerorder().getId() == coToDelete.getId()) {
						deleteOk = false;
						break;
					}
				}
				
				if (deleteOk) {
					Session session = getSession();
					session.delete(coToDelete);
					session.flush();
					coDeleted = true;
				}
				break;
			}
		}
		
		return coDeleted;
	}
	
}
