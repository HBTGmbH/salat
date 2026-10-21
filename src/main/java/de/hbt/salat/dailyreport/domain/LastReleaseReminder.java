package de.hbt.salat.dailyreport.domain;

import java.time.LocalDate;

/** The day a contract was last reminded automatically to release (#215). */
public record LastReleaseReminder(long employeecontractId, LocalDate remindedOn) {
}
