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
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;
import org.tb.auth.domain.AccessLevel;
import org.tb.common.GlobalConstants;
import org.tb.common.Validity;
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
     * „Wen leite ich, mit laufendem oder künftigem Vertrag?" — die nicht abgelaufenen Verträge des
     * Teams, geordnet nach Nachname. Das ist der Umfang, in dem eine Teamleitung Mitarbeiterdaten
     * sehen darf.
     *
     * <p>„Nicht abgelaufen" ist die Regel aus {@link Validity} und nur sie: ein Vertrag, der erst in
     * der Zukunft beginnt, ist nicht inaktiv, sondern noch nicht aktiv, und gehört deshalb in den
     * Sichtbereich (#1096, → ADR-0029). Sonst sieht eine Teamleitung die neu eingestellte Person
     * bis zum Vertragsbeginn nicht — und nach einem Wechsel der Zuständigkeit die übernommene
     * Person erst am Stichtag.
     *
     * @see #getTeamContractsIncludingExpired(long) für die rückblickende Frage
     */
    public List<Employeecontract> getActiveTeamContracts(long supervisorId) {
        return teamContracts(supervisorId, false);
    }

    /**
     * „Wen leite ich, auch rückblickend?" — alle nicht versteckten Verträge des Teams, geordnet nach
     * Nachname, die abgelaufenen eingeschlossen.
     *
     * <p>Das ist die Frage der Freigabe und Abnahme: ein Vertrag endet, die Abnahme seiner
     * Buchungen endet damit nicht (#324). Ein Datumskriterium gibt es hier deshalb nicht;
     * entrümpelt wird allein über das {@code hide}-Flag.
     *
     * <p>Die weitere der beiden Mengen steht bewusst unter dem längeren Namen: wer sie will, sagt
     * es, und ein Versehen landet bei {@link #getActiveTeamContracts(long)} — der engeren.
     */
    public List<Employeecontract> getTeamContractsIncludingExpired(long supervisorId) {
        return teamContracts(supervisorId, true);
    }

    /**
     * Die eine Umsetzung hinter den beiden Fragen. Sie unterscheiden sich in einem Schritt, und nur
     * der steht hier: ob die abgelaufenen Verträge mitkommen.
     */
    private List<Employeecontract> teamContracts(long supervisorId, boolean includeExpired) {
        var contracts = employeecontractRepository.findAllSupervised(supervisorId);
        if (includeExpired) return contracts;
        return contracts.stream()
            .filter(ec -> !Validity.isInactive(ec.getValidUntil()))
            .toList();
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
    public List<Employeecontract> getEmployeeContractsByFilters(Boolean showInactive, String filter, Long employeeId, Boolean showHidden) {
        boolean isFilter = filter != null && !filter.trim().isEmpty();
        return employeecontractRepository.findAll((Specification<Employeecontract>) (root, query, builder) -> {
            Set<Predicate> predicates = new HashSet<>();
            if (!TRUE.equals(showInactive)) {
                predicates.add(Validity.<Employeecontract>notInactive(Employeecontract_.validUntil).toPredicate(root, query, builder));
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
