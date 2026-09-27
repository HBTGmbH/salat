package org.tb.employee.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.Mockito.when;
import static org.tb.common.GlobalConstants.EMPLOYEE_SIGN_ADM;

import java.time.Duration;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.tb.auth.domain.AuthorizedUser;
import org.tb.auth.domain.SalatUser;
import org.tb.auth.persistence.AuthorizedUserAuditorAware;
import org.tb.auth.service.AuthService;
import org.tb.common.GlobalConstants;
import org.tb.common.palette.PaletteQuery;
import org.tb.common.test.FixedClock;
import org.tb.employee.auth.EmployeeAuthorization;
import org.tb.employee.auth.EmployeecontractAuthorization;
import org.tb.employee.domain.Employee;
import org.tb.employee.domain.Employeecontract;
import org.tb.employee.domain.PersonSearchResult;
import org.tb.employee.domain.PersonSearchRow;
import org.tb.employee.persistence.EmployeeDAO;
import org.tb.employee.persistence.EmployeecontractDAO;

/**
 * Whom the object search of the command palette offers as a person, and with which contract
 * (#1157). Who may be found is who may read one of the person's contracts
 * ({@link EmployeecontractAuthorization} with READ): everybody for a manager, oneself for everybody,
 * and for a people lead the contracts they supervise, ended ones included. Whether the person's
 * master data may be opened as well is the narrower question of {@link EmployeeAuthorization} over
 * the active team — a people lead finds a former team member, but may open only the contract.
 *
 * <p>The query writes that visibility out a second time, so that its limit counts readable rows
 * only. The tests therefore run the real query against the real authorization classes, and only the
 * login is mocked. {@link AuthService} is mocked because READ never consults the rules.
 */
@DataJpaTest
@Import({AuthorizedUserAuditorAware.class, EmployeeDAO.class, EmployeecontractDAO.class,
    EmployeeAuthorization.class, EmployeecontractAuthorization.class, PersonSearchService.class})
@FixedClock("2026-06-25T10:15:30")
@DisplayNameGeneration(ReplaceUnderscores.class)
class PersonSearchServiceTest {

  private static final LocalDate TODAY = LocalDate.of(2026, 6, 25);
  private static final LocalDate LONG_AGO = TODAY.minusYears(3);

  @Autowired
  private PersonSearchService personSearchService;

  @Autowired
  private TestEntityManager entityManager;

  @MockitoBean
  private AuthorizedUser authorizedUser;

  @MockitoBean
  private AuthService authService;

  // --- who finds whom -------------------------------------------------------------------------

  /** Hidden persons and hidden contracts are found too, but after everybody else. */
  @Test
  void a_manager_finds_everybody_and_the_hidden_last() {
    running(person("aaa"));
    ended(person("eee"));
    contract(person("ccc"), LONG_AGO, null, true);
    running(hiddenPerson("hhh"));
    loginAsManager();

    var found = search("muster");

    assertThat(found)
        .extracting(PersonSearchResult::sign, PersonSearchResult::hidden)
        .containsExactly(tuple("aaa", false), tuple("eee", false), tuple("ccc", true), tuple("hhh", true));
    assertThat(found).allMatch(PersonSearchResult::mayViewEmployee);
  }

  @Test
  void an_employee_finds_only_themselves() {
    running(person("mmm"));
    running(person("kkk"));
    loginAsEmployee("mmm");

    assertThat(search("muster"))
        .extracting(PersonSearchResult::sign, PersonSearchResult::mayViewEmployee)
        .containsExactly(tuple("mmm", true));
  }

  /** Being named as supervisor is not enough: the contracts of a team need the people lead role. */
  @Test
  void an_employee_named_as_supervisor_without_the_role_finds_only_themselves() {
    var self = person("mmm");
    running(self);
    running(person("kkk"), self);
    loginAsEmployee("mmm");

    assertThat(signsFound("muster")).containsExactly("mmm");
  }

