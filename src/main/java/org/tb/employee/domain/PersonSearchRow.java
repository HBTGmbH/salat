package org.tb.employee.domain;

import java.time.LocalDate;

/** A contract of a person as the command palette finds it (#1157): plain values, no entity. */
public record PersonSearchRow(long contractId, long employeeId, String sign, String firstname,
    String lastname, LocalDate validFrom, LocalDate validUntil, Boolean contractHide, Boolean employeeHide) {
}
