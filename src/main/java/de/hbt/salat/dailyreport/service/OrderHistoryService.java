package de.hbt.salat.dailyreport.service;

import static java.time.temporal.ChronoUnit.DAYS;
import static de.hbt.salat.common.exception.ErrorCode.GQ_GAP_OUT_OF_RANGE;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import de.hbt.salat.auth.domain.Authorized;
import de.hbt.salat.common.exception.InvalidDataException;
import de.hbt.salat.dailyreport.auth.TimereportVisibility;
import de.hbt.salat.dailyreport.auth.TimereportVisibilityService;
import de.hbt.salat.dailyreport.domain.OrderHistory;
import de.hbt.salat.dailyreport.domain.OrderHistoryPeriod;
import de.hbt.salat.dailyreport.persistence.TimereportListDAO;
import de.hbt.salat.dailyreport.persistence.TimereportListDAO.BookedDay;

/**
 * On which orders a person worked, and in which periods (#1534), worked out from the days booked: the customer orders,
 * or all orders (#1539).
 *
 * <p>Only what the user may read counts: the bookings come under the {@link TimereportVisibility} of the booking list,
 * over all time, so somebody responsible for an order sees the history of another person on that order and nowhere
 * else. A person without a visible booking has an empty history, not an error — the list answers the same.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Authorized(requireUnrestricted = true)
public class OrderHistoryService {

  /** The gap when none is asked for: a booking on 1 January and the next on 1 February start a new period. */
  public static final int DEFAULT_GAP_DAYS = 30;
  /** Ten years; a longer gap makes no difference any more, and the bound keeps the argument meaningful. */
  public static final int MAX_GAP_DAYS = 3650;

  private final TimereportListDAO timereportListDAO;
  private final TimereportVisibilityService visibilityService;

  /**
   * The orders, the one with the latest day booked first, then by sign; within each order its periods, the latest
   * first.
   *
   * @param gapDays      how many days without a booking on an order start a new period, 1 to {@value #MAX_GAP_DAYS}
   * @param billableOnly whether only customer orders count — those with at least one billable suborder — or every order,
   *                     internal ones, vacation and sickness included
   * @throws InvalidDataException {@code GQ-0003} for a gap out of that range
   */
  public List<OrderHistory> getOrderHistory(long employeeId, int gapDays, boolean billableOnly) {
    if (gapDays < 1 || gapDays > MAX_GAP_DAYS) {
      throw new InvalidDataException(GQ_GAP_OUT_OF_RANGE, MAX_GAP_DAYS);
    }
    var visibility = visibilityService.anyTime();
    if (visibility.isEmpty()) {
      return List.of();
    }
    var daysByOrder = new LinkedHashMap<Long, List<BookedDay>>();
    timereportListDAO.findBookedDays(employeeId, visibility, billableOnly)
        .forEach(day -> daysByOrder.computeIfAbsent(day.customerorderId(), id -> new ArrayList<>()).add(day));
    return daysByOrder.values().stream()
        .map(days -> new SignedHistory(days.getFirst().customerorderSign(), historyOf(days, gapDays)))
        .sorted(Comparator.comparing((SignedHistory signed) -> signed.history().until()).reversed()
            .thenComparing(SignedHistory::sign))
        .map(SignedHistory::history)
        .toList();
  }

  /** The days of one order, ordered by day, cut where {@code gapDays} or more pass without a booking. */
  private static OrderHistory historyOf(List<BookedDay> days, int gapDays) {
    var periods = new ArrayList<OrderHistoryPeriod>();
    int start = 0;
    for (int i = 1; i <= days.size(); i++) {
      if (i == days.size() || DAYS.between(days.get(i - 1).day(), days.get(i).day()) - 1 >= gapDays) {
        periods.add(periodOf(days.subList(start, i)));
        start = i;
      }
    }
    var first = days.getFirst();
    return new OrderHistory(first.customerorderId(), first.day(), days.getLast().day(), days.size(), sumOf(days),
        periods.reversed());
  }

  private static OrderHistoryPeriod periodOf(List<BookedDay> days) {
    return new OrderHistoryPeriod(days.getFirst().day(), days.getLast().day(), days.size(), sumOf(days));
  }

  private static Duration sumOf(List<BookedDay> days) {
    return days.stream().map(BookedDay::duration).reduce(Duration.ZERO, Duration::plus);
  }

  /** The sign only orders the orders; the order itself is the order module's to describe. */
  private record SignedHistory(String sign, OrderHistory history) {
  }
}
