package org.tb.persistence;

import org.apache.commons.lang.StringUtils;
import org.hibernate.Query;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.tb.bdom.Customerorder;
import org.tb.bdom.Employee;
import org.tb.bdom.ProjectID;
import org.tb.bdom.Suborder;
import org.tb.bdom.comparators.CustomerOrderComparator;

import java.util.*;
import java.util.Map.Entry;

/**
 * DAO class for 'Customerorder'
 *
 * @author oda
 */
@Component
public class CustomerorderDAO extends AbstractDAO {

    private final SuborderDAO suborderDAO;
    private final ProjectIDDAO projectIDDAO;

    @Autowired
    public CustomerorderDAO(SessionFactory sessionFactory, SuborderDAO suborderDAO, ProjectIDDAO projectIDDAO) {
        super(sessionFactory);
        this.suborderDAO = suborderDAO;
        this.projectIDDAO = projectIDDAO;
    }

    /**
     * Gets the customerorder for the given id.
     */
    public Customerorder getCustomerorderById(long id) {
        return (Customerorder) getSession().createQuery("from Customerorder co where co.id = ?").setLong(0, id).uniqueResult();
    }

    /**
     * Gets the customerorder for the given sign.
     */
    public Customerorder getCustomerorderBySign(String sign) {
        return (Customerorder) getSession().createQuery("from Customerorder c where c.sign = ?").setString(0, sign).uniqueResult();
    }

    /**
     * Get a list of all Customerorders ordered by their sign.
     */
    @SuppressWarnings("unchecked")
    public List<Customerorder> getCustomerorders() {
        return getSession().createQuery("from Customerorder order by sign").list();
    }

    /**
     * Get a list of all vivible Customerorders ordered by their sign.
     */
    @SuppressWarnings("unchecked")
    public List<Customerorder> getVisibleCustomerorders() {
        Date now = new Date();
        return getSession().createQuery("from Customerorder where (hide = null or hide = false) or (fromDate <= ? and (untilDate = null or untilDate >= ? )) order by sign").setDate(0, now)
                .setDate(1, now).list();
    }

    private List<Customerorder> createQuery(Long customerId, String filter, Date fromDate, Date untilDate) {
        Map<String, Object> args = new HashMap<String, Object>();
        List<String> clauses = new ArrayList<String>();

        if (customerId != null) {
            clauses.add("(co.customer.id = :customerId)");
            args.put("customerId", customerId);
        }

        if (fromDate != null) {
            clauses.add("(fromDate <= :fromDate)");
            args.put("fromDate", fromDate);
        }

        if (untilDate != null) {
            clauses.add("(untilDate = null or untilDate >= :untilDate)");
            args.put("untilDate", untilDate);
        }

        if (filter != null) {
            clauses.add("(upper(sign) like :filter " +
                    "or upper(description) like :filter " +
                    "or upper(responsible_customer_contractually) like :filter " +
                    "or upper(responsible_customer_technical) like :filter " +
                    "or upper(order_customer) like :filter " +
                    "or upper(customer.name) like :filter " +
                    "or upper(customer.shortname) like :filter " +
                    "or upper(responsible_hbt.firstname) like :filter " +
                    "or upper(responsible_hbt.lastname) like :filter)");
            args.put("filter", filter);
        }

        StringBuilder sb = new StringBuilder("from Customerorder co ");
        if (!clauses.isEmpty()) {
            sb.append("where (");
            sb.append(StringUtils.join(clauses, " and "));
            sb.append(")");
        }
        sb.append(" order by sign");

        Query query = getSession().createQuery(sb.toString());
        for (Entry<String, Object> entry : args.entrySet()) {
            query = query.setParameter(entry.getKey(), entry.getValue());
        }

        @SuppressWarnings("unchecked")
        List<Customerorder> result = query.list();
        return result;
    }

    /**
     * Get a list of all Customerorders fitting to the given filters ordered by their sign.
     */
    public List<Customerorder> getCustomerordersByFilters(Boolean showInvalid, String filter, Long customerId) {
        Date now = (showInvalid == null || !showInvalid) ? new Date() : null;

        if (customerId != null && customerId == -1) customerId = null;

        boolean isFilter = filter != null && !filter.trim().isEmpty();
        if (isFilter) {
            filter = "%" + filter.toUpperCase() + "%";
        } else {
            filter = null;
        }

        return createQuery(customerId, filter, now, now);
    }

    /**
     * Returns a list of all {@link Customerorder}s, where the given {@link Employee} is responsible.
     */
    @SuppressWarnings("unchecked")
    public List<Customerorder> getCustomerOrdersByResponsibleEmployeeId(long responsibleHbtId) {
        return getSession().createQuery("from Customerorder where RESPONSIBLE_HBT_ID = ? order by sign").setLong(0, responsibleHbtId).list();
    }

    /**
     * Returns a list of all {@link Customerorder}s, where the given {@link Employee} is responsible and statusreports are neccesary.
     */
    @SuppressWarnings("unchecked")
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
     */
    @SuppressWarnings("unchecked")
    public List<Customerorder> getVisibleCustomerOrdersByResponsibleEmployeeId(long responsibleHbtId) {
        Date now = new Date();
        return getSession().createQuery("from Customerorder where RESPONSIBLE_HBT_ID = ? " +
                "and ((hide = null or hide = false) " +
                "or (fromDate <= ? and (untilDate = null or untilDate >= ? ))) " +
                "order by sign").setLong(0, responsibleHbtId).setDate(1, now).setDate(2, now).list();
    }

    /**
     * Gets a list of all Customerorders by employee contract id.
     */
    public List<Customerorder> getCustomerordersByEmployeeContractId(long contractId) {
        List<Suborder> suborders = suborderDAO.getSubordersByEmployeeContractId(contractId);
        List<Customerorder> allCustomerorders = new ArrayList<>();
        outer:
        for (Suborder so : suborders) {
            Customerorder co = so.getCustomerorder();
            // check if order was already added to list with other suborder
            for (Customerorder coInList : allCustomerorders) {
                if (coInList.getId() == co.getId()) {
                    continue outer;
                }
            }
            allCustomerorders.add(co);
        }
        allCustomerorders.sort(CustomerOrderComparator.INSTANCE);
        return allCustomerorders;
    }

    @SuppressWarnings("unchecked")
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
     */
    public void save(Customerorder co) {
        save(co, null);
    }

    /**
     * Saves the given order and sets creation-/update-user and creation-/update-date.
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
     */
    public boolean deleteCustomerorderById(long coId) {
        List<Customerorder> allCustomerorders = getCustomerorders();

        for (Customerorder co : allCustomerorders) {
            if (co.getId() == coId) {
                // check if related suborders exist - if so, no deletion possible
                List<Suborder> allSuborders = suborderDAO.getSuborders(false);
                for (Suborder so : allSuborders) {
                    if (so.getCustomerorder().getId() == coId) {
                        return false;
                    }
                }

                // check if related ProjectIDs exist - if so, no deletion possible
                List<ProjectID> projectIDs = projectIDDAO.getProjectIDsByCustomerorderID(coId);
                if (!projectIDs.isEmpty()) {
                    return false;
                }

                Session session = getSession();
                session.delete(co);
                session.flush();
                return true;
            }
        }

        return false;
    }

}
