package org.tb.dailyreport.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.util.ReflectionTestUtils.setField;
import static org.tb.common.exception.ErrorCode.AA_NOT_ATHORIZED;

import java.time.Duration;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.support.MessageSourceAccessor;
import org.springframework.ui.ExtendedModelMap;
import org.tb.auth.domain.Authorized;
import org.tb.common.exception.AuthorizationException;
import org.tb.dailyreport.service.OvertimeService;
import org.tb.dailyreport.service.PublicholidayService;
import org.tb.dailyreport.service.TimereportService;
import org.tb.dailyreport.service.VacationService;
import org.tb.employee.domain.Employee;
import org.tb.employee.domain.Employeecontract;
import org.tb.employee.service.EmployeeService;
import org.tb.employee.service.EmployeecontractService;

/**
 * Welchen Vertrag das Dashboard zeigt (#1134). {@code fEmployeeContractId} ist ein Anfrageparameter,
 * den {@code UiState} sich merkt. Bis #1134 lud der Controller den Vertrag ohne Berechtigungsprüfung,
 * und jede angemeldete Person sah mit der ID eines fremden Vertrags dessen Überstundenkonto, Urlaub,
 * Stunden und Freigabestand. Ein Vertrag, den sie nicht lesen darf, fällt jetzt auf ihren eigenen
 * aktuellen Vertrag zurück.
 *
 * <p>Die Mocks sind nachsichtig, weil die übrigen Kennzahlen der Seite hier keine Rolle spielen und
 * mit ihren Leerwerten auskommen. {@code getEmployeecontractById} liefert den fremden Vertrag wie die
 * Datenbank: daran sieht der Test, ob der Controller am Lesen vorbei lädt.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DashboardContractSelectionTest {

  private static final long LOGIN_EMPLOYEE_ID = 5L;
  private static final long OWN_CONTRACT_ID = 42L;
  private static final long OTHER_CONTRACT_ID = 43L;
  private static final LocalDate OWN_RELEASE = LocalDate.parse("2026-05-29");
  private static final LocalDate OTHER_RELEASE = LocalDate.parse("2026-08-31");

  @Mock private EmployeecontractService employeecontractService;
  @Mock private EmployeeService employeeService;
  @Mock private OvertimeService overtimeService;
  @Mock private VacationService vacationService;
  @Mock private TimereportService timereportService;
  @Mock private PublicholidayService publicholidayService;
  @Mock private MessageSourceAccessor messageSourceAccessor;

  @InjectMocks private DashboardController dashboardController;

  private final Employeecontract ownContract = contract(OWN_CONTRACT_ID, OWN_RELEASE);
  private final Employeecontract otherContract = contract(OTHER_CONTRACT_ID, OTHER_RELEASE);

  @BeforeEach
  void loginWithOwnContract() {
    var loginEmployee = new Employee();
    setField(loginEmployee, "id", LOGIN_EMPLOYEE_ID);
    when(employeeService.getLoginEmployee()).thenReturn(loginEmployee);
    when(employeecontractService.getCurrentContract(LOGIN_EMPLOYEE_ID)).thenReturn(Optional.of(ownContract));
    when(employeecontractService.getEmployeecontractById(OWN_CONTRACT_ID)).thenReturn(ownContract);
    when(employeecontractService.getEmployeecontractForView(OWN_CONTRACT_ID)).thenReturn(ownContract);
    when(employeecontractService.getEmployeecontractById(OTHER_CONTRACT_ID)).thenReturn(otherContract);
  }

  @Test
  void a_contract_the_login_may_not_read_falls_back_to_the_own_one() {
    when(employeecontractService.getEmployeecontractForView(OTHER_CONTRACT_ID))
        .thenThrow(new AuthorizationException(AA_NOT_ATHORIZED));
    var model = new ExtendedModelMap();

    var view = dashboardController.dashboard(OTHER_CONTRACT_ID, model);

    // the page itself stays reachable: a remembered foreign id must not lock the start page
    assertThat(view).isEqualTo("dailyreport/dashboard");
    assertThat(model.getAttribute("releasedUntil")).isEqualTo(OWN_RELEASE);
    verify(overtimeService).calculateOvertime(OWN_CONTRACT_ID, false);
    verify(overtimeService, never()).calculateOvertime(OTHER_CONTRACT_ID, false);
    verify(timereportService, never())
        .getTimereportsByDatesAndEmployeeContractId(eq(OTHER_CONTRACT_ID), any(), any());
  }

  /** Manager und zuständige People Leads dürfen den Vertrag lesen; für sie ändert sich nichts. */
  @Test
  void a_contract_the_login_may_read_is_shown() {
    when(employeecontractService.getEmployeecontractForView(OTHER_CONTRACT_ID)).thenReturn(otherContract);
    var model = new ExtendedModelMap();

    dashboardController.dashboard(OTHER_CONTRACT_ID, model);

    assertThat(model.getAttribute("releasedUntil")).isEqualTo(OTHER_RELEASE);
    verify(overtimeService).calculateOvertime(OTHER_CONTRACT_ID, false);
  }

  @Test
  void an_unknown_contract_falls_back_to_the_own_one() {
    var model = new ExtendedModelMap();

    dashboardController.dashboard(99L, model);

    assertThat(model.getAttribute("releasedUntil")).isEqualTo(OWN_RELEASE);
  }

  @Test
  void the_own_contract_is_shown_without_a_selection() {
    var model = new ExtendedModelMap();

    dashboardController.dashboard(null, model);

    assertThat(model.getAttribute("releasedUntil")).isEqualTo(OWN_RELEASE);
  }

  /** Wie die übrigen Controller, aber ohne requireUnrestricted: restricted erreicht sein Dashboard. */
  @Test
  void the_controller_requires_a_login_but_admits_restricted() {
    var authorized = DashboardController.class.getAnnotation(Authorized.class);

    assertThat(authorized).isNotNull();
    assertThat(authorized.requiresAuthentication()).isTrue();
    assertThat(authorized.requireUnrestricted()).isFalse();
    assertThat(authorized.requiresPeopleLead()).isFalse();
    assertThat(authorized.requiresManager()).isFalse();
  }

  private static Employeecontract contract(long id, LocalDate releasedUntil) {
    var contract = new Employeecontract();
    setField(contract, "id", id);
    contract.setEmployee(new Employee());
    contract.setValidFrom(LocalDate.parse("2020-01-01"));
    contract.setDailyWorkingTime(Duration.ofHours(8));
    contract.setReportReleaseDate(releasedUntil);
    return contract;
  }

}
