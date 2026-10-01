package de.hbt.salat.dailyreport.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.util.ReflectionTestUtils.setField;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
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
import de.hbt.salat.auth.domain.AuthorizedUser;
import de.hbt.salat.auth.domain.SalatUser;
import de.hbt.salat.auth.service.AuthService;
import de.hbt.salat.dailyreport.auth.TimereportAuthorization;
import de.hbt.salat.dailyreport.domain.TargetEnd;
import de.hbt.salat.dailyreport.domain.TimereportDTO;
import de.hbt.salat.dailyreport.domain.Workingday;
import de.hbt.salat.employee.domain.Employee;
import de.hbt.salat.employee.domain.Employeecontract;
import de.hbt.salat.employee.service.EmployeecontractService;
import de.hbt.salat.order.domain.OrderType;

/**
 * Vertrag-Soll und Differenz zum Tagessoll in der Tagesansicht (#1236). Gerechnet wird gegen das
 * Tagessoll, nicht gegen die Tagesarbeitszeit des Vertrags, und an einem Tag ohne Soll oder bei
 * „Nicht gearbeitet" gibt es keins von beiden.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayNameGeneration(ReplaceUnderscores.class)
class DailyServiceTargetTest {

  private static final long CONTRACT_ID = 42L;
  private static final String OWNER = "own";
  private static final LocalDate DAY = LocalDate.of(2026, 5, 19);
  private static final Duration SEVEN_HOURS = Duration.ofHours(7);

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

  @BeforeEach
  void setUp() {
    dailyService = new DailyService(timereportService, workingdayService, publicholidayService, overtimeService,
        employeecontractService, new TimereportAuthorization(authorizedUser, authService, employeecontractService));

    var contract = new Employeecontract();
    setField(contract, "id", CONTRACT_ID);
    contract.setEmployee(employee());
    contract.setSupervisors(new ArrayList<>());
    contract.setValidFrom(LocalDate.of(2000, 1, 1));
    contract.setDailyWorkingTime(Duration.ofHours(8));

    when(employeecontractService.getEmployeecontractById(CONTRACT_ID)).thenReturn(contract);
    when(overtimeService.calculateWorkingTimeTarget(CONTRACT_ID, DAY, DAY)).thenReturn(SEVEN_HOURS);
    when(workingdayService.getEffectiveStart(any(), anyLong())).thenReturn(LocalTime.of(8, 45));
    when(workingdayService.calculateTargetEnd(any(), anyLong(), any()))
        .thenReturn(new TargetEnd(LocalTime.of(16, 15), Duration.ofMinutes(30)));
    when(authorizedUser.getEffectiveLoginSign()).thenReturn(OWNER);
    when(authorizedUser.getLoginSign()).thenReturn(OWNER);
  }

  @Test
  void before_the_target_is_reached_the_view_shows_what_is_left() {
    booked(Duration.ofMinutes(4 * 60 + 45));

    var view = dailyService.buildDailyView(DAY, CONTRACT_ID);

    assertThat(view.targetDifference()).isEqualTo("2:15");
    assertThat(view.targetReached()).isFalse();
  }

  @Test
  void beyond_the_target_the_view_shows_the_surplus_with_a_sign() {
    booked(Duration.ofMinutes(7 * 60 + 30));

    var view = dailyService.buildDailyView(DAY, CONTRACT_ID);

    assertThat(view.targetDifference()).isEqualTo("+0:30");
    assertThat(view.targetReached()).isTrue();
  }

  @Test
  void exactly_at_the_target_it_counts_as_reached() {
    booked(SEVEN_HOURS);

    var view = dailyService.buildDailyView(DAY, CONTRACT_ID);

    assertThat(view.targetDifference()).isEqualTo("+0:00");
    assertThat(view.targetReached()).isTrue();
  }

  @Test
  void standby_does_not_count_towards_the_target() {
    when(timereportService.getTimereportsByDateAndEmployeeContractId(CONTRACT_ID, DAY)).thenReturn(List.of(
        booking(1L, Duration.ofHours(5), false),
        booking(2L, Duration.ofHours(3), true)));

    var view = dailyService.buildDailyView(DAY, CONTRACT_ID);

    assertThat(view.targetDifference()).isEqualTo("2:00");
    assertThat(view.targetReached()).isFalse();
  }

  @Test
  void the_contract_target_is_calculated_against_the_target_of_the_day() {
    var view = dailyService.buildDailyView(DAY, CONTRACT_ID);

    assertThat(view.targetEnd()).isEqualTo(new TargetEnd(LocalTime.of(16, 15), Duration.ofMinutes(30)));
    verify(workingdayService).calculateTargetEnd(any(), eq(CONTRACT_ID), eq(SEVEN_HOURS));
  }

  @Test
  void a_day_without_target_shows_neither_the_contract_target_nor_the_difference() {
    when(overtimeService.calculateWorkingTimeTarget(CONTRACT_ID, DAY, DAY)).thenReturn(Duration.ZERO);
    booked(Duration.ofHours(2));

    var view = dailyService.buildDailyView(DAY, CONTRACT_ID);

    assertThat(view.targetEnd()).isNull();
    assertThat(view.targetDifference()).isNull();
    verify(workingdayService, never()).calculateTargetEnd(any(), anyLong(), any());
  }

  @Test
  void a_day_not_worked_shows_neither_the_contract_target_nor_the_difference() {
    var workingday = new Workingday();
    workingday.setType(Workingday.WorkingDayType.NOT_WORKED);
    when(workingdayService.getWorkingday(CONTRACT_ID, DAY)).thenReturn(workingday);

    var view = dailyService.buildDailyView(DAY, CONTRACT_ID);

    assertThat(view.targetEnd()).isNull();
    assertThat(view.targetDifference()).isNull();
  }

  private void booked(Duration duration) {
    when(timereportService.getTimereportsByDateAndEmployeeContractId(CONTRACT_ID, DAY))
        .thenReturn(List.of(booking(1L, duration, false)));
  }

  private static TimereportDTO booking(long id, Duration duration, boolean standby) {
    return TimereportDTO.builder()
        .id(id)
        .employeecontractId(CONTRACT_ID)
        .referenceday(DAY)
        .status("open")
        .orderType(standby ? OrderType.BEREITSCHAFT : null)
        .duration(duration)
        .build();
  }

  private static Employee employee() {
    var salatUser = new SalatUser();
    salatUser.setLoginname(OWNER);
    var employee = new Employee();
    employee.setSign(OWNER);
    employee.setSalatUser(salatUser);
    return employee;
  }
}
