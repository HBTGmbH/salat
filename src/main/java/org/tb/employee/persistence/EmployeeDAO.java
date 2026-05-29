package org.tb.employee.persistence;

import static java.lang.Boolean.TRUE;
import static org.springframework.data.domain.Sort.Direction.ASC;

import com.google.common.collect.Lists;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.Sort.Order;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;
import org.tb.auth.domain.AccessLevel;
import org.tb.auth.domain.AuthorizedUser;
import org.tb.common.GlobalConstants;
import org.tb.employee.auth.EmployeeAuthorization;
import org.tb.employee.domain.Employee;
import org.tb.employee.domain.Employee_;
import org.tb.employee.domain.Employeecontract;

@Component
@RequiredArgsConstructor
public class EmployeeDAO {

    private final EmployeecontractDAO employeecontractDAO;
    private final EmployeeRepository employeeRepository;
    private final EmployeeAuthorization employeeAuthorization;
    private final AuthorizedUser authorizedUser;

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
        var supervisedIds = getSupervisedEmployeeIds();
        return employeecontractDAO.getEmployeeContracts().stream()
            .map(Employeecontract::getEmployee)
            .filter(e -> !e.getSign().equals(GlobalConstants.EMPLOYEE_SIGN_ADM))
            .filter(e -> employeeAuthorization.isAuthorized(e, AccessLevel.READ, supervisedIds))
            .distinct()
            .sorted(Comparator.comparing(Employee::getName))
            .collect(Collectors.toList());
    }

    /**
     * @return Returns all {@link Employee}s with a valid contract.
     */
    public List<Employee> getEmployeesWithValidContracts() {
        var supervisedIds = getSupervisedEmployeeIds();
        return employeecontractDAO.getEmployeeContracts().stream()
            .filter(Employeecontract::getCurrentlyValid)
            .map(Employeecontract::getEmployee)
            .filter(e -> !TRUE.equals(e.getHide()))
            .filter(e -> !e.getSign().equals(GlobalConstants.EMPLOYEE_SIGN_ADM))
            .filter(e -> employeeAuthorization.isAuthorized(e, AccessLevel.READ, supervisedIds))
            .distinct()
            .sorted(Comparator.comparing(Employee::getName))
            .collect(Collectors.toList());
    }

    private Specification<Employee> notHidden() {
        return (root, query, builder) -> builder.notEqual(root.get(Employee_.hide), TRUE);
    }

    /**
     * Get a list of all non-hidden Employees ordered by name (for dropdowns).
     */
    public List<Employee> getEmployees() {
        var supervisedIds = getSupervisedEmployeeIds();
        return employeeRepository.findAll(notHidden()).stream()
            .filter(e -> employeeAuthorization.isAuthorized(e, AccessLevel.READ, supervisedIds))
            .sorted(Comparator.comparing(Employee::getName))
            .collect(Collectors.toList());
    }

    /**
     * Get a list of Employees fitting to the given filter ordered by name (for the list view).
     */
    public List<Employee> getEmployeesByFilter(String filter, Boolean showHidden) {
        var supervisedIds = getSupervisedEmployeeIds();
        boolean hasFilter = filter != null && !filter.trim().isEmpty();
        boolean excludeHidden = !TRUE.equals(showHidden);

        if (!hasFilter && !excludeHidden) {
            var order = new Order(ASC, Employee_.LASTNAME).ignoreCase();
            return Lists.newArrayList(employeeRepository.findAll(Sort.by(order))).stream()
                .filter(e -> employeeAuthorization.isAuthorized(e, AccessLevel.READ, supervisedIds))
                .sorted(Comparator.comparing(Employee::getName))
                .collect(Collectors.toList());
        }

        Specification<Employee> spec = excludeHidden ? notHidden() : null;
        return employeeRepository.findAll(spec).stream()
            .filter(e -> employeeAuthorization.isAuthorized(e, AccessLevel.READ, supervisedIds))
            .filter(e -> !hasFilter || filterMatchesInMemory(e, filter))
            .sorted(Comparator.comparing(Employee::getName))
            .collect(Collectors.toList());
    }

    private boolean filterMatchesInMemory(Employee e, String filter) {
        var upper = filter.toUpperCase();
        return containsIgnoreCase(e.getName(), upper)
            || containsIgnoreCase(e.getLastname(), upper)
            || containsIgnoreCase(e.getFirstname(), upper)
            || containsIgnoreCase(e.getSign(), upper)
            || containsIgnoreCase(e.getLoginname(), upper);
    }

    private static boolean containsIgnoreCase(String value, String upper) {
        return value != null && value.toUpperCase().contains(upper);
    }

    public Set<Long> getSupervisedEmployeeIds() {
        if (!authorizedUser.isPeopleLead() || authorizedUser.isManager()) return Set.of();
        return employeeRepository.findByLoginname(authorizedUser.getEffectiveLoginSign())
            .map(emp -> employeecontractDAO.getTeamContracts(emp.getId()).stream()
                .map(ec -> ec.getEmployee().getId())
                .collect(Collectors.toSet()))
            .orElse(Set.of());
    }

}
