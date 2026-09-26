package org.tb.dailyreport.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.util.ReflectionTestUtils.setField;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.support.MessageSourceAccessor;
import org.springframework.ui.ExtendedModelMap;
import org.tb.common.test.FixedClock;
import org.tb.dailyreport.domain.OvertimeStatus;
import org.tb.dailyreport.domain.OvertimeStatus.OvertimeStatusInfo;
import org.tb.dailyreport.service.OvertimeService;
import org.tb.dailyreport.service.PublicholidayService;
import org.tb.dailyreport.service.ReleaseService;
import org.tb.dailyreport.service.TimereportService;
import org.tb.dailyreport.service.VacationService;
import org.tb.employee.domain.Employee;
import org.tb.employee.domain.Employeecontract;
import org.tb.employee.service.EmployeeService;
import org.tb.employee.service.EmployeecontractService;

public class DashboardControllerTest {

  @Test
  public void grades_a_negative_total_on_the_negative_side_of_the_scale() {
    assertThat(DashboardController.overtimeColorClass(status(-25, 0))).isEqualTo("warning");
    assertThat(DashboardController.overtimeColorClass(status(-50, 0))).isEqualTo("danger");
  }

  @Test
  public void leaves_a_positive_total_where_it_was() {
    assertThat(DashboardController.overtimeColorClass(status(10, 0))).isEqualTo("success");
    assertThat(DashboardController.overtimeColorClass(status(50, 0))).isEqualTo("warning");
    assertThat(DashboardController.overtimeColorClass(status(85, 0))).isEqualTo("danger");
  }

  /* Der Pfeil zeigt nach unten, sobald die Dauer negativ ist. Die Farbe kommt aus derselben Dauer -
     ein Minussaldo kann damit nicht mehr gruen neben einem abwaertsgerichteten Pfeil stehen, wenn
     die Skala ihn bemaengelt (#1030). */
  @Test
  public void keeps_arrow_and_colour_from_contradicting_each_other() {
    var status = status(-25, 0);

    assertThat(status.get().getTotal().isNegative()).isTrue();
    assertThat(DashboardController.overtimeColorClass(status)).isNotEqualTo("success");
  }

  /* Die Monatszelle wurde mit dem Vorzeichen des Gesamtsaldos bewertet. Bei gegenlaeufigen
     Vorzeichen zeigt sich das: ein Monat von -20 h ist gelb, gleich wie der Gesamtsaldo steht. */
  @Test
  public void grades_the_month_without_looking_at_the_total() {
    assertThat(DashboardController.monthlyOvertimeColorClass(status(60, -20))).isEqualTo("warning");
    assertThat(DashboardController.monthlyOvertimeColorClass(status(-60, -20))).isEqualTo("warning");
    assertThat(DashboardController.monthlyOvertimeColorClass(status(-60, 20))).isEqualTo("warning");
    assertThat(DashboardController.monthlyOvertimeColorClass(status(-60, 35))).isEqualTo("danger");
    assertThat(DashboardController.monthlyOvertimeColorClass(status(60, -35))).isEqualTo("danger");
  }

  @Test
  public void grades_a_missing_status_as_unremarkable() {
    assertThat(DashboardController.overtimeColorClass(Optional.empty())).isEqualTo("success");
    assertThat(DashboardController.monthlyOvertimeColorClass(Optional.empty())).isEqualTo("success");
  }

  @Test
  public void grades_a_status_without_a_current_month_as_unremarkable() {
    var status = new OvertimeStatus();
    status.setTotal(info(-50));

    assertThat(DashboardController.monthlyOvertimeColorClass(Optional.of(status))).isEqualTo("success");
  }

  /* Ohne taegliche Sollarbeitszeit gibt es keine Abweichung vom Soll und damit keinen Saldo -
     OvertimeService.calculateOvertime steigt mit einem leeren Optional aus. Beide Felder bleiben
     leer, denn an ihnen haengt die Sichtbarkeit der beiden Zellen (#1031). */
  @Test
  void a_contract_without_target_hours_fills_neither_overtime_field() {
    var model = new ExtendedModelMap();

    DashboardController.addOvertimeAttributes(model, Optional.empty());

    assertThat(model.getAttribute("overtime")).isEqualTo("");
    assertThat(model.getAttribute("monthlyOvertime")).isEqualTo("");
    assertThat(model.getAttribute("overtimeMonth")).isEqualTo("");
  }

  /* Der Vertrag beruehrt den laufenden Monat nicht: OvertimeService setzt currentMonth dann nicht.
     Der Gesamtsaldo steht trotzdem, die Monatszelle faellt weg. */
  @Test
  void a_status_without_a_current_month_fills_only_the_total() {
    var status = new OvertimeStatus();
    status.setTotal(info(-50));
    var model = new ExtendedModelMap();

    DashboardController.addOvertimeAttributes(model, Optional.of(status));

    assertThat(model.getAttribute("overtime")).isEqualTo("-50:00");
    assertThat(model.getAttribute("monthlyOvertime")).isEqualTo("");
    assertThat(model.getAttribute("overtimeMonth")).isEqualTo("");
  }

