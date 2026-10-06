package de.hbt.salat.jira.domain;

import java.time.LocalDateTime;

/**
 * The earlier import whose column reading the preview proposes (#1386), so that the user sees where
 * the proposal comes from.
 *
 * @param scopeSign the scope it was imported into, as the order tree names it now
 */
public record JiraTicketImportOrigin(String fileName, LocalDateTime importedAt, String scopeSign) {
}
