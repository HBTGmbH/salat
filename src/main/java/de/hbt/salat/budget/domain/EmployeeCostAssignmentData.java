package de.hbt.salat.budget.domain;

import java.time.LocalDate;

/**
 * @param customerorderId the customer order the assignment is for (#1343), {@code null} for an assignment
 *                        to a suborder and for a general one
 * @param suborderId      the suborder the assignment is for, {@code null} for an assignment to a customer
 *                        order and for a general one
 */
public record EmployeeCostAssignmentData(
    String employeeCostName,
    Long employeeId,
    Long customerorderId,
    Long suborderId,
    LocalDate validFrom,
    LocalDate validUntil
) {}
