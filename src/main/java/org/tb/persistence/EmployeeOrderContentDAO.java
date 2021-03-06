package org.tb.persistence;

import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.tb.bdom.Employee;
import org.tb.bdom.Employeeorder;
import org.tb.bdom.Employeeordercontent;

import java.util.List;

@Component
public class EmployeeOrderContentDAO extends AbstractDAO {

    private final EmployeeorderDAO employeeorderDAO;

    @Autowired
    public EmployeeOrderContentDAO(SessionFactory sessionFactory, EmployeeorderDAO employeeorderDAO) {
        super(sessionFactory);
        this.employeeorderDAO = employeeorderDAO;
    }

    /**
     * Gets the {@link Employeeordercontent} for the given id.
     */
    public Employeeordercontent getEmployeeOrderContentById(long id) {
        return (Employeeordercontent) getSession().createQuery("from Employeeordercontent eoc where eoc.id = ?").setLong(0, id).uniqueResult();
    }

    /**
     * Get a list of all {@link Employeeordercontent}s.
     */
    @SuppressWarnings("unchecked")
    public List<Employeeordercontent> getEmployeeOrderContents() {
        return getSession().createQuery("from Employeeordercontent order by id asc").list();
    }

    /**
     * Calls {@link EmployeeOrderContentDAO#save(Employeeordercontent, Employee)} with {@link Employee} = null.
     */
    public void save(Employeeordercontent eoc) {
        save(eoc, null);
    }

    /**
     * Saves the given {@link Employeeordercontent} and sets creation-/update-user and creation-/update-date.
     */
    public void save(Employeeordercontent eoc, Employee loginEmployee) {
        if (loginEmployee == null) {
            throw new RuntimeException("the login-user must be passed to the db");
        }
        Session session = getSession();
        java.util.Date creationDate = eoc.getCreated();
        if (creationDate == null) {
            eoc.setCreated(new java.util.Date());
            eoc.setCreatedby(loginEmployee.getSign());
        } else {
            eoc.setLastupdate(new java.util.Date());
            eoc.setLastupdatedby(loginEmployee.getSign());
            Integer updateCounter = eoc.getUpdatecounter();
            updateCounter = (updateCounter == null) ? 1 : updateCounter + 1;
            eoc.setUpdatecounter(updateCounter);
        }
        session.saveOrUpdate(eoc);
        session.flush();
    }

    /**
     * Deletes the given {@link Employeeordercontent}.
     * Important note: {@link Employeeordercontent}s are deleted, when the associated {@link Employeeorder} is deleted.
     * An {@link Employeeordercontent} must not be deleted without deleting the associated {@link Employeeorder}!
     * The {@link Employeeorder} must be deleted first!
     *
     * @param eocId The id of the {@link Employeeordercontent} to delete
     */
    public boolean deleteEmployeeOrderContentById(long eocId) {
        List<Employeeordercontent> allEmployeeOrderContents = getEmployeeOrderContents();
        Employeeordercontent eocToDelete = getEmployeeOrderContentById(eocId);
        boolean eocDeleted = false;

        for (Employeeordercontent eoc : allEmployeeOrderContents) {
            if (eoc.getId() == eocToDelete.getId()) {
                boolean deleteOk = false;


                // check if related employee order still exists
                Employeeorder employeeorder = employeeorderDAO.getEmployeeOrderByContentId(eocId);
                if (employeeorder == null) {
                    deleteOk = true;
                }

                if (deleteOk) {
                    Session session = getSession();
                    session.delete(eocToDelete);
                    session.flush();
                    eocDeleted = true;
                }
                break;
            }
        }

        return eocDeleted;
    }


}
