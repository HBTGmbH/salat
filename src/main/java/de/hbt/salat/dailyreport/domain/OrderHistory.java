package de.hbt.salat.dailyreport.domain;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;

/**
 * The work of a person on one customer order (#1534): the bracket around all its periods, and the periods themselves.
 *
 * @param customerorderId the customer order, on any of whose suborders the days were booked
 * @param from            the first day booked, that of the earliest period
 * @param until           the last day booked, that of the latest period
 * @param bookedDays      on how many days something was booked, over all periods
 * @param duration        the sum of the bookings, over all periods
 * @param periods         the periods, the latest first
 */
public record OrderHistory(long customerorderId, LocalDate from, LocalDate until, int bookedDays, Duration duration,
    List<OrderHistoryPeriod> periods) {
}
