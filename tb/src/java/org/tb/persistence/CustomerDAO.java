package org.tb.persistence;

import java.util.Iterator;
import java.util.List;

import org.hibernate.Session;
import org.springframework.orm.hibernate3.support.HibernateDaoSupport;
import org.tb.bdom.Customer;
import org.tb.bdom.Customerorder;

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
	 * Saves the given customer.
	 * 
	 * @param Customer cu
	 */
	public void save(Customer cu) {
		Session session = getSession();
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
