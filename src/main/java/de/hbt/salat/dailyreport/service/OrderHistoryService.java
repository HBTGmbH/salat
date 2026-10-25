package de.hbt.salat.dailyreport.service;

import static java.time.temporal.ChronoUnit.DAYS;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import de.hbt.salat.auth.domain.Authorized;
import de.hbt.salat.dailyreport.auth.TimereportVisibility;
import de.hbt.salat.dailyreport.auth.TimereportVisibilityService;
import de.hbt.salat.dailyreport.domain.OrderHistoryPeriod;
import de.hbt.salat.dailyreport.persistence.TimereportListDAO;
import de.hbt.salat.dailyreport.persistence.TimereportListDAO.BookedDay;

/**
 * In which periods a person worked on which customer order (#1534), worked out from the days booked.
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

  /** How many days without a booking on an order end a period: a booking on 1 January and the next on 1 February. */
  public static final int GAP_DAYS = 30;

  private final TimereportListDAO timereportListDAO;
  private final TimereportVisibilityService visibilityService;

  /** The periods, the latest first, then by the sign of the order. */
  public List<OrderHistoryPeriod> getOrderHistory(long employeeId) {
    var visibility = visibilityService.anyTime();
    if (visibility.isEmpty()) {
      return List.of();
    }
    var days = timereportListDAO.findBookedDays(employeeId, visibility);
    var periods = new ArrayList<SignedPeriod>();
    int start = 0;
    for (int i = 1; i <= days.size(); i++) {
      if (i == days.size() || startsNewPeriod(days.get(i - 1), days.get(i))) {
        periods.add(periodOf(days.subList(start, i)));
        start = i;
      }
    }
    return periods.stream()
        .sorted(Comparator.comparing((SignedPeriod signed) -> signed.period().until()).reversed()
            .thenComparing(SignedPeriod::sign))
        .map(SignedPeriod::period)
        .toList();
  }

  /** Another order, or at least {@link #GAP_DAYS} days without a booking in between; the days come ordered by both. */
  private static boolean startsNewPeriod(BookedDay previous, BookedDay next) {
    return !previous.customerorderId().equals(next.customerorderId())
        || DAYS.between(previous.day(), next.day()) - 1 >= GAP_DAYS;
  }

  private static SignedPeriod periodOf(List<BookedDay> days) {
    var first = days.getFirst();
    var duration = days.stream().map(BookedDay::duration).reduce(Duration.ZERO, Duration::plus);
    return new SignedPeriod(first.customerorderSign(), new OrderHistoryPeriod(first.customerorderId(), first.day(),
        days.getLast().day(), days.size(), duration));
  }

  /** The sign only orders the periods; the order itself is the order module's to describe. */
  private record SignedPeriod(String sign, OrderHistoryPeriod period) {
  }
}
