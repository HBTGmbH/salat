package org.tb.persistence;

import java.util.Iterator;
import java.util.List;

import org.hibernate.Session;
import org.springframework.orm.hibernate3.support.HibernateDaoSupport;
import org.tb.bdom.Employeeorder;

/**
 * DAO class for 'Employeeorder'
 * 
 * @author oda
 *
 */
public class EmployeeorderDAO extends HibernateDaoSupport {

	
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
	 * Gets the customerorder for the given sign.
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
	 * Get a list of all Employeeorders ordered by their sign.
	 * 
	 * @return List<Employeeorder> 
	 */
	public List<Employeeorder> getEmployeeorders() {
		return getSession().createQuery("from Employeeorder order by sign").list();
	}

	
	/**
	 * Saves the given Employeeorder.
	 * 
	 * @param Employeeorder eo
	 */
	public void save(Employeeorder eo) {
		Session session = getSession();
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
