package org.tb.persistence;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.tb.GlobalConstants;
import org.tb.bdom.Customerorder;
import org.tb.bdom.Employee;
import org.tb.bdom.Employeecontract;
import org.tb.bdom.Suborder;
import org.tb.bdom.Timereport;


/**
 * DAO class for 'Timereport'
 * 
 * @author oda
 *
 */
public class TimereportDAO extends AbstractDAO {
    
    /**
     * Gets the timereport for the given id.
     * 
     * @param long id
     * 
     * @return Timereport
     */
    public Timereport getTimereportById(long id) {
        return (Timereport)getSession()
        		.createQuery("from Timereport t where t.id = ?")
        		.setLong(0, id)
        		.setCacheable(true)
        		.uniqueResult();
    }
    
    /**
     * Get a list of all Timereports.
     * 
     * @return List<Timereport>
     */
    @SuppressWarnings("unchecked")
    public List<Timereport> getTimereports() {
        return getSession()
        		.createQuery("from Timereport order by employeecontract.employee.sign asc, referenceday.refdate desc, sequencenumber asc")
        		.setCacheable(true)
        		.list();
    }
    
    /**
     * Get a list of all Timereports ordered by employee sign, customer order sign, suborder sign and refdate.
     * 
     * @return List<Timereport>
     */
    @SuppressWarnings("unchecked")
    public List<Timereport> getOrderedTimereports() {
        return getSession()
        		.createQuery("from Timereport order by employeecontract.employee.sign asc, suborder.customerorder.sign asc, suborder.sign asc, referenceday.refdate asc")
        		.setCacheable(true)
        		.list();
    }
    
    /**
     * Gets the sum of all duration minutes WITH considering the hours.
     * 
     * @param soId
     * @return
     */
    public long getTotalDurationMinutesForSuborder(long soId) {
        Object totalMinutes = getSession()
        		.createQuery("select sum(tr.durationminutes)+60*sum(tr.durationhours) from Timereport tr where tr.employeeorder.suborder.id = ?")
        		.setLong(0, soId)
        		.uniqueResult();
        return objectToLong(totalMinutes);
    }
    
    public long getTotalDurationMinutesForSuborderAndEmployeeContract(long soId, long ecId) {
        Object totalMinutes = getSession()
        		.createQuery("select sum(tr.durationminutes)+60*sum(tr.durationhours) from Timereport tr where tr.suborder.id = ? and tr.employeecontract.id = ?")
        		.setLong(0, soId)
        		.setLong(1, ecId)
        		.uniqueResult();
        return objectToLong(totalMinutes);
    }
    
    /**
     * Gets the sum of all duration minutes WITH considering the hours.
     * 
     * @param soId
     * @return
     */
    public long getTotalDurationMinutesForSuborders(List<Long> ids) {
    	if(ids == null || ids.isEmpty()) return 0;
    	
    	StringBuilder sb = new StringBuilder();
    	sb.append("select sum(tr.durationminutes)+60*sum(tr.durationhours) from Timereport tr where tr.employeeorder.suborder.id in (");
    	boolean isFirst = true;
    	for(Long id : ids) {
    		if(!isFirst) sb.append(",");
    		isFirst = false;
    		sb.append(id);
    	}
    	sb.append(")");
    	
        Object totalMinutes = getSession()
        		.createQuery(sb.toString())
        		.uniqueResult();
        return objectToLong(totalMinutes);
    }
    
    /**
     * Gets the sum of all duration minutes within a range of time WITH considering the hours.
     * 
     * @param soId
     * @return
     */
    public long getTotalDurationMinutesForSuborder(long soId, Date fromDate, Date untilDate) {
    	long minutes = objectToLong(getSession()
    			.createQuery("select sum(tr.durationminutes)+60*sum(tr.durationhours) from Timereport tr " +
        				"where tr.referenceday.refdate >= ? and tr.referenceday.refdate <= ? and tr.employeeorder.suborder.id = ? ")
        		.setDate(0,fromDate)
        		.setDate(1, untilDate)
        		.setLong(2, soId)
        		.uniqueResult());
        return minutes;
    }
    
    /**
     * Gets the sum of all duration minutes WITH consideration of the hours.
     * 
     * @param coId
     * @return
     */
    public long getTotalDurationMinutesForCustomerOrder(long coId) {
    	long totalMinutes = objectToLong(getSession()
    			.createQuery("select sum(tr.durationminutes)+60*sum(tr.durationhours) from Timereport tr " +
    					"where tr.employeeorder.suborder.customerorder.id = ?")
                .setLong(0, coId)
                .uniqueResult());
        return totalMinutes;
    }
    
    /**
     * Gets the sum of all duration minutes WITH considering the hours.
     * 
     * @param eoId
     * @return
     */
    public long getTotalDurationMinutesForEmployeeOrder(long eoId) {
    	Object totalMinutes = getSession()
        		.createQuery("select sum(durationminutes)+60*sum(durationhours) from Timereport tr WHERE tr.employeeorder.id = ?")
        		.setLong(0, eoId)
        		.setCacheable(true)
        		.uniqueResult();
    	return objectToLong(totalMinutes);
    }
    
