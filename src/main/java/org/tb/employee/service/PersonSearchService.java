package org.tb.employee.service;

import static org.tb.auth.domain.AccessLevel.READ;
import static org.tb.common.GlobalConstants.EMPLOYEE_SIGN_ADM;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.tb.auth.domain.Authorized;
import org.tb.auth.domain.AuthorizedUser;
import org.tb.common.Hiding;
import org.tb.common.Validity;
import org.tb.common.palette.PaletteQuery;
import org.tb.common.util.DateUtils;
import org.tb.employee.auth.EmployeeAuthorization;
import org.tb.employee.auth.EmployeecontractAuthorization;
import org.tb.employee.domain.Employeecontract;
import org.tb.employee.domain.PersonSearchResult;
import org.tb.employee.domain.PersonSearchRow;
import org.tb.employee.persistence.EmployeeDAO;
import org.tb.employee.persistence.EmployeecontractRepository;

/**
 * Persons for the object search of the command palette (#1157). A service of its own, so that the
 * search does not become one more dependency of {@link EmployeecontractService}.
 *
 * <p>Who may be found is who may be read: a person turns up when one of their contracts passes
 * {@link EmployeecontractAuthorization} with READ — everyone for a manager, oneself, and for a people
 * lead the contracts they supervise, ended ones included. Their master data is a question of its own
 * ({@link EmployeeAuthorization}, active team only): a people lead may open the contract of a former
 * team member but not the person, and the result says so.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Authorized
public class PersonSearchService {

  /** How many persons are checked and passed on; the palette shows the best of them. */
  private static final int PERSONS_CHECKED = 2 * PaletteQuery.HITS_PER_KIND;

  private final EmployeecontractRepository employeecontractRepository;
  private final EmployeeDAO employeeDAO;
  private final EmployeecontractAuthorization employeecontractAuthorization;
  private final EmployeeAuthorization employeeAuthorization;
  private final AuthorizedUser authorizedUser;

  public List<PersonSearchResult> getPalettePersons(PaletteQuery query) {
    var today = DateUtils.today();
    var rows = employeecontractRepository.findPaletteCandidates(query.likeWord(0), query.likeWord(1),
        query.likeWord(2), authorizedUser.isManager(), authorizedUser.getEffectiveLoginSign(),
        authorizedUser.isPeopleLead(), EMPLOYEE_SIGN_ADM, today,
        PageRequest.of(0, PaletteQuery.CANDIDATE_LIMIT));

    // one row per person: the contract that stands for them
    var representatives = new LinkedHashMap<Long, PersonSearchRow>();
    rows.forEach(row -> representatives.merge(row.employeeId(), row,
        (kept, other) -> representativeOrder(today).compare(kept, other) <= 0 ? kept : other));
    var kept = representatives.values().stream()
        .sorted(Comparator.comparing((PersonSearchRow row) -> hidden(row))
            .thenComparing(row -> Validity.isInactive(row.validUntil()))
            .thenComparing(Comparator.comparingInt((PersonSearchRow row) -> query.match(name(row), row.sign(),
                row.lastname())).reversed()))
        .limit(PERSONS_CHECKED)
        .toList();
    if (kept.isEmpty()) {
      return List.of();
    }

    var contracts = employeecontractRepository.findAllForAuthorizationByIdIn(
            kept.stream().map(PersonSearchRow::contractId).toList()).stream()
        .collect(Collectors.toMap(Employeecontract::getId, Function.identity()));
    var activeTeam = employeeDAO.getActiveTeamEmployeeIds();
    return kept.stream()
        .map(row -> {
          var contract = contracts.get(row.contractId());
          if (contract == null || !employeecontractAuthorization.isAuthorized(contract, READ)) {
            return null;
          }
          var mayViewEmployee = employeeAuthorization.isAuthorized(contract.getEmployee(), READ, activeTeam);
          return new PersonSearchResult(row.contractId(), row.employeeId(), row.sign(), name(row),
              row.validFrom(), row.validUntil(), hidden(row), mayViewEmployee);
        })
        .filter(Objects::nonNull)
        .toList();
  }

  /**
   * Which contract stands for a person, best first: one that is not hidden before a hidden one; a
   * running contract before the next one to start, and that before the one ended last.
   */
  static Comparator<PersonSearchRow> representativeOrder(LocalDate today) {
    return Comparator.comparing(PersonSearchService::hidden)
        .thenComparingInt(row -> phase(row, today))
        .thenComparingLong(row -> switch (phase(row, today)) {
          case 0 -> row.validFrom() == null ? 0 : -row.validFrom().toEpochDay(); // running: the latest start
          case 1 -> row.validFrom().toEpochDay();                                // future: the next start
          default -> -row.validUntil().toEpochDay();                             // ended: the latest end
        });
  }

  /** 0 running today, 1 starting later, 2 ended — the end alone decides "ended" (ADR-0029). */
  private static int phase(PersonSearchRow row, LocalDate today) {
    if (Validity.isInactive(row.validUntil())) {
      return 2;
    }
    return row.validFrom() != null && row.validFrom().isAfter(today) ? 1 : 0;
  }

  private static boolean hidden(PersonSearchRow row) {
    return Hiding.isHidden(row.contractHide()) || Hiding.isHidden(row.employeeHide());
  }

  private static String name(PersonSearchRow row) {
    return (Objects.toString(row.firstname(), "") + " " + Objects.toString(row.lastname(), "")).trim();
  }
}
