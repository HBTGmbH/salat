package org.tb.persistence;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.commons.lang.StringUtils;
import org.hibernate.Query;
import org.hibernate.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tb.bdom.Customerorder;
import org.tb.bdom.Employee;
import org.tb.bdom.Employeecontract;
import org.tb.bdom.Employeeorder;
import org.tb.bdom.Suborder;
import org.tb.bdom.comparators.SubOrderComparator;

/**
 * DAO class for 'Suborder'
 * 
 * @author oda
 *
 */
public class SuborderDAO extends AbstractDAO {
	private static final Logger LOG = LoggerFactory.getLogger(SuborderDAO.class);
    
    /**
     * Gets the suborder for the given id.
     * 
     * @param long id
     * 
     * @return Suborder
     */
    public Suborder getSuborderById(long id) {
        //		return (Suborder) getSession().createQuery("from Suborder so where so.id = ?").setLong(0, id).uniqueResult();
        return (Suborder)getSession().get(Suborder.class, id);
    }
    
    public Suborder getSuborderBySign(String sign) {
        return (Suborder)getSession().createQuery("from Suborder so where so.sign = ?").setString(0, sign).uniqueResult();
    }
    
    /**
     * Gets a list of Suborders by employee contract id.
     * 
     * @param long contractId
     * 
     * @return List<Suborder>
     */
    public List<Suborder> getSubordersByEmployeeContractId(long contractId) {

		@SuppressWarnings("unchecked")
		List<Suborder> suborders = getSession()
				.createQuery("select e.suborder from Employeeorder e where e.employeecontract.id = ? order by e.suborder.sign asc, e.suborder.description asc")
				.setLong(0, contractId)
				.list();

		
		return suborders;
    }
    
    /**
     * Gets a list of Suborders by employee contract id AND customerorder.
     * @param onlyValid 
     * 
     * @param long contractId
     * @param long coId
     * 
     * @return List<Suborder>
     */
    public List<Suborder> getSubordersByEmployeeContractIdAndCustomerorderId(long contractId, long coId, boolean onlyValid) {
        List<Suborder> employeeSpecificSuborders = getSubordersByEmployeeContractId(contractId);
        if(!onlyValid) return employeeSpecificSuborders;
        
        List<Suborder> allSuborders = new ArrayList<Suborder>();
        for (Suborder so : employeeSpecificSuborders) {
            if (so.getCustomerorder().getId() == coId) {
            	if(!onlyValid || so.getCurrentlyValid()) {
            		allSuborders.add(so);
            	}
            }
        }
        
        Collections.sort(allSuborders, new SubOrderComparator());
        return allSuborders;
    }
    
    /**
     * Gets all {@link Suborder}s for the given employee, restricted to those that have
     * valid {@link Employeeorder}s.
     * 
     * @param ecId id of the employee's contract
     * @param date the date to check validity against
     * @return a distinct list of matching {@link Suborder}s
     */
    @SuppressWarnings("unchecked")
    public List<Suborder> getSubordersByEmployeeContractIdWithValidEmployeeOrders(long ecId, Date date) {
    	Session session = getSession();
        return session.createQuery("select distinct so from Employeeorder eo inner join eo.suborder so inner join so.customerorder co " +
                "where " +
                "eo.employeecontract = :employeecontract " +
                "and eo.fromDate <= :refDate " +
                "and (eo.untilDate is null " +
                "or eo.untilDate >= :refDate) " +
                "order by co.sign asc, so.sign asc")
                .setEntity("employeecontract", session.load(Employeecontract.class, ecId))
                .setDate("refDate", date)
                .list();
    }
    
    @SuppressWarnings("unchecked")
	public List<Suborder> getSubordersByEmployeeContractIdAndCustomerorderIdWithValidEmployeeOrders(long ecId, long coId, Date date) {
        Session session = getSession();
		return session.createQuery("select distinct so from Employeeorder eo inner join eo.suborder so inner join so.customerorder co " +
                "where " +
                "eo.employeecontract = :employeecontract " +
                "and so.customerorder = :customerorder " +
                "and eo.fromDate <= :refDate " +
                "and (eo.untilDate is null " +
                "or eo.untilDate >= :refDate) " +
                "order by so.sign asc, so.description")
                .setEntity("employeecontract", session.load(Employeecontract.class, ecId))
                .setEntity("customerorder", session.load(Customerorder.class, coId))
                .setDate("refDate", date)
                .list();
    }
    
