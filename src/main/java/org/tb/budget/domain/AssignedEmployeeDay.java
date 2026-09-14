package org.tb.budget.domain;

import java.time.Duration;
import java.time.LocalDate;

/**
 * What one person booked on one suborder on one day, against one budget plan (#964).
 *
 * <p>A copy, not the {@code Timereport} entity: the query that builds it joins an entity of
 * {@code dailyreport} and reaches through it into {@code employee}, but nothing of either crosses
 * the module boundary (→ ADR-0021). The name is concatenated in the query the way
 * {@code Employee#getName()} does it, because the getter is computed and has no column to select.
 *
 * <p>Suborder and day stay in the key although the card shows one row per person: both rate lookups
 * resolve by them, so aggregating any coarser would throw away what the resolution needs. The
 * condensing to one row per person happens afterwards, in the service.
 *
 * <p>Both figures are boxed because that is what the aggregate returns: {@code count} is a
 * {@code Long}, and the sum is {@code null} — not zero — where a booking carries no duration at all.
 */
public record AssignedEmployeeDay(String employeeSign, String employeeName, long suborderId,
                                  LocalDate day, Long count, Long minutes) {

    public long bookings() {
        return count == null ? 0 : count;
    }

    public Duration duration() {
        return Duration.ofMinutes(minutes == null ? 0 : minutes);
    }

}
