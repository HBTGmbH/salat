package org.tb.budget.domain;

import java.time.Duration;

/**
 * Who booked how much on which plan (#964) — the rows behind the "Mitarbeitende" column of the plan
 * overview, read for every plan of the page in one statement.
 *
 * <p>A copy of plain values, never an entity (→ ADR-0021). The sum is boxed because the aggregate
 * returns {@code null} rather than zero where a booking carries no duration at all.
 */
public record BudgetEmployeeMinutes(long orderBudgetId, String employeeSign, String employeeName,
                                    Long minutes) {

    public Duration duration() {
        return Duration.ofMinutes(minutes == null ? 0 : minutes);
    }

}
