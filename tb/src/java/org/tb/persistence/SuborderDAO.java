package org.tb.persistence;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.Iterator;
import java.util.List;

import org.hibernate.Session;
import org.springframework.orm.hibernate3.support.HibernateDaoSupport;
import org.tb.bdom.Customerorder;
import org.tb.bdom.Employee;
import org.tb.bdom.Employeeorder;
import org.tb.bdom.Suborder;
import org.tb.bdom.Timereport;
import org.tb.bdom.comparators.SubOrderComparator;
import org.tb.logging.TbLogger;

/**
 * DAO class for 'Suborder'
 * 
 * @author oda
 *
 */
public class SuborderDAO extends HibernateDaoSupport {

	private EmployeeorderDAO employeeorderDAO;
	private TimereportDAO timereportDAO;
	private CustomerorderDAO customerorderDAO;
	private SuborderDAO suborderDAO;
	public void setCustomerorderDAO(CustomerorderDAO customerorderDAO) {
		this.customerorderDAO = customerorderDAO;
	}
		
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
			getSession().createQuery("from Employeeorder e where e.employeecontract.id = ? order by sign asc, suborder.sign asc").setLong(0, contractId).list();
		
		List<Suborder> allSuborders = new ArrayList();
		for (Iterator iter = employeeOrders.iterator(); iter.hasNext();) {
			Employeeorder eo = (Employeeorder) iter.next();			
			Suborder so = (Suborder) getSession().createQuery("from Suborder s where s.id = ? ").setLong(0, eo.getSuborder().getId()).uniqueResult();

			allSuborders.add(so);
		}
		Collections.sort(allSuborders, new SubOrderComparator());
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
		
