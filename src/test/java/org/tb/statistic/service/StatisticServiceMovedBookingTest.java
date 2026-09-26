package org.tb.statistic.service;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;
import org.tb.dailyreport.domain.TimereportDTO;
import org.tb.dailyreport.event.TimereportsCreatedOrUpdatedEvent;
import org.tb.dailyreport.service.TimereportService;
import org.tb.order.domain.Customerorder;
import org.tb.order.domain.Employeeorder;
import org.tb.order.domain.Suborder;
import org.tb.order.service.CustomerorderService;
import org.tb.order.service.EmployeeorderService;
import org.tb.order.service.SuborderService;
import org.tb.statistic.persistence.StatisticValueRepository;

/**
 * The minutes per month of a suborder are recalculated for the months the changed bookings are on.
 * A booking moved to another month has also left one — reading only the new day left the old month
 * holding minutes that are no longer there (#1125).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayNameGeneration(ReplaceUnderscores.class)
class StatisticServiceMovedBookingTest {

  private static final long TIMEREPORT_ID = 3L;
  private static final long EMPLOYEE_ORDER_ID = 10L;
  private static final long SUBORDER_ID = 20L;
  private static final long CUSTOMER_ORDER_ID = 30L;
  private static final LocalDate JANUARY_DAY = LocalDate.parse("2026-01-15");
  private static final LocalDate APRIL_DAY = LocalDate.parse("2026-04-14");
  private static final LocalDate OTHER_APRIL_DAY = LocalDate.parse("2026-04-15");
  private static final YearMonth JANUARY = YearMonth.of(2026, 1);

  @InjectMocks
  private StatisticService statisticService;

  @Mock
  private TimereportService timereportService;
  @Mock
  private EmployeeorderService employeeorderService;
  @Mock
  private SuborderService suborderService;
  @Mock
  private CustomerorderService customerorderService;
  @Mock
  private StatisticValueRepository statisticValueRepository;

  @BeforeEach
  void setUp() {
    var customerorder = new Customerorder();
    ReflectionTestUtils.setField(customerorder, "id", CUSTOMER_ORDER_ID);
    var suborder = new Suborder();
    ReflectionTestUtils.setField(suborder, "id", SUBORDER_ID);
    suborder.setCustomerorder(customerorder);
    var employeeorder = new Employeeorder();
    ReflectionTestUtils.setField(employeeorder, "id", EMPLOYEE_ORDER_ID);
    employeeorder.setSuborder(suborder);

    when(employeeorderService.getEmployeeorderById(EMPLOYEE_ORDER_ID)).thenReturn(employeeorder);
    when(suborderService.getSuborderById(SUBORDER_ID)).thenReturn(suborder);
    when(customerorderService.getCustomerorderById(CUSTOMER_ORDER_ID)).thenReturn(customerorder);
  }

  @Test
  void a_booking_moved_to_another_month_recalculates_the_month_it_left() {
    givenBookingOn(APRIL_DAY);

    statisticService.onTimereportsCreatedOrUpdated(movedFrom(JANUARY_DAY));

    verify(timereportService).getTotalDurationMinutesForSuborder(SUBORDER_ID, JANUARY.atDay(1), JANUARY.atEndOfMonth());
  }

  @Test
  void a_booking_moved_within_its_month_recalculates_only_that_month() {
    givenBookingOn(OTHER_APRIL_DAY);

    statisticService.onTimereportsCreatedOrUpdated(movedFrom(APRIL_DAY));

    verify(timereportService).getTotalDurationMinutesForSuborder(SUBORDER_ID, APRIL_DAY.withDayOfMonth(1),
        YearMonth.from(APRIL_DAY).atEndOfMonth());
    verify(timereportService, never()).getTotalDurationMinutesForSuborder(anyLong(), eq(JANUARY.atDay(1)), eq(JANUARY.atEndOfMonth()));
  }

  @Test
  void a_booking_changed_on_its_day_recalculates_only_its_month() {
    givenBookingOn(APRIL_DAY);

    statisticService.onTimereportsCreatedOrUpdated(new TimereportsCreatedOrUpdatedEvent(List.of(TIMEREPORT_ID)));

    verify(timereportService).getTotalDurationMinutesForSuborder(SUBORDER_ID, APRIL_DAY.withDayOfMonth(1),
        YearMonth.from(APRIL_DAY).atEndOfMonth());
    verify(timereportService, never()).getTotalDurationMinutesForSuborder(anyLong(), eq(JANUARY.atDay(1)), eq(JANUARY.atEndOfMonth()));
  }

  private void givenBookingOn(LocalDate day) {
    when(timereportService.getTimereportById(TIMEREPORT_ID)).thenReturn(TimereportDTO.builder()
        .id(TIMEREPORT_ID)
        .employeeorderId(EMPLOYEE_ORDER_ID)
        .referenceday(day)
        .build());
  }

  private static TimereportsCreatedOrUpdatedEvent movedFrom(LocalDate previousDay) {
    return new TimereportsCreatedOrUpdatedEvent(List.of(TIMEREPORT_ID), Map.of(TIMEREPORT_ID, previousDay));
  }

}
