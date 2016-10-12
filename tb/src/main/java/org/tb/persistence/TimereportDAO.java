package org.tb.persistence;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

import org.springframework.orm.hibernate3.support.HibernateDaoSupport;
import org.tb.GlobalConstants;
import org.tb.bdom.Customerorder;
import org.tb.bdom.Employee;
import org.tb.bdom.Employeecontract;
import org.tb.bdom.Employeeorder;
import org.tb.bdom.Suborder;
import org.tb.bdom.Timereport;
import org.tb.helper.TimereportHelper;


/**
 * DAO class for 'Timereport'
 * 
 * @author oda
 *
 */
public class TimereportDAO extends HibernateDaoSupport {
    
    private SuborderDAO suborderDAO;
    
    public void setSuborderDAO(SuborderDAO suborderDAO) {
        this.suborderDAO = suborderDAO;
    }
    
    /**
     * Gets the timereport for the given id.
     * 
     * @param long id
     * 
     * @return Timereport
     */
    public Timereport getTimereportById(long id) {
        return (Timereport)getSession().createQuery("from Timereport t where t.id = ?").setLong(0, id).setCacheable(true).uniqueResult();
    }
    
    /**
     * Get a list of all Timereports.
     * 
     * @return List<Timereport>
     */
    @SuppressWarnings("unchecked")
    public List<Timereport> getTimereports() {
        return getSession().createQuery("from Timereport " +
                "order by employeecontract.employee.sign asc, referenceday.refdate desc, sequencenumber asc").setCacheable(true).list();
    }
    
    /**
     * Get a list of all Timereports ordered by employee sign, customer order sign, suborder sign and refdate.
     * 
     * @return List<Timereport>
     */
    @SuppressWarnings("unchecked")
    public List<Timereport> getOrderedTimereports() {
        return getSession().createQuery("from Timereport " +
                "order by employeecontract.employee.sign asc, suborder.customerorder.sign asc, suborder.sign asc, referenceday.refdate asc").setCacheable(true).list();
    }
    
    /**
     * Get a list of all Timereports where the employeeorder_id is null.
     * 
     * @return List<Timereport>
     */
    @SuppressWarnings("unchecked")
    public List<Timereport> getTimereportsWithoutEmployeeOrderKey() {
        return getSession().createQuery("from Timereport where employeeorder_id <= ? " +
                "order by employeecontract.employee.sign asc, referenceday.refdate desc, sequencenumber asc").setBigInteger(0, new BigInteger("0")).setCacheable(true).list();
    }
    
    /**
     * 
     * @param suborderId
     * @return
     */
    @SuppressWarnings("unchecked")
    public List<Timereport> getTimereportsBySuborderId(long suborderId) {
        return getSession().createQuery("from Timereport tr where tr.suborder.id = ? " +
                "order by employeecontract.employee.sign asc, referenceday.refdate desc, sequencenumber asc").setLong(0, suborderId).setCacheable(true).list();
    }
    
    /**
     * Gets the sum of all duration hours without considering the minutes.
     * 
     * @param soId
     * @return
     */
    @SuppressWarnings("unchecked")
    public Long getTotalDurationHoursForSuborder(long soId) {
        //		BigInteger hours = (BigInteger) getSession().createSQLQuery("select sum(durationhours) from Timereport tr, Employeeorder eo " +
        //				"where tr.employeeorder_id = eo.id and eo.suborder_id = ?")
        //		.setLong(0, soId).uniqueResult();
        //		return hours == null ? 0l : hours.longValue();
        List<Employeeorder> employeeorders = getSession().createQuery("FROM Employeeorder eo WHERE eo.suborder.id = ?").setLong(0, soId).setCacheable(true).list();
        long hours = 0l;
        for (Employeeorder employeeorder : employeeorders) {
            List<Timereport> timereports = employeeorder.getSuborder().getTimereports();
            for (Timereport timereport : timereports) {
                if (timereport.getEmployeeorder().getId() == employeeorder.getId()) {
                    hours += timereport.getDurationhours();
                }
            }
        }
        return hours;
    }
    
