package de.hbt.salat.employee.domain;

import java.time.LocalDate;

/** The entry date of a person, read off {@link EmployeeEntry} without loading the row (#1501). */
public record EmployeeEntryDate(long employeeId, LocalDate entryDate) {
}
