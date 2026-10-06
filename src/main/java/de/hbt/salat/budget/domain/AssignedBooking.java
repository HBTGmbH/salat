package de.hbt.salat.budget.domain;

import java.time.Duration;
import java.time.LocalDate;

/**
 * One booking assigned to a budget plan, as the detail page lists it (#997).
 *
 * <p>A copy, not the {@code Timereport} entity: the query that builds it joins an entity of
 * {@code dailyreport}, but nothing of that entity crosses the module boundary (→ ADR-0021). The
 * fields are exactly the ones the list shows — plus {@code employeeName}, which the join yields for
 * free and the "Mitarbeitende" card (#964) needs, and {@code employeeId}, by which the rates of the
 * row are resolved (#968). That name is concatenated in the query the way
 * {@code Employee#getName()} does it, because the getter is computed and has no column to select.
 *
 * <p>{@code suborderSign} is filled in by the service, not by the query:
 * {@code Suborder#getCompleteOrderSign()} walks the parent chain of suborders and cannot be
 * expressed in JPQL. The constructor the query calls therefore takes the suborder id, and
 * {@link #withSuborderSign(String)} completes the record.
 */
public record AssignedBooking(
    long id,
    LocalDate day,
    long suborderId,
    String suborderSign,
    long employeeId,
    String employeeSign,
    String employeeName,
    Duration duration,
    String taskDescription) {

    /** The shape the JPQL constructor expression builds — still without the complete order sign. */
    public AssignedBooking(long id, LocalDate day, long suborderId, long employeeId,
                           String employeeSign, String employeeName, Duration duration,
                           String taskDescription) {
        this(id, day, suborderId, null, employeeId, employeeSign, employeeName, duration, taskDescription);
    }

    public AssignedBooking withSuborderSign(String suborderSign) {
        return new AssignedBooking(id, day, suborderId, suborderSign, employeeId, employeeSign,
            employeeName, duration, taskDescription);
    }

}
