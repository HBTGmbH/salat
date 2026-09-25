package org.tb.employee.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
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
import org.tb.common.GlobalConstants;
import org.tb.common.LocalDateRange;
import org.tb.common.test.FixedClock;
import org.tb.employee.auth.EmployeeAuthorization;
import org.tb.employee.auth.EmployeecontractAuthorization;
import org.tb.employee.domain.Employee;
import org.tb.employee.domain.Employeecontract;

/**
 * Wen eine Teamleitung sehen darf (#1096): {@link EmployeeDAO#getActiveTeamEmployeeIds()} ist die
 * Menge, die {@code EmployeeAuthorization#isAuthorized(Employee, AccessLevel, Set)} für
 * {@code READ} befragt — also eine Autorisierungsentscheidung, keine Anzeigefrage.
 *
 * <p>Maßgeblich ist die Regel aus {@link org.tb.common.Validity} (→ ADR-0029): inaktiv ist ein
 * Vertrag, dessen <em>Ende</em> vor heute liegt. Der Beginn zählt nicht mit — ein im Voraus
 * angelegter Vertrag ist nicht inaktiv, sondern noch nicht aktiv, und die Teamleitung muss die
 * Person vor deren erstem Arbeitstag einrichten können.
 *
 * <p>Die abgelaufenen Verträge bleiben draußen, und das prüft dieser Test mit: der Sichtbereich
 * wächst um die künftigen Verträge derselben Supervisoren und um nichts sonst.
 *
 * <p>Die zweite Frage an dasselbe Team — „wen leite ich, auch rückblickend?", die Grundlage von
 * Freigabe und Abnahme (#324) — steht hier daneben, damit sichtbar bleibt, dass sie ihre
 * abgelaufenen Verträge behält.
 */
@DataJpaTest
@Import({EmployeeDAO.class, EmployeecontractDAO.class, AuthorizedUserAuditorAware.class})
@FixedClock("2026-06-25T10:15:30")
@DisplayNameGeneration(ReplaceUnderscores.class)
class SupervisedEmployeeScopeTest {

  private static final LocalDate TODAY = LocalDate.of(2026, 6, 25);
  private static final String LEAD_LOGIN = "lead";

  @Autowired
  private EmployeeDAO employeeDAO;

  @Autowired
  private EmployeecontractDAO employeecontractDAO;

  @Autowired
  private TestEntityManager entityManager;

  @MockitoBean
  private AuthorizedUser authorizedUser;

  @MockitoBean
  private EmployeeAuthorization employeeAuthorization;

  @MockitoBean
  private EmployeecontractAuthorization employeecontractAuthorization;

  private Employee lead;

  @BeforeEach
  void setUp() {
    when(authorizedUser.isAuthenticated()).thenReturn(true);
    when(authorizedUser.getLoginSign()).thenReturn(LEAD_LOGIN);
    when(authorizedUser.getEffectiveLoginSign()).thenReturn(LEAD_LOGIN);
    when(authorizedUser.isPeopleLead()).thenReturn(true);
    when(authorizedUser.isManager()).thenReturn(false);
    lead = employee("lea", LEAD_LOGIN);
  }

  /** Der Befund aus #1096: die neu eingestellte Person, deren Vertrag erst nächsten Monat beginnt. */
  @Test
  void sees_a_contract_starting_in_the_future() {
    var hired = supervised("new", TODAY.plusMonths(1), null);

    assertThat(employeeDAO.getActiveTeamEmployeeIds()).containsExactly(hired.getId());
  }

  @Test
  void does_not_see_a_contract_that_ended_yesterday() {
    supervised("old", TODAY.minusYears(1), TODAY.minusDays(1));

    assertThat(employeeDAO.getActiveTeamEmployeeIds()).isEmpty();
  }

  @Test
  void sees_a_contract_ending_today() {
    var leaving = supervised("last", TODAY.minusYears(1), TODAY);

    assertThat(employeeDAO.getActiveTeamEmployeeIds()).containsExactly(leaving.getId());
  }

  @Test
  void sees_a_contract_without_an_end() {
    var running = supervised("run", TODAY.minusYears(1), null);

    assertThat(employeeDAO.getActiveTeamEmployeeIds()).containsExactly(running.getId());
  }

  /** Das offene Ende steht in der Datenbank auch als Sentinel — für die Regel derselbe Fall. */
  @Test
  void sees_a_contract_ending_on_the_sentinel() {
    var running = supervised("snt", TODAY.minusYears(1), LocalDateRange.FINIT_UNTIL_BOUNDARY);

    assertThat(employeeDAO.getActiveTeamEmployeeIds()).containsExactly(running.getId());
  }

