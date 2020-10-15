package org.tb.persistence;

import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.tb.bdom.Employeecontract;
import org.tb.bdom.Monthlyreport;

import java.util.List;

@Component
public class MonthlyreportDAO extends AbstractDAO {

    @Autowired
    public MonthlyreportDAO(SessionFactory sessionFactory) {
        super(sessionFactory);
    }

    /**
     * Gets the Monthlyreport for the given id.
     */
    public Monthlyreport getMonthlyreportById(long id) {
        return (Monthlyreport) getSession().createQuery("from Monthlyreport mr where mr.id = ?").setLong(0, id).uniqueResult();
    }

    /**
     * Gets the Monthlyreports for the given year and month.
     */
    public List<Monthlyreport> getMonthlyreportsByYearAndMonth(int year, int month) {

        @SuppressWarnings("unchecked")
        List<Monthlyreport> specificMonthlyreports =
                getSession().createQuery("from Monthlyreport mr where mr.year = ? and mr.month = ?").setInteger(0, year).setInteger(1, month).list();

        return specificMonthlyreports;
    }

    /**
     * Gets the Monthlyreport for the given year and month and employeecontract id.
     */
    public Monthlyreport getMonthlyreportByYearAndMonthAndEmployeecontract(long ecId, int year, int month) {

        Monthlyreport mr = (Monthlyreport)
                getSession().createQuery("from Monthlyreport mr where mr.year = ? and mr.month = ? and mr.employeecontract.id = ?").setInteger(0, year).setInteger(1, month).setLong(2, ecId).uniqueResult();

        return mr;
    }

    /**
     * Get a list of all Monthlyreports.
     */
    @SuppressWarnings("unchecked")
    public List<Monthlyreport> getMonthlyreports() {
        return getSession().createQuery("from Monthlyreport").list();
    }

    /**
     * Sets up a new Monthlyreport for given year/month/ec.
     */
    public Monthlyreport setNewReport(Employeecontract ec, int year, int month) {
        Monthlyreport mr = new Monthlyreport();
        mr.setEmployeecontract(ec);
        mr.setHourbalance(0.0);
        mr.setYear(year);
        mr.setMonth(month);
        mr.setOk_av(Boolean.FALSE);
        mr.setOk_ma(Boolean.FALSE);

        save(mr);

        return mr;
    }

    /**
     * Saves the given Monthlyreport.
     */
    public void save(Monthlyreport mr) {
        Session session = getSession();
        session.saveOrUpdate(mr);
        session.flush();
    }


    /**
     * Deletes the given Monthlyreport .
     */
    public boolean deleteMonthlyreportById(long mrId) {
        List<Monthlyreport> allMonthlyreports = getMonthlyreports();
        Monthlyreport mrToDelete = getMonthlyreportById(mrId);
        boolean mrDeleted = false;

        for (Monthlyreport mr : allMonthlyreports) {
            if (mr.getId() == mrToDelete.getId()) {
                Session session = getSession();
                session.delete(mrToDelete);
                session.flush();
                mrDeleted = true;

                break;
            }
        }

        return mrDeleted;
    }

}
