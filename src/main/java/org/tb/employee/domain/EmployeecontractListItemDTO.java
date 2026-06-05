package org.tb.employee.domain;

import java.time.Duration;
import java.time.LocalDate;

public record EmployeecontractListItemDTO(
    Long id,
    String employeeName,
    String taskDescription,
    String supervisorNames,
    LocalDate validFrom,
    LocalDate validUntil,
    boolean freelancer,
    Duration dailyWorkingTime,
    int vacationEntitlement,
    boolean currentlyValid,
    boolean hide
) {}
