package de.hbt.salat.dailyreport.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.util.ReflectionTestUtils.setField;
import static de.hbt.salat.common.exception.ErrorCode.WD_CLOSED_REQ_ADMIN;
import static de.hbt.salat.common.exception.ErrorCode.WD_COMMITTED_NOT_SELF;
import static de.hbt.salat.common.exception.ErrorCode.WD_COMMITTED_REQ_PEOPLE_LEAD_OR_MANAGER;
import static de.hbt.salat.dailyreport.domain.Workingday.WorkingDayType.WORKED;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.transaction.PlatformTransactionManager;
import de.hbt.salat.auth.domain.AuthorizedUser;
import de.hbt.salat.auth.domain.SalatUser;
import de.hbt.salat.auth.service.AuthService;
import de.hbt.salat.common.exception.AuthorizationException;
import de.hbt.salat.common.exception.ErrorCode;
import de.hbt.salat.dailyreport.auth.TimereportAuthorization;
import de.hbt.salat.dailyreport.domain.Workingday;
import de.hbt.salat.dailyreport.persistence.PublicholidayRepository;
import de.hbt.salat.dailyreport.persistence.TimereportDAO;
import de.hbt.salat.dailyreport.persistence.WorkingdayDAO;
import de.hbt.salat.dailyreport.persistence.WorkingdayRepository;
import de.hbt.salat.dailyreport.preferences.DailyPreferenceService;
import de.hbt.salat.employee.domain.Employee;
import de.hbt.salat.employee.domain.Employeecontract;
import de.hbt.salat.employee.service.EmployeecontractService;