		List<Suborder> allSuborders = new ArrayList<Suborder>();
		for (Iterator iter = employeeSpecificSuborders.iterator(); iter.hasNext();) {
			Suborder so = (Suborder) iter.next();			
			if (so.getCustomerorder().getId() == coId) {
				allSuborders.add(so);
			}
		}
		Collections.sort(allSuborders, new SubOrderComparator());
		return allSuborders;
	}
	
	public List<Suborder> getSubordersByEmployeeContractIdAndCustomerorderIdWithValidEmployeeOrders(long ecId, long coId, Date date) {
		return getSession().createSQLQuery("select distinct {so.*} from suborder so, employeeorder eo " +
				"where so.id = eo.suborder_id " +
				"and eo.employeecontract_id = ?" +
				"and so.customerorder_id = ?" +
				"and eo.fromdate <= ? " +
				"and (eo.untildate is null " +
					"or eo.untildate >= ?) " +
				"order by so.sign asc, so.description")
				.addEntity("so", Suborder.class)
				.setLong(0, ecId)
				.setLong(1, coId)
				.setDate(2, date)
				.setDate(3, date)
				.list();
	}
	
	
	/**
	 * Gets a list of Suborders by customer order id.
	 * 
	 * @param long customerorderId
	 * 
	 * @return List<Suborder>
	 */
	public List<Suborder> getSubordersByCustomerorderId(long customerorderId) {
		return getSession().createQuery("from Suborder s where s.customerorder.id = ? order by sign").setLong(0, customerorderId).list();
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
	 * Get a list of all suborders fitting to the given filters ordered by their sign.
	 * 
	 * 
	 * @return
	 */
	public List<Suborder> getSubordersByFilters(Boolean showInvalid,String filter,Long customerOrderId) {
		List<Suborder> suborders = new ArrayList<Suborder>();
		if (showInvalid == null || !showInvalid) {
			Date now = new Date();
			if (filter == null || filter.trim().equals("")) {
				if (customerOrderId == null || customerOrderId == -1) {
					// case 1
					suborders = getSession().createQuery("from Suborder s where " +
							"(fromDate <= ? " +
							"or (fromDate = null " +
							"and s.customerorder.fromDate <= ? ))" +
							"and (untilDate >= ? " +
							"or (untilDate = null " +
							"and (s.customerorder.untilDate = null " +
							"or s.customerorder.untilDate >= ?))) " +	
							"order by s.customerorder.sign ,sign")
							.setDate(0, now)
							.setDate(1, now)
							.setDate(2, now)
							.setDate(3, now).list();
				} else {
					// case 2
					suborders = getSession().createQuery("from Suborder s where " +
							"s.customerorder.id = ? " +
							"and (fromDate <= ? " +
							"or (fromDate = null " +
							"and s.customerorder.fromDate <= ? ))" +
							"and (untilDate >= ? " +
							"or (untilDate = null " +
							"and (s.customerorder.untilDate = null " +
							"or s.customerorder.untilDate >= ?))) " +	
							"order by s.customerorder.sign ,sign")
							.setLong(0, customerOrderId)
							.setDate(1, now)
							.setDate(2, now)
							.setDate(3, now)
							.setDate(4, now).list();
				}
			} else {
				if (customerOrderId == null || customerOrderId == -1) {
					// case 3
					suborders = getSession().createQuery("from Suborder s where (" +
							"upper(sign) like ? " +
							"or upper(description) like ? " +
							"or upper(s.customerorder.sign) like ? " +
							"or upper(s.customerorder.description) like ? " +
							"or upper(shortdescription) like ?  " +
							"or upper(s.customerorder.shortdescription) like ? " +
							"or upper(hourly_rate) like ?) " +
							"and (fromDate <= ? " +
							"or (fromDate = null " +
							"and s.customerorder.fromDate <= ? ))" +
							"and (untilDate >= ? " +
							"or (untilDate = null " +
							"and (s.customerorder.untilDate = null " +
							"or s.customerorder.untilDate >= ?))) " +	
							"order by s.customerorder.sign ,sign")
							.setString(0, filter)
							.setString(1, filter)
							.setString(2, filter)
							.setString(3, filter)
							.setString(4, filter)
							.setString(5, filter)
							.setString(6, filter)
							.setDate(7, now)
							.setDate(8, now)
							.setDate(9, now)
							.setDate(10, now).list();
				} else {
					// case 4
					suborders = getSession().createQuery("from Suborder s where " +
							"s.customerorder.id = ? " +
							"and (upper(sign) like ? " +
							"or upper(description) like ? " +
							"or upper(s.customerorder.sign) like ? " +
							"or upper(s.customerorder.description) like ? " +
							"or upper(shortdescription) like ?  " +
							"or upper(s.customerorder.shortdescription) like ? " +
							"or upper(hourly_rate) like ?) " +
							"and (fromDate <= ? " +
							"or (fromDate = null " +
							"and s.customerorder.fromDate <= ? ))" +
							"and (untilDate >= ? " +
							"or (untilDate = null " +
							"and (s.customerorder.untilDate = null " +
							"or s.customerorder.untilDate >= ?))) " +	
							"order by s.customerorder.sign ,sign")
							.setLong(0, customerOrderId)
							.setString(1, filter)
							.setString(2, filter)
							.setString(3, filter)
							.setString(4, filter)
							.setString(5, filter)
							.setString(6, filter)
							.setString(7, filter)
							.setDate(8, now)
							.setDate(9, now)
							.setDate(10, now)
							.setDate(11, now).list();
				}
			}	
		} else {
			if (filter == null || filter.trim().equals("")) {
				if (customerOrderId == null || customerOrderId == -1) {
					// case 5
					suborders = getSession().createQuery("from Suborder s " +
							"order by s.customerorder.sign ,sign")
							.list();
				} else {
					// case 6
					suborders = getSession().createQuery("from Suborder s where " +
							"s.customerorder.id = ? " +
							"order by s.customerorder.sign ,sign")
							.setLong(0, customerOrderId).list();
				}
			} else {
				if (customerOrderId == null || customerOrderId == -1) {
					// case 7
					suborders = getSession().createQuery("from Suborder s where " +
							"upper(sign) like ? " +
							"or upper(description) like ? " +
							"or upper(s.customerorder.sign) like ? " +
							"or upper(s.customerorder.description) like ? " +
							"or upper(shortdescription) like ?  " +
							"or upper(s.customerorder.shortdescription) like ? " +
							"or upper(hourly_rate) like ? " +
							"order by s.customerorder.sign ,sign")
							.setString(0, filter)
							.setString(1, filter)
							.setString(2, filter)
							.setString(3, filter)
							.setString(4, filter)
							.setString(5, filter)
							.setString(6, filter).list();
				} else {
					// case 8
					suborders = getSession().createQuery("from Suborder s where " +
							"s.customerorder.id = ? " +
							"and (upper(sign) like ? " +
							"or upper(description) like ? " +
							"or upper(s.customerorder.sign) like ? " +
							"or upper(s.customerorder.description) like ? " +
							"or upper(shortdescription) like ?  " +
							"or upper(s.customerorder.shortdescription) like ? " +
							"or upper(hourly_rate) like ?) " +
							"order by s.customerorder.sign ,sign")
							.setLong(0, customerOrderId)
							.setString(1, filter)
							.setString(2, filter)
							.setString(3, filter)
							.setString(4, filter)
							.setString(5, filter)
							.setString(6, filter)
							.setString(7, filter).list();
				}
			}
		}
		return suborders;
	}
	
	
	/**
	 * Get a list of all children of the suborder associated to the given soId ordered by their sign.
	 * 
	 * @return List<Suborder>
	 */
	public List<Suborder> getSuborderChildren(long soId) {
		return getSession().createQuery("from Suborder so where so.suborder.id = ? order by sign").setLong(0, soId).list();
	}
	
	
	/**
	 * Get a list of all Suborders ordered by the sign of {@link Customerorder} they are associated to.
	 * 
	 * @return
	 */
	public List<Suborder> getSubordersOrderedByCustomerorder() {
		List<Customerorder> customerorders = customerorderDAO.getCustomerorders();
		List<Suborder> suborders = new ArrayList<Suborder>();
		Customerorder customerorder;
		Iterator it = customerorders.iterator();
		while (it.hasNext()) {
			customerorder = (Customerorder) it.next();
			long customerorderId = customerorder.getId();
			suborders.addAll(getSubordersByCustomerorderId(customerorderId));
		}
		return suborders;
	}
	
	public List<Suborder> getSubordersByFilter(String filter) {
		return getSession().createQuery("from Suborder s where upper(sign) like ? or upper(description) like ? or upper(s.customerorder.sign) like ? or upper(s.customerorder.description) like ? or upper(shortdescription) like ?  or upper(s.customerorder.shortdescription) like ? or upper(hourly_rate) like ? order by s.customerorder.sign ,sign").setString(0, filter).setString(1, filter).setString(2, filter).setString(3, filter).setString(4, filter).setString(5, filter).setString(6, filter).list();
	}
	
	/**
	 * 
	 * @return Returns all {@link Suborder}s where the standard flag is true and that did not end before today.
	 */
	public List<Suborder> getStandardSuborders() {
		Date date = new Date();
		SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
		String dateString = dateFormat.format(date);
		try {
			date = dateFormat.parse(dateString);
		} catch (ParseException e) {
			// absurd error
			throw new RuntimeException("horrible exception - this should never happen ;D");
		}
		return getSession().createQuery("from Suborder where standard = ? and (untilDate = null or untilDate >= ? ) order by sign").setBoolean(0, true).setDate(1, date).list();
	}
	
	/**
	 * Calls {@link SuborderDAO#save(Suborder, Employee)} with {@link Employee} = null.
	 * @param so
	 */
	public void save(Suborder so) {
		save(so, null);
	}
	
	/**
	 * Saves the given suborderand sets creation-/update-user and creation-/update-date.
	 * 
	 * @param Suborder so
	 */
	public void save(Suborder so, Employee loginEmployee) {
		if (loginEmployee == null) {
			throw new RuntimeException("the login-user must be passed to the db");
		}
		Session session = getSession();
		java.util.Date creationDate = so.getCreated();
		if (creationDate == null) {
			so.setCreated(new java.util.Date());
			so.setCreatedby(loginEmployee.getSign());
		} else {
			so.setLastupdate(new java.util.Date());
			so.setLastupdatedby(loginEmployee.getSign());
			Integer updateCounter = so.getUpdatecounter();
			updateCounter = (updateCounter == null) ? 1 : updateCounter +1;
			so.setUpdatecounter(updateCounter);
		}
		session.saveOrUpdate(so);
		session.flush();
		session.clear();
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
		List<Suborder> allSuborders2 = getSuborders();
		Suborder soToDelete = getSuborderById(soId);
		boolean soDeleted = false;
		
		if (soToDelete == null) {
			return soDeleted;
		}
		
		for (Iterator iter = allSuborders.iterator(); iter.hasNext();) {
			Suborder so = (Suborder) iter.next();
			if(so.getId() == soToDelete.getId()) {
				// check if related timereports, employee orders or suborders exist - if so, no deletion possible
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
					for (Iterator iter3 = allSuborders2.iterator(); iter3.hasNext();) {
						Suborder tr = (Suborder) iter3.next();
						if ((tr.getParentorder() != null) && (tr.getParentorder().getId() == soToDelete.getId())) {
							deleteOk = false;
							break;
						}
					}
				}
				
				
				if (deleteOk) {
					Session session = getSession();
					session.delete(soToDelete);
					try{
						session.flush();
					}catch(Throwable th){}
					TbLogger.debug(SuborderDAO.class.toString(),"SuborderDAO.deleteSuborderById - deleted object " + soToDelete + " and flushed!");
					soDeleted = true;
				}
				break;
			}
		}
		
		return soDeleted;
	}
	
}
