package org.tb.employee.persistence;

import static org.springframework.data.domain.Sort.Direction.ASC;

import com.google.common.collect.Lists;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.Sort.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;
import org.tb.auth.domain.AccessLevel;
import org.tb.auth.domain.AuthorizedUser;
import org.tb.common.GlobalConstants;
import org.tb.employee.auth.EmployeeAuthorization;
import org.tb.employee.domain.Employee;
import org.tb.employee.domain.Employee_;
import org.tb.employee.domain.Employeecontract;
import org.tb.employee.domain.SalatUser;

@Component
@RequiredArgsConstructor
public class EmployeeDAO {

    private final EmployeecontractDAO employeecontractDAO;
    private final EmployeeRepository employeeRepository;
    private final SalatUserRepository salatUserRepository;
    private final AuthorizedUser authorizedUser;
    private final EmployeeAuthorization employeeAuthorization;

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
        return employeecontractDAO.getEmployeeContracts().stream()
            .map(Employeecontract::getEmployee)
            .filter(e -> !e.getSign().equals(GlobalConstants.EMPLOYEE_SIGN_ADM))
            .distinct()
            .sorted(Comparator.comparing(Employee::getName))
            .collect(Collectors.toList());
    }

    /**
     * @return Returns all {@link Employee}s with a contract.
     */
    public List<Employee> getEmployeesWithValidContracts() {
        return employeecontractDAO.getEmployeeContracts().stream()
            .filter(Employeecontract::getCurrentlyValid)
            .map(Employeecontract::getEmployee)
            .filter(e -> !e.getSign().equals(GlobalConstants.EMPLOYEE_SIGN_ADM))
            .distinct()
            .sorted(Comparator.comparing(Employee::getName))
            .collect(Collectors.toList());
    }

    /**
     * Get a list of all Employees ordered by lastname.
     */
    public List<Employee> getEmployees() {
        var order = new Order(ASC, Employee_.LASTNAME).ignoreCase();
        return Lists.newArrayList(employeeRepository.findAll(Sort.by(order))).stream()
            .filter(e -> employeeAuthorization.isAuthorized(e, AccessLevel.READ))
            .sorted(Comparator.comparing(Employee::getName))
            .collect(Collectors.toList());
    }

    /**
     * Get a list of all Employees fitting to the given filter ordered by lastname.
     */
    public List<Employee> getEmployeesByFilter(String filter) {
        var order = new Order(ASC, Employee_.LASTNAME).ignoreCase();
        if (filter == null || filter.trim().isEmpty()) {
            return Lists.newArrayList(employeeRepository
                .findAll(Sort.by(order))).stream()
                .filter(e -> employeeAuthorization.isAuthorized(e, AccessLevel.READ))
                .sorted(Comparator.comparing(Employee::getName))
                .collect(Collectors.toList());
        } else {
            var filterValue = "%" + filter.toUpperCase() + "%";
            return employeeRepository.findAll((root, query, builder) -> {
                var salatUserJoin = root.join("salatUser");
                return builder.or(
                    builder.like(builder.upper(salatUserJoin.get("loginname")), filterValue),
                    builder.like(builder.upper(root.get(Employee_.firstname)), filterValue),
                    builder.like(builder.upper(root.get(Employee_.lastname)), filterValue),
                    builder.like(builder.upper(root.get(Employee_.sign)), filterValue),
                    builder.like(builder.upper(salatUserJoin.get("status")), filterValue)
                );
            }).stream()
                .filter(e -> employeeAuthorization.isAuthorized(e, AccessLevel.READ))
                .sorted(Comparator.comparing(Employee::getName))
                .collect(Collectors.toList());
        }
    }

}
