package org.tb.dailyreport.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.util.ReflectionTestUtils.setField;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.support.MessageSourceAccessor;
import org.springframework.ui.ExtendedModelMap;
import org.tb.common.LocalDateRange;
import org.tb.common.test.FixedClock;
import org.tb.dailyreport.domain.OvertimeReport;
import org.tb.dailyreport.domain.OvertimeReportTotal;
import org.tb.dailyreport.domain.OvertimeStatus;
import org.tb.dailyreport.domain.OvertimeStatus.OvertimeStatusInfo;
import org.tb.dailyreport.service.OvertimeService;
import org.tb.dailyreport.service.TimereportService;
import org.tb.dailyreport.service.VacationService;
import org.tb.employee.domain.Employeecontract;
import org.tb.employee.service.EmployeeService;
import org.tb.employee.service.EmployeecontractService;
import org.tb.order.domain.Employeeorder;
import org.tb.order.domain.Suborder;
import org.tb.order.service.EmployeeorderService;

/**
 * Das Urlaubskonto rechnet den Rest eines abgelaufenen Urlaubsauftrags auf die tatsaechlich
 * gebuchte Zeit herunter. Ein offenes Ende ist nie abgelaufen (→ ADR-0029), also behaelt es sein
 * volles Soll - und darf die Seite nicht kosten (#1097).
 */
@FixedClock("2026-06-25T10:15:30")
@ExtendWith(MockitoExtension.class)
class MyAccountsControllerTest {

  private static final LocalDate TODAY = LocalDate.parse("2026-06-25");
  private static final long CONTRACT_ID = 10L;
  private static final long ORDER_ID = 100L;
  private static final Duration DAILY_WORKING_TIME = Duration.ofHours(8);
  private static final Duration BUDGET = Duration.ofHours(8 * 30); // 30 Tage Soll

  @Mock
  private OvertimeService overtimeService;
  @Mock
  private VacationService vacationService;
  @Mock
  private EmployeeorderService employeeorderService;
  @Mock
  private TimereportService timereportService;
  @Mock
  private MessageSourceAccessor messageSourceAccessor;
  @Mock
  private EmployeecontractService employeecontractService;
  @Mock
  private EmployeeService employeeService;

  @InjectMocks
  private MyAccountsController myAccountsController;

  @BeforeEach
  void setUp() {
    when(employeecontractService.getEmployeecontractById(CONTRACT_ID)).thenReturn(contract());
    when(overtimeService.createDetailedReportForEmployee(CONTRACT_ID, false))
        .thenReturn(new OvertimeReport(null, List.of()));
  }

  /* Vor #1097 las die Berechnung das Ende des Urlaubsauftrags ungeschuetzt. Bei offenem Ende ist es
     null, und damit fiel nicht nur die Registerkarte Urlaub aus, sondern die ganze Seite. */
  @Test
  void an_open_ended_vacation_order_does_not_break_the_page() {
    vacationOrderEndingOn(null);

    var model = new ExtendedModelMap();
    var view = myAccountsController.show(CONTRACT_ID, model);

    assertThat(view).isEqualTo("dailyreport/my-accounts");
  }

  /* Ein offenes Ende liegt nicht in der Vergangenheit: der Anspruch bleibt vollstaendig stehen,
     statt auf den bereits gebuchten Urlaub zusammenzufallen. */
  @Test
  void an_open_ended_vacation_order_keeps_its_full_entitlement() {
    vacationOrderEndingOn(null);
    bookedUntilToday(Duration.ofHours(8 * 10)); // 10 Tage bereits gebucht

    var model = new ExtendedModelMap();
    myAccountsController.show(CONTRACT_ID, model);

    assertThat(model.getAttribute("annualEntitlementDays")).isEqualTo("30,0");
    assertThat(model.getAttribute("takenDays")).isEqualTo("10,0");
  }

