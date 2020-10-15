package org.tb.persistence;

import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.tb.bdom.Workingday;

import java.util.Iterator;
import java.util.List;

@Component
public class WorkingdayDAO extends AbstractDAO {

    @Autowired
    public WorkingdayDAO(SessionFactory sessionFactory) {
        super(sessionFactory);
    }

    @SuppressWarnings("unchecked")
    public List<Workingday> getWorkingdays() {
        return getSession().createQuery("from Workingday order by employeecontract.id asc, refday asc").list();
    }

    public Workingday getWorkingdayByDateAndEmployeeContractId(java.sql.Date refdate, long employeeContractId) {
        @SuppressWarnings("unchecked")
        List<Workingday> workingdays = getSession().createQuery("from Workingday w where w.refday = ? and w.employeecontract.id = ? ").setDate(0, refdate).setLong(1, employeeContractId).list();
        return workingdays != null && workingdays.size() > 0 ? workingdays.iterator().next() : null;
    }

    public Workingday getWorkingdayById(long workingdayId) {
        return (Workingday) getSession().createQuery("from Workingday w where w.id = ? ").setLong(0, workingdayId).uniqueResult();
    }

    public void save(Workingday wd) {
        Session session = getSession();
        session.saveOrUpdate(wd);
        session.flush();
    }

    /**
     * Deletes the workingday with the given id.
     */
    public boolean deleteWorkingdayById(long wdId) {
        List<Workingday> allWorkingdays = getWorkingdays();
        Workingday WorkingdayToDelete = getWorkingdayById(wdId);
        boolean wdDeleted = false;

        for (Iterator<Workingday> iter = allWorkingdays.iterator(); iter.hasNext(); ) {
            Workingday wd = (Workingday) iter.next();
            if (wd.getId() == WorkingdayToDelete.getId()) {
                Session session = getSession();
                session.delete(WorkingdayToDelete);
                session.flush();
                wdDeleted = true;
                break;
            }
        }
        return wdDeleted;
    }

}