    /**
     * Gets the sum of all duration minutes without considering the hours.
     * 
     * @param soId
     * @return
     */
    @SuppressWarnings("unchecked")
    public Long getTotalDurationMinutesForSuborder(long soId) {
        //		BigInteger minutes = (BigInteger) getSession().createSQLQuery("select sum(durationminutes) from Timereport tr, Employeeorder eo " +
        //				"where tr.employeeorder_id = eo.id and eo.suborder_id = ?")
        //		.setLong(0, soId).uniqueResult();
        //		return minutes == null ? 0l : minutes.longValue();
        List<Employeeorder> employeeorders = getSession().createQuery("FROM Employeeorder eo WHERE eo.suborder.id = ?").setLong(0, soId).setCacheable(true).list();
        long minutes = 0l;
        for (Employeeorder employeeorder : employeeorders) {
            List<Timereport> timereports = employeeorder.getSuborder().getTimereports();
            for (Timereport timereport : timereports) {
                if (timereport.getEmployeeorder().getId() == employeeorder.getId()) {
                    minutes += timereport.getDurationminutes();
                }
            }
        }
        return minutes;
    }
    
    /**
     * Gets the sum of all duration hours within a range of time without considering the minutes.
     * 
     * @param soId
     * @return
     */
    @SuppressWarnings("unchecked")
    public Long getTotalDurationHoursForSuborder(long soId, java.sql.Date fromDate, java.sql.Date untilDate) {
        //		BigInteger hours = (BigInteger) getSession().createSQLQuery("select sum(durationhours) from Timereport tr, Employeeorder eo, Referenceday rd " +
        //				"where rd.refdate >= ? and rd.refdate <= ? and tr.employeeorder_id = eo.id and eo.suborder_id = ? and rd.id = tr.referenceday_id")
        //		.setDate(0,fromDate).setDate(1, untilDate).setLong(2, soId).uniqueResult();
        //		return hours == null ? 0l : hours.longValue();
        List<Employeeorder> employeeorders = getSession().createQuery("FROM Employeeorder eo WHERE eo.suborder.id = ?").setLong(0, soId).setCacheable(true).list();
        long hours = 0l;
        for (Employeeorder employeeorder : employeeorders) {
            List<Timereport> timereports = employeeorder.getSuborder().getTimereports();
            for (Timereport timereport : timereports) {
                java.sql.Date refDate = timereport.getReferenceday().getRefdate();
                if (timereport.getEmployeeorder().getId() == employeeorder.getId()
                        && !refDate.before(fromDate) && !refDate.after(untilDate)) {
                    hours += timereport.getDurationhours();
                }
            }
        }
        return hours;
    }
    
    /**
     * Gets the sum of all duration minutes within a range of time without considering the hours.
     * 
     * @param soId
     * @return
     */
    @SuppressWarnings("unchecked")
    public Long getTotalDurationMinutesForSuborder(long soId, java.sql.Date fromDate, java.sql.Date untilDate) {
        //		BigInteger minutes = (BigInteger) getSession().createSQLQuery("select sum(durationminutes) from Timereport tr, Employeeorder eo, Referenceday rd " +
        //				"where rd.refdate >= ? and rd.refdate <= ? and tr.employeeorder_id = eo.id and eo.suborder_id = ? and rd.id = tr.referenceday_id")
        //		.setDate(0,fromDate).setDate(1, untilDate).setLong(2, soId).uniqueResult();
        //		return minutes == null ? 0l : minutes.longValue();
        List<Employeeorder> employeeorders = getSession().createQuery("FROM Employeeorder eo WHERE eo.suborder.id = ?").setLong(0, soId).setCacheable(true).list();
        long minutes = 0l;
        for (Employeeorder employeeorder : employeeorders) {
            List<Timereport> timereports = employeeorder.getSuborder().getTimereports();
            for (Timereport timereport : timereports) {
                java.sql.Date refDate = timereport.getReferenceday().getRefdate();
                if (timereport.getEmployeeorder().getId() == employeeorder.getId()
                        && !refDate.before(fromDate) && !refDate.after(untilDate)) {
                    minutes += timereport.getDurationminutes();
                }
            }
        }
        return minutes;
    }
    
