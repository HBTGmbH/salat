package org.tb.persistence;

import org.hibernate.HibernateException;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.Transaction;
import org.hibernate.criterion.Restrictions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;
import org.tb.GlobalConstants;
import org.tb.bdom.Employee;
import org.tb.bdom.Employeecontract;

import javax.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class EmployeeDAO extends AbstractDAO {

    private final EmployeecontractDAO employeecontractDAO;
    private final List<String> adminNames;

    @Autowired
    public EmployeeDAO(SessionFactory sessionFactory, EmployeecontractDAO employeecontractDAO, List<String> adminNames) {
        super(sessionFactory);
        this.employeecontractDAO = employeecontractDAO;
        this.adminNames = adminNames;
    }

    /**
     * Registers an employee in the system.
     */
    public void registerEmployee(String username, String password)
            throws EmployeeAlreadyExistsException {

        Assert.notNull(username, "loginname");
        Assert.notNull(password, "password");

        Session session = getSession();
        Transaction trx = null;
        try {
            trx = session.beginTransaction();
            Employeecontract employee = new Employeecontract();
            session.save(employee);
            trx.commit();
        } catch (HibernateException ex) {
            if (trx != null) {
                try {
                    trx.rollback();
                } catch (HibernateException exRb) {
                }
            }
            throw new RuntimeException(ex.getMessage());
        } finally {
            try {
                session.flush();
            } catch (Exception exCl) {
            }
        }
    }

    /**
     * Retrieves the employee with the given username.
     * @return the LoginEmployee instance or <code>null</code> if no
     *         employee matches the given username.
     */
    public Employee getLoginEmployee(String username) {
        Assert.notNull(username, "loginname");
        return (Employee) getSession()
                .createCriteria(Employee.class).add(
                        Restrictions.eq("loginname", username)).uniqueResult();
    }

    /**
     * Checks if the given employee is an administrator.
     */
    public boolean isAdmin(Employee employee) {
        return adminNames.contains(employee.getSign());
    }

    /**
     * Gets the employee from the given sign (unique).
     */
    public Employee getEmployeeBySign(String sign) {
        return (Employee) getSession().createQuery(
                "from Employee p where p.sign = ?").setString(0, sign).uniqueResult();
    }

    /**
     * Gets the employee with the given id.
     */
    public Employee getEmployeeById(long id) {
        return (Employee) getSession().createQuery("from Employee em where em.id = ?").setLong(0, id).uniqueResult();
    }

    /**
     * @return Returns all {@link Employee}s with a contract.
     */
    public List<Employee> getEmployeesWithContracts() {
        List<Employeecontract> employeeContracts = employeecontractDAO.getEmployeeContracts();
        List<Employee> employees = new ArrayList<>();
        for (Employeecontract employeecontract : employeeContracts) {
            if (!employees.contains(employeecontract.getEmployee())) {
                employees.add(employeecontract.getEmployee());
            }
        }
        // remove admin
        Employee admin = getEmployeeBySign("adm");
        employees.remove(admin);
        return employees;
    }

    /**
     * @return Returns all {@link Employee}s with a contract.
     */
    public List<Employee> getEmployeesWithValidContracts() {
        List<Employeecontract> employeeContracts = employeecontractDAO.getEmployeeContracts();
        List<Employee> employees = new ArrayList<>();
        for (Employeecontract employeecontract : employeeContracts) {
            if (employeecontract.getCurrentlyValid() && !employees.contains(employeecontract.getEmployee())) {
                employees.add(employeecontract.getEmployee());
            }
        }
        // remove admin
        Employee admin = getEmployeeBySign("adm");
        employees.remove(admin);
        return employees;
    }


    /**
     * Get a list of all Employees ordered by lastname.
     */
    @SuppressWarnings("unchecked")
    public List<Employee> getEmployees() {
        return getSession().createQuery("from Employee p order by upper(p.lastname)").list();
    }


    /**
     * Get a list of all Employees fitting to the given filter ordered by lastname.
     */
    @SuppressWarnings("unchecked")
    public List<Employee> getEmployeesByFilter(String filter) {
        List<Employee> employees = null;
        if (filter == null || filter.trim().equals("")) {
            employees = getSession().createQuery("from Employee p " + "order by upper(p.lastname)").list();
        } else {
            filter = "%" + filter.toUpperCase() + "%";
            employees = getSession().createQuery("from Employee p where " +
                            "upper(id) like ? " +
                            "or upper(loginname) like ? " +
                            "or upper(firstname) like ? " +
                            "or upper(lastname) like ? " +
                            "or upper(sign) like ? " +
                            "or upper(status) like ? " +
                            "order by upper(p.lastname)")
                    .setString(0, filter)
                    .setString(1, filter)
                    .setString(2, filter)
                    .setString(3, filter)
                    .setString(4, filter)
                    .setString(5, filter).list();
        }
        return employees;
    }


    /**
     * Saves the given employee and sets creation-/update-user and creation-/update-date.
     */
    public void save(Employee employee, Employee loginEmployee) {
        if (loginEmployee == null) {
            throw new RuntimeException("the login-user must be passed to the db");
        }
        Session session = getSession();
        java.util.Date creationDate = employee.getCreated();
        if (creationDate == null) {
            employee.setCreated(new java.util.Date());
            employee.setCreatedby(loginEmployee.getSign());
        } else {
            employee.setLastupdate(new java.util.Date());
            employee.setLastupdatedby(loginEmployee.getSign());
            Integer updateCounter = employee.getUpdatecounter();
            updateCounter = (updateCounter == null) ? 1 : updateCounter + 1;
            employee.setUpdatecounter(updateCounter);
        }

        if (session.contains(employee)) {
            // existing and attached to session
            session.saveOrUpdate(employee);
        } else {
            if (employee.getId() != 0L) {
                // existing but detached from session
                session.merge(employee);
            } else {
                // new object -> persist it!
                session.saveOrUpdate(employee);
            }
        }

        session.flush();
    }

    /**
     * Deletes the given employee.
     */
    public boolean deleteEmployeeById(long emId) {
        Employee emToDelete = getEmployeeById(emId);

        List<Employeecontract> employeeContracts = employeecontractDAO.getEmployeeContractsByFilters(true, null, emId);

        if (employeeContracts == null || employeeContracts.isEmpty()) {
            Session session = getSession();
            session.delete(emToDelete);
            session.flush();

            return true;
        }

        return false;
    }

}