  /**
   * The contract of a former team member stays readable, the person does not: the master data
   * belong to the active team only (#1096), and a hit must not lead to a page answering 403.
   */
  @Test
  void a_people_lead_finds_the_team_but_may_open_only_active_members_as_a_person() {
    var lead = person("ppp");
    running(lead);
    running(person("aaa"), lead);
    contract(person("nnn"), TODAY.plusMonths(1), null, false, lead);
    ended(person("fff"), lead);
    running(person("ooo"), person("sup"));
    loginAsPeopleLead("ppp");

    assertThat(search("muster"))
        .extracting(PersonSearchResult::sign, PersonSearchResult::mayViewEmployee)
        .containsExactlyInAnyOrder(
            tuple("ppp", true),
            tuple("aaa", true),
            tuple("nnn", true),
            tuple("fff", false));
  }

  /**
   * A person who moved to another team: the people lead supervised the ended contract, not the
   * running one. The contract they may read stands for the person — the running one would answer 403.
   */
  @Test
  void a_people_lead_is_offered_the_contract_they_supervised_not_the_one_of_the_new_team() {
    var lead = person("ppp");
    var moved = person("www");
    var supervised = contract(moved, LONG_AGO, TODAY.minusMonths(1), false, lead);
    contract(moved, TODAY.minusMonths(1).plusDays(1), null, false, person("sup"));
    loginAsPeopleLead("ppp");

    assertThat(search("www"))
        .extracting(PersonSearchResult::contractId, PersonSearchResult::mayViewEmployee)
        .containsExactly(tuple(supervised.getId(), false));
  }

  @Test
  void a_restricted_user_finds_only_themselves() {
    var self = person("rrr");
    running(self);
    running(person("kkk"), self);
    loginAsRestricted("rrr");

    assertThat(search("muster"))
        .extracting(PersonSearchResult::sign, PersonSearchResult::mayViewEmployee)
        .containsExactly(tuple("rrr", true));
  }

  @Test
  void nobody_finds_the_technical_administrator() {
    running(person(EMPLOYEE_SIGN_ADM));
    running(person("aaa"));
    loginAsManager();

    assertThat(signsFound("muster")).containsExactly("aaa");
    assertThat(signsFound(EMPLOYEE_SIGN_ADM)).isEmpty();
  }

  /** An anonymized person keeps a sign carrying the database id (#966); there is no one to find. */
  @Test
  void nobody_finds_an_anonymized_person() {
    running(person("ANON-4711", "Anonymized", "User", true));
    running(person("uuu", "Person", "User", false));
    loginAsManager();

    assertThat(signsFound("user")).containsExactly("uuu");
    assertThat(signsFound("anonymized")).isEmpty();
    assertThat(signsFound("anon")).isEmpty();
  }

  // --- one result per person ------------------------------------------------------------------

  @Test
  void offers_one_result_per_person_represented_by_running_then_future_then_ended_contract() {
    var changing = person("vvv");
    contract(changing, LONG_AGO, TODAY.minusYears(1), false);
    var runningContract = contract(changing, TODAY.minusYears(1).plusDays(1), TODAY.plusMonths(6), false);
    contract(changing, TODAY.plusMonths(6).plusDays(1), null, false);
    var starting = person("www");
    contract(starting, LONG_AGO, TODAY.minusMonths(1), false);
    var futureContract = contract(starting, TODAY.plusMonths(1), null, false);
    var gone = person("xxx");
    contract(gone, LONG_AGO, LONG_AGO.plusYears(1), false);
    var lastContract = contract(gone, LONG_AGO.plusYears(1).plusDays(1), TODAY.minusMonths(1), false);
    loginAsManager();

    assertThat(search("muster"))
        .extracting(PersonSearchResult::sign, PersonSearchResult::contractId)
        .containsExactlyInAnyOrder(
            tuple("vvv", runningContract.getId()),
            tuple("www", futureContract.getId()),
            tuple("xxx", lastContract.getId()));
  }

