package de.hbt.salat.jira.domain;

import java.util.List;
import java.util.Map;

/**
 * The ticket page (#1386): the rows up to the limit, and the figures over every hit.
 *
 * @param countByType the hits per type, most frequent first; a ticket without type under {@code ""}
 * @param issueTypes every type the order's tickets carry, for the type filter
 */
public record JiraTicketListResult(List<JiraTicketRow> rows, long totalCount, long replicatedCount,
    Map<String, Long> countByType, List<String> issueTypes) {

  public static JiraTicketListResult empty(List<String> issueTypes) {
    return new JiraTicketListResult(List.of(), 0, 0, Map.of(), issueTypes);
  }

  public long manualCount() {
    return totalCount - replicatedCount;
  }

  public boolean truncated() {
    return rows.size() < totalCount;
  }
}
