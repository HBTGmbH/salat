package org.tb.jira.domain;

import java.util.List;

/**
 * What came of asking a JIRA instance for its fields (#1013).
 *
 * <p>A failure is reported back rather than thrown, for the same reason
 * {@link JiraReplicationRunOutcome} does: that the foreign system is unreachable is an ordinary
 * answer to "show me the fields", and it belongs inside the dialogue the user opened — not on an
 * error page instead of the form they were editing.
 *
 * @param errorMessage on failure, the technical reason with the stored password removed from it
 */
public record JiraFieldCatalog(List<JiraFieldOption> options, String errorMessage) {

  public static JiraFieldCatalog of(List<JiraFieldOption> options) {
    return new JiraFieldCatalog(List.copyOf(options), null);
  }

  public static JiraFieldCatalog failed(String errorMessage) {
    return new JiraFieldCatalog(List.of(), errorMessage);
  }

  public boolean hasError() {
    return errorMessage != null;
  }
}