  // --- what the words match -------------------------------------------------------------------

  @Test
  void finds_a_person_by_first_name() {
    namedPersons();

    assertThat(signsFound("testa")).containsExactlyInAnyOrder("tmf", "tbb");
  }

  @Test
  void finds_a_person_by_last_name_ignoring_case_and_by_part_of_it() {
    namedPersons();

    assertThat(signsFound("MUSTERFELD")).containsExactly("tmf");
    assertThat(signsFound("feld")).containsExactly("tmf");
  }

  @Test
  void finds_a_person_by_sign() {
    namedPersons();

    assertThat(signsFound("tbb")).containsExactly("tbb");
  }

  @Test
  void finds_a_person_by_first_and_last_name_in_either_order() {
    namedPersons();

    assertThat(signsFound("testa beispielberg")).containsExactly("tbb");
    assertThat(signsFound("beispiel tes")).containsExactly("tbb");
  }

  @Test
  void every_word_has_to_match() {
    namedPersons();

    assertThat(signsFound("testa unbekannt")).isEmpty();
  }

  /** Unescaped, {@code t_b} would match the sign {@code tbb}. */
  @Test
  void an_underscore_is_a_character_not_a_wildcard() {
    namedPersons();

    assertThat(signsFound("t_b")).isEmpty();
  }

  // --- which contract stands for a person (representativeOrder) -------------------------------

  @Test
  void a_running_contract_comes_before_a_future_one_and_that_before_an_ended_one() {
    var ended = row(1, LONG_AGO, TODAY.minusDays(1), false);
    var future = row(2, TODAY.plusDays(1), null, false);
    var running = row(3, LONG_AGO, null, false);

    assertThat(representativesFirst(ended, future, running)).containsExactly(3L, 2L, 1L);
  }

  /** The end alone decides "ended", and it is inclusive (ADR-0029). */
  @Test
  void a_contract_ending_today_is_still_running() {
    var future = row(1, TODAY.plusDays(1), null, false);
    var endingToday = row(2, LONG_AGO, TODAY, false);

    assertThat(representativesFirst(future, endingToday)).containsExactly(2L, 1L);
  }

  @Test
  void of_two_running_contracts_the_later_start_comes_first() {
    var earlier = row(1, LONG_AGO, null, false);
    var later = row(2, TODAY.minusMonths(1), null, false);

    assertThat(representativesFirst(earlier, later)).containsExactly(2L, 1L);
  }

  @Test
  void of_two_future_contracts_the_next_to_start_comes_first() {
    var afterNext = row(1, TODAY.plusMonths(6), null, false);
    var next = row(2, TODAY.plusMonths(1), TODAY.plusMonths(6).minusDays(1), false);

    assertThat(representativesFirst(afterNext, next)).containsExactly(2L, 1L);
  }

  @Test
  void of_two_ended_contracts_the_last_to_end_comes_first() {
    var first = row(1, LONG_AGO, LONG_AGO.plusYears(1), false);
    var last = row(2, LONG_AGO.plusYears(1).plusDays(1), TODAY.minusDays(1), false);

    assertThat(representativesFirst(first, last)).containsExactly(2L, 1L);
  }

  @Test
  void a_visible_contract_comes_before_a_hidden_one_even_when_it_has_ended() {
    var hiddenRunning = row(1, LONG_AGO, null, true);
    var visibleEnded = row(2, LONG_AGO, TODAY.minusDays(1), false);

    assertThat(representativesFirst(hiddenRunning, visibleEnded)).containsExactly(2L, 1L);
  }

  /** {@code hide} never set is not hidden (#1104). */
  @Test
  void a_contract_whose_hide_flag_was_never_set_counts_as_visible() {
    var hiddenRunning = row(1, LONG_AGO, null, true);
    var neverSet = row(2, LONG_AGO, TODAY.minusDays(1), null);

    assertThat(representativesFirst(hiddenRunning, neverSet)).containsExactly(2L, 1L);
  }

