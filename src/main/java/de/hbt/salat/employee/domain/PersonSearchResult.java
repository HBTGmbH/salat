package de.hbt.salat.employee.domain;

import java.time.LocalDate;

/**
 * A person the command palette offers (#1157), represented by one of their contracts, with what the
 * current user may open of them.
 *
 * @param mayViewEmployee the person's master data ({@code /employees/view}); the contract itself is
 *                        always readable, otherwise the person is not offered
 */
public record PersonSearchResult(long contractId, long employeeId, String sign, String name,
    LocalDate validFrom, LocalDate validUntil, boolean hidden, boolean mayViewEmployee) {
}
