package org.tb.budget.domain;

/**
 * Which plan a booking is assigned to, as a flat pair (#913).
 *
 * <p>The controlling needs the whole mapping of a customer order at once and nothing else from the
 * assignment — loading the entities would drag their plan association along for every row.
 */
public record TimereportBudgetLink(Long timereportId, Long orderBudgetId) {
}
