package org.tb.budget.domain;

/**
 * Who booked on which plan (#964) — the rows behind the "Mitarbeitende" column of the plan overview,
 * read for every plan of the page in one statement.
 *
 * <p>Signs and names, no hours. The overview answers the question before the numbers — who works on
 * this plan at all — and has to stay narrow; hours and rates are on the detail page, where the full
 * card is. The rows arrive in alphabetical order of the sign, which is the order the column shows.
 *
 * <p>A copy of plain values, never an entity (→ ADR-0021).
 */
public record BudgetEmployeeSign(long orderBudgetId, String employeeSign, String employeeName) {
}