    /**
     * Gets a list of Suborders by customer order id.
     * @param onlyValid 
     * 
     * @param long customerorderId
     * 
     * @return List<Suborder>
     */
    public List<Suborder> getSubordersByCustomerorderId(long customerorderId, boolean onlyValid) {
    	if(onlyValid) {
    		return getSubordersByCustomerorderId(customerorderId, new java.util.Date());
    	} else {
	        @SuppressWarnings("unchecked")
			List<Suborder> result = getSession()
				.createQuery("from Suborder s where s.customerorder.id = ? order by sign")
				.setLong(0, customerorderId)
				.setCacheable(true)
				.list();
	        return result;
    	}
    }
    
    /**
     * Gets a list of Suborders by customer order id.
     * @param onlyValid 
     * 
     * @param long customerorderId
     * 
     * @return List<Suborder>
     */
    public List<Suborder> getSubordersByCustomerorderId(long customerorderId, java.util.Date date) {
        @SuppressWarnings("unchecked")
        
		List<Suborder> result = getSession()
			.createQuery("from Suborder s where s.customerorder.id = :coId " +
	                "and s.fromDate <= :refDate " +
	                "and (s.untilDate is null " +
	                "or s.untilDate >= :refDate) " +
					"order by sign")
			.setParameter("coId", customerorderId)
			.setParameter("refDate", date)
			.setCacheable(true)
			.list();
        return result;
    }
    
    /**
     * Get a list of all Suborders ordered by their sign.
     * @param onlyValid return only valid suborders
     * 
     * @return List<Suborder>
     */
    public List<Suborder> getSuborders(boolean onlyValid) {
    	if(onlyValid) {
    		return getSuborders(new java.util.Date());
    	} else {
	    	
	        @SuppressWarnings("unchecked")
			List<Suborder> result = getSession().createQuery("from Suborder order by sign").list();
	        
	        return result;
    	}
    }
    
    /**
     * Get a list of all Suborders ordered by their sign.
     * @param onlyValid return only valid suborders
     * 
     * @return List<Suborder>
     */
    public List<Suborder> getSuborders(java.util.Date date) {
        @SuppressWarnings("unchecked")
		List<Suborder> result = getSession().createQuery("from Suborder " +
                "and s.fromDate <= :refDate " +
                "and (s.untilDate is null " +
                "or s.untilDate >= :refDate) " +
				"order by sign")
			.setParameter("refDate", date)
			.list();
        
        return result;
    }
    
    private List<Suborder> createSuborderQuery(Long customerOrderId, String filter, Date dateFrom, Date dateTill) {
    	Map<String, Object> args = new HashMap<String, Object>();
    	List<String> terms = new ArrayList<String>();

    	if(customerOrderId != null) {
    		terms.add("s.customerorder.id = :custormerOrderId");
            args.put("custormerOrderId", customerOrderId);
    	}
    	
    	if(filter != null) {
    		terms.add("(upper(sign) like :filter "
    				+ "or upper(description) like :filter "
					+ "or upper(s.customerorder.sign) like :filter "
					+ "or upper(s.customerorder.description) like :filter "
    				+ "or upper(shortdescription) like :filter "
    				+ "or upper(s.customerorder.shortdescription) like :filter "
    				+ "or upper(hourly_rate) like :filter)");
    		args.put("filter", filter);
    	}

    	if(dateFrom != null) {
	    	terms.add("(fromDate <= :fromDate "
	    			+ "or (fromDate = null "
	    			+ "and s.customerorder.fromDate <= :fromDate ))");
	    	args.put("fromDate", dateFrom);
    	}
    	
    	if(dateTill != null) {
	    	terms.add("(untilDate >= :untilDate "
	    			+ "or (untilDate = null "
	    			+ "and (s.customerorder.untilDate = null "
	    			+ "or s.customerorder.untilDate <= :untilDate )))");
	    	args.put("untilDate", dateTill);
    	}

    	StringBuilder sb = new StringBuilder();
    	sb.append("from Suborder s ");
    	if(!terms.isEmpty()) {
    		String termsStr = StringUtils.join(terms, " and ");
    		sb.append(" where (");
    		sb.append(termsStr);
    		sb.append(") ");
    	}
    	sb.append(" order by s.customerorder.sign ,sign");
    	
    	Query query = getSession().createQuery(sb.toString());
    	for(Entry<String, Object> entry : args.entrySet()) {
    		query = query.setParameter(entry.getKey(), entry.getValue());
    	}
    	
    	@SuppressWarnings("unchecked")
		List<Suborder> suborders = query.list();
    	return suborders;
    }
    
