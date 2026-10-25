package de.hbt.salat.dailyreport.domain;

import java.time.Duration;
import java.time.LocalDate;

/**
 * A stretch of time in which a person worked on a customer order (#1534): from the first to the last day booked, with
 * no gap of the requested length or more without a booking on the order in between.
 *
 * @param from       the first day booked
 * @param until      the last day booked
 * @param bookedDays on how many days something was booked
 * @param duration   the sum of the bookings
 */
public record OrderHistoryPeriod(LocalDate from, LocalDate until, int bookedDays, Duration duration) {
}