  /* Ein ausgerechnetes 0:00 ist ein Wert und bleibt sichtbar: die Unterscheidung ist "kein Wert"
     gegen "Wert ist null", nicht "Text ist 0:00". */
  @Test
  void a_month_that_really_is_balanced_keeps_its_cell() {
    var status = new OvertimeStatus();
    status.setTotal(info(0));
    status.setCurrentMonth(info(0));
    status.getCurrentMonth().setBegin(LocalDate.parse("2026-09-01"));
    var model = new ExtendedModelMap();

    DashboardController.addOvertimeAttributes(model, Optional.of(status));

    assertThat(model.getAttribute("monthlyOvertime")).isEqualTo("0:00");
    assertThat(model.getAttribute("overtimeMonth")).isEqualTo("2026-09");
  }

  /* Der Hinweis auf Arbeitstage der Vorwoche ohne Buchung bezieht sich auf den Vertrag, den das
     Dashboard zeigt: den aus fEmployeeContractId, sonst den aktuellen der angemeldeten Person. Die
     Links des Hinweises nennen denselben Vertrag (#1124). Die Mocks sind nachsichtig, weil die
     uebrigen Kennzahlen der Seite hier keine Rolle spielen und mit ihren Leerwerten auskommen. */
  @Nested
  @FixedClock("2026-09-28T08:00:00")
  @ExtendWith(MockitoExtension.class)
  @MockitoSettings(strictness = Strictness.LENIENT)
  class UnbookedDaysHint {

    private static final List<LocalDate> DAYS = List.of(LocalDate.parse("2026-09-22"), LocalDate.parse("2026-09-24"));

    @Mock
    private EmployeecontractService employeecontractService;
    @Mock
    private EmployeeService employeeService;
    @Mock
    private OvertimeService overtimeService;
    @Mock
    private VacationService vacationService;
    @Mock
    private TimereportService timereportService;
    @Mock
    private PublicholidayService publicholidayService;
    @Mock
    private ReleaseService releaseService;
    @Mock
    private MessageSourceAccessor messageSourceAccessor;

    @InjectMocks
    private DashboardController dashboardController;

    @Test
    void follows_the_contract_the_page_was_asked_for() {
      when(employeecontractService.getEmployeecontractById(42L)).thenReturn(contract(42L));
      when(releaseService.getUnbookedWorkingDaysOfPreviousWeek(42L)).thenReturn(DAYS);
      var model = new ExtendedModelMap();

      var view = dashboardController.dashboard(42L, model);

      assertThat(view).isEqualTo("dailyreport/dashboard");
      verify(releaseService).getUnbookedWorkingDaysOfPreviousWeek(42L);
      assertThat(model.getAttribute("unbookedDays")).isEqualTo(DAYS);
      assertThat(model.getAttribute("shownContractId")).isEqualTo(42L);
    }

    @Test
    void without_a_selection_follows_the_current_contract_of_the_login() {
      var loginEmployee = new Employee();
      setField(loginEmployee, "id", 5L);
      when(employeeService.getLoginEmployee()).thenReturn(loginEmployee);
      when(employeecontractService.getCurrentContract(5L)).thenReturn(Optional.of(contract(43L)));
      when(releaseService.getUnbookedWorkingDaysOfPreviousWeek(43L)).thenReturn(DAYS);
      var model = new ExtendedModelMap();

      dashboardController.dashboard(null, model);

      verify(releaseService).getUnbookedWorkingDaysOfPreviousWeek(43L);
      assertThat(model.getAttribute("unbookedDays")).isEqualTo(DAYS);
      assertThat(model.getAttribute("shownContractId")).isEqualTo(43L);
    }

    @Test
    void puts_an_empty_list_when_every_working_day_is_booked() {
      when(employeecontractService.getEmployeecontractById(42L)).thenReturn(contract(42L));
      when(releaseService.getUnbookedWorkingDaysOfPreviousWeek(42L)).thenReturn(List.of());
      var model = new ExtendedModelMap();

      dashboardController.dashboard(42L, model);

      assertThat(model.getAttribute("unbookedDays")).isEqualTo(List.of());
    }

    private Employeecontract contract(long id) {
      var contract = new Employeecontract();
      setField(contract, "id", id);
      contract.setEmployee(new Employee());
      contract.setValidFrom(LocalDate.parse("2020-01-01"));
      contract.setDailyWorkingTime(Duration.ofHours(8));
      return contract;
    }
  }

  private static Optional<OvertimeStatus> status(long totalHours, long monthHours) {
    var status = new OvertimeStatus();
    status.setTotal(info(totalHours));
    status.setCurrentMonth(info(monthHours));
    return Optional.of(status);
  }

  /* Wie OvertimeService.toStatusInfo: die Dauer traegt das Vorzeichen, negative ist nur der Merker
     fuer die Pfeilrichtung. */
  private static OvertimeStatusInfo info(long hours) {
    var info = new OvertimeStatusInfo();
    var duration = Duration.ofHours(hours);
    info.setDuration(duration);
    info.setNegative(duration.isNegative());
    return info;
  }

}