    /**
     * Get a list of all suborders fitting to the given filters ordered by their sign.
     * 
     * 
     * @return
     */
	public List<Suborder> getSubordersByFilters(Boolean showInvalid, String filter, Long customerOrderId) {
        boolean isFilter = filter != null && !filter.trim().isEmpty();
        if(isFilter) {
        	filter = "%" + filter.toUpperCase() + "%";
        } else {
        	filter = null;
        }
        if(customerOrderId != null && customerOrderId == -1) customerOrderId = null;
        
        Date now = showInvalid == null || !showInvalid ? new Date() : null;
        return createSuborderQuery(customerOrderId, filter, now, now);
    }
    
    /**
     * Get a list of all children of the suborder associated to the given soId ordered by their sign.
     * 
     * @return List<Suborder>
     */
    @SuppressWarnings("unchecked")
	public List<Suborder> getSuborderChildren(long soId) {
        return getSession().createQuery("from Suborder so where so.suborder.id = ? order by sign").setLong(0, soId).list();
    }
    
    /**
     * 
     * @return Returns all {@link Suborder}s where the standard flag is true and that did not end before today.
     */
    @SuppressWarnings("unchecked")
	public List<Suborder> getStandardSuborders() {
    	Calendar cal = Calendar.getInstance();
    	cal.set(Calendar.HOUR_OF_DAY, 0);
    	cal.set(Calendar.MINUTE, 0);
    	cal.set(Calendar.SECOND, 0);
    	cal.set(Calendar.MILLISECOND, 0);
        return getSession()
        		.createQuery("from Suborder where standard = ? and (untilDate = null or untilDate >= ? ) order by sign")
        		.setBoolean(0, true)
        		.setDate(1, cal.getTime())
        		.list();
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
            updateCounter = updateCounter == null ? 1 : updateCounter + 1;
            so.setUpdatecounter(updateCounter);
        }
        session.saveOrUpdate(so);
        session.flush();
        //session.clear(); nicht praktikabel da später benötigte objekte der session bekannt aber nicht mehr vorhanden sind. bei speicherproblemen evtl. session.evict()
    }
    
    /**
     * Deletes the given suborder.
     * 
     * @param long soId
     * 
     * @return boolean
     */
    public boolean deleteSuborderById(long soId) {
    	Suborder soToDelete = getSuborderById(soId);
        if (soToDelete == null) {
            return false;
        }
        
        // check if related timereports, employee orders, suborders or tickets exist - if so, no deletion possible
        if(soToDelete.getEmployeeorders() != null && !soToDelete.getEmployeeorders().isEmpty()) return false;
        
        if(soToDelete.getTimereports() != null && !soToDelete.getTimereports().isEmpty()) return false;

        if(soToDelete.getSuborders() != null && !soToDelete.getSuborders().isEmpty()) return false;
        
        if(soToDelete.getTickets() != null && !soToDelete.getTickets().isEmpty()) return false;
            
        Session session = getSession();
        session.delete(soToDelete);
        try {
            session.flush();
        } catch (Throwable th) {}
        LOG.debug("SuborderDAO.deleteSuborderById - deleted object {} and flushed!", soToDelete);
        return true;
    }
    
}
