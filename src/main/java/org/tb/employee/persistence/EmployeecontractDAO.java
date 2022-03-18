package org.tb.employee.persistence;

import static java.lang.Boolean.TRUE;
import static java.util.Comparator.comparing;

import java.time.LocalDate;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import javax.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.hibernate.Hibernate;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;
import org.tb.auth.AuthorizedUser;
import org.tb.common.util.DateUtils;
import org.tb.dailyreport.domain.Timereport;
import org.tb.dailyreport.domain.Vacation;
import org.tb.dailyreport.persistence.VacationDAO;
import org.tb.employee.domain.Employee;
import org.tb.employee.domain.Employee_;
import org.tb.employee.domain.Employeecontract;
import org.tb.employee.domain.Employeecontract_;
import org.tb.employee.domain.Overtime;
import org.tb.order.domain.Employeeorder;

@Component
@RequiredArgsConstructor
public class EmployeecontractDAO {

    private final VacationDAO vacationDAO;
    private final OvertimeDAO overtimeDAO;
    private final EmployeecontractRepository employeecontractRepository;
    private final AuthorizedUser authorizedUser;

    /**
     * Gets the EmployeeContract with the given employee id, that is valid for the given date.
     */
    public Employeecontract getEmployeeContractByEmployeeIdAndDate(long employeeId, LocalDate date) {
        return employeecontractRepository.findByEmployeeIdAndValidAt(employeeId, date).orElse(null);
    }

    /**
     * Gets the EmployeeContract with the given id.
     */
    public Employeecontract getEmployeeContractById(long id) {
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
     * Calls {@link EmployeecontractDAO#save(Employeecontract, Employee)} with {@link Employee} = null.
     */
    public void save(Employeecontract ec) {
        employeecontractRepository.save(ec);
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
            var fromDateLess = builder.lessThanOrEqualTo(root.get(Employeecontract_.validFrom), now);
            var untilDateNullOrGreater = builder.or(
                builder.isNull(root.get(Employeecontract_.validUntil)),
                builder.greaterThanOrEqualTo(root.get(Employeecontract_.validUntil), now)
            );
            var notHidden = builder.notEqual(root.get(Employeecontract_.hide), TRUE);
            return builder.and(fromDateLess, untilDateNullOrGreater, notHidden);
        };
    }

    private Specification<Employeecontract> matchingEmployeeId(long employeeId) {
        return (root, query, builder) -> builder.equal(root.join(Employeecontract_.employee).get(Employee_.id), employeeId);
    }

    private Specification<Employeecontract> filterMatches(String filter) {
        final var filterValue = ('%' + filter + '%').toUpperCase();
        return (root, query, builder) -> builder.or(
            builder.like(builder.upper(root.get(Employeecontract_.taskDescription)), filterValue),
            builder.like(builder.upper(root.join(Employeecontract_.employee).get(Employee_.firstname)), filterValue),
            builder.like(builder.upper(root.join(Employeecontract_.employee).get(Employee_.lastname)), filterValue),
            builder.like(builder.upper(root.join(Employeecontract_.employee).get(Employee_.sign)), filterValue),
            builder.like(builder.upper(root.join(Employeecontract_.employee).get(Employee_.loginname)), filterValue)
        );
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
            .filter(c -> authorizedUser.isManager() || c.getEmployee().getId().equals(authorizedUser.getEmployeeId()))
            .sorted(comparing((Employeecontract e) -> e.getEmployee().getLastname()).thenComparing(Employeecontract::getValidFrom))
                .collect(Collectors.toList());
    }

    /**
     * Get a list of all Employeecontracts where the hide flag is unset or that is currently valid ordered by employee sign.
     *
     * @return List<Employeecontract>
     */
    public List<Employeecontract> getVisibleEmployeeContractsOrderedByEmployeeSign() {
        return employeecontractRepository.findAllValidAtAndNotHidden(DateUtils.today()).stream()
            .sorted(comparing((Employeecontract a) -> a.getEmployee().getSign().toLowerCase())
                .thenComparing(Employeecontract::getValidFrom))
            .collect(Collectors.toList());
    }

    public List<Employeecontract> getVisibleEmployeeContractsForAuthorizedUser() {
        if (!authorizedUser.isManager()) {
            // may only see his own contracts
            return getVisibleEmployeeContractsOrderedByEmployeeSign().stream()
                .filter(e -> e.getEmployee().getId().equals(authorizedUser.getEmployeeId()))
                .collect(Collectors.toList());
        } else {
            return getVisibleEmployeeContractsOrderedByEmployeeSign();
        }
    }

    /**
     * Get a list of all Employeecontracts that are currently valid, ordered by Firstname
     */
    public List<Employeecontract> getValidEmployeeContractsOrderedByFirstname() {
        return getVisibleEmployeeContractsOrderedByEmployeeSign().stream()
            .sorted(comparing((Employeecontract e) -> e.getEmployee().getFirstname())
                .thenComparing(Employeecontract::getValidFrom))
            .collect(Collectors.toList());
    }

    /**
     * Deletes the given employee contract.
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

    public List<Employeecontract> getEmployeeContractsByEmployeeId(Long employeeId) {
        return employeecontractRepository.findAllByEmployeeId(employeeId);
    }

}
