package org.tb.persistence;

import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.tb.bdom.Customerorder;
import org.tb.bdom.Employee;
import org.tb.bdom.Statusreport;

import java.util.Date;
import java.util.List;

@Component
public class StatusReportDAO extends AbstractDAO {

    private final StatusreportRepository statusreportRepository;

    @Autowired
    public StatusReportDAO(SessionFactory sessionFactory,
        StatusreportRepository statusreportRepository) {
        super(sessionFactory);
        this.statusreportRepository = statusreportRepository;
    }

    /**
     * Gets the {@link Statusreport} for the given id.
     */
    public Statusreport getStatusReportById(long id) {
        return (Statusreport) getSession().createQuery("from Statusreport sr where sr.id = ?").setLong(0, id).uniqueResult();
    }

    /**
     * Get a list of all {@link Statusreport}s.
     */
    @SuppressWarnings("unchecked")
    public List<Statusreport> getStatusReports() {
        return getSession().createQuery("from Statusreport sr order by sr.customerorder.sign asc, sr.sort asc, sr.fromdate asc, sr.untildate asc, sr.sender.sign asc").list();
    }

    /**
     * Get a list of all visible {@link Statusreport}s.
     */
    @SuppressWarnings("unchecked")
    public List<Statusreport> getVisibleStatusReports() {
        Date now = new Date();
        return getSession().createQuery("from Statusreport sr " +
                "where sr.customerorder.hide is null " +
                "or sr.customerorder.hide = false " +
                "or (sr.customerorder.fromDate <= ? and (sr.customerorder.untilDate = null or sr.customerorder.untilDate >= ? )) " +
                "order by sr.customerorder.sign asc, sr.sort asc, sr.fromdate asc, sr.untildate asc, sr.sender.sign asc")
                .setDate(0, now).setDate(1, now).list();
    }

    /**
     * Get a list of all {@link Statusreport}s associated with the given {@link Customerorder}.
     */
    @SuppressWarnings("unchecked")
    public List<Statusreport> getStatusReportsByCustomerOrderId(long coId) {
        return getSession().createQuery("from Statusreport sr where customerorder.id = ? " +
                "order by sr.customerorder.sign asc, sr.sort asc, sr.fromdate asc, sr.untildate asc, sr.sender.sign asc")
                .setLong(0, coId)
                .list();
    }

    /**
     * Get a list of all released final {@link Statusreport}s associated with the given {@link Customerorder}.
     */
    public List<Statusreport> getReleasedFinalStatusReportsByCustomerOrderId(long coId) {
        return statusreportRepository.getReleasedFinalStatusReportsByCustomerOrderId(coId);
    }

    /**
     * Get a list of all unreleased final {@link Statusreport}s with an untildate > the given date,
     * that are associated with the given customerOrderId, senderId.
     */
    public List<Statusreport> getUnreleasedFinalStatusReports(long customerOrderId, long senderId, Date date) {
        return statusreportRepository.getUnreleasedFinalStatusReports(customerOrderId, senderId, date);
    }

    /**
     * Get a list of all unreleased periodical {@link Statusreport}s with an untildate > the given date,
     * that are associated with the given customerOrderId, senderId.
     */
    public List<Statusreport> getUnreleasedPeriodicalStatusReports(long customerOrderId, long senderId, Date date) {
        return statusreportRepository.getUnreleasedPeriodicalStatusReports(customerOrderId, senderId, date);
    }

    /**
     * Get a list of all released but not accepted {@link Statusreport}s associated with the given recipient.
     */
    public List<Statusreport> getReleasedStatusReportsByRecipientId(long employeeId) {
        return statusreportRepository.getReleasedStatusReportsByRecipientId(employeeId);
    }

    public Date getMaxUntilDateForCustomerOrderId(long coId) {
        return statusreportRepository.getMaxUntilDateForCustomerOrderId(coId).orElse(null);
    }


    /**
     * Calls {@link StatusReportDAO#save(Statusreport, Employee)} with {@link Employee} = null.
     *
     * @param sr The {@link Statusreport} to save
     */
    public void save(Statusreport sr) {
        save(sr, null);
    }

    /**
     * Saves the given {@link Statusreport} and sets creation-/update-user and creation-/update-date.
     *
     * @param sr            The {@link Statusreport} to save
     * @param loginEmployee The login employee
     */
    public void save(Statusreport sr, Employee loginEmployee) {
        if (loginEmployee == null) {
            throw new RuntimeException("the login-user must be passed to the db");
        }
        Session session = getSession();
        java.util.Date creationDate = sr.getCreated();
        if (creationDate == null) {
            sr.setCreated(new java.util.Date());
            sr.setCreatedby(loginEmployee.getSign());
        } else {
            sr.setLastupdate(new java.util.Date());
            sr.setLastupdatedby(loginEmployee.getSign());
            Integer updateCounter = sr.getUpdatecounter();
            updateCounter = (updateCounter == null) ? 1 : updateCounter + 1;
            sr.setUpdatecounter(updateCounter);
        }
        session.saveOrUpdate(sr);
        session.flush();
    }

    /**
     * Deletes the given {@link Statusreport}.
     *
     * @param srId The id of the {@link Statusreport} to delete
     * @return boolean
     */
    public boolean deleteStatusReportById(long srId) {
        Statusreport srToDelete = getStatusReportById(srId);
        boolean srDeleted = false;

        // check if related status report still exists

        if (srToDelete != null) {
            Session session = getSession();
            session.delete(srToDelete);
            session.flush();
            srDeleted = true;
        }
        return srDeleted;
    }

}