    private long objectToLong(Object o) {
    	if(o instanceof Long) {
    		return (Long)o;
    	} else if(o instanceof BigDecimal) {
    		return ((BigDecimal)o).longValue();
    	} else {
    		return 0;
    	}
    }
    
    /**
     * 
     * @param ecId
     * @param suborderId
     * @return
     */
    @SuppressWarnings("unchecked")
    public List<Timereport> getTimereportsBySuborderIdAndEmployeeContractId(long suborderId, long ecId) {
        return getSession()
        		.createQuery("from Timereport tr where tr.suborder.id = ? and tr.employeecontract.id = ? " +
                "order by employeecontract.employee.sign asc, referenceday.refdate desc, sequencenumber asc")
        		.setLong(0, suborderId)
        		.setLong(1, ecId)
        		.setCacheable(true)
        		.list();
    }
    
    /**
     * 
     * @param employeeContractId
     * @return Returns a list of all {@link Timereport}s associated to the given {@link Employeecontract#getId()}.
     */
    @SuppressWarnings("unchecked")
    public List<Timereport> getTimereportsByEmployeeContractId(long employeeContractId) {
        return getSession()
        		.createQuery("from Timereport where employeecontract.id = ? order by employeecontract.employee.sign asc, referenceday.refdate desc, sequencenumber asc")
        		.setLong(0, employeeContractId)
        		.setCacheable(true)
        		.list();
    }
    
    /**
     * Gets a list of Timereports by month/year.
     * month must have format EEE here, e.g. 'Jan' !
     * 
     * @param String month
     * @param String year
     * 
     * @return List<Timereport>
     */
    @SuppressWarnings("unchecked")
	public List<Timereport> getTimereportsByMonthAndYear(Date dateOfMonthAndYear) {
    	LocalDate localDateStartOfMonth = dateOfMonthAndYear.toLocalDate().withDayOfMonth(1);
    	Date startOfMonth = Date.valueOf(localDateStartOfMonth);
    	
    	LocalDate localDateEndOfMonth = localDateStartOfMonth
    			.plusMonths(1)
    			.minusDays(1);
    	Date endOfMonth = Date.valueOf(localDateEndOfMonth);
    	
        return getSession()
        		.createQuery("from Timereport where referenceday.refdate >= ? and referenceday.refdate <= ? order by employeecontract.employee.sign asc, referenceday.refdate desc, sequencenumber asc")
        		.setDate(0, startOfMonth)
        		.setDate(1, endOfMonth)
        		.setCacheable(true)
        		.list();
    }
    
    /**
     * Gets a list of 'W' Timereports by month/year and customerorder.
     * 
     * @param long coId
     * @param String month
     * @param String year
     * @param String sortOfReport
     * 
     * @return List<Timereport>
     */
//    @SuppressWarnings("unchecked")
//    public List<Timereport> getTimereportsByMonthAndYearAndCustomerorder(long coId, String month, String year, String sortOfReport) {
//        List<Suborder> suborders = suborderDAO.getSuborders(false);
//        List<Timereport> allTimereports = new ArrayList<Timereport>();
//        for (Suborder suborder : suborders) {
//            // get all timereports for this suborder...
//            List<Timereport> specificTimereports = getSession().createQuery("from Timereport t " +
//                    "where t.suborder.id = ? and t.suborder.customerorder.id = ? " +
//                    "order by employeecontract.employee.sign asc, referenceday.refdate desc, sequencenumber asc")
//                    .setLong(0, suborder.getId()).setLong(1, coId).setCacheable(true).list();
//            for (Timereport timereport : specificTimereports) {
//                // if timereport belongs to reference month/year, add it to result list...
//                if (sortOfReport != null && timereport.getSortofreport().equals("W")) {
//                    if (TimereportHelper.getMonthStringFromTimereport(timereport).equalsIgnoreCase(month) &&
//                            TimereportHelper.getYearStringFromTimereport(timereport).equalsIgnoreCase(year)) {
//                        allTimereports.add(timereport);
//                    }
//                }
//            }
//        }
//        return allTimereports;
//    }
    
    /**
     * Gets a list of Timereports by month/year and customerorder.
     * 
     * @param long coId
     * @param Date dt
     * @param String sortOfReport
     * 
     * @return List<Timereport>
     */
//    @SuppressWarnings("unchecked")
//    public List<Timereport> getTimereportsByDateAndCustomerorder(long coId, Date dt, String sortOfReport) {
//        List<Suborder> suborders = suborderDAO.getSuborders(false);
//        List<Timereport> allTimereports = new ArrayList<Timereport>();
//        for (Suborder suborder : suborders) {
//            // get all timereports for this suborder...
//            List<Timereport> specificTimereports = getSession().createQuery("from Timereport t " +
//                    "where t.referenceday.refdate = ? and t.suborder.id = ? and t.suborder.customerorder.id = ? " +
//                    "order by employeecontract.employee.sign asc, referenceday.refdate desc, sequencenumber asc")
//                    .setDate(0, dt).setLong(1, suborder.getId()).setLong(2, coId).setCacheable(true).list();
//            for (Timereport specificTimereport : specificTimereports) {
//                // if timereport belongs to reference month/year, add it to result list...
//                if (sortOfReport != null && specificTimereport.getSortofreport().equals("W")) {
//                    allTimereports.add(specificTimereport);
//                }
//            }
//        }
//        return allTimereports;
//    }
    
