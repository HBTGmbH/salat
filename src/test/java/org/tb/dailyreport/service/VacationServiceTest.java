package org.tb.dailyreport.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.util.ReflectionTestUtils.setField;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.tb.common.test.FixedClock;
import org.tb.dailyreport.domain.VacationInfo;
import org.tb.employee.domain.Employeecontract;
import org.tb.order.domain.Customerorder;
import org.tb.order.domain.Employeeorder;
import org.tb.order.domain.Suborder;
import org.tb.order.service.EmployeeorderService;

/**
 * Welche Urlaubsauftraege Dashboard und Kontenuebersicht zeigen (#1175): die heute gueltigen, und
 * dazu die kuenftigen, auf die schon gebucht ist.
 */
@FixedClock("2026-09-28T10:00:00")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class VacationServiceTest {

  private static final long CONTRACT_ID = 10L;

  @Mock
  private EmployeeorderService employeeorderService;
  @Mock
  private TimereportService timereportService;

  @InjectMocks
  private VacationService vacationService;

  private final Employeecontract contract = contract();

  @Test
  void shows_a_future_vacation_order_that_is_already_booked() {
    var current = order(1L, 101L, "2026", LocalDate.parse("2026-01-01"));
    var next = order(2L, 102L, "2027", LocalDate.parse("2027-01-01"));
    currentOrders(current);
    futureOrders(current, next);
    booked(101L, 80, 16);
    booked(102L, 40, 40);

    var vacations = vacationService.getVacations(contract);

    assertThat(vacations).extracting(VacationInfo::suborderSign).containsExactly("2026", "2027");
    // alles auf dem kuenftigen Auftrag liegt nach heute, ist also geplant
    assertThat(vacations.get(1).plannedVacationMinutes()).isEqualTo(vacations.get(1).usedVacationMinutes());
  }

  @Test
  void leaves_out_a_future_vacation_order_without_bookings() {
    var current = order(1L, 101L, "2026", LocalDate.parse("2026-01-01"));
    var next = order(2L, 102L, "2027", LocalDate.parse("2027-01-01"));
    currentOrders(current);
    futureOrders(current, next);
    booked(101L, 80, 16);

    assertThat(vacationService.getVacations(contract)).extracting(VacationInfo::suborderSign).containsExactly("2026");
  }

  /* Ein heute gueltiger Auftrag mit offenem Ende gilt auch in der Zukunft; er steht trotzdem nur einmal da. */
  @Test
  void names_an_order_valid_today_and_later_only_once() {
    var current = order(1L, 101L, "2026", LocalDate.parse("2026-01-01"));
    currentOrders(current);
    futureOrders(current);

    assertThat(vacationService.getVacations(contract)).hasSize(1);
  }

  /* Sonderurlaub steht nicht je Auftrag, sondern als eine Zeile am Ende: genommen im laufenden Jahr
     und alles schon Geplante, ueber jeden Sonderurlaubsauftrag ab Jahresbeginn (#1175). */
  @Test
  void sums_up_special_leave_in_one_line_at_the_end() {
    var current = order(1L, 101L, "2026", LocalDate.parse("2026-01-01"));
    var pastSpecial = order(3L, 103L, "Sonderurlaub", LocalDate.parse("2026-03-02"));
    var futureSpecial = order(4L, 103L, "Sonderurlaub", LocalDate.parse("2026-10-15"));
    currentOrders(current);
    futureOrders(current, pastSpecial, futureSpecial);
    booked(101L, 80, 16);
    when(timereportService.getTotalDurationMinutesForEmployeeOrder(eq(3L), any(), any()))
        .thenAnswer(call -> call.getArgument(1, LocalDate.class).isAfter(LocalDate.parse("2026-09-28")) ? 0L : 8 * 60L);
    when(timereportService.getTotalDurationMinutesForEmployeeOrder(eq(4L), any(), any())).thenReturn(8 * 60L);

    var vacations = vacationService.getVacations(contract);

    assertThat(vacations).extracting(VacationInfo::suborderSign).containsExactly("2026", "Sonderurlaub");
    var special = vacations.get(1);
    assertThat(special.special()).isTrue();
    assertThat(special.usedVacationMinutes()).isEqualTo(16 * 60);
    assertThat(special.plannedVacationMinutes()).isEqualTo(8 * 60);
    assertThat(special.suborderIds()).containsExactly(103L);
  }

  @Test
  void leaves_out_special_leave_without_bookings() {
    var current = order(1L, 101L, "2026", LocalDate.parse("2026-01-01"));
    var special = order(3L, 103L, "Sonderurlaub", LocalDate.parse("2026-10-15"));
    currentOrders(current);
    futureOrders(current, special);
    booked(101L, 80, 16);

    assertThat(vacationService.getVacations(contract)).extracting(VacationInfo::suborderSign).containsExactly("2026");
  }

  private void currentOrders(Employeeorder... orders) {
    when(employeeorderService.getVacationEmployeeOrders(CONTRACT_ID)).thenReturn(List.of(orders));
  }

  private void futureOrders(Employeeorder... orders) {
    when(employeeorderService.getVacationEmployeeOrders(eq(CONTRACT_ID), any())).thenReturn(List.of(orders));
  }

  private void booked(long suborderId, long hours, long plannedHours) {
    when(timereportService.getTotalDurationMinutesForSuborderAndEmployeeContract(suborderId, CONTRACT_ID))
        .thenReturn(hours * 60);
    when(timereportService.getTotalDurationMinutesForSuborderAndEmployeeContractAfter(eq(suborderId), anyLong(), any()))
        .thenReturn(plannedHours * 60);
  }

  private Employeeorder order(long id, long suborderId, String sign, LocalDate from) {
    var suborder = new Suborder();
    setField(suborder, "id", suborderId);
    suborder.setSign(sign);
    suborder.setCustomerorder(new Customerorder());
    var order = new Employeeorder();
    setField(order, "id", id);
    order.setSuborder(suborder);
    order.setEmployeecontract(contract);
    order.setFromDate(from);
    order.setDebithours(Duration.ofHours(240));
    return order;
  }

  private static Employeecontract contract() {
    var contract = new Employeecontract();
    setField(contract, "id", CONTRACT_ID);
    contract.setValidFrom(LocalDate.parse("2020-01-01"));
    contract.setDailyWorkingTime(Duration.ofHours(8));
    return contract;
  }
}
