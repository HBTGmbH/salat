package org.tb.persistence;

import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.tb.bdom.Employee;
import org.tb.bdom.Overtime;

import java.util.List;

@Component
public class OvertimeDAO extends AbstractDAO {

    private final OvertimeRepository overtimeRepository;

    @Autowired
    public OvertimeDAO(SessionFactory sessionFactory, OvertimeRepository overtimeRepository) {
        super(sessionFactory);
        this.overtimeRepository = overtimeRepository;
    }

    /**
     * @return Returns a list with all {@link Overtime}s.
     */
    @SuppressWarnings("unchecked")
    public List<Overtime> getOvertimes() {
        return getSession().createQuery("from Overtime order by employeecontract.id asc, created asc").list();
    }

    /**
     * @return Returns a list with all {@link Overtime}s associated to the given employeeContractId.
     */
    @SuppressWarnings("unchecked")
    public List<Overtime> getOvertimesByEmployeeContractId(long employeeContractId) {
        return overtimeRepository.findAllByEmployeecontractId(employeeContractId);
    }

    /**
     * @return Returns the {@link Overtime} associated to the given id.
     */
    private Overtime getOvertimeById(long overtimeId) {
        return (Overtime) getSession().createQuery("from Overtime where id = ? ").setLong(0, overtimeId).uniqueResult();
    }

    /**
     * Calls {@link OvertimeDAO#save(Overtime, Employee)} with the given {@link Overtime} and null for the {@link Employee}.
     */
    public void save(Overtime overtime) {
        save(overtime, null);
    }

    /**
     * Saves the given overtime and sets creation-user and creation-date.
     */
    public void save(Overtime overtime, Employee loginEmployee) {
        if (loginEmployee == null) {
            throw new RuntimeException("the login-user must be passed to the db");
        }
        Session session = getSession();

        overtime.setCreated(new java.util.Date());
        overtime.setCreatedby(loginEmployee.getSign());

        session.saveOrUpdate(overtime);
        session.flush();
        session.clear();
    }

    /**
     * Deletes the {@link Overtime} associated to the given id.
     * @return Returns true, if delete action was succesful.
     */
    public boolean deleteOvertimeById(long overtimeId) {
        List<Overtime> allOvertimes = getOvertimes();
        Overtime overtimeToDelete = getOvertimeById(overtimeId);
        boolean overtimeDeleted = false;

        for (Overtime overtime : allOvertimes) {
            if (overtime.getId() == overtimeToDelete.getId()) {
                Session session = getSession();
                session.delete(overtimeToDelete);
                session.flush();
                overtimeDeleted = true;

                break;
            }
        }

        return overtimeDeleted;
    }


}