    /**
     * Gets a list of Timereports by employee contract id and month/year.
     * 
     * @param long contractId
     * @param String month
     * @param String year
     * 
     * @return List<Timereport>
     */
    @SuppressWarnings("unchecked")
    public List<Timereport> getTimereportsByMonthAndYearAndEmployeeContractId(long contractId, Date dateOfMonthAndYear) {
    	LocalDate localDateStartOfMonth = dateOfMonthAndYear.toLocalDate().withDayOfMonth(1);
    	Date startOfMonth = Date.valueOf(localDateStartOfMonth);
    	
    	LocalDate localDateEndOfMonth = localDateStartOfMonth
    			.plusMonths(1)
    			.minusDays(1);
    	Date endOfMonth = Date.valueOf(localDateEndOfMonth);
    	
        return getSession()
        		.createQuery("from Timereport where employeecontract.id = ? and referenceday.refdate >= ? and referenceday.refdate <= ? order by employeecontract.employee.sign asc, referenceday.refdate desc, sequencenumber asc")
        		.setLong(0, contractId)
        		.setDate(1, startOfMonth)
        		.setDate(2, endOfMonth)
        		.setCacheable(true)
        		.list();

    }
    
    /**
     * Gets a list of Timereports by employee contract id and date.
     * 
     * @param long contractId
     * @param Date date
     * 
     * @return List<Timereport>
     */
    @SuppressWarnings("unchecked")
    public List<Timereport> getTimereportsByDateAndEmployeeContractId(long contractId, Date date) {
        List<Timereport> allTimereports = getSession().createQuery("from Timereport t " +
                "where t.employeecontract.id = ? and t.referenceday.refdate = ? " +
                "order by employeecontract.employee.sign asc, sequencenumber asc")
                .setLong(0, contractId).setDate(1, date).setCacheable(true).list();
        return allTimereports;
    }
    
    /**
     * Gets a list of all {@link Timereport}s that fulfill following criteria: 
     * 1) associated to the given employee contract
     * 2) refdate out of range of the employee contract
     * 
     * @param employeecontract
     * @return Returns a {@link List} with all {@link Timereport}s, that fulfill the criteria.
     */
    @SuppressWarnings("unchecked")
    public List<Timereport> getTimereportsOutOfRangeForEmployeeContract(Employeecontract employeecontract) {
        Long employeeContractId = employeecontract.getId();
        Date contractBegin = employeecontract.getValidFrom();
        Date contractEnd = employeecontract.getValidUntil();
        List<Timereport> allTimereports = new ArrayList<Timereport>();
        if (contractEnd == null) {
            allTimereports = getSession().createQuery("from Timereport t " +
                    "where t.employeecontract.id = ? and t.referenceday.refdate < ? " +
                    "order by t.referenceday.refdate asc, t.suborder.customerorder.sign asc, t.suborder.sign asc")
                    .setLong(0, employeeContractId).setDate(1, contractBegin).setCacheable(true).list();
        } else {
            allTimereports = getSession().createQuery("from Timereport t " +
                    "where t.employeecontract.id = ? and (t.referenceday.refdate < ? or  t.referenceday.refdate > ?) " +
                    "order by t.referenceday.refdate asc, t.suborder.customerorder.sign asc, t.suborder.sign asc")
                    .setLong(0, employeeContractId).setDate(1, contractBegin).setDate(2, contractEnd).setCacheable(true).list();
        }
        return allTimereports;
    }
    
    /**
     * Gets a list of all {@link Timereport}s that fulfill following criteria: 
     * 1) associated to the given employee contract
     * 2) refdate out of range of the associated employee order
     * 
     * @param employeecontract
     * @return Returns a {@link List} with all {@link Timereport}s, that fulfill the criteria.
     */
    @SuppressWarnings("unchecked")
    public List<Timereport> getTimereportsOutOfRangeForEmployeeOrder(Employeecontract employeecontract) {
        Long employeeContractId = employeecontract.getId();
        List<Timereport> allTimereports = getSession().createQuery("from Timereport t " +
                "where t.employeecontract.id = ? and (t.referenceday.refdate < t.employeeorder.fromDate or t.referenceday.refdate > t.employeeorder.untilDate) " +
                "order by t.referenceday.refdate asc, t.suborder.customerorder.sign asc, t.suborder.sign asc")
                .setLong(0, employeeContractId).setCacheable(true).list();
        return allTimereports;
    }
    
