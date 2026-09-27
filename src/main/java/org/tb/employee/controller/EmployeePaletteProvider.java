package org.tb.employee.controller;

import static org.tb.common.palette.PaletteKind.PERSON;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.tb.auth.domain.AuthorizedUser;
import org.tb.common.Validity;
import org.tb.common.palette.PaletteHit;
import org.tb.common.palette.PaletteLink;
import org.tb.common.palette.PaletteProvider;
import org.tb.common.palette.PaletteQuery;
import org.tb.common.palette.PaletteTarget;
import org.tb.common.palette.PaletteText;
import org.tb.common.util.DateUtils;
import org.tb.employee.domain.PersonSearchResult;
import org.tb.employee.service.PersonSearchService;

/**
 * Persons for the object search of the command palette (#1157, ADR-0031), keyed by the contract
 * that stands for them — the booking module adds the daily and the matrix view of that contract.
 *
 * <p>The person and the contract pages answer 403 to restricted users, whatever the record
 * ({@code requireUnrestricted} on both controllers). A restricted user still finds themselves — the
 * contract is their own —, but without a target of this module.
 */
@Component
@RequiredArgsConstructor
public class EmployeePaletteProvider implements PaletteProvider {

  private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy");

  private final PersonSearchService personSearchService;
  private final AuthorizedUser authorizedUser;

  @Override
  public List<PaletteHit> search(PaletteQuery query) {
    return personSearchService.getPalettePersons(query).stream().map(person -> hit(query, person)).toList();
  }

  private PaletteHit hit(PaletteQuery query, PersonSearchResult person) {
    var ended = Validity.isInactive(person.validUntil());
    var targets = new ArrayList<PaletteTarget>();
    if (!authorizedUser.isRestricted()) {
      var manager = authorizedUser.isManager();
      if (person.mayViewEmployee()) {
        targets.add(new PaletteTarget(PaletteText.of("main.palette.target.person.employee"),
            PaletteLink.to(manager ? "/employees/edit" : "/employees/view").param("id", person.employeeId()).build(),
            PaletteTarget.OPEN));
      }
      targets.add(new PaletteTarget(PaletteText.of("main.palette.target.person.contract"),
          PaletteLink.to(manager ? "/employees/contracts/edit" : "/employees/contracts/view")
              .param("id", person.contractId()).build(), 1));
    }
    return new PaletteHit(PERSON, String.valueOf(person.contractId()), person.name(), person.sign(),
        period(person, ended), ended, person.hidden(), query.matchWithKey(person.sign(), person.name()), targets);
  }

  /** "Vertrag seit …" for a running contract, "ab …" for one to come, "bis …" for an ended one. */
  private static PaletteText period(PersonSearchResult person, boolean ended) {
    if (ended) {
      return PaletteText.of("main.palette.context.contract.until", DATE.format(person.validUntil()));
    }
    if (person.validFrom() != null && person.validFrom().isAfter(DateUtils.today())) {
      return PaletteText.of("main.palette.context.contract.from", DATE.format(person.validFrom()));
    }
    return person.validFrom() == null ? null
        : PaletteText.of("main.palette.context.contract.since", DATE.format(person.validFrom()));
  }
}
