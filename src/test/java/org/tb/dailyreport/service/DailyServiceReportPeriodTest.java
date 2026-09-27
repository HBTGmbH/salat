package org.tb.dailyreport.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.test.util.ReflectionTestUtils.setField;
import static org.tb.common.GlobalConstants.TIMEREPORT_STATUS_CLOSED;
import static org.tb.common.GlobalConstants.TIMEREPORT_STATUS_COMMITED;
import static org.tb.common.GlobalConstants.TIMEREPORT_STATUS_OPEN;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.tb.auth.domain.AuthorizedUser;
import org.tb.auth.domain.SalatUser;
import org.tb.auth.service.AuthService;
import org.tb.dailyreport.auth.TimereportAuthorization;
import org.tb.dailyreport.domain.ListViewData.ListDay;
import org.tb.dailyreport.domain.TimereportDTO;
import org.tb.employee.domain.Employee;
import org.tb.employee.domain.Employeecontract;
import org.tb.employee.service.EmployeecontractService;

/**
 * Tagesansicht und Liste bieten Anlegen, Bearbeiten und den Arbeitstag nur dort an, wo das Speichern gelingt, und
 * zwar je Tag (#1164). Bis dahin entschied der ganze Monat: endete die Freigabe mitten im Monat, bot die Liste an den
 * freigegebenen Tagen das Anlegen weiter an, und erst das Speichern scheiterte.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayNameGeneration(ReplaceUnderscores.class)
class DailyServiceReportPeriodTest {

  private static final long CONTRACT_ID = 42L;
  private static final String OWNER = "own";
  private static final String MANAGER = "gf";
  /** Mai 2026, freigegeben bis Freitag, den 15., abgenommen bis Freitag, den 8. */
  private static final YearMonth MAY = YearMonth.of(2026, 5);
  private static final LocalDate ACCEPTED_UNTIL = MAY.atDay(8);
  private static final LocalDate RELEASED_UNTIL = MAY.atDay(15);

  @Mock
  private TimereportService timereportService;
  @Mock
  private WorkingdayService workingdayService;
  @Mock
  private PublicholidayService publicholidayService;
  @Mock
  private OvertimeService overtimeService;
  @Mock
  private EmployeecontractService employeecontractService;
  @Mock
  private AuthorizedUser authorizedUser;
  @Mock
  private AuthService authService;

  private DailyService dailyService;
  private Employeecontract contract;

  @BeforeEach
  void setUp() {
    dailyService = new DailyService(timereportService, workingdayService, publicholidayService, overtimeService,
        employeecontractService, new TimereportAuthorization(authorizedUser, authService));

    contract = new Employeecontract();
    setField(contract, "id", CONTRACT_ID);
    contract.setEmployee(employee(OWNER));
    contract.setSupervisors(new ArrayList<>());
    contract.setValidFrom(LocalDate.of(2000, 1, 1));
    contract.setReportAcceptanceDate(ACCEPTED_UNTIL);
    contract.setReportReleaseDate(RELEASED_UNTIL);

    when(employeecontractService.getEmployeecontractById(CONTRACT_ID)).thenReturn(contract);
    when(overtimeService.calculateWorkingTimeTarget(anyLong(), any(), any())).thenReturn(Duration.ZERO);
    when(workingdayService.getEffectiveStart(any(), anyLong())).thenReturn(LocalTime.of(8, 0));
  }

  @Test
  void the_list_offers_the_person_to_book_only_after_the_release() {
    loggedInAs(OWNER, false);

    var days = dailyService.buildListView(MAY, CONTRACT_ID).days();

    assertThat(creatable(days)).allMatch(day -> day.isAfter(RELEASED_UNTIL)).startsWith(MAY.atDay(16));
  }

  @Test
  void the_list_offers_a_manager_the_released_days_of_somebody_else_but_not_the_accepted_ones() {
    loggedInAs(MANAGER, false);

    var days = dailyService.buildListView(MAY, CONTRACT_ID).days();

    assertThat(creatable(days)).startsWith(MAY.atDay(9)).allMatch(day -> day.isAfter(ACCEPTED_UNTIL));
  }

  @Test
  void the_daily_view_offers_neither_booking_nor_the_working_day_on_a_released_day() {
    loggedInAs(OWNER, false);

    var released = dailyService.buildDailyView(MAY.atDay(12), CONTRACT_ID);
    var open = dailyService.buildDailyView(MAY.atDay(19), CONTRACT_ID);

    assertThat(released.canCreateTimereport()).isFalse();
    assertThat(released.workingdayEditable()).isFalse();
    assertThat(open.canCreateTimereport()).isTrue();
    assertThat(open.workingdayEditable()).isTrue();
  }

  @Test
  void a_manager_cannot_book_their_own_released_day_either() {
    contract.setEmployee(employee(MANAGER));
    loggedInAs(MANAGER, false);

    var released = dailyService.buildDailyView(MAY.atDay(12), CONTRACT_ID);

    assertThat(released.canCreateTimereport()).isFalse();
    assertThat(released.workingdayEditable()).isFalse();
  }

  @Test
  void after_the_acceptance_only_an_admin_is_offered_to_change_anything() {
    var accepted = MAY.atDay(5);
    when(timereportService.getTimereportsByDateAndEmployeeContractId(CONTRACT_ID, accepted))
        .thenReturn(List.of(booking(7L, accepted, TIMEREPORT_STATUS_CLOSED)));

    loggedInAs(MANAGER, false);
    var asManager = dailyService.buildDailyView(accepted, CONTRACT_ID);
    loggedInAs("adm", true);
    var asAdmin = dailyService.buildDailyView(accepted, CONTRACT_ID);

    assertThat(asManager.canCreateTimereport()).isFalse();
    assertThat(asManager.workingdayEditable()).isFalse();
    assertThat(asManager.editableTimereportIds()).isEmpty();
    assertThat(asAdmin.canCreateTimereport()).isTrue();
    assertThat(asAdmin.workingdayEditable()).isTrue();
    assertThat(asAdmin.editableTimereportIds()).containsExactly(7L);
  }

  /** Die Überschrift des Tages sagt, in welchem Zeitraum er liegt. */
  @Test
  void the_daily_view_names_the_period_of_the_day() {
    loggedInAs(OWNER, false);

    assertThat(dailyService.buildDailyView(MAY.atDay(5), CONTRACT_ID).reportStatus()).isEqualTo(TIMEREPORT_STATUS_CLOSED);
    assertThat(dailyService.buildDailyView(MAY.atDay(12), CONTRACT_ID).reportStatus()).isEqualTo(TIMEREPORT_STATUS_COMMITED);
    assertThat(dailyService.buildDailyView(MAY.atDay(19), CONTRACT_ID).reportStatus()).isEqualTo(TIMEREPORT_STATUS_OPEN);
  }

  @Test
  void the_list_names_how_far_acceptance_and_release_reach_into_the_month() {
    loggedInAs(OWNER, false);

    var period = dailyService.buildListView(MAY, CONTRACT_ID).reportPeriod();

    assertThat(period.acceptedUntil()).isEqualTo(ACCEPTED_UNTIL);
    assertThat(period.releasedUntil()).isEqualTo(RELEASED_UNTIL);
  }

  @Test
  void the_person_edits_only_their_open_bookings() {
    when(timereportService.getTimereportsByDatesAndEmployeeContractId(CONTRACT_ID, MAY.atDay(1), MAY.atEndOfMonth()))
        .thenReturn(List.of(
            booking(1L, MAY.atDay(5), TIMEREPORT_STATUS_CLOSED),
            booking(2L, MAY.atDay(12), TIMEREPORT_STATUS_COMMITED),
            booking(3L, MAY.atDay(19), TIMEREPORT_STATUS_OPEN)));
    loggedInAs(OWNER, false);

    var list = dailyService.buildListView(MAY, CONTRACT_ID);

    assertThat(list.editableTimereportIds()).containsExactly(3L);
  }

  private static List<LocalDate> creatable(List<ListDay> days) {
    return days.stream().filter(ListDay::canCreate).map(ListDay::date).toList();
  }

  private void loggedInAs(String sign, boolean admin) {
    boolean manager = admin || MANAGER.equals(sign);
    when(authorizedUser.getEffectiveLoginSign()).thenReturn(sign);
    when(authorizedUser.getLoginSign()).thenReturn(sign);
    when(authorizedUser.isManager()).thenReturn(manager);
    when(authorizedUser.isAdmin()).thenReturn(admin);
    when(authorizedUser.isPeopleLead()).thenReturn(manager);
  }

  private static TimereportDTO booking(long id, LocalDate day, String status) {
    return TimereportDTO.builder()
        .id(id)
        .employeecontractId(CONTRACT_ID)
        .referenceday(day)
        .status(status)
        .duration(Duration.ofHours(1))
        .build();
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