    /**
     * Gets a list of all {@link Timereport}s, that have no duration and are associated to the given ecId.
     * 
     * @param ecId id of the {@link Employeecontract}
     * @param date 
     * @return
     */
    @SuppressWarnings("unchecked")
    public List<Timereport> getTimereportsWithoutDurationForEmployeeContractId(long ecId, Date releaseDate) {
        /*  sql: function changed to just get zero-duration-timereports that were made after the last releasedate, since only those can be changed by the employee himself and therefore the earlier ones should not be shown as warning */
        return getSession()
                .createQuery("from Timereport t where t.employeecontract.id = ?  and t.referenceday.refdate >= ?"
                        + "and t.suborder.sign not like ? and durationminutes = 0 and durationhours = 0"
                        + "order by t.referenceday.refdate asc, t.suborder.customerorder.sign asc, t.suborder.sign asc")
                .setLong(0, ecId).setDate(1, releaseDate).setString(2, GlobalConstants.SUBORDER_SIGN_OVERTIME_COMPENSATION).setCacheable(true).list();
    }
    
    /**
     * Gets a list of all {@link Timereport}s that fulfill following criteria: 
     * 1) associated to the given employee contract id
     * 2) valid before and at the given date
     * 3) status is open 
     * 
     * @param contractId
     * @param date
     * @return
     */
    @SuppressWarnings("unchecked")
    public List<Timereport> getOpenTimereportsByEmployeeContractIdBeforeDate(long contractId, Date date) {
        List<Timereport> allTimereports = getSession().createQuery("from Timereport t " +
                "where t.employeecontract.id = ? and t.referenceday.refdate <= ? and status = ?")
                .setLong(0, contractId).setDate(1, date).setString(2, GlobalConstants.TIMEREPORT_STATUS_OPEN).setCacheable(true).list();
        return allTimereports;
    }
    
    /**
     * Gets a list of all {@link Timereport}s that fulfill following criteria: 
     * 1) associated to the given employee contract id
     * 2) valid before and at the given date
     * 3) status is commited 
     * 
     * @param contractId
     * @param date
     * @return
     */
    @SuppressWarnings("unchecked")
    public List<Timereport> getCommitedTimereportsByEmployeeContractIdBeforeDate(long contractId, Date date) {
        List<Timereport> allTimereports = getSession().createQuery("from Timereport t " +
                "where t.employeecontract.id = ? and t.referenceday.refdate <= ? and status = ?")
                .setLong(0, contractId).setDate(1, date).setString(2, GlobalConstants.TIMEREPORT_STATUS_COMMITED).setCacheable(true).list();
        return allTimereports;
    }
    
    /**
     * Gets a list of all {@link Timereport}s that fulfill following criteria: 
     * 1) associated to the given employee contract id
     * 2) valid after and at the given date
     * 
     * 
     * @param contractId
     * @param dt
     * @return
     */
    @SuppressWarnings("unchecked")
    public List<Timereport> getTimereportsByEmployeeContractIdAfterDate(long contractId, Date dt) {
        List<Timereport> allTimereports = getSession().createQuery("from Timereport t " +
                "where t.employeecontract.id = ? and t.referenceday.refdate >= ? ")
                .setLong(0, contractId).setDate(1, dt).setCacheable(true).list();
        return allTimereports;
    }
    
    /**
     * Gets a list of Timereports by employee contract id and two dates.
     * 
     * @param long contractId
     * @param Date begin
     * @param Date end
     * 
     * @return List<Timereport>
     */
    @SuppressWarnings("unchecked")
    public List<Timereport> getTimereportsByDatesAndEmployeeContractId(long contractId, Date begin, Date end) {
        List<Timereport> allTimereports;
        if (begin.equals(end)) {
            allTimereports = getSession().createQuery("from Timereport t " +
                    "where t.employeecontract.id = ? and t.referenceday.refdate >= ? and t.referenceday.refdate <= ? " +
                    "order by t.employeecontract.employee.sign asc, t.referenceday.refdate asc, t.sequencenumber asc")
                    .setLong(0, contractId).setDate(1, begin).setDate(2, end).setCacheable(true).list();
        } else {
            allTimereports = getSession().createQuery("from Timereport t " +
                    "where t.employeecontract.id = ? and t.referenceday.refdate >= ? and t.referenceday.refdate <= ? " +
                    "order by t.employeecontract.employee.sign asc, t.referenceday.refdate asc, t.employeeorder.suborder.customerorder.sign asc, t.employeeorder.suborder.sign asc")
                    .setLong(0, contractId).setDate(1, begin).setDate(2, end).setCacheable(true).list();
        }
        return allTimereports;
    }
    
