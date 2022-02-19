package org.tb.persistence;

import org.hibernate.Hibernate;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.tb.bdom.*;

import java.util.Collections;
import java.util.Date;
import java.util.List;

@Component
public class EmployeecontractDAO extends AbstractDAO {

    private final VacationDAO vacationDAO;
    private final OvertimeDAO overtimeDAO;
    private final EmployeecontractRepository employeecontractRepository;

    @Autowired
    public EmployeecontractDAO(SessionFactory sessionFactory, VacationDAO vacationDAO, OvertimeDAO overtimeDAO,
        EmployeecontractRepository employeecontractRepository) {
        super(sessionFactory);
        this.vacationDAO = vacationDAO;
        this.overtimeDAO = overtimeDAO;
        this.employeecontractRepository = employeecontractRepository;
    }

    /**
     * Gets the EmployeeContract with the given employee id, that is valid for the given date.
     */
    public Employeecontract getEmployeeContractByEmployeeIdAndDate(long employeeId, Date date) {
        return (Employeecontract) getSession()
                .createQuery("from Employeecontract e where e.employee.id = ? and e.validFrom <= ? and (e.validUntil >= ? or e.validUntil = null)")
                .setLong(0, employeeId)
                .setDate(1, date)
                .setDate(2, date)
                .uniqueResult();
    }

    /**
     * Gets the EmployeeContract with the given id.
     *
     * @param id
     * @return Employeecontract
     */
    public Employeecontract getEmployeeContractById(long id) {
        return employeecontractRepository.findById(id).orElse(null);
    }

    /**
     * Gets the EmployeeContract with the given id and concretly initialize vacations.
     *
     * @param id
     * @return Employeecontract
     */
    public Employeecontract getEmployeeContractByIdInitializeEager(long id) {
        Session session = getSession();
        Employeecontract ec = (Employeecontract) session
                .createQuery("from Employeecontract ec where ec.id = ?")
                .setLong(0, id)
                .uniqueResult();
        Hibernate.initialize(ec.getVacations());
        return ec;
    }

    /**
     * Calls {@link EmployeecontractDAO#save(Employeecontract, Employee)} with {@link Employee} = null.
     *
     * @param ec
     */
    public void save(Employeecontract ec) {
        save(ec, null);
    }

    /**
     * Saves the given Employeecontract and sets creation-/update-user and creation-/update-date.
     *
     * @param ec
     */
    public void save(Employeecontract ec, Employee loginEmployee) {
        employeecontractRepository.save(ec);
    }

    /**
     * Get a list of all Employeecontracts ordered by lastname.
     *
     * @return List<Employeecontract>
     */
    @SuppressWarnings("unchecked")
    public List<Employeecontract> getEmployeeContracts() {
        return getSession()
                .createQuery("from Employeecontract e order by employee.lastname asc, validFrom asc")
                .list();
    }

    /**
     * Get a list of all Employeecontracts ordered by lastname.
     *
     * @return List<Employeecontract>
     */
    @SuppressWarnings("unchecked")
    public List<Employeecontract> getTeamContracts(Long supervisorId) {
        Date now = new Date();
        return getSession()
                .createQuery("from Employeecontract ec " +
                                     "where supervisor.id = ? " +
                                     "and validFrom <= ? " +
                                     "and (validUntil = null " +
                                     "or validUntil >= ?) " +
                                     "order by employee.lastname asc, validFrom asc")
                .setLong(0, supervisorId)
                .setDate(1, now)
                .setDate(2, now)
                .list();
    }

