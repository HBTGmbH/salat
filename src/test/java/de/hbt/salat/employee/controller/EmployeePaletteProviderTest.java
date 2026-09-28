package de.hbt.salat.employee.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static de.hbt.salat.common.palette.PaletteKind.PERSON;

import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import de.hbt.salat.auth.domain.AuthorizedUser;
import de.hbt.salat.common.palette.PaletteHit;
import de.hbt.salat.common.palette.PaletteQuery;
import de.hbt.salat.common.palette.PaletteTarget;
import de.hbt.salat.common.palette.PaletteText;
import de.hbt.salat.common.test.FixedClock;
import de.hbt.salat.employee.domain.PersonSearchResult;
import de.hbt.salat.employee.service.PersonSearchService;

/**
 * Where a person in the command palette leads (#1157). Who is found at all, the service has decided;
 * this class decides the targets, and none of them may answer 403. The person and the contract pages
 * refuse a restricted user whatever the record, so a restricted user finds themselves without a
 * target of this module. A manager edits, everybody else views, and the master data only where
 * {@link PersonSearchResult#mayViewEmployee()} allows it — a people lead may open the contract of a
 * former team member, but not the person.
 *
 * <p>The hit is keyed by the contract, not by the person: the booking module adds the daily and the
 * matrix view of that contract to it.
 */
@ExtendWith(MockitoExtension.class)
@FixedClock("2026-06-25T10:15:30")
@DisplayNameGeneration(ReplaceUnderscores.class)
class EmployeePaletteProviderTest {

  private static final LocalDate TODAY = LocalDate.of(2026, 6, 25);
  private static final LocalDate START = LocalDate.of(2024, 4, 1);
  private static final long EMPLOYEE_ID = 7L;
  private static final long CONTRACT_ID = 42L;
  private static final PaletteText PERSON_LABEL = PaletteText.of("main.palette.target.person.employee");
  private static final PaletteText CONTRACT_LABEL = PaletteText.of("main.palette.target.person.contract");

  @Mock
  private PersonSearchService personSearchService;

  @Mock
  private AuthorizedUser authorizedUser;

  @InjectMocks
  private EmployeePaletteProvider provider;

  private final PaletteQuery query = PaletteQuery.of("person");

  // --- the targets per role -------------------------------------------------------------------

  @Test
  void a_restricted_user_finds_themselves_without_a_target_of_this_module() {
    when(authorizedUser.isRestricted()).thenReturn(true);

    var hit = onlyHit(person(START, null, true));

    assertThat(hit.key()).isEqualTo("42");
    assertThat(hit.targets()).isEmpty();
  }

  @Test
  void a_manager_edits_the_person_and_the_contract() {
    loginAsManager();

    var hit = onlyHit(person(START, null, true));

    assertThat(hit.targets()).containsExactly(
        new PaletteTarget(PERSON_LABEL, "/employees/edit?id=7", PaletteTarget.OPEN),
        new PaletteTarget(CONTRACT_LABEL, "/employees/contracts/edit?id=42", 1));
  }

  @Test
  void an_employee_views_the_person_and_the_contract() {
    loginWithoutManagerRole();

    var hit = onlyHit(person(START, null, true));

    assertThat(hit.targets()).containsExactly(
        new PaletteTarget(PERSON_LABEL, "/employees/view?id=7", PaletteTarget.OPEN),
        new PaletteTarget(CONTRACT_LABEL, "/employees/contracts/view?id=42", 1));
  }

  /** The master data belong to the active team only (#1096); the contract stays readable. */
  @Test
  void a_people_lead_views_an_active_team_member_but_only_the_contract_of_a_former_one() {
    loginWithoutManagerRole();
    var active = new PersonSearchResult(42, 7, "aaa", "Person A", START, null, false, true);
    var former = new PersonSearchResult(43, 8, "fff", "Person F", START, TODAY.minusMonths(1), false, false);

    var hits = search(active, former);

    assertThat(hits).extracting(PaletteHit::key).containsExactly("42", "43");
    assertThat(hits.get(0).targets()).extracting(PaletteTarget::href)
        .containsExactly("/employees/view?id=7", "/employees/contracts/view?id=42");
    assertThat(hits.get(1).targets()).containsExactly(
        new PaletteTarget(CONTRACT_LABEL, "/employees/contracts/view?id=43", 1));
  }

  // --- what the hit shows ---------------------------------------------------------------------

