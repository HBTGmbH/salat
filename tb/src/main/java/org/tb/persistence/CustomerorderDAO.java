package org.tb.persistence;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import org.hibernate.Session;
import org.springframework.orm.hibernate3.support.HibernateDaoSupport;
import org.tb.bdom.Customerorder;
import org.tb.bdom.Employee;
import org.tb.bdom.Employeeorder;
import org.tb.bdom.ProjectID;
import org.tb.bdom.Suborder;
import org.tb.bdom.comparators.CustomerOrderComparator;

/**
	 * DAO class for 'Customerorder'
	 * 
	 * @author oda
	 *
	 */
public class CustomerorderDAO extends HibernateDaoSupport {
    
    private SuborderDAO suborderDAO;
    private ProjectIDDAO projectIDDAO;
    
    public void setSuborderDAO(SuborderDAO suborderDAO) {
        this.suborderDAO = suborderDAO;
    }
    
    public void setProjectIDDAO(ProjectIDDAO projectIDDAO) {
        this.projectIDDAO = projectIDDAO;
    }
    
    /**
     * Gets the customerorder for the given id.
     * 
     * @param long id
     * 
     * @return Customerorder
     */
    public Customerorder getCustomerorderById(long id) {
        return (Customerorder)getSession().createQuery("from Customerorder co where co.id = ?").setLong(0, id).uniqueResult();
    }
    