    /**
     * Get a list of all Employeecontracts fitting to the given filters ordered by lastname.
     *
     * @return List<Employeecontract>
     */
    @SuppressWarnings("unchecked")
    public List<Employeecontract> getEmployeeContractsByFilters(Boolean showInvalid, String filter, Long employeeId) {
        List<Employeecontract> employeeContracts = null;
        boolean isFilter = filter != null && !filter.trim().isEmpty();
        if (isFilter) {
            filter = "%" + filter.toUpperCase() + "%";
        }
        if (showInvalid == null || !showInvalid) {
            Date now = new Date();
            if (!isFilter) {
                if (employeeId == null || employeeId == -1) {
                    // case 1
                    employeeContracts = getSession().createQuery("from Employeecontract e where " +
                                                                         "validFrom <= ? " +
                                                                         "and (validUntil = null " +
                                                                         "or validUntil >= ?) " +
                                                                         "order by employee.lastname asc, validFrom asc")
                                                    .setDate(0, now)
                                                    .setDate(1, now)
                                                    .list();
                } else {
                    // case 2
                    employeeContracts = getSession().createQuery("from Employeecontract e where " +
                                                                         "e.employee.id = ? " +
                                                                         "and validFrom <= ? " +
                                                                         "and (validUntil = null " +
                                                                         "or validUntil >= ?) " +
                                                                         "order by employee.lastname asc, validFrom asc")
                                                    .setLong(0, employeeId)
                                                    .setDate(1, now)
                                                    .setDate(2, now)
                                                    .list();
                }
            } else {
                if (employeeId == null || employeeId == -1) {
                    // case 3
                    employeeContracts = getSession().createQuery("from Employeecontract e where " +
                                                                         "(upper(e.employee.firstname) like ? " +
                                                                         "or upper(e.employee.lastname) like ? " +
                                                                         "or upper(e.taskDescription) like ? " +
                                                                         "or upper(id) like ?) " +
                                                                         "and validFrom <= ? " +
                                                                         "and (validUntil = null " +
                                                                         "or validUntil >= ?) " +
                                                                         "order by employee.lastname asc, validFrom asc")
                                                    .setString(0, filter)
                                                    .setString(1, filter)
                                                    .setString(2, filter)
                                                    .setString(3, filter)
                                                    .setDate(4, now)
                                                    .setDate(5, now)
                                                    .list();
                } else {
                    // case 4
                    employeeContracts = getSession().createQuery("from Employeecontract e where " +
                                                                         "(upper(e.employee.firstname) like ? " +
                                                                         "or upper(e.employee.lastname) like ? " +
                                                                         "or upper(e.taskDescription) like ? " +
                                                                         "or upper(id) like ?) " +
                                                                         "and e.employee.id = ? " +
                                                                         "and validFrom <= ? " +
                                                                         "and (validUntil = null " +
                                                                         "or validUntil >= ?) " +
                                                                         "order by employee.lastname asc, validFrom asc")
                                                    .setString(0, filter)
                                                    .setString(1, filter)
                                                    .setString(2, filter)
                                                    .setString(3, filter)
                                                    .setLong(4, employeeId)
                                                    .setDate(5, now)
                                                    .setDate(6, now)
                                                    .list();
                }
            }
        } else {
            if (!isFilter) {
                if (employeeId == null || employeeId == -1) {
                    // case 5
                    employeeContracts = getSession().createQuery("from Employeecontract e " +
                                                                         "order by employee.lastname asc, validFrom asc")
                                                    .list();
                } else {
                    // case 6
                    employeeContracts = getSession().createQuery("from Employeecontract e where " +
                                                                         "e.employee.id = ? " +
                                                                         "order by employee.lastname asc, validFrom asc")
                                                    .setLong(0, employeeId)
                                                    .list();
                }
            } else {
                if (employeeId == null || employeeId == -1) {
                    // case 7
                    employeeContracts = getSession().createQuery("from Employeecontract e where " +
                                                                         "upper(e.employee.firstname) like ? " +
                                                                         "or upper(e.employee.lastname) like ? " +
                                                                         "or upper(e.taskDescription) like ? " +
                                                                         "or upper(id) like ? " +
                                                                         "order by employee.lastname asc, validFrom asc")
                                                    .setString(0, filter)
                                                    .setString(1, filter)
                                                    .setString(2, filter)
                                                    .setString(3, filter)
                                                    .list();
                } else {
                    // case 8
                    employeeContracts = getSession().createQuery("from Employeecontract e where " +
                                                                         "(upper(e.employee.firstname) like ? " +
                                                                         "or upper(e.employee.lastname) like ? " +
                                                                         "or upper(e.taskDescription) like ? " +
                                                                         "or upper(id) like ?) " +
                                                                         "and e.employee.id = ? " +
                                                                         "order by employee.lastname asc, validFrom asc")
                                                    .setString(0, filter)
                                                    .setString(1, filter)
                                                    .setString(2, filter)
                                                    .setString(3, filter)
                                                    .setLong(4, employeeId)
                                                    .list();
                }
            }
        }
        return employeeContracts;
    }