    /**
     * Gets the sum of all duration minutes WITH consideration of the hours.
     * 
     * @param coId
     * @return
     */
    public Long getTotalDurationMinutesForCustomerOrder(long coId) {
    	BigDecimal totalMinutes = (BigDecimal)getSession().createSQLQuery("select sum(durationminutes)+60*sum(durationhours) from Timereport tr, Employeeorder eo, Suborder so " +
                "where tr.employeeorder_id = eo.id and eo.suborder_id = so.id and so.customerorder_id = ?")
                .setLong(0, coId)
                .uniqueResult();
        return totalMinutes == null ? 0l : totalMinutes.longValue();
    }
    
    /**
     * Gets the sum of all duration hours without considering the minutes.
     * 
     * @param eoId
     * @return
     */
    @SuppressWarnings("unchecked")
    public Long getTotalDurationHoursForEmployeeOrder(long eoId) {
        List<Timereport> timereports = getSession().createQuery("FROM Timereport tr WHERE tr.employeeorder.id = ?").setLong(0, eoId).setCacheable(true).list();
        long hours = 0l;
        for (Timereport timereport : timereports) {
            hours += timereport.getDurationhours();
        }
        return hours;
    }
    
    /**
     * Gets the sum of all duration minutes without considering the hours.
     * 
     * @param eoId
     * @return
     */
    @SuppressWarnings("unchecked")
    public Long getTotalDurationMinutesForEmployeeOrder(long eoId) {
        List<Timereport> timereports = getSession().createQuery("FROM Timereport tr WHERE tr.employeeorder.id = ?").setLong(0, eoId).setCacheable(true).list();
        long minutes = 0l;
        for (Timereport timereport : timereports) {
            minutes += timereport.getDurationminutes();
        }
        return minutes;
    }
    
    /**
     * 
     * @param ecId
     * @param suborderId
     * @return
     */
    @SuppressWarnings("unchecked")
    public List<Timereport> getTimereportsBySuborderIdAndEmployeeContractId(long suborderId, long ecId) {
        return getSession().createQuery("from Timereport tr where tr.suborder.id = ? and tr.employeecontract.id = ? " +
                "order by employeecontract.employee.sign asc, referenceday.refdate desc, sequencenumber asc").setLong(0, suborderId).setLong(1, ecId).setCacheable(true).list();
    }
    
