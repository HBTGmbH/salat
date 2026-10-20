package de.hbt.salat.dailyreport.domain;

import java.time.LocalDate;

/** The training a person booked on one day, in minutes (#1501). */
public record TrainingDay(long employeeId, LocalDate day, long minutes) {
}
