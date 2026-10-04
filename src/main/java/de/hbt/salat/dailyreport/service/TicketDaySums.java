package de.hbt.salat.dailyreport.service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import de.hbt.salat.dailyreport.persistence.BookedTicketReference;
import de.hbt.salat.jira.command.TicketDaySum;

/**
 * The minutes per day and ticket the worklog sync writes to JIRA (#1007), out of the references of
 * the bookings (#1326).
 *
 * <p>A booking with several references has its duration split evenly among them, so JIRA shows as
 * much time as was booked, not that time once per ticket. Minutes that do not divide go to the
 * references in front, one each: 10 minutes on three tickets are 4, 3 and 3. Splitting happens per
 * booking, before anything is summed, so every booking's minutes add up to its duration exactly.
 */
public final class TicketDaySums {

  private TicketDaySums() {
  }

  /** @param rows ordered by booking and position, as the repository reads them */
  public static List<TicketDaySum> of(List<BookedTicketReference> rows) {
    var byBooking = new LinkedHashMap<Long, List<BookedTicketReference>>();
    rows.forEach(row -> byBooking.computeIfAbsent(row.timereportId(), id -> new ArrayList<>()).add(row));

    var sums = new LinkedHashMap<DayAndReference, Long>();
    byBooking.values().forEach(references -> {
      var shares = shares(references.getFirst().minutes(), references.size());
      for (int i = 0; i < references.size(); i++) {
        var row = references.get(i);
        sums.merge(new DayAndReference(row.workDate(), row.reference()), shares[i], Long::sum);
      }
    });
    return sums.entrySet().stream()
        .map(entry -> new TicketDaySum(entry.getKey().workDate(), entry.getKey().reference(), entry.getValue()))
        .toList();
  }

  /** {@code minutes} split into {@code count} shares, the remainder one minute each to the front ones. */
  static long[] shares(long minutes, int count) {
    var shares = new long[count];
    var base = minutes / count;
    var remainder = minutes % count;
    for (int i = 0; i < count; i++) {
      shares[i] = base + (i < remainder ? 1 : 0);
    }
    return shares;
  }

  private record DayAndReference(LocalDate workDate, String reference) {
  }
}