    /**
     * Gets the customerorder for the given sign.
     * 
     * @param String sign
     * 
     * @return Customerorder
     */
    public Customerorder getCustomerorderBySign(String sign) {
        Customerorder co = (Customerorder)getSession().createQuery("from Customerorder c where c.sign = ?").setString(0, sign).uniqueResult();
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
     * Get a list of all vivible Customerorders ordered by their sign.
     * 
     * 
     * @return
     */
    public List<Customerorder> getVisibleCustomerorders() {
        Date now = new Date();
        return getSession().createQuery("from Customerorder where (hide = null or hide = false) or (fromDate <= ? and (untilDate = null or untilDate >= ? )) order by sign").setDate(0, now)
                .setDate(1, now).list();
    }
    
    /**
     * Get a list of all Customerorders fitting to the given filters ordered by their sign.
     * 
     * 
     * @return
     */
    public List<Customerorder> getCustomerordersByFilters(Boolean showInvalid, String filter, Long customerId) {
        List<Customerorder> customerorders = new ArrayList<Customerorder>();
        Date now = new Date();
        
        if (showInvalid == null || !showInvalid) {
            if (filter == null || filter.trim().equals("")) {
                if (customerId == null || customerId == -1) {
                    // case 1
                    customerorders = getSession().createQuery(
                            "from Customerorder where fromdate <= ? " +
                                    "and (untildate = null or untildate >= ?) " +
                                    "order by sign").setDate(0, now).setDate(1, now).list();
                } else {
                    // case 2
                    customerorders = getSession().createQuery(
                            "from Customerorder co where co.customer.id = ? " +
                                    "and fromdate <= ? " +
                                    "and (untildate = null or untildate >= ?) " +
                                    "order by sign").setLong(0, customerId).setDate(1, now).setDate(2, now).list();
                }
            } else {
                if (customerId == null || customerId == -1) {
                    // case 3
                    customerorders = getSession().createQuery(
                            "from Customerorder where fromdate <= ? " +
                                    "and (untildate = null or untildate >= ?) " +
                                    "and (upper(sign) like ? " +
                                    "or upper(description) like ? " +
                                    "or upper(responsible_customer_contractually) like ? " +
                                    "or upper(responsible_customer_technical) like ? " +
                                    "or upper(order_customer) like ? " +
                                    "or upper(customer.name) like ? " +
                                    "or upper(customer.shortname) like ? " +
                                    "or upper(responsible_hbt.firstname) like ? " +
                                    "or upper(responsible_hbt.lastname) like ?)" +
                                    "order by sign").setDate(0, now).setDate(1, now).setString(2, filter).setString(3, filter)
                            .setString(4, filter).setString(5, filter).setString(6, filter).setString(7, filter)
                            .setString(8, filter).setString(9, filter).setString(10, filter).list();
                } else {
                    // case 4
                    customerorders = getSession().createQuery(
                            "from Customerorder co where co.customer.id = ? " +
                                    "and fromdate <= ? " +
                                    "and (untildate = null or untildate >= ?) " +
                                    "and (upper(sign) like ? " +
                                    "or upper(description) like ? " +
                                    "or upper(responsible_customer_contractually) like ? " +
                                    "or upper(responsible_customer_technical) like ? " +
                                    "or upper(order_customer) like ? " +
                                    "or upper(customer.name) like ? " +
                                    "or upper(customer.shortname) like ? " +
                                    "or upper(responsible_hbt.firstname) like ? " +
                                    "or upper(responsible_hbt.lastname) like ?)" +
                                    "order by sign").setLong(0, customerId).setDate(1, now).setDate(2, now).setString(3, filter)
                            .setString(4, filter).setString(5, filter).setString(6, filter).setString(7, filter)
                            .setString(8, filter).setString(9, filter).setString(10, filter).setString(11, filter).list();
                }
            }
        } else {
            if (filter == null || filter.trim().equals("")) {
                if (customerId == null || customerId == -1) {
                    // case 5
                    customerorders = getSession().createQuery(
                            "from Customerorder " +
                                    "order by sign").list();
                } else {
                    // case 6
                    customerorders = getSession().createQuery(
                            "from Customerorder co where co.customer.id = ? " +
                                    "order by sign").setLong(0, customerId).list();
                }
            } else {
                if (customerId == null || customerId == -1) {
                    // case 7
                    customerorders = getSession().createQuery(
                            "from Customerorder where " +
                                    "upper(sign) like ? " +
                                    "or upper(description) like ? " +
                                    "or upper(responsible_customer_contractually) like ? " +
                                    "or upper(responsible_customer_technical) like ? " +
                                    "or upper(order_customer) like ? " +
                                    "or upper(customer.name) like ? " +
                                    "or upper(customer.shortname) like ? " +
                                    "or upper(responsible_hbt.firstname) like ? " +
                                    "or upper(responsible_hbt.lastname) like ?" +
                                    "order by sign").setString(0, filter).setString(1, filter)
                            .setString(2, filter).setString(3, filter).setString(4, filter).setString(5, filter)
                            .setString(6, filter).setString(7, filter).setString(8, filter).list();
                } else {
                    // case 8
                    customerorders = getSession().createQuery(
                            "from Customerorder co where co.customer.id = ? " +
                                    "and (upper(sign) like ? " +
                                    "or upper(description) like ? " +
                                    "or upper(responsible_customer_contractually) like ? " +
                                    "or upper(responsible_customer_technical) like ? " +
                                    "or upper(order_customer) like ? " +
                                    "or upper(customer.name) like ? " +
                                    "or upper(customer.shortname) like ? " +
                                    "or upper(responsible_hbt.firstname) like ? " +
                                    "or upper(responsible_hbt.lastname) like ?)" +
                                    "order by sign").setLong(0, customerId).setString(1, filter)
                            .setString(2, filter).setString(3, filter).setString(4, filter).setString(5, filter)
                            .setString(6, filter).setString(7, filter).setString(8, filter).setString(9, filter).list();
                }
            }
        }
        
        return customerorders;
    }
    
    /**
     * Returns a list of all {@link Customerorder}s, where the given {@link Employee} is responsible.
     * 
     * @param responsibleHbtId
     * @return
     */
    public List<Customerorder> getCustomerOrdersByResponsibleEmployeeId(long responsibleHbtId) {
        return getSession().createQuery("from Customerorder where RESPONSIBLE_HBT_ID = ? order by sign").setLong(0, responsibleHbtId).list();
    }
    
    /**
     * Returns a list of all {@link Customerorder}s, where the given {@link Employee} is responsible and statusreports are neccesary.
     * 
     * @param responsibleHbtId
     * @return
     */
    public List<Customerorder> getCustomerOrdersByResponsibleEmployeeIdWithStatusReports(long responsibleHbtId) {
        return getSession().createQuery("from Customerorder " +
                "where RESPONSIBLE_HBT_ID = ? " +
                "and (statusreport = 4 " +
                "or statusreport = 6 " +
                "or statusreport = 12) " +
                "order by sign").setLong(0, responsibleHbtId).list();
    }
    
    /**
     * Returns a list of all {@link Customerorder}s, where the given {@link Employee} is responsible.
     * 
     * @param responsibleHbtId
     * @return
     */
    public List<Customerorder> getVisibleCustomerOrdersByResponsibleEmployeeId(long responsibleHbtId) {
        Date now = new Date();
        return getSession().createQuery("from Customerorder where RESPONSIBLE_HBT_ID = ? " +
                "and ((hide = null or hide = false) " +
                "or (fromDate <= ? and (untilDate = null or untilDate >= ? ))) " +
                "order by sign").setLong(0, responsibleHbtId).setDate(1, now).setDate(2, now).list();
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
        for (Object element : employeeOrders) {
            Employeeorder eo = (Employeeorder)element;
            Suborder so = (Suborder)getSession().createQuery("from Suborder s where s.id = ? order by customerorder.sign").setLong(0, eo.getSuborder().getId()).uniqueResult();
            allSuborders.add(so);
        }
        
        List<Suborder> suborders = suborderDAO.getSubordersByEmployeeContractId(contractId);
        List<Customerorder> allCustomerorders = new ArrayList();
        for (Object element : suborders) {
            Suborder so = (Suborder)element;
            Customerorder co = (Customerorder)getSession().createQuery("from Customerorder c where c.id = ?").setLong(0, so.getCustomerorder().getId()).uniqueResult();
            // check if order was already added to list with other suborder
            boolean inList = false;
            for (Object element2 : allCustomerorders) {
                Customerorder coInList = (Customerorder)element2;
                if (coInList.getId() == co.getId()) {
                    inList = true;
                    break;
                }
            }
            if (!inList) {
                allCustomerorders.add(co);
            }
        }
        Collections.sort(allCustomerorders, new CustomerOrderComparator());
        return allCustomerorders;
    }
    
    public List<Customerorder> getCustomerordersWithValidEmployeeOrders(long employeeContractId, Date date) {
        return getSession().createSQLQuery("select distinct {co.*} from customerorder co, employeeorder eo, suborder so " +
                "where so.id = eo.suborder_id " +
                "and co.id = so.customerorder_id " +
                "and eo.employeecontract_id = ? " +
                "and eo.fromdate <= ? " +
                "and (eo.untildate is null " +
                "or eo.untildate >= ?) " +
                "order by co.sign asc, co.description")
                .addEntity("co", Customerorder.class)
                .setLong(0, employeeContractId)
                .setDate(1, date)
                .setDate(2, date)
                .list();
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
            updateCounter = updateCounter == null ? 1 : updateCounter + 1;
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
        
        for (Object element : allCustomerorders) {
            Customerorder co = (Customerorder)element;
            if (co.getId() == coToDelete.getId()) {
                // check if related suborders exist - if so, no deletion possible
                boolean deleteOk = true;
                List<Suborder> allSuborders = suborderDAO.getSuborders();
                for (Object element2 : allSuborders) {
                    Suborder so = (Suborder)element2;
                    if (so.getCustomerorder().getId() == coToDelete.getId()) {
                        deleteOk = false;
                        break;
                    }
                }
                
                // check if related ProjectIDs exist - if so, no deletion possible
                List<ProjectID> projectIDs = projectIDDAO.getProjectIDsByCustomerorderID(coId);
                if (!projectIDs.isEmpty()) {
                    deleteOk = false;
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
