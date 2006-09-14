package org.tb.persistence;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.hibernate.Session;
import org.springframework.orm.hibernate3.support.HibernateDaoSupport;
import org.tb.bdom.Employeeorder;
import org.tb.bdom.Suborder;
import org.tb.bdom.Timereport;

/**
 * DAO class for 'Suborder'
 * 
 * @author oda
 *
 */
public class SuborderDAO extends HibernateDaoSupport {

	private EmployeeorderDAO employeeorderDAO;
	private TimereportDAO timereportDAO;
		
	public void setEmployeeorderDAO(EmployeeorderDAO employeeorderDAO) {
		this.employeeorderDAO = employeeorderDAO;
	}

	public void setTimereportDAO(TimereportDAO timereportDAO) {
		this.timereportDAO = timereportDAO;
	}

	/**
	 * Gets the suborder for the given id.
	 * 
	 * @param long id
	 * 
	 * @return Suborder
	 */
	public Suborder getSuborderById(long id) {
		return (Suborder) getSession().createQuery("from Suborder so where so.id = ?").setLong(0, id).uniqueResult();
	}
	
	/**
	 * Gets a list of Suborders by employee contract id.
	 * 
	 * @param long contractId
	 * 
	 * @return List<Suborder>
	 */
	public List<Suborder> getSubordersByEmployeeContractId(long contractId) {

		List<Employeeorder> employeeOrders = 
			getSession().createQuery("from Employeeorder e where e.employeecontract.id = ? order by sign").setLong(0, contractId).list();
		
		List<Suborder> allSuborders = new ArrayList();
		for (Iterator iter = employeeOrders.iterator(); iter.hasNext();) {
			Employeeorder eo = (Employeeorder) iter.next();			
			Suborder so = (Suborder) getSession().createQuery("from Suborder s where s.id = ?").setLong(0, eo.getSuborder().getId()).uniqueResult();

			allSuborders.add(so);
		}
		
		return allSuborders;
	}
	
	/**
	 * Gets a list of Suborders by employee contract id AND customerorder.
	 * 
	 * @param long contractId
	 * @param long coId
	 * 
	 * @return List<Suborder>
	 */
	public List<Suborder> getSubordersByEmployeeContractIdAndCustomerorderId(long contractId, long coId) {

		List<Suborder> employeeSpecificSuborders = getSubordersByEmployeeContractId(contractId);
		
		List<Suborder> allSuborders = new ArrayList();
		for (Iterator iter = employeeSpecificSuborders.iterator(); iter.hasNext();) {
			Suborder so = (Suborder) iter.next();			
			if (so.getCustomerorder().getId() == coId) {
				allSuborders.add(so);
			}
		}

		return allSuborders;
	}
	
	/**
	 * Get a list of all Suborders ordered by their sign.
	 * 
	 * @return List<Suborder>
	 */
	public List<Suborder> getSuborders() {
		return getSession().createQuery("from Suborder order by sign").list();
	}

	
	/**
	 * Saves the given suborder.
	 * 
	 * @param Suborder so
	 */
	public void save(Suborder so) {
		Session session = getSession();
		session.saveOrUpdate(so);
		session.flush();
	}

	/**
	 * Deletes the given suborder.
	 * 
	 * @param long soId
	 * 
	 * @return boolean
	 */
	public boolean deleteSuborderById(long soId) {
		List<Suborder> allSuborders = getSuborders();
		Suborder soToDelete = getSuborderById(soId);
		boolean soDeleted = false;
		
		for (Iterator iter = allSuborders.iterator(); iter.hasNext();) {
			Suborder so = (Suborder) iter.next();
			if(so.getId() == soToDelete.getId()) {
				// check if related timereports or employee orders exist - if so, no deletion possible
				boolean deleteOk = true;
				List<Employeeorder> allEmployeeorders = employeeorderDAO.getEmployeeorders();
				for (Iterator iter2 = allEmployeeorders.iterator(); iter2.hasNext();) {
					Employeeorder eo = (Employeeorder) iter2.next();
					if (eo.getSuborder().getId() == soToDelete.getId()) {
						deleteOk = false;
						break;
					}
				}
				
				if (deleteOk) {
					List<Timereport> allTimereports = timereportDAO.getTimereports();
					for (Iterator iter2 = allTimereports.iterator(); iter2.hasNext();) {
						Timereport tr = (Timereport) iter2.next();
						if ((tr.getSuborder() != null) && (tr.getSuborder().getId() == soToDelete.getId())) {
							deleteOk = false;
							break;
						}
					}
				}
				
				if (deleteOk) {
					Session session = getSession();
					session.delete(soToDelete);
					session.flush();
					soDeleted = true;
				}
				break;
			}
		}
		
		return soDeleted;
	}
	
}
