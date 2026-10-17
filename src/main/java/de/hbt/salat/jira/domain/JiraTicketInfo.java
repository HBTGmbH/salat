package de.hbt.salat.jira.domain;

import java.time.LocalDateTime;

/**
 * A replicated ticket as another module gets to see it (#1092): key, what it is about, its type and where it hangs in
 * the tree. A copy of plain values rather than the entity — {@link JiraTicket} stays in this module (→ ADR-0021).
 *
 * <p>A key is unique within a scope only, so the same key may come back once per scope. Scope and update timestamp
 * let the caller pick the entry that counts within a branch of the order tree (#1390): the most recently updated one
 * among the scopes of that branch, as the suggestions do.
 *
 * @param key             the issue key as JIRA spells it, e.g. {@code NWP-110}
 * @param summary         the title
 * @param issueType       Epic, Story, Subtask …; what a type is called comes from JIRA, this module invents none
 * @param parentKey       the key of the ticket above it, {@code null} at the top
 * @param customerorderId the customer order of the scope the ticket is kept under
 * @param suborderId      the suborder of the scope, {@code null} when it is kept for the whole order
 * @param updatedTs       when the ticket was last changed, {@code null} if never recorded
 */
public record JiraTicketInfo(String key, String summary, String issueType, String parentKey, Long customerorderId,
                             Long suborderId, LocalDateTime updatedTs) {}
