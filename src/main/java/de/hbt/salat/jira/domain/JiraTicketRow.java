package de.hbt.salat.jira.domain;

import java.time.LocalDateTime;

/**
 * A ticket as the ticket page lists it (#1386).
 *
 * @param scopeSign the scope as the order tree names it now — the order sign or the complete order
 *     sign of the suborder
 * @param replicationId the replication that maintains the ticket, {@code null} for one maintained by
 *     hand
 * @param maintainedByHand whether the ticket may be changed and deleted on the page: no replication
 *     maintains it
 * @param updatedTs the last update — in JIRA for a replicated ticket, on the page for one by hand
 * @param inSystemSince when the row was written first
 */
public record JiraTicketRow(
    long id,
    long customerorderId,
    Long suborderId,
    String key,
    String summary,
    String issueType,
    String parentKey,
    String topLevelKey,
    String scopeSign,
    Long replicationId,
    String replicationName,
    boolean maintainedByHand,
    LocalDateTime updatedTs,
    LocalDateTime inSystemSince
) {
}
