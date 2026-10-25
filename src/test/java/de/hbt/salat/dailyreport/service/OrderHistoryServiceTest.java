package de.hbt.salat.dailyreport.service;

import static java.time.LocalDate.of;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import de.hbt.salat.dailyreport.auth.TimereportVisibility;
import de.hbt.salat.dailyreport.auth.TimereportVisibilityService;
import de.hbt.salat.dailyreport.domain.OrderHistoryPeriod;
import de.hbt.salat.dailyreport.persistence.TimereportListDAO;
import de.hbt.salat.dailyreport.persistence.TimereportListDAO.BookedDay;

/** The periods of the order history (#1534): cut where 30 days or more pass without a booking on the order. */
@ExtendWith(MockitoExtension.class)
@DisplayNameGeneration(ReplaceUnderscores.class)
class OrderHistoryServiceTest {

  private static final long EMPLOYEE_ID = 7L;
  private static final long ALPHA = 1L;
  private static final long BETA = 2L;

  @Mock
  private TimereportListDAO timereportListDAO;
  @Mock
  private TimereportVisibilityService visibilityService;
  @InjectMocks
  private OrderHistoryService orderHistoryService;

  private final TimereportVisibility visibility =
      TimereportVisibility.of(List.of(TimereportVisibility.Clause.forEmployees(Set.of(EMPLOYEE_ID))));

  @BeforeEach
  void setUp() {
    when(visibilityService.anyTime()).thenReturn(visibility);
  }

  @Test
  void the_days_of_an_order_without_a_gap_make_one_period() {
    givenDays(
        day(ALPHA, "ALPHA", of(2026, 1, 1), 60),
        day(ALPHA, "ALPHA", of(2026, 1, 2), 120),
        day(ALPHA, "ALPHA", of(2026, 1, 20), 30));

    assertThat(orderHistoryService.getOrderHistory(EMPLOYEE_ID)).containsExactly(
        new OrderHistoryPeriod(ALPHA, of(2026, 1, 1), of(2026, 1, 20), 3, Duration.ofMinutes(210)));
  }

  /** 1 January and 1 February: 30 days without a booking in between — two periods. */
  @Test
  void thirty_days_without_a_booking_start_a_new_period() {
    givenDays(
        day(ALPHA, "ALPHA", of(2026, 1, 1), 60),
        day(ALPHA, "ALPHA", of(2026, 2, 1), 60));

    assertThat(orderHistoryService.getOrderHistory(EMPLOYEE_ID)).containsExactly(
        new OrderHistoryPeriod(ALPHA, of(2026, 2, 1), of(2026, 2, 1), 1, Duration.ofMinutes(60)),
        new OrderHistoryPeriod(ALPHA, of(2026, 1, 1), of(2026, 1, 1), 1, Duration.ofMinutes(60)));
  }

  /** 1 January and 31 January: 29 days without a booking in between — still one period. */
  @Test
  void twenty_nine_days_without_a_booking_do_not() {
    givenDays(
        day(ALPHA, "ALPHA", of(2026, 1, 1), 60),
        day(ALPHA, "ALPHA", of(2026, 1, 31), 60));

    assertThat(orderHistoryService.getOrderHistory(EMPLOYEE_ID)).containsExactly(
        new OrderHistoryPeriod(ALPHA, of(2026, 1, 1), of(2026, 1, 31), 2, Duration.ofMinutes(120)));
  }

  /** Bookings on another order in between do not bridge a gap, and the latest period comes first. */
  @Test
  void each_order_has_its_own_periods_and_the_latest_comes_first() {
    givenDays(
        day(ALPHA, "ALPHA", of(2025, 1, 1), 60),
        day(ALPHA, "ALPHA", of(2025, 6, 1), 60),
        day(BETA, "BETA", of(2025, 3, 1), 60),
        day(BETA, "BETA", of(2025, 6, 1), 60));

    assertThat(orderHistoryService.getOrderHistory(EMPLOYEE_ID))
        .extracting(OrderHistoryPeriod::customerorderId, OrderHistoryPeriod::from)
        .containsExactly(
            tuple(ALPHA, of(2025, 6, 1)),
            tuple(BETA, of(2025, 6, 1)),
            tuple(BETA, of(2025, 3, 1)),
            tuple(ALPHA, of(2025, 1, 1)));
  }

  @Test
  void without_a_booking_the_history_is_empty() {
    givenDays();

    assertThat(orderHistoryService.getOrderHistory(EMPLOYEE_ID)).isEmpty();
  }

  @Test
  void whoever_sees_no_booking_at_all_is_not_even_asked_for_one() {
    when(visibilityService.anyTime()).thenReturn(TimereportVisibility.of(List.of()));

    assertThat(orderHistoryService.getOrderHistory(EMPLOYEE_ID)).isEmpty();
    verify(timereportListDAO, never()).findBookedDays(anyLong(), any());
  }

  private void givenDays(BookedDay... days) {
    when(timereportListDAO.findBookedDays(EMPLOYEE_ID, visibility)).thenReturn(List.of(days));
  }

  private static BookedDay day(long orderId, String sign, LocalDate day, long minutes) {
    return new BookedDay(orderId, sign, day, minutes);
  }
}
