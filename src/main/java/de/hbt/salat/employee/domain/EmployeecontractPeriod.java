package de.hbt.salat.employee.domain;

import java.time.LocalDate;

/**
 * The part of a period an employee contract covers (#1450): {@code from} and {@code until} are the
 * contract's validity cut to the period asked for.
 *
 * <p>A period of bookings may span a change of contract, and the bookings after the change belong to
 * the new contract. Readers of a period therefore ask once for these parts and read each contract
 * over its part, instead of resolving the contract on the first day and reading every day with it —
 * that dropped the bookings after a change without a word. Days outside every part have no contract.
 */
public record EmployeecontractPeriod(long employeecontractId, LocalDate from, LocalDate until) {

    public static EmployeecontractPeriod of(Employeecontract contract, LocalDate from, LocalDate until) {
        var validUntil = contract.getValidUntil();
        return new EmployeecontractPeriod(contract.getId(),
                contract.getValidFrom().isAfter(from) ? contract.getValidFrom() : from,
                validUntil != null && validUntil.isBefore(until) ? validUntil : until);
    }

}