  // --- helpers --------------------------------------------------------------------------------

  private List<PersonSearchResult> search(String text) {
    return personSearchService.getPalettePersons(PaletteQuery.of(text));
  }

  private List<String> signsFound(String text) {
    return search(text).stream().map(PersonSearchResult::sign).toList();
  }

  private static List<Long> representativesFirst(PersonSearchRow... rows) {
    return Arrays.stream(rows)
        .sorted(PersonSearchService.representativeOrder(TODAY))
        .map(PersonSearchRow::contractId)
        .toList();
  }

  private static PersonSearchRow row(long contractId, LocalDate validFrom, LocalDate validUntil,
      Boolean contractHide) {
    return new PersonSearchRow(contractId, 1L, "ppp", "Person", "P", validFrom, validUntil, contractHide,
        false);
  }

  private void login(String loginname) {
    when(authorizedUser.isAuthenticated()).thenReturn(true);
    when(authorizedUser.getLoginSign()).thenReturn(loginname);
    when(authorizedUser.getEffectiveLoginSign()).thenReturn(loginname);
  }

  /** A manager is people lead and backoffice as well (EmployeeStatusAuthorities). */
  private void loginAsManager() {
    login("mgr");
    when(authorizedUser.isManager()).thenReturn(true);
    when(authorizedUser.isPeopleLead()).thenReturn(true);
    when(authorizedUser.isBackoffice()).thenReturn(true);
  }

  private void loginAsPeopleLead(String loginname) {
    login(loginname);
    when(authorizedUser.isPeopleLead()).thenReturn(true);
  }

  private void loginAsEmployee(String loginname) {
    login(loginname);
  }

  private void loginAsRestricted(String loginname) {
    login(loginname);
    when(authorizedUser.isRestricted()).thenReturn(true);
  }

  private void namedPersons() {
    running(person("tmf", "Testa", "Musterfeld", false));
    running(person("tbb", "Testa", "Beispielberg", false));
    loginAsManager();
  }

  private Employeecontract running(Employee employee, Employee... supervisors) {
    return contract(employee, LONG_AGO, null, false, supervisors);
  }

  private Employeecontract ended(Employee employee, Employee... supervisors) {
    return contract(employee, LONG_AGO, TODAY.minusMonths(1), false, supervisors);
  }

  private Employeecontract contract(Employee employee, LocalDate validFrom, LocalDate validUntil, boolean hide,
      Employee... supervisors) {
    var contract = new Employeecontract();
    contract.setEmployee(employee);
    contract.setSupervisors(List.of(supervisors));
    contract.setValidFrom(validFrom);
    contract.setValidUntil(validUntil);
    contract.setDailyWorkingTime(Duration.ofHours(8));
    contract.setOvertimeStatic(Duration.ZERO);
    contract.setHide(hide);
    entityManager.persist(contract);
    entityManager.flush();
    return contract;
  }

  private Employee person(String sign) {
    return person(sign, "Person", "Muster-" + sign.toUpperCase(Locale.ROOT), false);
  }

  private Employee hiddenPerson(String sign) {
    return person(sign, "Person", "Muster-" + sign.toUpperCase(Locale.ROOT), true);
  }

  /** The login name is the sign, as for everybody in these tests. */
  private Employee person(String sign, String firstname, String lastname, boolean hide) {
    var user = new SalatUser();
    user.setLoginname(sign);
    user.setStatus(GlobalConstants.EMPLOYEE_STATUS_MA);
    entityManager.persist(user);

    var employee = new Employee();
    employee.setSign(sign);
    employee.setFirstname(firstname);
    employee.setLastname(lastname);
    employee.setGender(GlobalConstants.GENDER_FEMALE);
    employee.setHide(hide);
    employee.setSalatUser(user);
    return entityManager.persist(employee);
  }
}
