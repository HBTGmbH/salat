package org.tb.budget.domain;

import java.time.LocalDate;

public record EmployeeCostData(
    String name,
    Integer costCentsPerHour,
    LocalDate validFrom,
    LocalDate validUntil
) {}