    /**
     * Gets a list of Timereports by associated to the given employee contract id, customer order id and the time period between the two given dates.
     * 
     * @param long contractId
     * @param Date begin
     * @param Date end
     * @param customerOrderId
     * 
     * @return List<Timereport>
     */
    @SuppressWarnings("unchecked")
    public List<Timereport> getTimereportsByDatesAndEmployeeContractIdAndCustomerOrderId(long contractId, Date begin, Date end, long customerOrderId) {
        List<Timereport> allTimereports;
        if (begin.equals(end)) {
            allTimereports = getSession().createQuery("from Timereport t " +
                    "where t.employeecontract.id = ? and t.referenceday.refdate >= ? and t.referenceday.refdate <= ? and t.suborder.customerorder.id = ? " +
                    "order by employeecontract.employee.sign asc, referenceday.refdate asc, sequencenumber asc")
                    .setLong(0, contractId).setDate(1, begin).setDate(2, end).setLong(3, customerOrderId).setCacheable(true).list();
        } else {
            allTimereports = getSession().createQuery("from Timereport t " +
                    "where t.employeecontract.id = ? and t.referenceday.refdate >= ? and t.referenceday.refdate <= ? and t.suborder.customerorder.id = ? " +
                    "order by employeecontract.employee.sign asc, referenceday.refdate asc, suborder.customerorder.sign asc, suborder.sign asc")
                    .setLong(0, contractId).setDate(1, begin).setDate(2, end).setLong(3, customerOrderId).setCacheable(true).list();
        }
        return allTimereports;
    }
    
    /**
     * Gets a list of Timereports by associated to the given employee contract id, suborder id and the time period between the two given dates.
     * 
     * @param long suborderId
     * @param Date begin
     * @param Date end
     * @param customerOrderId
     * 
     * @return List<Timereport>
     */
    @SuppressWarnings("unchecked")
    public List<Timereport> getTimereportsByDatesAndEmployeeContractIdAndSuborderId(long contractId, Date begin, Date end, long suborderId) {
        List<Timereport> allTimereports;
        if (end == null) {
            allTimereports = getSession().createQuery("from Timereport t " +
                    "where t.employeecontract.id = ? and t.referenceday.refdate >= ? and t.suborder.id = ? " +
                    "order by employeecontract.employee.sign asc, referenceday.refdate asc, sequencenumber asc")
                    .setLong(0, contractId).setDate(1, begin).setLong(3, suborderId).setCacheable(true).list();
        } else {
            if (begin.equals(end)) {
                allTimereports = getSession().createQuery("from Timereport t " +
                        "where t.employeecontract.id = ? and t.referenceday.refdate >= ? and t.referenceday.refdate <= ? and t.suborder.id = ? " +
                        "order by employeecontract.employee.sign asc, referenceday.refdate asc, sequencenumber asc")
                        .setLong(0, contractId).setDate(1, begin).setDate(2, end).setLong(3, suborderId).setCacheable(true).list();
            } else {
                allTimereports = getSession().createQuery("from Timereport t " +
                        "where t.employeecontract.id = ? and t.referenceday.refdate >= ? and t.referenceday.refdate <= ? and t.suborder.id = ? " +
                        "order by employeecontract.employee.sign asc, referenceday.refdate asc, suborder.customerorder.sign asc, suborder.sign asc")
                        .setLong(0, contractId).setDate(1, begin).setDate(2, end).setLong(3, suborderId).setCacheable(true).list();
            }
        }
        return allTimereports;
    }
    
    /**
     * Gets a list of Timereports by employee order id.
     * 
     * @param employeeOrderId
     * 
     * @return List<Timereport>
     */
    @SuppressWarnings("unchecked")
    public List<Timereport> getTimereportsByEmployeeOrderId(long employeeOrderId) {
        return getSession().createQuery("from Timereport t where t.employeeorder.id = ? " +
                "order by employeecontract.employee.sign asc, sequencenumber asc")
                .setLong(0, employeeOrderId).setCacheable(true).list();
    }
    
    /**
     * Gets a list of Timereports by date.
     * 
     * @param Date dt
     * 
     * @return List<Timereport>
     */
    @SuppressWarnings("unchecked")
    public List<Timereport> getTimereportsByDate(Date date) {
        return getSession().createQuery("from Timereport t where t.referenceday.refdate = ? " +
                "order by employeecontract.employee.sign asc, sequencenumber asc")
                .setDate(0, date).setCacheable(true).list();
    }
    
    /**
     * Gets a list of timereports, which lay between two dates.
     * 
     * @param Date begin
     * @param Date end
     * 
     * @return List<Timereport>
     */
    @SuppressWarnings("unchecked")
    public List<Timereport> getTimereportsByDates(Date begin, Date end) {
        List<Timereport> allTimereports;
        if (begin.equals(end)) {
            allTimereports = getSession().createQuery("from Timereport t where t.referenceday.refdate >= ? and t.referenceday.refdate <= ? " +
                    "order by employeecontract.employee.sign asc, referenceday.refdate asc, sequencenumber asc")
                    .setDate(0, begin).setDate(1, end).setCacheable(true).list();
        } else {
            allTimereports = getSession().createQuery("from Timereport t where t.referenceday.refdate >= ? and t.referenceday.refdate <= ? " +
                    "order by employeecontract.employee.sign asc, referenceday.refdate asc, suborder.customerorder.sign asc, suborder.sign asc")
                    .setDate(0, begin).setDate(1, end).setCacheable(true).list();
        }
        return allTimereports;
    }
    
