package de.hbt.salat.jira.domain;

/**
 * What can be written on a ticket maintained by hand (#1386) — the same columns a replication fills
 * from JIRA, as far as the booking needs them. The scope is not part of it: it is chosen when the
 * ticket is created and stays.
 *
 * @param key the issue key, required and unique within the scope
 * @param parentKey the key of the parent ticket; it need not exist, a booking reference is free text
 */
public record JiraManualTicketData(String key, String summary, String issueType, String parentKey) {
}
