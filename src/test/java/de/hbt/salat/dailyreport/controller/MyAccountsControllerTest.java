package de.hbt.salat.dailyreport.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static de.hbt.salat.common.exception.ErrorCode.AA_NOT_ATHORIZED;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
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
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;
import de.hbt.salat.common.LocalDateRange;
import de.hbt.salat.common.exception.AuthorizationException;
import de.hbt.salat.common.test.FixedClock;
import de.hbt.salat.dailyreport.domain.OvertimeReport;
import de.hbt.salat.dailyreport.domain.OvertimeReportTotal;
import de.hbt.salat.dailyreport.domain.OvertimeStatus;
import de.hbt.salat.dailyreport.domain.OvertimeStatus.OvertimeStatusInfo;
import de.hbt.salat.dailyreport.domain.AccountBooking;
import de.hbt.salat.dailyreport.service.OvertimeService;
import de.hbt.salat.dailyreport.service.TimereportService;
import de.hbt.salat.dailyreport.service.TrainingBenchmarkService;
import de.hbt.salat.dailyreport.domain.TrainingBenchmark;
import de.hbt.salat.dailyreport.domain.WorkingDays;
import java.math.BigDecimal;
import de.hbt.salat.dailyreport.service.VacationService;
import de.hbt.salat.employee.domain.Employee;
import de.hbt.salat.employee.domain.Employeecontract;
import de.hbt.salat.employee.service.EmployeeService;
import de.hbt.salat.employee.service.EmployeecontractService;
import de.hbt.salat.order.domain.Employeeorder;
import de.hbt.salat.order.domain.Suborder;
import de.hbt.salat.order.service.EmployeeorderService;
import de.hbt.salat.order.service.SpecialOrders;

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
  private static final long FOREIGN_CONTRACT_ID = 11L;
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
  @Mock
  private SpecialOrders specialOrders;
  @Mock
  private TrainingBenchmarkService trainingBenchmarkService;

  @InjectMocks
  private MyAccountsController myAccountsController;

  @BeforeEach
  void setUp() {
    // lenient: der Test des fremden Vertrags kommt bis zu keinem der beiden
    lenient().when(employeecontractService.getEmployeecontractById(CONTRACT_ID)).thenReturn(contract());
    lenient().when(overtimeService.createDetailedReportForEmployee(CONTRACT_ID, false))
        .thenReturn(new OvertimeReport(null, List.of()));
  }

  /* Den Vertrag waehlt die Seite ueber den Parameter. Ein Vertrag, den die angemeldete Person nicht
     lesen darf, zeigt sie nicht (#1514) - bis dahin hielt nur der Filter je Buchung die Fortbildung
     eines fremden Vertrags zurueck, die Konten standen offen. */
  @Test
  void a_contract_the_user_may_not_read_is_not_shown() {
    doThrow(new AuthorizationException(AA_NOT_ATHORIZED)).when(employeecontractService).checkReadable(FOREIGN_CONTRACT_ID);

    assertThatThrownBy(() -> myAccountsController.show(FOREIGN_CONTRACT_ID, null, new ExtendedModelMap()))
        .isInstanceOf(AuthorizationException.class);
  }

  /* Eine Anmeldung mit Status adm kommt ohne gueltigen Vertrag herein; die Seite zeigt ihr einen
     Hinweis statt eines Fehlers 500 (#1559). */
  @Test
  void a_login_without_a_current_contract_sees_a_hint_instead_of_an_error() {
    loginWithoutContract();

    assertThat(myAccountsController.show(null, null, new ExtendedModelMap())).isEqualTo("dailyreport/no-current-contract");
  }

  @Test
  void correcting_the_overtime_without_a_current_contract_answers_with_a_toast() {
    loginWithoutContract();
    when(messageSourceAccessor.getMessage("main.nocurrentcontract.title")).thenReturn("Kein Vertrag");
    var redirectAttributes = new RedirectAttributesModelMap();

    var view = myAccountsController.correctOvertime(null, redirectAttributes);

    assertThat(view).isEqualTo("redirect:/my-accounts");
    assertThat(redirectAttributes.getFlashAttributes().get("toastError")).isEqualTo("Kein Vertrag");
    verify(overtimeService, never()).updateOvertimeStatic(any(Long.class));
  }

  private void loginWithoutContract() {
    var loginEmployee = new Employee();
    setField(loginEmployee, "id", 5L);
    when(employeeService.getLoginEmployee()).thenReturn(loginEmployee);
    when(employeecontractService.getCurrentContract(5L)).thenReturn(Optional.empty());
  }

  /* Vor #1097 las die Berechnung das Ende des Urlaubsauftrags ungeschuetzt. Bei offenem Ende ist es
     null, und damit fiel nicht nur die Registerkarte Urlaub aus, sondern die ganze Seite. */
  @Test
  void an_open_ended_vacation_order_does_not_break_the_page() {
    vacationOrderEndingOn(null);

    var model = new ExtendedModelMap();
    var view = myAccountsController.show(CONTRACT_ID, null, model);

    assertThat(view).isEqualTo("dailyreport/my-accounts");
  }

  /* Ein offenes Ende liegt nicht in der Vergangenheit: der Anspruch bleibt vollstaendig stehen,
     statt auf den bereits gebuchten Urlaub zusammenzufallen. */
  @Test
  void an_open_ended_vacation_order_keeps_its_full_entitlement() {
    vacationOrderEndingOn(null);
    bookedUntilToday(Duration.ofHours(8 * 10)); // 10 Tage bereits gebucht

    var model = new ExtendedModelMap();
    myAccountsController.show(CONTRACT_ID, null, model);

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
    myAccountsController.show(CONTRACT_ID, null, model);

    assertThat(model.getAttribute("annualEntitlementDays")).isEqualTo("10,0");
  }

  /* Der Vergleich ist einschliessend: ein Ende am heutigen Tag ist noch nicht abgelaufen. */
  @Test
  void a_vacation_order_that_ends_today_keeps_its_full_entitlement() {
    vacationOrderEndingOn(TODAY);
    bookedUntilToday(Duration.ofHours(8 * 10));

    var model = new ExtendedModelMap();
    myAccountsController.show(CONTRACT_ID, null, model);

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
    myAccountsController.show(CONTRACT_ID, null, model);

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
    myAccountsController.show(CONTRACT_ID, null, model);

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
    myAccountsController.show(CONTRACT_ID, null, model);

    assertThat(model.getAttribute("vacationUsedPercent")).isEqualTo(100);
    assertThat(model.getAttribute("vacationBudgetExceeded")).isEqualTo(false);
  }

  @Test
  void a_vacation_beyond_the_budget_is_exceeded() {
    vacationOrderEndingOn(null);
    bookedUntilToday(Duration.ofHours(8 * 10));
    plannedAfterToday(Duration.ofHours(8 * 21));

    var model = new ExtendedModelMap();
    myAccountsController.show(CONTRACT_ID, null, model);

    assertThat(model.getAttribute("vacationBudgetExceeded")).isEqualTo(true);
  }

  /* Das Diagramm zeigt nur das laufende Jahr. Urlaub im Folgejahr zaehlt ueber jeden Auftrag, der
     dann gilt - auch einen, der erst dann beginnt (#1175). */
  @Test
  void names_the_vacation_planned_for_the_following_year() {
    var nextYear = new LocalDateRange(LocalDate.of(2027, 1, 1), LocalDate.of(2027, 12, 31));
    var nextYearOrder = new Employeeorder();
    setField(nextYearOrder, "id", 200L);
    var nextYearSuborder = new Suborder();
    setField(nextYearSuborder, "id", 2000L);
    nextYearSuborder.setSign("2027");
    nextYearSuborder.setFromDate(LocalDate.of(2027, 1, 1));
    nextYearOrder.setSuborder(nextYearSuborder);
    // im laufenden Jahr gilt hier kein Urlaubsauftrag, im Folgejahr der neue
    when(employeeorderService.getVacationEmployeeOrders(eq(CONTRACT_ID), any())).thenReturn(List.of());
    when(employeeorderService.getVacationEmployeeOrders(CONTRACT_ID, nextYear)).thenReturn(List.of(nextYearOrder));
    when(timereportService.getTotalDurationMinutesForEmployeeOrder(200L, nextYear.getFrom(), nextYear.getUntil()))
        .thenReturn(Duration.ofHours(8 * 5).toMinutes());

    var model = new ExtendedModelMap();
    myAccountsController.show(CONTRACT_ID, null, model);

    assertThat(model.getAttribute("hasNextYearPlannedDays")).isEqualTo(true);
    assertThat(model.getAttribute("nextYear")).isEqualTo("2027");
    assertThat(model.getAttribute("nextYearPlannedDays")).isEqualTo("5,0");
  }

  @Test
  void says_nothing_without_vacation_in_the_following_year() {
    var model = new ExtendedModelMap();
    myAccountsController.show(CONTRACT_ID, null, model);

    assertThat(model.getAttribute("hasNextYearPlannedDays")).isEqualTo(false);
  }

  /* Das Diagramm reicht bis zum letzten Monat mit geplantem Urlaub, hier Februar des Folgejahres,
     und jede Beschriftung traegt dann ihr Jahr. Urlaub und Sonderurlaub stehen getrennt, jeweils
     genommen und geplant (#1175). Heute ist der 25.06.2026. */
  @Test
  @SuppressWarnings("unchecked")
  void the_chart_reaches_the_last_planned_month_and_separates_special_leave() {
    specialOrderBesidesVacation();
    var horizon = TODAY.plusYears(2);
    when(timereportService.getAccountBookingsOfEmployeeorders(CONTRACT_ID, List.of(ORDER_ID, SPECIAL_ORDER_ID),
        LocalDate.of(2026, 1, 1), horizon))
        .thenReturn(List.of(booking(ORDER_ID, "2026-06-10"), booking(SPECIAL_ORDER_ID, "2026-10-15"),
            booking(ORDER_ID, "2027-02-03")));

    var model = new ExtendedModelMap();
    myAccountsController.show(CONTRACT_ID, null, model);

    assertThat((List<String>) model.getAttribute("vacationMonthLabels")).hasSize(14)
        .startsWith("Jan. '26").endsWith("Feb. '27");
    assertThat((List<Double>) model.getAttribute("vacationMonthDays")).element(5).isEqualTo(1.0);
    assertThat((List<Double>) model.getAttribute("vacationMonthPlannedDays")).element(13).isEqualTo(1.0);
    assertThat((List<Double>) model.getAttribute("vacationMonthSpecialPlannedDays")).element(9).isEqualTo(1.0);
    assertThat((List<Double>) model.getAttribute("vacationMonthSpecialDays")).containsOnly(0.0);
  }

  @Test
  @SuppressWarnings("unchecked")
  void without_planned_vacation_beyond_the_year_the_chart_ends_in_december() {
    vacationOrderEndingOn(null);

    var model = new ExtendedModelMap();
    myAccountsController.show(CONTRACT_ID, null, model);

    assertThat((List<String>) model.getAttribute("vacationMonthLabels")).hasSize(12).startsWith("Jan.").endsWith("Dez.");
  }

  /* Sonderurlaub zaehlt genommen und geplant; die Zeile steht auch, wenn nur geplanter da ist. */
  @Test
  void counts_planned_special_leave_in_the_summary() {
    specialOrderBesidesVacation();
    lenient().when(timereportService.getTotalDurationMinutesForEmployeeOrder(SPECIAL_ORDER_ID, TODAY.plusDays(1), TODAY.plusYears(2)))
        .thenReturn(Duration.ofHours(8).toMinutes());

    var model = new ExtendedModelMap();
    myAccountsController.show(CONTRACT_ID, null, model);

    assertThat(model.getAttribute("hasSpecialDays")).isEqualTo(true);
    assertThat(model.getAttribute("specialDays")).isEqualTo("1,0");
    assertThat(model.getAttribute("specialPlannedDays")).isEqualTo("1,0");
  }

  /* Der Zeitraum der Fortbildung ist waehlbar (#1175); heute ist der 25.06.2026. Ein unbekannter
     Wert faellt auf das laufende Jahr zurueck. */
  @org.junit.jupiter.params.ParameterizedTest
  @org.junit.jupiter.params.provider.CsvSource({
      ",               2026-01-01, 2026-06-25, 6",
      "CURRENT_YEAR,   2026-01-01, 2026-06-25, 6",
      "LAST_YEAR,      2025-01-01, 2025-12-31, 12",
      "LAST_12_MONTHS, 2025-07-01, 2026-06-25, 12",
      "unbekannt,      2026-01-01, 2026-06-25, 6"
  })
  @SuppressWarnings("unchecked")
  void reads_the_training_of_the_chosen_period(String period, LocalDate from, LocalDate until, int months) {
    var model = new ExtendedModelMap();
    myAccountsController.show(CONTRACT_ID, period, model);

    verify(timereportService).getAccountBookingsOfContract(CONTRACT_ID, from, until);
    assertThat(model.getAttribute("trainingFrom")).isEqualTo(from);
    assertThat(model.getAttribute("trainingUntil")).isEqualTo(until);
    assertThat((List<String>) model.getAttribute("trainingChartLabels")).hasSize(months);
  }

  /* Regulaere und projektbezogene Fortbildung stehen im Diagramm getrennt (#1175). */
  @Test
  @SuppressWarnings("unchecked")
  void separates_regular_from_order_training_in_the_chart() {
    when(timereportService.getAccountBookingsOfContract(CONTRACT_ID, LocalDate.of(2026, 1, 1), TODAY))
        .thenReturn(List.of(
            training("2026-02-10", REGULAR_TRAINING_SUBORDER_ID, 4),
            training("2026-02-12", ORDER_TRAINING_SUBORDER_ID, 2),
            training("2026-05-05", ORDER_TRAINING_SUBORDER_ID, 3)));
    // which suborders are regular training is configuration, by id (#1341)
    lenient().when(specialOrders.isRegularTraining(REGULAR_TRAINING_SUBORDER_ID)).thenReturn(true);

    var model = new ExtendedModelMap();
    myAccountsController.show(CONTRACT_ID, null, model);

    assertThat((List<Double>) model.getAttribute("trainingChartHours")).containsExactly(0.0, 4.0, 0.0, 0.0, 0.0, 0.0);
    assertThat((List<Double>) model.getAttribute("trainingChartOrderHours")).containsExactly(0.0, 2.0, 0.0, 0.0, 3.0, 0.0);
  }

  /* #827: neben der eigenen regulaeren Fortbildung in Arbeitstagen steht der Median der Kolleginnen
     und Kollegen, hervorgehoben, wenn er deutlich darueber liegt. */
  @Test
  void shows_the_own_regular_training_in_working_days_and_the_median_beside_it() {
    when(timereportService.getAccountBookingsOfContract(CONTRACT_ID, LocalDate.of(2026, 1, 1), TODAY))
        .thenReturn(List.of(training("2026-02-10", REGULAR_TRAINING_SUBORDER_ID, 12)));
    lenient().when(specialOrders.isRegularTraining(REGULAR_TRAINING_SUBORDER_ID)).thenReturn(true);
    when(trainingBenchmarkService.getBenchmark(LocalDate.of(2026, 1, 1), TODAY, WorkingDays.of(12 * 60, 8 * 60)))
        .thenReturn(Optional.of(new TrainingBenchmark(new BigDecimal("6.25"), true)));

    var model = new ExtendedModelMap();
    myAccountsController.show(CONTRACT_ID, null, model);

    assertThat(model.getAttribute("regularTrainingDays")).isEqualTo("1,5");
    assertThat(model.getAttribute("trainingMedianDays")).isEqualTo("6,3");
    assertThat(model.getAttribute("trainingMedianHighlighted")).isEqualTo(true);
  }

  @Test
  void without_a_benchmark_no_median_is_shown() {
    var model = new ExtendedModelMap();
    myAccountsController.show(CONTRACT_ID, null, model);

    assertThat(model.getAttribute("regularTrainingDays")).isEqualTo("0,0");
    assertThat(model.containsAttribute("trainingMedianDays")).isFalse();
  }

  /* Der Median vergleicht nur, solange der Vertrag gilt: begann er im April, von April an (#827). */
  @Test
  void the_period_is_cut_to_the_validity_of_the_contract() {
    var contract = contract();
    contract.setValidFrom(LocalDate.parse("2026-04-01"));
    when(employeecontractService.getEmployeecontractById(CONTRACT_ID)).thenReturn(contract);

    var model = new ExtendedModelMap();
    myAccountsController.show(CONTRACT_ID, null, model);

    verify(timereportService).getAccountBookingsOfContract(CONTRACT_ID, LocalDate.parse("2026-04-01"), TODAY);
    verify(trainingBenchmarkService).getBenchmark(LocalDate.parse("2026-04-01"), TODAY, WorkingDays.of(0, 8 * 60));
  }

  @Test
  void a_contract_that_ended_is_read_until_its_end() {
    var contract = contract();
    contract.setValidUntil(LocalDate.parse("2026-03-31"));
    when(employeecontractService.getEmployeecontractById(CONTRACT_ID)).thenReturn(contract);

    var model = new ExtendedModelMap();
    myAccountsController.show(CONTRACT_ID, null, model);

    verify(timereportService).getAccountBookingsOfContract(CONTRACT_ID, LocalDate.of(2026, 1, 1),
        LocalDate.parse("2026-03-31"));
  }

  /* Fortbildung in der Zukunft zaehlt im Fortbildungskonto nicht - anders als der geplante Urlaub. */
  @Test
  void training_after_today_is_never_read() {
    var model = new ExtendedModelMap();
    myAccountsController.show(CONTRACT_ID, "CURRENT_YEAR", model);
    myAccountsController.show(CONTRACT_ID, "LAST_12_MONTHS", model);

    verify(timereportService, never()).getAccountBookingsOfContract(eq(CONTRACT_ID), any(),
        argThat(until -> until.isAfter(TODAY)));
  }

  private static final long REGULAR_TRAINING_SUBORDER_ID = 500L;
  private static final long ORDER_TRAINING_SUBORDER_ID = 501L;

  private static AccountBooking training(String day, long suborderId, long hours) {
    return new AccountBooking(ORDER_ID, suborderId, LocalDate.parse(day), Duration.ofHours(hours), true, "ORD/01",
        "TK", "Fortbildung");
  }

  private static final long SPECIAL_ORDER_ID = 300L;

  private static final long SPECIAL_SUBORDER_ID = 3000L;

  private void specialOrderBesidesVacation() {
    var suborder = new Suborder();
    setField(suborder, "id", SPECIAL_SUBORDER_ID);
    suborder.setSign("Sonderurlaub");
    // special leave is configured by id, not recognized by its sign (#1341)
    lenient().when(specialOrders.isVacationDoNotCalculate(SPECIAL_SUBORDER_ID)).thenReturn(true);
    var special = new Employeeorder();
    setField(special, "id", SPECIAL_ORDER_ID);
    special.setSuborder(suborder);
    special.setFromDate(LocalDate.parse("2026-10-15"));
    special.setDebithours(Duration.ZERO);
    when(employeeorderService.getVacationEmployeeOrders(eq(CONTRACT_ID), any()))
        .thenReturn(List.of(vacationOrder(null), special));
  }

  private static AccountBooking booking(long employeeorderId, String day) {
    return new AccountBooking(employeeorderId, 1000L, LocalDate.parse(day), DAILY_WORKING_TIME, false, "URLAUB/2026",
        "HBT", null);
  }

  private void plannedAfterToday(Duration planned) {
    when(timereportService.getTotalDurationMinutesForEmployeeOrder(ORDER_ID, TODAY.plusDays(1), TODAY.plusYears(2)))
        .thenReturn(planned.toMinutes());
  }

  private void balanceOf(Duration balance) {
    var info = new OvertimeStatusInfo();
    info.setBalance(balance);
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

  /* Regulaerer Jahres-Unterauftrag: er beginnt im laufenden Jahr, damit das Soll als Jahresanspruch
     und nicht als Rest aus dem Vorjahr zaehlt - das Jahr kommt aus dem Beginn, nicht aus dem
     Kuerzel (#1341). */
  private static Employeeorder vacationOrder(LocalDate untilDate) {
    var suborder = new Suborder();
    setField(suborder, "id", 1000L);
    suborder.setSign(String.valueOf(TODAY.getYear()));
    suborder.setFromDate(LocalDate.of(TODAY.getYear(), 1, 1));

    var employeeorder = new Employeeorder();
    setField(employeeorder, "id", ORDER_ID);
    employeeorder.setSuborder(suborder);
    employeeorder.setFromDate(LocalDate.of(TODAY.getYear(), 1, 1));
    employeeorder.setUntilDate(untilDate);
    employeeorder.setDebithours(BUDGET);
    return employeeorder;
  }

}
