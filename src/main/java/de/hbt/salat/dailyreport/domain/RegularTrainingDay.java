package de.hbt.salat.dailyreport.domain;

import java.time.Duration;
import java.time.LocalDate;

/**
 * The regular training a person booked on one day on one contract, in minutes, with the daily working
 * time of that contract (#1512) - what the working days of the yearly training achievement are
 * counted from.
 */
public record RegularTrainingDay(long employeeId, LocalDate day, long minutes, Duration dailyWorkingTime) {
}