    /**
     * Gets a list of timereports, which lay between two dates and belong to the given {@link Customerorder} id.
     * 
     * @param Date begin
     * @param Date end
     * @param coId
     * 
     * @return List<Timereport>
     */
    @SuppressWarnings("unchecked")
    public List<Timereport> getTimereportsByDatesAndCustomerOrderId(Date begin, Date end, long coId) {
        List<Timereport> allTimereports;
        if (begin.equals(end)) {
            allTimereports = getSession().createQuery("from Timereport t " +
                    "where t.referenceday.refdate >= ? and t.referenceday.refdate <= ? and t.suborder.customerorder.id = ? " +
                    "order by employeecontract.employee.sign asc, referenceday.refdate asc, sequencenumber asc")
                    .setDate(0, begin).setDate(1, end).setLong(2, coId).setCacheable(true).list();
        } else {
            allTimereports = getSession().createQuery("from Timereport t " +
                    "where t.referenceday.refdate >= ? and t.referenceday.refdate <= ? and t.suborder.customerorder.id = ? " +
                    "order by employeecontract.employee.sign asc, referenceday.refdate asc, suborder.customerorder.sign asc, suborder.sign asc")
                    .setDate(0, begin).setDate(1, end).setLong(2, coId).setCacheable(true).list();
        }
        return allTimereports;
    }
    
    /**
     * Gets a list of timereports, which lay between two dates and belong to the given {@link Suborder} id.
     * 
     * @param Date begin
     * @param Date end
     * @param suborderId
     * 
     * @return List<Timereport>
     */
    @SuppressWarnings("unchecked")
    public List<Timereport> getTimereportsByDatesAndSuborderId(Date begin, Date end, long suborderId) {
        List<Timereport> allTimereports;
        if (begin.equals(end)) {
            allTimereports = getSession().createQuery("from Timereport t " +
                    "where t.referenceday.refdate >= ? and t.referenceday.refdate <= ? and t.suborder.id = ? " +
                    "order by employeecontract.employee.sign asc, referenceday.refdate asc, sequencenumber asc")
                    .setDate(0, begin).setDate(1, end).setLong(2, suborderId).setCacheable(true).list();
        } else {
            allTimereports = getSession().createQuery("from Timereport t " +
                    "where t.referenceday.refdate >= ? and t.referenceday.refdate <= ? and t.suborder.id = ? " +
                    "order by employeecontract.employee.sign asc, referenceday.refdate asc, suborder.customerorder.sign asc, suborder.sign asc")
                    .setDate(0, begin).setDate(1, end).setLong(2, suborderId).setCacheable(true).list();
        }
        return allTimereports;
    }
    
    /**
     * Gets a list of timereports, which lay between two dates and belong to the given {@link Suborder} id.
     * 
     * @param Date begin
     * @param Date end
     * @param suborderId
     * 
     * @return List<Timereport>
     */
    @SuppressWarnings("unchecked")
    public List<Timereport> getTimereportsByDatesAndSuborderIdOrderedByDateAndEmployeeSign(Date begin, Date end, long suborderId) {
        List<Timereport> allTimereports;
        if (begin.equals(end)) {
            allTimereports = getSession().createQuery("from Timereport t " +
                    "where t.referenceday.refdate >= ? and t.referenceday.refdate <= ? and t.suborder.id = ? " +
                    "order by referenceday.refdate asc, employeecontract.employee.sign asc")
                    .setDate(0, begin).setDate(1, end).setLong(2, suborderId).setCacheable(true).list();
        } else {
            allTimereports = getSession().createQuery("from Timereport t " +
                    "where t.referenceday.refdate >= ? and t.referenceday.refdate <= ? and t.suborder.id = ? " +
                    "order by referenceday.refdate asc, employeecontract.employee.sign asc, suborder.customerorder.sign asc, suborder.sign asc")
                    .setDate(0, begin).setDate(1, end).setLong(2, suborderId).setCacheable(true).list();
        }
        return allTimereports;
    }
    