  @Test
  void the_hit_is_keyed_by_the_contract_and_titled_with_name_and_sign() {
    loginWithoutManagerRole();

    var hit = onlyHit(new PersonSearchResult(CONTRACT_ID, EMPLOYEE_ID, "ppp", "Person P", START, null, true,
        true));

    assertThat(hit.kind()).isEqualTo(PERSON);
    assertThat(hit.key()).isEqualTo("42");
    assertThat(hit.title()).isEqualTo("Person P");
    assertThat(hit.subtitle()).isEqualTo("ppp");
    assertThat(hit.hidden()).isTrue();
  }

  /** 5: the query is the sign; 4: the name begins with it; 3: the sign does. */
  @Test
  void ranks_the_hit_by_name_and_sign() {
    loginWithoutManagerRole();
    var person = person(START, null, true);
    var bySign = PaletteQuery.of("ppp");
    var byName = PaletteQuery.of("person");
    var bySignStart = PaletteQuery.of("pp");
    when(personSearchService.getPalettePersons(bySign)).thenReturn(List.of(person));
    when(personSearchService.getPalettePersons(byName)).thenReturn(List.of(person));
    when(personSearchService.getPalettePersons(bySignStart)).thenReturn(List.of(person));

    assertThat(provider.search(bySign).getFirst().match()).isEqualTo(5);
    assertThat(provider.search(byName).getFirst().match()).isEqualTo(4);
    assertThat(provider.search(bySignStart).getFirst().match()).isEqualTo(3);
  }

  @Test
  void a_running_contract_reads_since_its_start() {
    loginWithoutManagerRole();

    var hit = onlyHit(person(START, null, true));

    assertThat(hit.context()).isEqualTo(PaletteText.of("main.palette.context.contract.since", "01.04.2024"));
    assertThat(hit.ended()).isFalse();
  }

  /** The end is inclusive (ADR-0029): on its last day the contract still runs. */
  @Test
  void a_contract_ending_today_still_reads_since_its_start() {
    loginWithoutManagerRole();

    var hit = onlyHit(person(START, TODAY, true));

    assertThat(hit.context()).isEqualTo(PaletteText.of("main.palette.context.contract.since", "01.04.2024"));
    assertThat(hit.ended()).isFalse();
  }

  @Test
  void a_future_contract_reads_from_its_start() {
    loginWithoutManagerRole();

    var hit = onlyHit(person(LocalDate.of(2026, 8, 1), null, true));

    assertThat(hit.context()).isEqualTo(PaletteText.of("main.palette.context.contract.from", "01.08.2026"));
    assertThat(hit.ended()).isFalse();
  }

  @Test
  void an_ended_contract_reads_until_its_end_and_is_marked_ended() {
    loginWithoutManagerRole();

    var hit = onlyHit(person(START, LocalDate.of(2026, 3, 1), true));

    assertThat(hit.context()).isEqualTo(PaletteText.of("main.palette.context.contract.until", "01.03.2026"));
    assertThat(hit.ended()).isTrue();
  }

  @Test
  void a_contract_without_a_start_has_no_context() {
    loginWithoutManagerRole();

    var hit = onlyHit(person(null, null, true));

    assertThat(hit.context()).isNull();
    assertThat(hit.ended()).isFalse();
  }

  // --- helpers --------------------------------------------------------------------------------

  private void loginAsManager() {
    when(authorizedUser.isRestricted()).thenReturn(false);
    when(authorizedUser.isManager()).thenReturn(true);
  }

  /**
   * An employee or a people lead: the provider asks for neither role. What tells them apart reaches
   * it as {@link PersonSearchResult#mayViewEmployee()}.
   */
  private void loginWithoutManagerRole() {
    when(authorizedUser.isRestricted()).thenReturn(false);
    when(authorizedUser.isManager()).thenReturn(false);
  }

  private static PersonSearchResult person(LocalDate validFrom, LocalDate validUntil, boolean mayViewEmployee) {
    return new PersonSearchResult(CONTRACT_ID, EMPLOYEE_ID, "ppp", "Person P", validFrom, validUntil, false,
        mayViewEmployee);
  }

  private PaletteHit onlyHit(PersonSearchResult person) {
    var hits = search(person);
    assertThat(hits).hasSize(1);
    return hits.getFirst();
  }

  private List<PaletteHit> search(PersonSearchResult... persons) {
    when(personSearchService.getPalettePersons(query)).thenReturn(List.of(persons));
    return provider.search(query);
  }
}
