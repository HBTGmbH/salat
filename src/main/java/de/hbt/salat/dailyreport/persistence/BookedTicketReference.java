package de.hbt.salat.dailyreport.persistence;

import java.time.LocalDate;

/**
 * One ticket reference of one booking, with the duration of the whole booking (#1326) — the row
 * {@code TimereportRepository.getBookedTicketReferences} reads for the worklog sync.
 *
 * @param position where the reference stands on the booking, counted from 1
 */
public record BookedTicketReference(Long timereportId, LocalDate workDate, Integer position, String reference,
                                    Integer durationhours, Integer durationminutes) {

  public long minutes() {
    return 60L * (durationhours == null ? 0 : durationhours) + (durationminutes == null ? 0 : durationminutes);
  }
}