    /**
     * Gets a list of 'W' Timereports by employee contract id and customer id and month/year.
     * 
     * @param long contractId
     * @param long coId
     * @param String month
     * @param String year
     * @param String sortOfReport
     * 
     * @return List<Timereport>
     */
//    @SuppressWarnings("unchecked")
//    public List<Timereport> getTimereportsByMonthAndYearAndEmployeeContractIdAndCustomerorderId(long contractId, long coId, String month, String year, String sortOfReport) {
//        List<Suborder> suborders = suborderDAO.getSubordersByEmployeeContractId(contractId);
//        List<Timereport> allTimereports = new ArrayList<Timereport>();
//        for (Suborder suborder : suborders) {
//            // get all timereports for this suborder AND employee contract...
//            List<Timereport> specificTimereports = getSession().createQuery("from Timereport t " +
//                    "where t.employeecontract.id = ? and t.suborder.id = ? and t.suborder.customerorder.id = ? " +
//                    "order by employeecontract.employee.sign asc, referenceday.refdate desc, sequencenumber asc")
//                    .setLong(0, contractId).setLong(1, suborder.getId()).setLong(2, coId).setCacheable(true).list();
//            for (Timereport specificTimereport : specificTimereports) {
//                // if timereport belongs to reference month/year, add it to result list...
//                if (sortOfReport != null && specificTimereport.getSortofreport().equals("W")
//                        && TimereportHelper.getMonthStringFromTimereport(specificTimereport).equalsIgnoreCase(month)
//                        && TimereportHelper.getYearStringFromTimereport(specificTimereport).equalsIgnoreCase(year)) {
//                    allTimereports.add(specificTimereport);
//                }
//            }
//        }
//        return allTimereports;
//    }
    
    /**
     * Gets a list of Timereports by employee contract id and customer id and date.
     * 
     * @param long contractId
     * @param long coId
     * @param Date dt
     * @param String sortOfReport
     * 
     * @return List<Timereport>
     */
//    @SuppressWarnings("unchecked")
//    public List<Timereport> getTimereportsByDateAndEmployeeContractIdAndCustomerorderId(long contractId, long coId, Date dt, String sortOfReport) {
//        List<Suborder> suborders = suborderDAO.getSubordersByEmployeeContractId(contractId);
//        List<Timereport> allTimereports = new ArrayList<Timereport>();
//        for (Suborder suborder : suborders) {
//            // get all timereports for this suborder...
//            List<Timereport> specificTimereports = getSession().createQuery("from Timereport t " +
//                    "where t.referenceday.refdate = ? and t.suborder.id = ? and t.suborder.customerorder.id = ? " +
//                    "order by employeecontract.employee.sign asc, referenceday.refdate desc, sequencenumber asc")
//                    .setDate(0, dt).setLong(1, suborder.getId()).setLong(2, coId).setCacheable(true).list();
//            for (Timereport specificTimereport : specificTimereports) {
//                // if timereport belongs to reference month/year, add it to result list...
//                if (sortOfReport != null && specificTimereport.getSortofreport().equals("W")) {
//                    allTimereports.add(specificTimereport);
//                }
//            }
//        }
//        return allTimereports;
//    }
    
    /**
     * 
     * @param begin
     * @param end
     * @param customerOrderSign
     * @return Returns a list of all timereports that are associated to the given customer order sign and are valid between the given dates. 
     */
//    @SuppressWarnings("unchecked")
//    public List<Timereport> getTimereportsByDatesAndCustomerOrderSign(Date begin, Date end, String customerOrderSign) {
//        return getSession().createQuery("from Timereport t " +
//                "where t.referenceday.refdate >= ? and t.referenceday.refdate <= ? and t.suborder.customerorder.sign = ? ")
//                .setDate(0, begin).setDate(1, end).setString(2, customerOrderSign).setCacheable(true).list();
//    }
    
    /**
     * @param end
     * @param coId
     * @return Returns a timereport thats valid between the first and the last day of the given date and belonging to employeecontractid 
     */
    @SuppressWarnings("unchecked")
    public Timereport getLastAcceptedTimereportByDateAndEmployeeContractId(Date end, long ecId) {
    	LocalDate localDate = end.toLocalDate();
    	Date firstDay = Date.valueOf(localDate.withDayOfMonth(1));
    	Date lastDay = Date.valueOf(localDate.withDayOfMonth(localDate.lengthOfMonth()));
        List<Timereport> timereportList = getSession().createQuery("from Timereport t " +
                "where t.accepted is not null and t.employeeorder.employeecontract.id = ? and t.referenceday.refdate >= ? and t.referenceday.refdate <= ? " +
                "order by t.referenceday.refdate desc")
                .setLong(0, ecId).setDate(1, firstDay).setDate(2, lastDay).setCacheable(true).list();
        if (!timereportList.isEmpty()) {
            return timereportList.get(0);
        } else {
            return null;
        }
    }
    
    @SuppressWarnings("unchecked")
    public List<Timereport> getTimereportsByEmployeeorderIdInvalidForDates(Date begin, Date end, Long employeeOrderId) {
        if (end == null) {
            return getSession().createQuery("from Timereport t " +
                    "where t.employeeorder.id = ? and t.referenceday.refdate < ? " +
                    "order by t.referenceday.refdate asc, t.employeeorder.employeecontract.employee.sign asc, t.employeeorder.suborder.customerorder.sign asc, t.employeeorder.suborder.sign asc")
                    .setLong(0, employeeOrderId).setDate(1, begin).setCacheable(true).list();
        } else {
            return getSession().createQuery("from Timereport t " +
                    "where t.employeeorder.id = ? and (t.referenceday.refdate < ? or t.referenceday.refdate > ?) " +
                    "order by t.referenceday.refdate asc, t.employeeorder.employeecontract.employee.sign asc, t.employeeorder.suborder.customerorder.sign asc, t.employeeorder.suborder.sign asc")
                    .setLong(0, employeeOrderId).setDate(1, begin).setDate(2, end).setCacheable(true).list();
        }
    }
    
