package de.hbt.salat.budget.domain;

import java.time.LocalDate;

public record EmployeeCostAssignmentData(
    String employeeCostName,
    Long employeeId,
    Long suborderId,
    LocalDate validFrom,
    LocalDate validUntil
) {}
