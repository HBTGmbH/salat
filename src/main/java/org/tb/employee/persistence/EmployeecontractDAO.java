package org.tb.employee.persistence;

import static java.lang.Boolean.TRUE;
import static java.util.Comparator.comparing;

import jakarta.persistence.criteria.Predicate;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import lombok.RequiredArgsConstructor;
import org.hibernate.Hibernate;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;
import org.tb.auth.domain.AccessLevel;
import org.tb.auth.domain.AuthorizedUser;
import org.tb.common.GlobalConstants;
import org.tb.common.util.DateUtils;
import org.tb.employee.auth.EmployeeAuthorization;
import org.tb.employee.domain.Employee_;
import org.tb.employee.domain.Employeecontract;
import org.tb.employee.domain.Employeecontract_;

@Component
@RequiredArgsConstructor
public class EmployeecontractDAO {

    private final EmployeecontractRepository employeecontractRepository;
    private final AuthorizedUser authorizedUser;
    private final EmployeeAuthorization employeeAuthorization;

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
     * Get a list of all Employeecontracts ordered by lastname.
     *
     * @return List<Employeecontract>
     */
    public List<Employeecontract> getEmployeeContracts() {
        return StreamSupport.stream(employeecontractRepository.findAll().spliterator(), false)
            .sorted(comparing((Employeecontract ec) -> ec.getEmployee().getLastname()).thenComparing(Employeecontract::getValidFrom))
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
        return (root, query, builder) -> {
            var untilDateNullOrGreater = builder.or(
                builder.isNull(root.get(Employeecontract_.validUntil)),
                builder.greaterThanOrEqualTo(root.get(Employeecontract_.validUntil), now)
            );
            var notHidden = builder.notEqual(root.get(Employeecontract_.hide), TRUE);
            return builder.and(untilDateNullOrGreater, notHidden);
        };
    }

    private Specification<Employeecontract> matchingEmployeeId(long employeeId) {
        return (root, query, builder) -> builder.equal(root.join(Employeecontract_.employee).get(Employee_.id), employeeId);
    }

    private Specification<Employeecontract> filterMatches(String filter) {
        final var filterValue = ('%' + filter + '%').toUpperCase();
        return (root, query, builder) -> {
            var employeeJoin = root.join(Employeecontract_.employee);
            var salatUserJoin = employeeJoin.join("salatUser");
            return builder.or(
                builder.like(builder.upper(root.get(Employeecontract_.taskDescription)), filterValue),
                builder.like(builder.upper(employeeJoin.get(Employee_.firstname)), filterValue),
                builder.like(builder.upper(employeeJoin.get(Employee_.lastname)), filterValue),
                builder.like(builder.upper(employeeJoin.get(Employee_.sign)), filterValue),
                builder.like(builder.upper(salatUserJoin.get("loginname")), filterValue)
            );
        };
    }

    /**
     * Get a list of all Employeecontracts fitting to the given filters ordered by lastname.
     *
     * @return List<Employeecontract>
     */
    public List<Employeecontract> getEmployeeContractsByFilters(Boolean showInvalid, String filter, Long employeeId) {
        return employeecontractRepository.findAll((Specification<Employeecontract>) (root, query, builder) -> {
            Set<Predicate> predicates = new HashSet<>();
            if(!TRUE.equals(showInvalid)) {
                predicates.add(showOnlyValid().toPredicate(root, query, builder));
            }
            if(employeeId != null && employeeId > 0) {
                predicates.add(matchingEmployeeId(employeeId).toPredicate(root, query, builder));
            }
            boolean isFilter = filter != null && !filter.trim().isEmpty();
            if(isFilter) {
                predicates.add(filterMatches(filter).toPredicate(root, query, builder));
            }
            return builder.and(predicates.toArray(new Predicate[0]));
        }).stream()
            .filter(c -> employeeAuthorization.isAuthorized(c.getEmployee(), AccessLevel.READ))
            .sorted(comparing((Employeecontract e) -> e.getEmployee().getLastname()).thenComparing(Employeecontract::getValidFrom))
                .collect(Collectors.toList());
    }

    /**
     * Get a list of all Employeecontracts where the hide flag is unset or that is currently valid ordered by employee sign.
     *
     * @return List<Employeecontract>
     * @param validAt
     */
    private List<Employeecontract> getAllVisibleEmployeeContractsOrderedByEmployeeSign(LocalDate validAt) {
        return employeecontractRepository.findAllValidAtAndNotHidden(validAt).stream()
            .filter(c -> !c.getEmployee().getSign().equals(GlobalConstants.EMPLOYEE_SIGN_ADM))
            .sorted(
                comparing((Employeecontract a) -> a.getEmployee().getSign().toLowerCase())
                .thenComparing(Employeecontract::getValidFrom)
            )
            .collect(Collectors.toList());
    }

    public List<Employeecontract> getTimeReportableEmployeeContractsForAuthorizedUser() {
        if (!authorizedUser.isManager()) {
            // may only see his own contracts
            return getAllVisibleEmployeeContractsOrderedByEmployeeSign(DateUtils.today()).stream()
                .filter(e -> e.getEmployee().getId().equals(authorizedUser.getEmployeeId()))
                .collect(Collectors.toList());
        } else {
            return getAllVisibleEmployeeContractsOrderedByEmployeeSign(DateUtils.today());
        }
    }

    public List<Employeecontract> getViewableEmployeeContractsForAuthorizedUser(LocalDate validAt) {
        return getViewableEmployeeContractsForAuthorizedUser(true, validAt);
    }

    public List<Employeecontract> getViewableEmployeeContractsForAuthorizedUser(boolean limitAccess, LocalDate validAt) {
        if (limitAccess) {
            // may only see his own contracts
            return getAllVisibleEmployeeContractsOrderedByEmployeeSign(validAt).stream()
                .filter(e -> employeeAuthorization.isAuthorized(e.getEmployee(), AccessLevel.READ))
                .collect(Collectors.toList());
        } else {
            return getAllVisibleEmployeeContractsOrderedByEmployeeSign(validAt);
        }
    }

    /**
     * Get a list of all Employeecontracts that are currently valid, ordered by Firstname
     * @param validAt
     */
    public List<Employeecontract> getAllVisibleEmployeeContractsValidAtOrderedByFirstname(LocalDate validAt) {
        return getAllVisibleEmployeeContractsOrderedByEmployeeSign(validAt).stream()
            .sorted(comparing((Employeecontract e) -> e.getEmployee().getFirstname())
                .thenComparing(Employeecontract::getValidFrom))
            .collect(Collectors.toList());
    }

    public List<Employeecontract> getEmployeeContractsByEmployeeId(Long employeeId) {
        return employeecontractRepository.findAllByEmployeeId(employeeId);
    }

}
