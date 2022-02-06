package org.tb.persistence;

import java.lang.ref.Reference;
import java.util.Optional;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.tb.bdom.Publicholiday;
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
     * Gets the referenceday for the given date. In case the
     * referenceday does not exists, create a new one.
     */
    public Referenceday getOrAddReferenceday(Date refDate) {
        Referenceday referenceday = (Referenceday) getSession()
            .createQuery("from Referenceday r where r.refdate = :refDate")
            .setDate("refDate", refDate)
            .uniqueResult();
        if(referenceday == null) {
            addReferenceday(refDate);
            referenceday = (Referenceday) getSession()
                .createQuery("from Referenceday r where r.refdate = :refDate")
                .setDate("refDate", refDate)
                .uniqueResult();
        }
        return referenceday;
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
    public void addReferenceday(Date dt) {
        Referenceday rd = new Referenceday();
        rd.setRefdate(new java.sql.Date(dt.getTime()));

        // set day of week
        String dow = DateUtils.getDoW(dt);
        rd.setDow(dow);

        // checks for public holidays
        Optional<Publicholiday> publicHoliday = publicholidayDAO.getPublicHoliday(dt);
        if (publicHoliday.isPresent()) {
            rd.setHoliday(Boolean.TRUE); // TODO warum ist das true?!? Also Sonntag ist ja nicht generell ein FEiertag, oder?
            rd.setName(publicHoliday.get().getName());
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
