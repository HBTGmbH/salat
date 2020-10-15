package org.tb.persistence;

import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.tb.GlobalConstants;
import org.tb.bdom.Employeecontract;
import org.tb.bdom.Vacation;

import java.util.List;

@Component
public class VacationDAO extends AbstractDAO {

    @Autowired
    public VacationDAO(SessionFactory sessionFactory) {
        super(sessionFactory);
    }

    /**
     * Saves the given Vacation
     */
    public void save(Vacation va) {
        Session session = getSession();
        session.saveOrUpdate(va);
        session.flush();
    }

    /**
     * Gets the Vacation for the given id.
     */
    public Vacation getVacationById(long id) {
        return (Vacation) getSession().createQuery("from Vacation va where va.id = ?").setLong(0, id).uniqueResult();
    }

    /**
     * Gets the Vacations for the given year.
     */
    public List<Vacation> getVacationsByYear(int year) {
        List<Vacation> specificVacations = getSession().createQuery("from Vacation va where va.year = ?").setInteger(0, year).list();
        return specificVacations;
    }

    /**
     * Gets the Vacation for the given year and employeecontract id.
     */
    public Vacation getVacationByYearAndEmployeecontract(long ecId, int year) {
        return (Vacation) getSession().createQuery("from Vacation va where va.year = ? and va.employeecontract.id = ?").setInteger(0, year).setLong(1, ecId).uniqueResult();
    }

    /**
     * Get a list of all Vacations.
     *
     * @return List<Vacation>
     */
    @SuppressWarnings("unchecked")
    public List<Vacation> getVacations() {
        return getSession().createQuery("from Vacation").list();
    }

    /**
     * Sets up a new Vacation for given year/ec.
     */
    public Vacation setNewVacation(Employeecontract ec, int year) {
        Vacation va = new Vacation();
        va.setEmployeecontract(ec);
        va.setYear(year);
        va.setEntitlement(GlobalConstants.VACATION_PER_YEAR);
        va.setUsed(0);
        save(va);
        return va;
    }

    /**
     * Deletes the given Vacation.
     */
    public boolean deleteVacationById(long vaId) {
        Session session = getSession();
        Vacation vaToDelete = (Vacation) session.createQuery("from Vacation va where va.id = ?").setLong(0, vaId).uniqueResult();
        if (vaToDelete != null) {
            session.delete(vaToDelete);
            session.flush();
            return true;
        } else {
            return false;
        }
    }

}
