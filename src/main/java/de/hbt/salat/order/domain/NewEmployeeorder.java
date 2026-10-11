package de.hbt.salat.order.domain;

import java.time.LocalDate;

/**
 * An employee order another module asks for, as values (#1459): for the contract on the suborder,
 * from the given day on. What else it carries — its end, its debit — the order module decides
 * (→ {@code EmployeeorderService#createEmployeeorder}).
 *
 * @param employeecontractId the contract of the person
 * @param suborderId         the suborder the person is to book on
 * @param fromDate           the first day of the order
 */
public record NewEmployeeorder(long employeecontractId, long suborderId, LocalDate fromDate) {
}
