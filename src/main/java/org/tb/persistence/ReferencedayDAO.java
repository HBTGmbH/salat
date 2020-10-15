package org.tb.persistence;

import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.tb.bdom.Referenceday;
import org.tb.util.DateUtils;

import java.util.Date;
import java.util.List;

/**
 * DAO class for 'Referenceday'
 *
 * @author oda
 */
@Component
public class ReferencedayDAO extends AbstractDAO {

    private final PublicholidayDAO publicholidayDAO;

    @Autowired
    public ReferencedayDAO(SessionFactory sessionFactory, PublicholidayDAO publicholidayDAO) {
        super(sessionFactory);
        this.publicholidayDAO = publicholidayDAO;
    }

    /**
     * Saves the given referenceday.
     */
    public void save(Referenceday ref) {
        Session session = getSession();
        session.saveOrUpdate(ref);
        session.flush();
    }

    /**
     * Gets the referenceday for the given id.
     */
    public Referenceday getReferencedayById(long id) {
        return (Referenceday) getSession().createQuery("from Referenceday rd where rd.id = ?").setLong(0, id).uniqueResult();
    }

    /**
     * Gets the referenceday for the given date.
     */
    public Referenceday getReferencedayByDate(Date dt) {
        return (Referenceday)
                getSession().createQuery("from Referenceday r where r.refdate = ?").setDate(0, dt).uniqueResult();
    }

    /**
     * Get a list of all Referencedays.
     */
    @SuppressWarnings("unchecked")
    public List<Referenceday> getReferencedays() {
        return getSession().createQuery("from Referenceday").list();
    }

    /**
     * Adds a referenceday to database at the time when it is first referenced in a new timereport.
     */
    public void addReferenceday(java.sql.Date dt) {
        Referenceday rd = new Referenceday();
        rd.setRefdate(dt);

        // set day of week
        String dow = DateUtils.getDoW(dt);
        rd.setDow(dow);

        // checks for public holidays
        String publicHoliday = publicholidayDAO.getPublicHoliday(dt);
        if ((publicHoliday != null) && (publicHoliday.length() > 0)) {
            rd.setHoliday(Boolean.TRUE);
            rd.setName(publicHoliday);
        } else if (dow.equals("Sun")) {
            rd.setHoliday(Boolean.TRUE);
            rd.setName("");
        } else {
            rd.setHoliday(Boolean.FALSE);
            rd.setName("");
        }

        // check workingday
        if ((rd.getHoliday()) || (dow.equals("Sat")) || (dow.equals("Sun"))) {
            rd.setWorkingday(Boolean.FALSE);
        } else {
            rd.setWorkingday(Boolean.TRUE);
        }

        save(rd);
    }

}
