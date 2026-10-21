package de.hbt.salat.dailyreport.domain;

import java.time.Duration;
import java.time.LocalDate;

/**
 * A booking as the tabs of „Meine Konten“ read it (#1514): the values they count and list, from one
 * query, instead of a {@link TimereportDTO} for which each booking fetched its day, employee order,
 * suborder and customer order one at a time (docs/performance-tips.md §10).
 *
 * @param employeeorderId   the employee order booked on
 * @param suborderId        its suborder, which decides regular training and special leave by id (#1341)
 * @param day               the day booked
 * @param duration          the time booked
 * @param training          flagged as training on the job (#836)
 * @param completeOrderSign {@code Suborder#getCompleteOrderSign()}, a stored column (#1342)
 * @param customerShortname the short name of the customer of the order
 * @param taskdescription   the comment of the booking
 */
public record AccountBooking(long employeeorderId, long suborderId, LocalDate day, Duration duration,
    boolean training, String completeOrderSign, String customerShortname, String taskdescription) {
}
