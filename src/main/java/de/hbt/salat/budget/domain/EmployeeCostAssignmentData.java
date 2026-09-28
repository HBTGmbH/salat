package de.hbt.salat.budget.domain;

import java.time.LocalDate;

public record EmployeeCostAssignmentData(
    String employeeCostName,
    Long employeeId,
    String suborderSign,
    LocalDate validFrom,
    LocalDate validUntil
) {}
