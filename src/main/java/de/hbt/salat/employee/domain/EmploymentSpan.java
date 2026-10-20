package de.hbt.salat.employee.domain;

import java.time.LocalDate;

/**
 * The validity of one contract of a person (#1501), as {@link Tenure} reads it: {@code until} is the
 * last day of the contract, {@code null} for a contract without an end.
 */
public record EmploymentSpan(long employeeId, LocalDate from, LocalDate until) {
}