/**
 * Beginn, Pause und „Nicht gearbeitet" gehören zum Tag wie seine Buchungen und folgen derselben Regel (#1164): im
 * offenen Zeitraum schreiben die Person selbst und die Geschäftsführung, im freigegebenen die Geschäftsführung und die
 * zuständige People Lead, aber nie die Person selbst, im abgenommenen nur noch ein Admin.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayNameGeneration(ReplaceUnderscores.class)
class WorkingdayReportPeriodTest {

  private static final long CONTRACT_ID = 42L;
  private static final String OWNER = "own";
  private static final String PEOPLE_LEAD = "pl";
  private static final String MANAGER = "gf";

  private static final LocalDate ACCEPTED_UNTIL = LocalDate.of(2026, 3, 31);
  private static final LocalDate RELEASED_UNTIL = LocalDate.of(2026, 5, 29);
  private static final LocalDate ACCEPTED_DAY = LocalDate.of(2026, 3, 17);
  private static final LocalDate RELEASED_DAY = LocalDate.of(2026, 5, 20);
  private static final LocalDate OPEN_DAY = LocalDate.of(2026, 6, 10);

  @Mock
  private WorkingdayRepository workingdayRepository;
  @Mock
  private PublicholidayRepository publicholidayRepository;
  @Mock
  private TimereportDAO timereportDAO;
  @Mock
  private AuthorizedUser authorizedUser;
  @Mock
  private WorkingdayDAO workingdayDAO;
  @Mock
  private AuthService authService;
  @Mock
  private EmployeecontractService employeecontractService;
  @Mock
  private DailyPreferenceService dailyPreferenceService;
  @Mock
  private PlatformTransactionManager transactionManager;

  private WorkingdayService workingdayService;
  private Employeecontract contract;

  @BeforeEach
  void setUp() {
    workingdayService = new WorkingdayService(workingdayRepository, publicholidayRepository, timereportDAO,
        authorizedUser, workingdayDAO, authService, employeecontractService, dailyPreferenceService,
        transactionManager, new TimereportAuthorization(authorizedUser, authService));

    contract = new Employeecontract();
    setField(contract, "id", CONTRACT_ID);
    contract.setEmployee(employee(OWNER));
    contract.setSupervisors(new ArrayList<>(List.of(employee(PEOPLE_LEAD))));
    contract.setValidFrom(LocalDate.of(2000, 1, 1));
    contract.setReportAcceptanceDate(ACCEPTED_UNTIL);
    contract.setReportReleaseDate(RELEASED_UNTIL);

    when(employeecontractService.getEmployeecontractById(CONTRACT_ID)).thenReturn(contract);
    when(timereportDAO.getTimereportsByDateAndEmployeeContractId(anyLong(), any())).thenReturn(List.of());
    when(workingdayRepository.findByRefdayAndEmployeecontractId(any(), anyLong()))
        .thenAnswer(call -> Optional.of(storedWorkingday(call.getArgument(0))));
    when(workingdayRepository.findById(anyLong())).thenAnswer(call -> Optional.of(storedWorkingday(RELEASED_DAY)));
  }

  @Test
  void the_person_changes_their_working_day_in_the_open_period() {
    loggedInAs(OWNER, false, false);

    workingdayService.upsertWorkingday(storedWorkingday(OPEN_DAY));

    verify(workingdayRepository).save(any());
  }

  @Test
  void the_person_cannot_change_start_or_break_in_the_released_period() {
    loggedInAs(OWNER, false, false);

    assertRefused(() -> workingdayService.upsertWorkingday(storedWorkingday(RELEASED_DAY)), WD_COMMITTED_REQ_PEOPLE_LEAD_OR_MANAGER);
  }

  @Test
  void the_person_cannot_mark_a_released_day_as_not_worked() {
    loggedInAs(OWNER, false, false);

    assertRefused(() -> workingdayService.markNotWorked(contract, RELEASED_DAY), WD_COMMITTED_REQ_PEOPLE_LEAD_OR_MANAGER);
  }

  /** Das Buchungsformular legt den Arbeitstag vor der Buchung an, in eigener Transaktion (#1111). */
  @Test
  void a_booking_attempt_in_the_released_period_does_not_seed_the_working_day() {
    loggedInAs(OWNER, false, false);

    assertRefused(() -> workingdayService.seedWorkingday(CONTRACT_ID, RELEASED_DAY, 8, 0), WD_COMMITTED_REQ_PEOPLE_LEAD_OR_MANAGER);
  }

  @Test
  void the_person_cannot_delete_a_released_working_day() {
    loggedInAs(OWNER, false, false);

    assertRefused(() -> workingdayService.deleteWorkingdayById(4711L), WD_COMMITTED_REQ_PEOPLE_LEAD_OR_MANAGER);
    verify(workingdayRepository, never()).deleteById(anyLong());
  }

  @Test
  void a_manager_cannot_change_their_own_released_working_day_either() {
    contract.setEmployee(employee(MANAGER));
    loggedInAs(MANAGER, true, false);

    assertRefused(() -> workingdayService.upsertWorkingday(storedWorkingday(RELEASED_DAY)), WD_COMMITTED_NOT_SELF);
  }

  @Test
  void the_supervising_people_lead_changes_a_released_working_day_during_the_acceptance() {
    loggedInAs(PEOPLE_LEAD, false, false);

    workingdayService.upsertWorkingday(storedWorkingday(RELEASED_DAY));

    verify(workingdayRepository).save(any());
  }

  @Test
  void a_people_lead_of_somebody_else_cannot_change_a_released_working_day() {
    contract.setSupervisors(new ArrayList<>());
    loggedInAs(PEOPLE_LEAD, false, false);

    assertRefused(() -> workingdayService.upsertWorkingday(storedWorkingday(RELEASED_DAY)),
        WD_COMMITTED_REQ_PEOPLE_LEAD_OR_MANAGER);
  }

  @Test
  void a_manager_changes_a_released_working_day_of_somebody_else() {
    loggedInAs(MANAGER, true, false);

    workingdayService.upsertWorkingday(storedWorkingday(RELEASED_DAY));

    verify(workingdayRepository).save(any());
  }

  @Test
  void after_the_acceptance_not_even_a_manager_changes_the_working_day() {
    loggedInAs(MANAGER, true, false);

    assertRefused(() -> workingdayService.upsertWorkingday(storedWorkingday(ACCEPTED_DAY)), WD_CLOSED_REQ_ADMIN);
  }

  @Test
  void after_the_acceptance_the_supervising_people_lead_cannot_either() {
    loggedInAs(PEOPLE_LEAD, false, false);

    assertRefused(() -> workingdayService.upsertWorkingday(storedWorkingday(ACCEPTED_DAY)), WD_CLOSED_REQ_ADMIN);
  }

  @Test
  void an_admin_changes_the_working_day_after_the_acceptance() {
    loggedInAs("adm", true, true);

    workingdayService.upsertWorkingday(storedWorkingday(ACCEPTED_DAY));

    verify(workingdayRepository).save(any());
  }

  private void assertRefused(Runnable action, ErrorCode expected) {
    var denial = catchThrowableOfType(AuthorizationException.class, action::run);
    assertThat(denial).isNotNull();
    assertThat(denial.getMessages()).extracting(message -> message.getErrorCode()).containsExactly(expected);
    verify(workingdayRepository, never()).save(any());
  }

  private void loggedInAs(String sign, boolean manager, boolean admin) {
    when(authorizedUser.getEffectiveLoginSign()).thenReturn(sign);
    when(authorizedUser.getLoginSign()).thenReturn(sign);
    when(authorizedUser.isManager()).thenReturn(manager);
    when(authorizedUser.isAdmin()).thenReturn(admin);
    when(authorizedUser.isPeopleLead()).thenReturn(manager || PEOPLE_LEAD.equals(sign));
  }

  /** Ein gespeicherter Arbeitstag: geschrieben wird ohne eigene Transaktion. */
  private Workingday storedWorkingday(LocalDate day) {
    var workingday = new Workingday();
    setField(workingday, "id", 4711L);
    workingday.setEmployeecontract(contract);
    workingday.setRefday(day);
    workingday.setType(WORKED);
    workingday.setStarttimehour(8);
    return workingday;
  }

  private static Employee employee(String sign) {
    var salatUser = new SalatUser();
    salatUser.setLoginname(sign);
    var employee = new Employee();
    employee.setSign(sign);
    employee.setSalatUser(salatUser);
    return employee;
  }
}
