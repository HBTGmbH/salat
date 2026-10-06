package de.hbt.salat.jira.domain;

/**
 * A ticket as the ticket page lists it (#1386).
 *
 * @param scopeSign the scope as the order tree names it now — the order sign or the complete order
 *     sign of the suborder
 * @param replicationName the replication that maintains the ticket, {@code null} for one maintained
 *     by hand
 * @param maintainedByHand whether the ticket may be changed and deleted on the page: no replication
 *     maintains it
 */
public record JiraTicketRow(
    long id,
    long customerorderId,
    Long suborderId,
    String key,
    String summary,
    String issueType,
    String parentKey,
    String scopeSign,
    String replicationName,
    boolean maintainedByHand
) {
}