    @SuppressWarnings("unchecked")
    public List<Timereport> getTimereportsBySuborderIdInvalidForDates(Date begin, Date end, Long suborderId) {
        return getSession().createQuery("from Timereport t " +
                "where t.employeeorder.suborder.id = ? and (t.referenceday.refdate < ? or t.referenceday.refdate > ?) " +
                "order by t.employeeorder.employeecontract.employee.sign asc, t.referenceday.refdate asc, t.employeeorder.suborder.customerorder.sign asc, t.employeeorder.suborder.sign asc")
                .setLong(0, suborderId).setDate(1, begin).setDate(2, end).setCacheable(true).list();
    }
    
    @SuppressWarnings("unchecked")
    public List<Timereport> getTimereportsByCustomerOrderIdInvalidForDates(Date begin, Date end, Long customerOrderId) {
        return getSession().createQuery("from Timereport t " +
                "where t.employeeorder.suborder.customerorder.id = ? and (t.referenceday.refdate < ? or t.referenceday.refdate > ?) " +
                "order by t.employeeorder.employeecontract.employee.sign asc, t.referenceday.refdate asc, t.employeeorder.suborder.customerorder.sign asc, t.employeeorder.suborder.sign asc")
                .setLong(0, customerOrderId).setDate(1, begin).setDate(2, end).setCacheable(true).list();
    }
    
    @SuppressWarnings("unchecked")
    public List<Timereport> getTimereportsByEmployeeContractIdInvalidForDates(Date begin, Date end, Long employeeContractId) {
        if (end != null) {
            return getSession().createQuery("from Timereport t " +
                    "where t.employeeorder.employeecontract.id = ? and (t.referenceday.refdate < ? or t.referenceday.refdate > ?) " +
                    "order by t.employeeorder.employeecontract.employee.sign asc, t.referenceday.refdate asc, t.employeeorder.suborder.customerorder.sign asc, t.employeeorder.suborder.sign asc")
                    .setLong(0, employeeContractId).setDate(1, begin).setDate(2, end).setCacheable(true).list();
        } else {
            return getSession().createQuery("from Timereport t " +
                    "where t.employeeorder.employeecontract.id = ? and t.referenceday.refdate < ? " +
                    "order by t.employeeorder.employeecontract.employee.sign asc, t.referenceday.refdate asc, t.employeeorder.suborder.customerorder.sign asc, t.employeeorder.suborder.sign asc")
                    .setLong(0, employeeContractId).setDate(1, begin).list();
        }
    }
    
    @SuppressWarnings("unchecked")
	public List<Timereport> getTimereportsByTicketID(long ticketID) {
        return getSession().createQuery("from Timereport t where t.ticket.id = ?")
                .setLong(0, ticketID).list();
    }
    
    /**
     * Calls {@link TimereportDAO#save(Timereport, Employee)} with {@link Employee} = null.
     * @param tr
     */
    public void save(Timereport tr) {
        save(tr, null, true);
    }
    
    /**
     * Saves the given timereport and sets creation-/update-user and creation-/update-date.
     * 
     * @param Timereport tr
     * 
     */
    public void save(Timereport tr, Employee loginEmployee, boolean changeUpdateDate) {
        if (loginEmployee == null) {
            throw new RuntimeException("the login-user must be passed to the db");
        }
        java.util.Date now = new java.util.Date();
        if (tr.getCreated() == null) {
            tr.setCreated(now);
            tr.setCreatedby(loginEmployee.getSign());
        } else if (changeUpdateDate) {
            tr.setLastupdate(now);
            tr.setLastupdatedby(loginEmployee.getSign());
            Integer updateCounter = tr.getUpdatecounter();
            updateCounter = updateCounter == null ? 1 : updateCounter + 1;
            tr.setUpdatecounter(updateCounter);
        }
        getSession().saveOrUpdate(tr);
        getSession().flush();
    }
    
    /**
     * Deletes the given timereport.
     * 
     * @param long trId - timereport id
     * 
     */
    public boolean deleteTimereportById(long trId) {
        Timereport timereport = getTimereportById(trId);
        boolean deleted = false;
        
        if (timereport != null) {
//        	Worklog worklog = worklogDAO.getWorklogByTimereportID(trId);
//        	if (worklog != null) {
//        		worklogDAO.deleteWorklog(worklog);
//        	}
        	
            getSession().delete(timereport);
            getSession().flush();
            deleted = true;
        }
        return deleted;
    }
    
}
