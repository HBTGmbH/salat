package org.tb.persistence;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.hibernate.Session;
import org.springframework.orm.hibernate3.support.HibernateDaoSupport;
import org.tb.bdom.Customerorder;
import org.tb.bdom.Employeeorder;
import org.tb.bdom.Suborder;

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
			getSession().createQuery("from Employeeorder e where e.employeecontract.id = ? order by sign").setLong(0, contractId).list();

		List<Suborder> allSuborders = new ArrayList();
		for (Iterator iter = employeeOrders.iterator(); iter.hasNext();) {
			Employeeorder eo = (Employeeorder) iter.next();			
			Suborder so = (Suborder) getSession().createQuery("from Suborder s where s.id = ?").setLong(0, eo.getSuborder().getId()).uniqueResult();
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

		return allCustomerorders;
	}
		
	/**
	 * Saves the given order.
	 * 
	 * @param Customerorder co
	 * 
	 */
	public void save(Customerorder co) {
		Session session = getSession();
		session.saveOrUpdate(co);
		session.flush();
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
