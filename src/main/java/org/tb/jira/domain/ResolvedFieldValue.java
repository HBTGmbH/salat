package org.tb.jira.domain;

/**
 * One resolved value of an inherited JIRA field, together with where it came from (#881).
 *
 * <p>Stored as the value side of {@link JiraTicket#getCustomFieldsEffective()}, so the shape below is
 * what an evaluating view reads:
 * {@code {"customfield_10123": {"value": "Wartung", "from": "PROJ-1"}}}.
 *
 * @param value the value as it was read from JIRA
 * @param from the key of the ancestor the value was taken from, or {@code null} when the ticket
 *     carries the value itself. An own value always beats an inherited one.
 */
public record ResolvedFieldValue(String value, String from) {

}
