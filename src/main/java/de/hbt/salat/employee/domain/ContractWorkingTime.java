package de.hbt.salat.employee.domain;

import java.time.Duration;

/** A contract with its daily working time and nothing else (#827), for a comparison across the workforce. */
public record ContractWorkingTime(long employeecontractId, Duration dailyWorkingTime) {
}
