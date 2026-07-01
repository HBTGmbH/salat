package org.tb.employee.persistence;

import static java.lang.Boolean.TRUE;
import static java.util.Comparator.comparing;
import static org.tb.common.GlobalConstants.EMPLOYEE_STATUS_ADM;

import jakarta.persistence.criteria.Predicate;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import lombok.RequiredArgsConstructor;
import org.hibernate.Hibernate;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;
import org.tb.auth.domain.AccessLevel;
import org.tb.common.GlobalConstants;
import org.tb.common.util.DateUtils;
import org.tb.employee.auth.EmployeecontractAuthorization;
import org.tb.employee.domain.Employee_;
import org.tb.employee.domain.Employeecontract;
import org.tb.employee.domain.Employeecontract_;

@Component
@RequiredArgsConstructor
public class EmployeecontractDAO {

    private final EmployeecontractRepository employeecontractRepository;
    private final EmployeecontractAuthorization employeecontractAuthorization;

    /**
     * Gets the EmployeeContract with the given employee id, that is valid for the given date.
     */
    public Employeecontract getEmployeeContractByEmployeeIdAndDate(long employeeId, LocalDate date) {
        return employeecontractRepository.findByEmployeeIdAndValidAt(employeeId, date).orElse(null);
    }

    /**
     * Gets the EmployeeContract with the given id.
     */
    public Employeecontract getEmployeecontractById(long id) {
        return employeecontractRepository.findById(id).orElse(null);
    }

    /**
     * Gets the EmployeeContract with the given id and concretly initialize vacations.
     */
    public Employeecontract getEmployeeContractByIdInitializeEager(long id) {
        return employeecontractRepository.findById(id).map(e -> {
            Hibernate.initialize(e.getVacations());
            return e;
        }).orElse(null);
    }

    /**
     * Get a list of all Employeecontracts ordered by full name.
     *
     * @return List<Employeecontract>
     */
    public List<Employeecontract> getEmployeeContracts() {
        return StreamSupport.stream(employeecontractRepository.findAll().spliterator(), false)
            .sorted(comparing((Employeecontract ec) -> ec.getEmployee().getName()).thenComparing(Employeecontract::getValidFrom))
            .collect(Collectors.toList());
    }

    /**
     * Get a list of all Employeecontracts ordered by lastname.
     *
     * @return List<Employeecontract>
     */
    public List<Employeecontract> getTeamContracts(long supervisorId) {
        LocalDate now = DateUtils.today();
        return employeecontractRepository.findAllSupervisedValidAt(supervisorId, now);
    }

    private Specification<Employeecontract> showOnlyValid() {
        LocalDate now = DateUtils.today();
        return (root, query, builder) -> builder.or(
            builder.isNull(root.get(Employeecontract_.validUntil)),
            builder.greaterThanOrEqualTo(root.get(Employeecontract_.validUntil), now)
        );
    }

    private Specification<Employeecontract> notHidden() {
        return (root, query, builder) -> builder.notEqual(root.get(Employeecontract_.hide), TRUE);
    }

    private Specification<Employeecontract> matchingEmployeeId(long employeeId) {
        return (root, query, builder) -> builder.equal(root.join(Employeecontract_.employee).get(Employee_.id), employeeId);
    }

    private boolean filterMatchesInMemory(Employeecontract ec, String filter) {
        var upper = filter.toUpperCase();
        var emp = ec.getEmployee();
        return containsIgnoreCase(emp.getName(), upper)
            || containsIgnoreCase(ec.getTaskDescription(), upper)
            || ec.getSupervisors().stream().anyMatch(s -> containsIgnoreCase(s.getName(), upper));
    }

    private static boolean containsIgnoreCase(String value, String upper) {
        return value != null && value.toUpperCase().contains(upper);
    }

    /**
     * Get a list of all Employeecontracts fitting to the given filters ordered by full name.
     *
     * @return List<Employeecontract>
     */
    public List<Employeecontract> getEmployeeContractsByFilters(Boolean showInvalid, String filter, Long employeeId, Boolean showHidden) {
        boolean isFilter = filter != null && !filter.trim().isEmpty();
        return employeecontractRepository.findAll((Specification<Employeecontract>) (root, query, builder) -> {
            Set<Predicate> predicates = new HashSet<>();
            if (!TRUE.equals(showInvalid)) {
                predicates.add(showOnlyValid().toPredicate(root, query, builder));
            }
            if (!TRUE.equals(showHidden)) {
                predicates.add(notHidden().toPredicate(root, query, builder));
            }
            if (employeeId != null && employeeId > 0) {
                predicates.add(matchingEmployeeId(employeeId).toPredicate(root, query, builder));
            }
            return builder.and(predicates.toArray(new Predicate[0]));
        }).stream()
            .filter(c -> employeecontractAuthorization.isAuthorized(c, AccessLevel.READ))
            .filter(c -> !isFilter || filterMatchesInMemory(c, filter))
            .sorted(comparing((Employeecontract e) -> e.getEmployee().getName()).thenComparing(Employeecontract::getValidFrom))
            .collect(Collectors.toList());
    }

    private List<Employeecontract> getAllVisibleEmployeeContracts() {
        return employeecontractRepository.findAllNotHidden().stream()
                .filter(c -> !c.getEmployee().getSign().equals(GlobalConstants.EMPLOYEE_SIGN_ADM))
                .sorted(comparing((Employeecontract e) -> e.getEmployee().getName()).thenComparing(Employeecontract::getValidFrom))
                .collect(Collectors.toList());
    }

    public List<Employeecontract> getVisibleEmployeeContractsForAuthorizedUser() {
        return getVisibleEmployeeContractsForAuthorizedUser(true);
    }

    public List<Employeecontract> getVisibleEmployeeContractsForAuthorizedUser(boolean limitAccess) {
        if (limitAccess) {
            return getAllVisibleEmployeeContracts().stream()
                    .filter(e -> employeecontractAuthorization.isAuthorized(e, AccessLevel.READ))
                    .collect(Collectors.toList());
        } else {
            return getAllVisibleEmployeeContracts();
        }
    }

    public List<Employeecontract> getEmployeeContractsByEmployeeId(Long employeeId) {
        return employeecontractRepository.findAllByEmployeeId(employeeId).stream()
            .sorted(comparing((Employeecontract e) -> e.getEmployee().getName()).thenComparing(Employeecontract::getValidFrom))
            .toList();
    }

  public List<Employeecontract> getVisibleEmployeeContracts() {
    return employeecontractRepository.findAllNotHidden()
        .stream()
        .filter(ec -> !Objects.equals(ec.getEmployee().getStatus(), EMPLOYEE_STATUS_ADM))
        .filter(ec -> employeecontractAuthorization.isAuthorized(ec, AccessLevel.READ))
        .sorted(comparing((Employeecontract e) -> e.getEmployee().getName()).thenComparing(Employeecontract::getValidFrom))
        .toList();
  }

}