  /* Ein gestern abgelaufener Auftrag ist abgelaufen: der nicht genommene Rest steht nicht mehr zur
     Verfuegung, das Soll faellt auf die gebuchte Zeit. */
  @Test
  void a_vacation_order_that_ended_yesterday_keeps_only_what_was_booked() {
    vacationOrderEndingOn(TODAY.minusDays(1));
    bookedUntilToday(Duration.ofHours(8 * 10));

    var model = new ExtendedModelMap();
    myAccountsController.show(CONTRACT_ID, model);

    assertThat(model.getAttribute("annualEntitlementDays")).isEqualTo("10,0");
  }

  /* Der Vergleich ist einschliessend: ein Ende am heutigen Tag ist noch nicht abgelaufen. */
  @Test
  void a_vacation_order_that_ends_today_keeps_its_full_entitlement() {
    vacationOrderEndingOn(TODAY);
    bookedUntilToday(Duration.ofHours(8 * 10));

    var model = new ExtendedModelMap();
    myAccountsController.show(CONTRACT_ID, model);

    assertThat(model.getAttribute("annualEntitlementDays")).isEqualTo("30,0");
  }

  /* Die Dauer des Saldos traegt ihr Vorzeichen schon (OvertimeService.toStatusInfo). Die
     Kontenuebersicht wandte isNegative ein zweites Mal an und bewertete Minusstunden damit auf der
     positiven Seite der Skala: -25 h standen gruen da, waehrend das Dashboard sie gelb zeigt
     (#1030, #1175). */
  @Test
  void grades_a_negative_balance_on_the_negative_side_of_the_scale() {
    balanceOf(Duration.ofHours(-25));

    var model = new ExtendedModelMap();
    myAccountsController.show(CONTRACT_ID, model);

    assertThat(model.getAttribute("balanceColorClass")).isEqualTo("warning");
  }

  /* Genommen und geplant stehen im Balken nebeneinander (#1175): 10 von 30 Tagen genommen, 5 geplant. */
  @Test
  void splits_the_vacation_bar_into_taken_and_planned() {
    vacationOrderEndingOn(null);
    bookedUntilToday(Duration.ofHours(8 * 10));
    when(timereportService.getTotalDurationMinutesForEmployeeOrder(ORDER_ID, TODAY.plusDays(1), TODAY.plusYears(2)))
        .thenReturn(Duration.ofHours(8 * 5).toMinutes());

    var model = new ExtendedModelMap();
    myAccountsController.show(CONTRACT_ID, model);

    assertThat(model.getAttribute("vacationUsedPercent")).isEqualTo(50);
    assertThat(model.getAttribute("vacationTakenPercent")).isEqualTo(33);
    assertThat(model.getAttribute("vacationPlannedPercent")).isEqualTo(17);
  }

  /* Rot erst bei Ueberschreitung. Der Prozentwert ist bei 100 gedeckelt und kann "genau
     aufgebraucht" nicht von "ueberschritten" unterscheiden; bis #1175 war der Balken schon bei 0
     verbleibenden Tagen rot. */
  @Test
  void a_vacation_used_up_exactly_is_not_exceeded() {
    vacationOrderEndingOn(null);
    bookedUntilToday(Duration.ofHours(8 * 10));
    plannedAfterToday(Duration.ofHours(8 * 20));

    var model = new ExtendedModelMap();
    myAccountsController.show(CONTRACT_ID, model);

    assertThat(model.getAttribute("vacationUsedPercent")).isEqualTo(100);
    assertThat(model.getAttribute("vacationBudgetExceeded")).isEqualTo(false);
  }

  @Test
  void a_vacation_beyond_the_budget_is_exceeded() {
    vacationOrderEndingOn(null);
    bookedUntilToday(Duration.ofHours(8 * 10));
    plannedAfterToday(Duration.ofHours(8 * 21));

    var model = new ExtendedModelMap();
    myAccountsController.show(CONTRACT_ID, model);

    assertThat(model.getAttribute("vacationBudgetExceeded")).isEqualTo(true);
  }

