package de.hbt.salat.dailyreport.domain;

import java.time.Duration;
import java.time.LocalDate;

/**
 * A stretch of time in which a person worked on a customer order (#1534): from the first to the last day booked, with
 * no gap of {@value de.hbt.salat.dailyreport.service.OrderHistoryService#GAP_DAYS} days or more without a booking on
 * the order in between.
 *
 * @param customerorderId the customer order, on any of whose suborders the days were booked
 * @param from            the first day booked
 * @param until           the last day booked
 * @param bookedDays      on how many days something was booked
 * @param duration        the sum of the bookings
 */
public record OrderHistoryPeriod(long customerorderId, LocalDate from, LocalDate until, int bookedDays,
    Duration duration) {
}
