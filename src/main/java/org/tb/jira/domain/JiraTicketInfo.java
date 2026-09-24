package org.tb.jira.domain;

/**
 * A replicated ticket as another module gets to see it (#1092): key, what it is about, its type and where it hangs in
 * the tree. A copy of plain values rather than the entity — {@link JiraTicket} stays in this module (→ ADR-0021).
 *
 * @param key       the issue key as JIRA spells it, e.g. {@code NWP-110}
 * @param summary   the title
 * @param issueType Epic, Story, Subtask …; what a type is called comes from JIRA, this module invents none
 * @param parentKey the key of the ticket above it, {@code null} at the top
 * @param scopeSign the replication this ticket was fetched by — the same key may exist under several
 */
public record JiraTicketInfo(String key, String summary, String issueType, String parentKey, String scopeSign) {}
