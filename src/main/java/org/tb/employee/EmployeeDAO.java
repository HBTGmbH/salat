package org.tb.employee;

import static org.springframework.data.domain.Sort.Direction.ASC;

import com.google.common.collect.Lists;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.Sort.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

@Component
@RequiredArgsConstructor
public class EmployeeDAO {

    private final EmployeecontractDAO employeecontractDAO;
    private final EmployeeRepository employeeRepository;

    /**
     * Retrieves the employee with the given loginname.
     * @return the LoginEmployee instance or <code>null</code> if no
     *         employee matches the given loginname.
     */
    public Employee getLoginEmployee(String loginname) {
        Assert.notNull(loginname, "loginname");
        return employeeRepository.findByLoginname(loginname).orElse(null);
    }

    /**
     * Gets the employee from the given sign (unique).
     */
    public Employee getEmployeeBySign(String sign) {
        return employeeRepository.findBySign(sign).orElse(null);
    }

    /**
     * Gets the employee with the given id.
     */
    public Employee getEmployeeById(long id) {
        return employeeRepository.findById(id).orElse(null);
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
    public List<Employee> getEmployees() {
        var order = new Order(ASC, Employee_.LASTNAME).ignoreCase();
        return Lists.newArrayList(employeeRepository.findAll(Sort.by(order)));
    }

    /**
     * Get a list of all Employees fitting to the given filter ordered by lastname.
     */
    public List<Employee> getEmployeesByFilter(String filter) {
        var order = new Order(ASC, Employee_.LASTNAME).ignoreCase();
        if (filter == null || filter.trim().equals("")) {
            return Lists.newArrayList(employeeRepository.findAll(Sort.by(order)));
        } else {
            var filterValue = "%" + filter.toUpperCase() + "%";
            return employeeRepository.findAll((root, query, builder) -> builder.or(
                builder.like(builder.upper(root.get(Employee_.loginname)), filterValue),
                builder.like(builder.upper(root.get(Employee_.firstname)), filterValue),
                builder.like(builder.upper(root.get(Employee_.lastname)), filterValue),
                builder.like(builder.upper(root.get(Employee_.sign)), filterValue),
                builder.like(builder.upper(root.get(Employee_.status)), filterValue)
            ));
        }
    }


    /**
     * Saves the given employee and sets creation-/update-user and creation-/update-date.
     */
    public void save(Employee employee, Employee loginEmployee) {
        employeeRepository.save(employee);
    }

    /**
     * Deletes the given employee.
     */
    public boolean deleteEmployeeById(long employeeId) {
        Employee emToDelete = getEmployeeById(employeeId);

        List<Employeecontract> employeeContracts = employeecontractDAO.getEmployeeContractsByFilters(
            true,
            null,
            employeeId
        );
        if (employeeContracts == null || employeeContracts.isEmpty()) {
            employeeRepository.delete(emToDelete);
            return true;
        }

        return false;
    }

}