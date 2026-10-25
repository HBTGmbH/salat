package de.hbt.salat.dailyreport.service;

import static java.time.LocalDate.of;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import de.hbt.salat.common.exception.ErrorCode;
import de.hbt.salat.common.exception.InvalidDataException;
import de.hbt.salat.dailyreport.auth.TimereportVisibility;
import de.hbt.salat.dailyreport.auth.TimereportVisibilityService;
import de.hbt.salat.dailyreport.domain.OrderHistory;
import de.hbt.salat.dailyreport.domain.OrderHistoryPeriod;
import de.hbt.salat.dailyreport.persistence.TimereportListDAO;
import de.hbt.salat.dailyreport.persistence.TimereportListDAO.BookedDay;

/**
 * The order history (#1534): one entry per order, its periods cut where the given number of days or more pass without
 * a booking on the order.
 */
@ExtendWith(MockitoExtension.class)
@DisplayNameGeneration(ReplaceUnderscores.class)
class OrderHistoryServiceTest {

  private static final long EMPLOYEE_ID = 7L;
  private static final long ALPHA = 1L;
  private static final long BETA = 2L;
  private static final long GAMMA = 3L;

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
    // lenient: a gap out of range is refused before anybody asks for the visibility
    lenient().when(visibilityService.anyTime()).thenReturn(visibility);
  }

  @Test
  void the_days_of_an_order_without_a_gap_make_one_period() {
    givenDays(
        day(ALPHA, "ALPHA", of(2026, 1, 1), 60),
        day(ALPHA, "ALPHA", of(2026, 1, 2), 120),
        day(ALPHA, "ALPHA", of(2026, 1, 20), 30));

    assertThat(orderHistoryService.getOrderHistory(EMPLOYEE_ID, 30)).containsExactly(
        new OrderHistory(ALPHA, of(2026, 1, 1), of(2026, 1, 20), 3, Duration.ofMinutes(210), List.of(
            new OrderHistoryPeriod(of(2026, 1, 1), of(2026, 1, 20), 3, Duration.ofMinutes(210)))));
  }

  /**
   * 1 January and 1 February: 30 days without a booking in between — two periods, the latest first, in one entry
   * whose bracket spans both.
   */
  @Test
  void thirty_days_without_a_booking_start_a_new_period_within_the_same_order() {
    givenDays(
        day(ALPHA, "ALPHA", of(2026, 1, 1), 60),
        day(ALPHA, "ALPHA", of(2026, 2, 1), 90));

    assertThat(orderHistoryService.getOrderHistory(EMPLOYEE_ID, 30)).containsExactly(
        new OrderHistory(ALPHA, of(2026, 1, 1), of(2026, 2, 1), 2, Duration.ofMinutes(150), List.of(
            new OrderHistoryPeriod(of(2026, 2, 1), of(2026, 2, 1), 1, Duration.ofMinutes(90)),
            new OrderHistoryPeriod(of(2026, 1, 1), of(2026, 1, 1), 1, Duration.ofMinutes(60)))));
  }

  /** 1 January and 31 January: 29 days without a booking in between — still one period. */
  @Test
  void twenty_nine_days_without_a_booking_do_not() {
    givenDays(
        day(ALPHA, "ALPHA", of(2026, 1, 1), 60),
        day(ALPHA, "ALPHA", of(2026, 1, 31), 60));

    assertThat(orderHistoryService.getOrderHistory(EMPLOYEE_ID, 30).getFirst().periods()).hasSize(1);
  }

  /** The caller chooses the gap: a week with the next booking ten days later makes two periods. */
  @Test
  void the_gap_is_the_callers_to_choose() {
    givenDays(
        day(ALPHA, "ALPHA", of(2026, 1, 1), 60),
        day(ALPHA, "ALPHA", of(2026, 1, 11), 60));

    assertThat(orderHistoryService.getOrderHistory(EMPLOYEE_ID, 7).getFirst().periods()).hasSize(2);
    assertThat(orderHistoryService.getOrderHistory(EMPLOYEE_ID, 10).getFirst().periods()).hasSize(1);
  }

  @ParameterizedTest
  @ValueSource(ints = {0, -1, OrderHistoryService.MAX_GAP_DAYS + 1})
  void a_gap_out_of_range_is_refused(int gapDays) {
    assertThatThrownBy(() -> orderHistoryService.getOrderHistory(EMPLOYEE_ID, gapDays))
        .isInstanceOfSatisfying(InvalidDataException.class,
            exception -> assertThat(exception.getMessages().getFirst().getErrorCode()).isEqualTo(ErrorCode.GQ_GAP_OUT_OF_RANGE));
  }

  /** The order with the latest day booked comes first; on the same day, the sign decides. */
  @Test
  void the_order_with_the_latest_day_comes_first() {
    givenDays(
        day(ALPHA, "ALPHA", of(2025, 1, 1), 60),
        day(ALPHA, "ALPHA", of(2025, 6, 1), 60),
        day(BETA, "BETA", of(2025, 3, 1), 60),
        day(BETA, "BETA", of(2025, 6, 1), 60),
        day(GAMMA, "GAMMA", of(2025, 7, 1), 60));

    assertThat(orderHistoryService.getOrderHistory(EMPLOYEE_ID, 30))
        .extracting(OrderHistory::customerorderId)
        .containsExactly(GAMMA, ALPHA, BETA);
  }

  @Test
  void without_a_booking_the_history_is_empty() {
    givenDays();

    assertThat(orderHistoryService.getOrderHistory(EMPLOYEE_ID, 30)).isEmpty();
  }

  @Test
  void whoever_sees_no_booking_at_all_is_not_even_asked_for_one() {
    when(visibilityService.anyTime()).thenReturn(TimereportVisibility.of(List.of()));

    assertThat(orderHistoryService.getOrderHistory(EMPLOYEE_ID, 30)).isEmpty();
    verify(timereportListDAO, never()).findBookedDays(anyLong(), any());
  }

  private void givenDays(BookedDay... days) {
    when(timereportListDAO.findBookedDays(EMPLOYEE_ID, visibility)).thenReturn(List.of(days));
  }

  private static BookedDay day(long orderId, String sign, LocalDate day, long minutes) {
    return new BookedDay(orderId, sign, day, minutes);
  }
}
