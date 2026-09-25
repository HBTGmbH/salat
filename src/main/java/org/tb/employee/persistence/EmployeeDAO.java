package org.tb.employee.persistence;

import static java.lang.Boolean.TRUE;
import static org.springframework.data.domain.Sort.Direction.ASC;

import com.google.common.collect.Lists;
import java.util.Comparator;
import java.util.Collection;
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
import org.tb.common.Hiding;
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
     * The employees with these ids, in one statement and without the read filter the other finders apply (#1092).
     * Whether these people may be shown is decided by the caller — see {@code EmployeeService#getEmployeesByIds}.
     */
    public List<Employee> getEmployeesByIds(Collection<Long> ids) {
        return Lists.newArrayList(employeeRepository.findAllById(ids));
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
        var supervisedIds = getActiveTeamEmployeeIds();
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
        var supervisedIds = getActiveTeamEmployeeIds();
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

    /**
     * Nicht verborgen — und {@code null} zählt als nicht verborgen, so wie
     * {@link Employee#getHide()} es liest. Die Regel steht in {@link Hiding} (#1104).
     */
    private Specification<Employee> notHidden() {
        return Hiding.notHidden(Employee_.hide);
    }

    /**
     * Get a list of all non-hidden Employees ordered by name (for dropdowns).
     */
    public List<Employee> getEmployees() {
        var supervisedIds = getActiveTeamEmployeeIds();
        return employeeRepository.findAll(notHidden()).stream()
            .filter(e -> employeeAuthorization.isAuthorized(e, AccessLevel.READ, supervisedIds))
            .sorted(Comparator.comparing(Employee::getName))
            .collect(Collectors.toList());
    }

    /**
     * Get a list of the Employees offered in a select box, ordered by name: everything not hidden,
     * plus the one carrying {@code keepSign} even if it is hidden (#956). Without that exception a
     * stored employee would drop off the record the next time it is edited, and the record could no
     * longer be saved at all.
     *
     * <p>The exception applies to {@code hide} only — the read authorization is checked for the kept
     * employee like for every other one.
     */
    public List<Employee> getSelectableEmployees(String keepSign) {
        var supervisedIds = getActiveTeamEmployeeIds();
        return employeeRepository.findAll(notHiddenOrSign(keepSign)).stream()
            .filter(e -> employeeAuthorization.isAuthorized(e, AccessLevel.READ, supervisedIds))
            .sorted(Comparator.comparing(Employee::getName))
            .collect(Collectors.toList());
    }

    private Specification<Employee> notHiddenOrSign(String keepSign) {
        if (keepSign == null || keepSign.isBlank()) {
            return notHidden();
        }
        return (root, query, builder) -> builder.or(
            notHidden().toPredicate(root, query, builder),
            builder.equal(root.get(Employee_.sign), keepSign));
    }

    /**
     * Get a list of Employees fitting to the given filter ordered by name (for the list view).
     */
    public List<Employee> getEmployeesByFilter(String filter, Boolean showHidden) {
        var supervisedIds = getActiveTeamEmployeeIds();
        boolean hasFilter = filter != null && !filter.trim().isEmpty();
        boolean excludeHidden = !TRUE.equals(showHidden);

        if (!hasFilter && !excludeHidden) {
            var order = new Order(ASC, Employee_.LASTNAME).ignoreCase();
            return Lists.newArrayList(employeeRepository.findAll(Sort.by(order))).stream()
                .filter(e -> employeeAuthorization.isAuthorized(e, AccessLevel.READ, supervisedIds))
                .sorted(Comparator.comparing(Employee::getName))
                .collect(Collectors.toList());
        }

        Specification<Employee> spec = excludeHidden ? notHidden() : (root, query, builder) -> builder.conjunction();
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

    /**
     * Wessen Stammdaten die angemeldete Teamleitung lesen darf: die Personen ihres Teams, deren
     * Vertrag nicht abgelaufen ist — laufende und im Voraus angelegte gleichermaßen (#1096,
     * → ADR-0029).
     *
     * <p>Das ist die engere der beiden Antworten auf „wen leite ich". Die weitere —
     * {@code EmployeecontractService#getTeamEmployeeIdsIncludingExpired} über
     * {@link EmployeecontractDAO#getTeamContractsIncludingExpired(long)} — trägt die Sichtbarkeit
     * von Buchungen und reicht über beendete Verträge zurück (#1092, #324): ein Vertrag endet, die
     * Verantwortung für seine Buchungen endet damit nicht — der Anspruch auf die Stammdaten der
     * Person schon. Beide liegen auf einer Umsetzung, und die Frage steht im Namen des Aufrufs.
     */
    public Set<Long> getActiveTeamEmployeeIds() {
        if (!authorizedUser.isPeopleLead() || authorizedUser.isManager()) return Set.of();
        return employeeRepository.findByLoginname(authorizedUser.getEffectiveLoginSign())
            .map(emp -> employeecontractDAO.getActiveTeamContracts(emp.getId()).stream()
                .map(ec -> ec.getEmployee().getId())
                .collect(Collectors.toSet()))
            .orElse(Set.of());
    }

}
