package org.tb.persistence;

import java.util.Iterator;
import java.util.List;

import org.hibernate.Session;
import org.springframework.orm.hibernate3.support.HibernateDaoSupport;
import org.tb.bdom.Customer;
import org.tb.bdom.Customerorder;
import org.tb.bdom.Employee;

/**
 * DAO class for 'Customer'
 * 
 * @author oda
 *
 */
public class CustomerDAO extends HibernateDaoSupport {

	private CustomerorderDAO customerorderDAO;
	
	public void setCustomerorderDAO(CustomerorderDAO customerorderDAO) {
		this.customerorderDAO = customerorderDAO;
	}

	/**
	 * Get a list of all Customers ordered by name.
	 * 
	 * @return List<Customer>
	 */
	public List<Customer> getCustomers() {
		return getSession().createQuery("from Customer order by name asc").list();
	}
	
	/**
	 * Gets the customer for the given id.
	 * 
	 * @param long id
	 * 
	 * @return Customer
	 */
	public Customer getCustomerById(long id) {
		return (Customer) getSession().createQuery("from Customer cu where cu.id = ?").setLong(0, id).uniqueResult();
	}
	
	/**
	 * Gets the customer for the given name.
	 * 
	 * @param String name
	 * 
	 * @return Customer
	 */
	public Customer getCustomerBySign(String name) {
		Customer cu = (Customer) getSession().createQuery("from Customer c where c.name = ?").setString(0, name).uniqueResult();
		return cu;
	}
	
	/**
	 * Calls {@link CustomerDAO#save(Customer, Employee)} with {@link Employee} = null.
	 * @param cu
	 */
	public void save(Customer cu) {
		save(cu, null);
	}
	
	
	/**
	 * Saves the given customer and sets creation-/update-user and creation-/update-date.
	 * 
	 * @param Customer cu
	 */
	public void save(Customer cu, Employee loginEmployee) {
		if (loginEmployee == null) {
			throw new RuntimeException("the login-user must be passed to the db");
		}
		Session session = getSession();
		java.util.Date creationDate = cu.getCreated();
		if (creationDate == null) {
			cu.setCreated(new java.util.Date());
			cu.setCreatedby(loginEmployee.getSign());
		} else {
			cu.setLastupdate(new java.util.Date());
			cu.setLastupdatedby(loginEmployee.getSign());
			Integer updateCounter = cu.getUpdatecounter();
			updateCounter = (updateCounter == null) ? 1 : updateCounter +1;
			cu.setUpdatecounter(updateCounter);
		}
		session.saveOrUpdate(cu);
		session.flush();
		session.clear();
	}

	/**
	 * Deletes the given customer .
	 * 
	 * @param long cuId
	 * 
	 * @return boolean
	 */
	public boolean deleteCustomerById(long cuId) {
		List<Customer> allCustomers = getCustomers();
		Customer cuToDelete = getCustomerById(cuId);
		boolean cuDeleted = false;
		
		for (Iterator iter = allCustomers.iterator(); iter.hasNext();) {
			Customer cu = (Customer) iter.next();
			if(cu.getId() == cuToDelete.getId()) {
				// check if related customerorders exist - if so, no deletion possible
				boolean deleteOk = true;
				List<Customerorder> allCustomerorders = customerorderDAO.getCustomerorders();
				for (Iterator iter2 = allCustomerorders.iterator(); iter2.hasNext();) {
					Customerorder co = (Customerorder) iter2.next();
					if (co.getCustomer().getId() == cuToDelete.getId()) {
						deleteOk = false;
						break;
					}
				}
				
				if (deleteOk) {
					Session session = getSession();
					session.delete(cuToDelete);
					session.flush();
					cuDeleted = true;
				}
				break;
			}
		}
		
		return cuDeleted;
	}
}