  /* Das Diagramm zeigt nur das laufende Jahr. Urlaub im Folgejahr zaehlt ueber jeden Auftrag, der
     dann gilt - auch einen, der erst dann beginnt (#1175). */
  @Test
  void names_the_vacation_planned_for_the_following_year() {
    var nextYear = new LocalDateRange(LocalDate.of(2027, 1, 1), LocalDate.of(2027, 12, 31));
    var nextYearOrder = new Employeeorder();
    setField(nextYearOrder, "id", 200L);
    // im laufenden Jahr gilt hier kein Urlaubsauftrag, im Folgejahr der neue
    when(employeeorderService.getVacationEmployeeOrders(eq(CONTRACT_ID), any())).thenReturn(List.of());
    when(employeeorderService.getVacationEmployeeOrders(CONTRACT_ID, nextYear)).thenReturn(List.of(nextYearOrder));
    when(timereportService.getTotalDurationMinutesForEmployeeOrder(200L, nextYear.getFrom(), nextYear.getUntil()))
        .thenReturn(Duration.ofHours(8 * 5).toMinutes());

    var model = new ExtendedModelMap();
    myAccountsController.show(CONTRACT_ID, model);

    assertThat(model.getAttribute("hasNextYearPlannedDays")).isEqualTo(true);
    assertThat(model.getAttribute("nextYear")).isEqualTo("2027");
    assertThat(model.getAttribute("nextYearPlannedDays")).isEqualTo("5,0");
  }

  @Test
  void says_nothing_without_vacation_in_the_following_year() {
    var model = new ExtendedModelMap();
    myAccountsController.show(CONTRACT_ID, model);

    assertThat(model.getAttribute("hasNextYearPlannedDays")).isEqualTo(false);
  }

  private void plannedAfterToday(Duration planned) {
    when(timereportService.getTotalDurationMinutesForEmployeeOrder(ORDER_ID, TODAY.plusDays(1), TODAY.plusYears(2)))
        .thenReturn(planned.toMinutes());
  }

  private void balanceOf(Duration balance) {
    var info = new OvertimeStatusInfo();
    info.setDuration(balance);
    info.setNegative(balance.isNegative());
    var status = new OvertimeStatus();
    status.setTotal(info);
    when(overtimeService.calculateOvertime(CONTRACT_ID, false)).thenReturn(Optional.of(status));
    // der Reiter Ueberstundenkonto vergleicht den gespeicherten Saldo mit dem Bericht
    when(overtimeService.createDetailedReportForEmployee(CONTRACT_ID, false)).thenReturn(new OvertimeReport(
        OvertimeReportTotal.builder().diffCumulative(balance).build(), List.of()));
  }

  private void vacationOrderEndingOn(LocalDate untilDate) {
    when(employeeorderService.getVacationEmployeeOrders(eq(CONTRACT_ID), any()))
        .thenReturn(List.of(vacationOrder(untilDate)));
  }

  private void bookedUntilToday(Duration booked) {
    when(timereportService.getTotalDurationMinutesForEmployeeOrder(
        ORDER_ID, LocalDate.of(TODAY.getYear(), 1, 1), TODAY)).thenReturn(booked.toMinutes());
  }

  private static Employeecontract contract() {
    var contract = new Employeecontract();
    setField(contract, "id", CONTRACT_ID);
    contract.setValidFrom(LocalDate.parse("2020-01-01"));
    contract.setDailyWorkingTime(DAILY_WORKING_TIME);
    return contract;
  }

  /* Regulaerer Jahres-Unterauftrag: sein Zeichen ist das laufende Jahr, damit das Soll als
     Jahresanspruch und nicht als Rest aus dem Vorjahr zaehlt. */
  private static Employeeorder vacationOrder(LocalDate untilDate) {
    var suborder = new Suborder();
    suborder.setSign(String.valueOf(TODAY.getYear()));

    var employeeorder = new Employeeorder();
    setField(employeeorder, "id", ORDER_ID);
    employeeorder.setSuborder(suborder);
    employeeorder.setFromDate(LocalDate.of(TODAY.getYear(), 1, 1));
    employeeorder.setUntilDate(untilDate);
    employeeorder.setDebithours(BUDGET);
    return employeeorder;
  }

}
