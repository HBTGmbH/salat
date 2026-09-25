package org.tb.dailyreport.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.util.ReflectionTestUtils.setField;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.support.MessageSourceAccessor;
import org.springframework.ui.ExtendedModelMap;
import org.tb.common.test.FixedClock;
import org.tb.dailyreport.domain.OvertimeReport;
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