    /**
     * 
     * @param employeeContractId
     * @return Returns a list of all {@link Timereport}s associated to the given {@link Employeecontract#getId()}.
     */
    @SuppressWarnings("unchecked")
    public List<Timereport> getTimereportsByEmployeeContractId(long employeeContractId) {
        return getSession().createQuery("from Timereport where employeecontract.id = ? " +
                "order by employeecontract.employee.sign asc, referenceday.refdate desc, sequencenumber asc").setLong(0, employeeContractId).setCacheable(true).list();
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
    public List<Timereport> getTimereportsByMonthAndYear(String month, String year) {
        List<Timereport> specificTimereports = new ArrayList<Timereport>();
        List<Timereport> allTimereports = getTimereports();
        for (Timereport timereport : allTimereports) {
            // if timereport belongs to reference month/year, add it to result list...
            if (TimereportHelper.getMonthStringFromTimereport(timereport).equalsIgnoreCase(month) &&
                    TimereportHelper.getYearStringFromTimereport(timereport).equalsIgnoreCase(year)) {
                specificTimereports.add(timereport);
            }
        }
        return specificTimereports;
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
    @SuppressWarnings("unchecked")
    public List<Timereport> getTimereportsByMonthAndYearAndCustomerorder(long coId, String month, String year, String sortOfReport) {
        List<Suborder> suborders = suborderDAO.getSuborders(false);
        List<Timereport> allTimereports = new ArrayList<Timereport>();
        for (Suborder suborder : suborders) {
            // get all timereports for this suborder...
            List<Timereport> specificTimereports = getSession().createQuery("from Timereport t " +
                    "where t.suborder.id = ? and t.suborder.customerorder.id = ? " +
                    "order by employeecontract.employee.sign asc, referenceday.refdate desc, sequencenumber asc")
                    .setLong(0, suborder.getId()).setLong(1, coId).setCacheable(true).list();
            for (Timereport timereport : specificTimereports) {
                // if timereport belongs to reference month/year, add it to result list...
                if (sortOfReport != null && timereport.getSortofreport().equals("W")) {
                    if (TimereportHelper.getMonthStringFromTimereport(timereport).equalsIgnoreCase(month) &&
                            TimereportHelper.getYearStringFromTimereport(timereport).equalsIgnoreCase(year)) {
                        allTimereports.add(timereport);
                    }
                }
            }
        }
        return allTimereports;
    }
    
    /**
     * Gets a list of Timereports by month/year and customerorder.
     * 
     * @param long coId
     * @param java.sql.Date dt
     * @param String sortOfReport
     * 
     * @return List<Timereport>
     */
    @SuppressWarnings("unchecked")
    public List<Timereport> getTimereportsByDateAndCustomerorder(long coId, java.sql.Date dt, String sortOfReport) {
        List<Suborder> suborders = suborderDAO.getSuborders(false);
        List<Timereport> allTimereports = new ArrayList<Timereport>();
        for (Suborder suborder : suborders) {
            // get all timereports for this suborder...
            List<Timereport> specificTimereports = getSession().createQuery("from Timereport t " +
                    "where t.referenceday.refdate = ? and t.suborder.id = ? and t.suborder.customerorder.id = ? " +
                    "order by employeecontract.employee.sign asc, referenceday.refdate desc, sequencenumber asc")
                    .setDate(0, dt).setLong(1, suborder.getId()).setLong(2, coId).setCacheable(true).list();
            for (Timereport specificTimereport : specificTimereports) {
                // if timereport belongs to reference month/year, add it to result list...
                if (sortOfReport != null && specificTimereport.getSortofreport().equals("W")) {
                    allTimereports.add(specificTimereport);
                }
            }
        }
        return allTimereports;
    }
    
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
    public List<Timereport> getTimereportsByMonthAndYearAndEmployeeContractId(long contractId, String month, String year) {
        List<Timereport> allTimereports = new ArrayList<Timereport>();
        List<Timereport> specificTimereports = getSession().createQuery("from Timereport t where t.employeecontract.id = ? " +
                "order by employeecontract.employee.sign asc, referenceday.refdate desc, sequencenumber asc")
                .setLong(0, contractId).setCacheable(true).list();
        for (Timereport specificTimereport : specificTimereports) {
            // if timereport belongs to reference month/year, add it to result list...
            // month has format EEE, e.g., 'Jan'
            if (TimereportHelper.getMonthStringFromTimereport(specificTimereport).equalsIgnoreCase(month)
                    && TimereportHelper.getYearStringFromTimereport(specificTimereport).equalsIgnoreCase(year)) {
                allTimereports.add(specificTimereport);
            }
        }
        return allTimereports;
    }
    
    /**
     * Gets a list of Timereports by employee contract id and date.
     * 
     * @param long contractId
     * @param java.sql.Date date
     * 
     * @return List<Timereport>
     */
    @SuppressWarnings("unchecked")
    public List<Timereport> getTimereportsByDateAndEmployeeContractId(long contractId, java.sql.Date date) {
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
    public List<Timereport> getTimereportsWithoutDurationForEmployeeContractId(long ecId, java.sql.Date releaseDate) {
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
    public List<Timereport> getOpenTimereportsByEmployeeContractIdBeforeDate(long contractId, java.sql.Date date) {
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
    public List<Timereport> getCommitedTimereportsByEmployeeContractIdBeforeDate(long contractId, java.sql.Date date) {
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
    public List<Timereport> getTimereportsByEmployeeContractIdAfterDate(long contractId, java.sql.Date dt) {
        List<Timereport> allTimereports = getSession().createQuery("from Timereport t " +
                "where t.employeecontract.id = ? and t.referenceday.refdate >= ? ")
                .setLong(0, contractId).setDate(1, dt).setCacheable(true).list();
        return allTimereports;
    }
    
    /**
     * Gets a list of Timereports by employee contract id and two dates.
     * 
     * @param long contractId
     * @param java.sql.Date begin
     * @param java.sql.Date end
     * 
     * @return List<Timereport>
     */
    @SuppressWarnings("unchecked")
    public List<Timereport> getTimereportsByDatesAndEmployeeContractId(long contractId, java.sql.Date begin, java.sql.Date end) {
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
     * @param java.sql.Date begin
     * @param java.sql.Date end
     * @param customerOrderId
     * 
     * @return List<Timereport>
     */
    @SuppressWarnings("unchecked")
    public List<Timereport> getTimereportsByDatesAndEmployeeContractIdAndCustomerOrderId(long contractId, java.sql.Date begin, java.sql.Date end, long customerOrderId) {
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
     * @param java.sql.Date begin
     * @param java.sql.Date end
     * @param customerOrderId
     * 
     * @return List<Timereport>
     */
    @SuppressWarnings("unchecked")
    public List<Timereport> getTimereportsByDatesAndEmployeeContractIdAndSuborderId(long contractId, java.sql.Date begin, java.sql.Date end, long suborderId) {
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
     * @param java.sql.Date dt
     * 
     * @return List<Timereport>
     */
    @SuppressWarnings("unchecked")
    public List<Timereport> getTimereportsByDate(java.sql.Date date) {
        return getSession().createQuery("from Timereport t where t.referenceday.refdate = ? " +
                "order by employeecontract.employee.sign asc, sequencenumber asc")
                .setDate(0, date).setCacheable(true).list();
    }
    
    /**
     * Gets a list of timereports, which lay between two dates.
     * 
     * @param java.sql.Date begin
     * @param java.sql.Date end
     * 
     * @return List<Timereport>
     */
    @SuppressWarnings("unchecked")
    public List<Timereport> getTimereportsByDates(java.sql.Date begin, java.sql.Date end) {
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
     * @param java.sql.Date begin
     * @param java.sql.Date end
     * @param coId
     * 
     * @return List<Timereport>
     */
    @SuppressWarnings("unchecked")
    public List<Timereport> getTimereportsByDatesAndCustomerOrderId(java.sql.Date begin, java.sql.Date end, long coId) {
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
     * @param java.sql.Date begin
     * @param java.sql.Date end
     * @param suborderId
     * 
     * @return List<Timereport>
     */
    @SuppressWarnings("unchecked")
    public List<Timereport> getTimereportsByDatesAndSuborderId(java.sql.Date begin, java.sql.Date end, long suborderId) {
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
     * @param java.sql.Date begin
     * @param java.sql.Date end
     * @param suborderId
     * 
     * @return List<Timereport>
     */
    @SuppressWarnings("unchecked")
    public List<Timereport> getTimereportsByDatesAndSuborderIdOrderedByDateAndEmployeeSign(java.sql.Date begin, java.sql.Date end, long suborderId) {
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
    @SuppressWarnings("unchecked")
    public List<Timereport> getTimereportsByMonthAndYearAndEmployeeContractIdAndCustomerorderId(long contractId, long coId, String month, String year, String sortOfReport) {
        List<Suborder> suborders = suborderDAO.getSubordersByEmployeeContractId(contractId);
        List<Timereport> allTimereports = new ArrayList<Timereport>();
        for (Suborder suborder : suborders) {
            // get all timereports for this suborder AND employee contract...
            List<Timereport> specificTimereports = getSession().createQuery("from Timereport t " +
                    "where t.employeecontract.id = ? and t.suborder.id = ? and t.suborder.customerorder.id = ? " +
                    "order by employeecontract.employee.sign asc, referenceday.refdate desc, sequencenumber asc")
                    .setLong(0, contractId).setLong(1, suborder.getId()).setLong(2, coId).setCacheable(true).list();
            for (Timereport specificTimereport : specificTimereports) {
                // if timereport belongs to reference month/year, add it to result list...
                if (sortOfReport != null && specificTimereport.getSortofreport().equals("W")
                        && TimereportHelper.getMonthStringFromTimereport(specificTimereport).equalsIgnoreCase(month)
                        && TimereportHelper.getYearStringFromTimereport(specificTimereport).equalsIgnoreCase(year)) {
                    allTimereports.add(specificTimereport);
                }
            }
        }
        return allTimereports;
    }
    
    /**
     * Gets a list of Timereports by employee contract id and customer id and date.
     * 
     * @param long contractId
     * @param long coId
     * @param java.sql.Date dt
     * @param String sortOfReport
     * 
     * @return List<Timereport>
     */
    @SuppressWarnings("unchecked")
    public List<Timereport> getTimereportsByDateAndEmployeeContractIdAndCustomerorderId(long contractId, long coId, java.sql.Date dt, String sortOfReport) {
        List<Suborder> suborders = suborderDAO.getSubordersByEmployeeContractId(contractId);
        List<Timereport> allTimereports = new ArrayList<Timereport>();
        for (Suborder suborder : suborders) {
            // get all timereports for this suborder...
            List<Timereport> specificTimereports = getSession().createQuery("from Timereport t " +
                    "where t.referenceday.refdate = ? and t.suborder.id = ? and t.suborder.customerorder.id = ? " +
                    "order by employeecontract.employee.sign asc, referenceday.refdate desc, sequencenumber asc")
                    .setDate(0, dt).setLong(1, suborder.getId()).setLong(2, coId).setCacheable(true).list();
            for (Timereport specificTimereport : specificTimereports) {
                // if timereport belongs to reference month/year, add it to result list...
                if (sortOfReport != null && specificTimereport.getSortofreport().equals("W")) {
                    allTimereports.add(specificTimereport);
                }
            }
        }
        return allTimereports;
    }
    
    /**
     * 
     * @param begin
     * @param end
     * @param customerOrderSign
     * @return Returns a list of all timereports that are associated to the given customer order sign and are valid between the given dates. 
     */
    @SuppressWarnings("unchecked")
    public List<Timereport> getTimereportsByDatesAndCustomerOrderSign(java.sql.Date begin, java.sql.Date end, String customerOrderSign) {
        return getSession().createQuery("from Timereport t " +
                "where t.referenceday.refdate >= ? and t.referenceday.refdate <= ? and t.suborder.customerorder.sign = ? ")
                .setDate(0, begin).setDate(1, end).setString(2, customerOrderSign).setCacheable(true).list();
    }
    
    /**
     * @param end
     * @param coId
     * @return Returns a timereport thats valid between the first and the last day of the given date and belonging to employeecontractid 
     */
    @SuppressWarnings("unchecked")
    public Timereport getLastAcceptedTimereportByDateAndEmployeeContractId(java.sql.Date end, long ecId) {
        Calendar cal = Calendar.getInstance();
        cal.setTime(end);
        cal.set(Calendar.DAY_OF_MONTH, 1);
        Date firstDay = cal.getTime();
        cal.set(Calendar.DAY_OF_MONTH, cal.getActualMaximum(Calendar.DAY_OF_MONTH));
        Date lastDay = cal.getTime();
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
    public List<Timereport> getTimereportsByEmployeeorderIdInvalidForDates(java.sql.Date begin, java.sql.Date end, Long employeeOrderId) {
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
    public List<Timereport> getTimereportsBySuborderIdInvalidForDates(java.sql.Date begin, java.sql.Date end, Long suborderId) {
        return getSession().createQuery("from Timereport t " +
                "where t.employeeorder.suborder.id = ? and (t.referenceday.refdate < ? or t.referenceday.refdate > ?) " +
                "order by t.employeeorder.employeecontract.employee.sign asc, t.referenceday.refdate asc, t.employeeorder.suborder.customerorder.sign asc, t.employeeorder.suborder.sign asc")
                .setLong(0, suborderId).setDate(1, begin).setDate(2, end).setCacheable(true).list();
    }
    
    @SuppressWarnings("unchecked")
    public List<Timereport> getTimereportsByCustomerOrderIdInvalidForDates(java.sql.Date begin, java.sql.Date end, Long customerOrderId) {
        return getSession().createQuery("from Timereport t " +
                "where t.employeeorder.suborder.customerorder.id = ? and (t.referenceday.refdate < ? or t.referenceday.refdate > ?) " +
                "order by t.employeeorder.employeecontract.employee.sign asc, t.referenceday.refdate asc, t.employeeorder.suborder.customerorder.sign asc, t.employeeorder.suborder.sign asc")
                .setLong(0, customerOrderId).setDate(1, begin).setDate(2, end).setCacheable(true).list();
    }
    
    @SuppressWarnings("unchecked")
    public List<Timereport> getTimereportsByEmployeeContractIdInvalidForDates(java.sql.Date begin, java.sql.Date end, Long employeeContractId) {
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
        if (tr.getCreated() == null) {
            tr.setCreated(new java.util.Date());
            tr.setCreatedby(loginEmployee.getSign());
        } else if (changeUpdateDate) {
            tr.setLastupdate(new java.util.Date());
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