    /**
     * Get a list of all Employeecontracts where the hide flag is unset or that is currently valid ordered by employee sign.
     *
     * @return List<Employeecontract>
     */
    @SuppressWarnings("unchecked")
    public List<Employeecontract> getVisibleEmployeeContractsOrderedByEmployeeSign() {
        java.util.Date date = new Date();
        Boolean hide = false;
        return getSession()
                .createQuery("from Employeecontract e where hide = ? or hide = null or (validFrom <= ? and (validUntil >= ? or validUntil = null)) order by employee.sign asc, validFrom asc")
                .setBoolean(0, hide)
                .setDate(1, date)
                .setDate(2, date)
                .list();
    }

    @SuppressWarnings("unchecked")
    public List<Employeecontract> getVisibleEmployeeContractsForEmployee(Employee loginEmployee) {
        if (loginEmployee != null && loginEmployee.isRestricted()) {
            java.util.Date date = new Date();
            return getSession()
                    .createQuery("from Employeecontract e where (validFrom <= :date and (validUntil >= :date or validUntil = null) and employee.id = :eId) order by employee.sign asc, validFrom asc")
                    .setParameter("date", date)
                    .setParameter("eId", loginEmployee.getId())
                    .list();
        } else {
            return getVisibleEmployeeContractsOrderedByEmployeeSign();
        }
    }

    /**
     * Get a list of all Employeecontracts that are currently valid, ordered by Firstname
     *
     * @return List<Employeecontract>
     */
    @SuppressWarnings("unchecked")
    public List<Employeecontract> getValidEmployeeContractsOrderedByFirstname() {
        java.util.Date date = new Date();
        return getSession()
                .createQuery("from Employeecontract e where validFrom <= ? and (validUntil >= ? or validUntil = null) order by employee.firstname asc, validFrom asc")
                .setDate(0, date)
                .setDate(1, date)
                .list();
    }

    //	/**
    //	 * 
    //	 * @param date
    //	 * @return Returns a list of all {@link Employeecontract}s that are valid for the given date.
    //	 */
    //	public List<Employeecontract> getEmployeeContractsValidForDate(java.util.Date date) {
    //		return getSession().createQuery("from Employeecontract e where e.validFrom <= ? and e.validUntil >= ? order by employee.lastname").setDate(0, date).setDate(1, date).list();
    //	}

    /**
     * Deletes the given employee contract .
     *
     * @param ecId
     * @return boolean
     */
    public boolean deleteEmployeeContractById(long ecId) {
        Employeecontract ec = getEmployeeContractById(ecId);

        if (ec != null) {
            // check if related employeeorders/timereports exist
            // if so, no deletion possible

            List<Employeeorder> employeeorders = ec.getEmployeeorders();
            if (employeeorders != null && !employeeorders.isEmpty()) {
                return false;
            }

            List<Timereport> timereports = ec.getTimereports();
            if (timereports != null && !timereports.isEmpty()) {
                return false;
            }

            // if ok for deletion, check for overtime and vacation entries and
            // delete them successively (cannot yet be done via web application)

            List<Overtime> overtimes = overtimeDAO.getOvertimesByEmployeeContractId(ecId);
            for (Overtime overtime : overtimes) {
                overtimeDAO.deleteOvertimeById(overtime.getId());
            }

            List<Vacation> allVacations = ec.getVacations();
            if (allVacations != null) {
                ec.setVacations(Collections.emptyList());
                for (Vacation va : allVacations) {
                    vacationDAO.deleteVacationById(va.getId());
                }
            }

            // finally, go for deletion of employeecontract
            employeecontractRepository.delete(ec);
            return true;
        }
        return false;
    }
}
