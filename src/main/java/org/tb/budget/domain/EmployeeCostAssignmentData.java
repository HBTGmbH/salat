package org.tb.budget.domain;

import java.time.LocalDate;

public record EmployeeCostAssignmentData(
    String employeeCostName,
    String employeeSign,
    String suborderSign,
    LocalDate validFrom,
    LocalDate validUntil
) {}
