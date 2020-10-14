package org.tb.persistence;

import org.hibernate.Session;
import org.tb.bdom.Customer;
import org.tb.bdom.Employee;

import java.util.List;

/**
 * DAO class for 'Customer'
 *
 * @author oda
 */
public class CustomerDAO extends AbstractDAO {

    /**
     * Get a list of all Customers ordered by name.
     *
     * @return List<Customer>
     */
    @SuppressWarnings("unchecked")
    public List<Customer> getCustomers() {
        return getSession().createQuery("from Customer order by name asc").list();
    }

    /**
     * Get a list of all Customers ordered by name.
     *
     * @return List<Customer>
     */
    @SuppressWarnings("unchecked")
    public List<Customer> getCustomersByFilter(String filter) {
        if (filter == null || filter.trim().equals("")) {
            return getSession().createQuery("from Customer order by name asc").list();
        } else {
            filter = "%" + filter.toUpperCase() + "%";
            return getSession().createQuery("from Customer where " +
                    "upper(id) like ? " +
                    "or upper(name) like ? " +
                    "or upper(address) like ? " +
                    "or upper(shortname) like ? " +
                    "order by name asc")
                    .setString(0, filter)
                    .setString(1, filter)
                    .setString(2, filter)
                    .setString(3, filter).list();
        }
    }

    /**
     * Get a list of all Customers ordered by short name.
     *
     * @return List<Customer>
     */
    @SuppressWarnings("unchecked")
    public List<Customer> getCustomersOrderedByShortName() {
        return getSession().createQuery("from Customer order by shortname asc").list();
    }

    /**
     * Gets the customer for the given id.
     *
     * @param long id
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
//	public Customer getCustomerBySign(String name) {
//		Customer cu = (Customer) getSession().createQuery("from Customer c where c.name = ?").setString(0, name).uniqueResult();
//		return cu;
//	}

    /**
     * Calls {@link CustomerDAO#save(Customer, Employee)} with {@link Employee} = null.
     *
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
            updateCounter = (updateCounter == null) ? 1 : updateCounter + 1;
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
     * @return boolean
     */
    public boolean deleteCustomerById(long cuId) {
        Customer cu = getCustomerById(cuId);

        if (cu != null) {
            // check if related customerorders exist - if so, no deletion possible
            if (cu.getCustomerorders() != null && !cu.getCustomerorders().isEmpty()) return false;

            Session session = getSession();
            session.delete(cu);
            session.flush();
            return true;
        }

        return false;
    }
}
