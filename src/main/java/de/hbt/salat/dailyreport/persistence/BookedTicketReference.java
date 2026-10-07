package de.hbt.salat.dailyreport.persistence;

import java.time.Duration;
import java.time.LocalDate;

/**
 * One ticket reference of one booking, with the duration of the whole booking (#1326) — the row
 * {@code TimereportRepository.getBookedTicketReferences} reads for the worklog sync.
 *
 * @param position where the reference stands on the booking, counted from 1
 * @param employeeSign the sign of the person who booked (#1408) — the only thing about the person
 *                     the worklog comment names
 */
public record BookedTicketReference(Long timereportId, LocalDate workDate, Integer position, String reference,
                                    Duration duration, String employeeSign) {

  public long minutes() {
    return duration.toMinutes();
  }
}
