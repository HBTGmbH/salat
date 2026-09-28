package de.hbt.salat.dailyreport.domain;

import java.time.Duration;
import java.util.List;
import java.util.Set;

/**
 * The answer of the booking list (#1092).
 *
 * <p>The rows are cut to the chosen maximum, the numbers are not: a sum that depends on a display setting is a sum
 * nobody can use. {@link #truncated()} is what the view says out loud.
 *
 * @param timereports     the rows to show, already limited
 * @param totalCount      how many bookings the filter hits
 * @param totalDuration   their duration
 * @param billableDuration the part of it booked on a billable suborder
 * @param employeeCount   over how many employees the hits spread
 * @param orderCount      over how many customer orders
 * @param editableIds     the shown rows the current user may change — the answer the booking form gets when saving,
 *                        so the list offers editing only where saving would succeed (#1190)
 */
public record TimereportListResult(
    List<TimereportDTO> timereports,
    long totalCount,
    Duration totalDuration,
    Duration billableDuration,
    long employeeCount,
    long orderCount,
    Set<Long> editableIds
) {

  public static TimereportListResult empty() {
    return new TimereportListResult(List.of(), 0, Duration.ZERO, Duration.ZERO, 0, 0, Set.of());
  }

  public boolean isEditable(long timereportId) {
    return editableIds.contains(timereportId);
  }

  public boolean truncated() {
    return timereports.size() < totalCount;
  }
}