  /**
   * Der Nachweis zur Ausweitung: von den vier Konstellationen kommt genau eine hinzu — der künftige
   * Vertrag desselben Supervisors. Der abgelaufene bleibt draußen, der versteckte auch.
   */
  @Test
  void grows_by_the_future_contracts_and_by_nothing_else() {
    var running = supervised("run", TODAY.minusYears(1), null);
    var future = supervised("new", TODAY.plusMonths(1), TODAY.plusYears(1));
    supervised("old", TODAY.minusYears(2), TODAY.minusDays(1));
    hidden("hid", TODAY.plusMonths(1), null);
    foreign("oth", TODAY.plusMonths(1), null);

    assertThat(employeeDAO.getActiveTeamEmployeeIds())
        .containsExactlyInAnyOrder(running.getId(), future.getId());
  }

  /**
   * Die andere der beiden Fragen bleibt, wie sie war: Freigabe und Abnahme reichen über beendete
   * Verträge zurück (#324, #1092). Sie wird ausdrücklich verlangt — der Name sagt es am Aufrufer —
   * und liefert dieselbe Menge wie vor #1096, künftige Verträge eingeschlossen.
   */
  @Test
  void keeps_the_expired_contracts_for_release_and_acceptance() {
    var running = supervised("run", TODAY.minusYears(1), null);
    var future = supervised("new", TODAY.plusMonths(1), TODAY.plusYears(1));
    var gone = supervised("old", TODAY.minusYears(2), TODAY.minusDays(1));
    hidden("hid", TODAY.minusYears(1), null);

    assertThat(employeecontractDAO.getTeamContractsIncludingExpired(lead.getId()))
        .extracting(ec -> ec.getEmployee().getId())
        .containsExactlyInAnyOrder(running.getId(), future.getId(), gone.getId());
  }

  /** Ohne die Rolle gibt es keinen Sichtbereich über das Team — die Menge bleibt leer. */
  @Test
  void stays_empty_without_the_people_lead_role() {
    supervised("new", TODAY.plusMonths(1), null);
    when(authorizedUser.isPeopleLead()).thenReturn(false);

    assertThat(employeeDAO.getActiveTeamEmployeeIds()).isEmpty();
  }

  /** Eine Managerin liest ohnehin alles; die Menge ist für sie ohne Bedeutung und bleibt leer. */
  @Test
  void stays_empty_for_a_manager() {
    supervised("new", TODAY.plusMonths(1), null);
    when(authorizedUser.isManager()).thenReturn(true);

    assertThat(employeeDAO.getActiveTeamEmployeeIds()).isEmpty();
  }

  private Employee supervised(String sign, LocalDate validFrom, LocalDate validUntil) {
    return contract(sign, validFrom, validUntil, List.of(lead), false);
  }

  private Employee hidden(String sign, LocalDate validFrom, LocalDate validUntil) {
    return contract(sign, validFrom, validUntil, List.of(lead), true);
  }

  private Employee foreign(String sign, LocalDate validFrom, LocalDate validUntil) {
    return contract(sign, validFrom, validUntil, List.of(employee("sup", "other")), false);
  }

  private Employee contract(String sign, LocalDate validFrom, LocalDate validUntil,
      List<Employee> supervisors, boolean hide) {
    var employee = employee(sign, sign);
    var contract = new Employeecontract();
    contract.setEmployee(employee);
    contract.setSupervisors(supervisors);
    contract.setValidFrom(validFrom);
    contract.setValidUntil(validUntil);
    contract.setDailyWorkingTime(Duration.ofHours(8));
    contract.setOvertimeStatic(Duration.ZERO);
    contract.setHide(hide);
    entityManager.persist(contract);
    entityManager.flush();
    return employee;
  }

  private Employee employee(String sign, String loginname) {
    var user = new SalatUser();
    user.setLoginname(loginname);
    user.setStatus(GlobalConstants.EMPLOYEE_STATUS_MA);
    entityManager.persist(user);

    var employee = new Employee();
    employee.setSign(sign);
    employee.setFirstname("Vorname");
    employee.setLastname("Nachname-" + sign);
    employee.setGender(GlobalConstants.GENDER_FEMALE);
    employee.setHide(false);
    employee.setSalatUser(user);
    return entityManager.persist(employee);
  }
}
