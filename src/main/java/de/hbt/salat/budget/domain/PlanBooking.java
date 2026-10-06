package de.hbt.salat.budget.domain;

import java.time.Duration;
import java.time.LocalDate;

/**
 * One booking assigned to a budget plan, with exactly what its revenue is priced from — the plan,
 * the suborder, the person, the day and the duration (#1222).
 *
 * <p>A copy, not the {@code Timereport} entity: the query that builds it joins an entity of
 * {@code dailyreport}, but nothing of that entity crosses the module boundary (→ ADR-0021). The
 * dashboard reads one of these per booking of every plan it shows; loading the entities instead
 * pulled day, contract, person and employee order of every booking along, most of them one
 * statement at a time.
 */
public record PlanBooking(long orderBudgetId, long suborderId, long employeeId, LocalDate day,
                          Duration duration) {
}
