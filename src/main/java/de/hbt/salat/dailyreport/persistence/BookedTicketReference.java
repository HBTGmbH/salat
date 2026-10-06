package de.hbt.salat.dailyreport.persistence;

import java.time.Duration;
import java.time.LocalDate;

/**
 * One ticket reference of one booking, with the duration of the whole booking (#1326) — the row
 * {@code TimereportRepository.getBookedTicketReferences} reads for the worklog sync.
 *
 * @param position where the reference stands on the booking, counted from 1
 */
public record BookedTicketReference(Long timereportId, LocalDate workDate, Integer position, String reference,
                                    Duration duration) {

  public long minutes() {
    return duration.toMinutes();
  }
}
