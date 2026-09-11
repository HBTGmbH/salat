package org.tb.jira.domain;

/**
 * What came of a replication started by hand from the user interface (#984).
 *
 * <p>A failure is reported back rather than thrown, because the failure of one replication is an
 * ordinary answer to "run this now": the page has to show what went wrong next to the other
 * replications, not an error page instead of them.
 *
 * @param message on failure, the technical reason with the stored password removed from it
 */
public record JiraReplicationRunOutcome(String name, boolean success, String message) {

  public static JiraReplicationRunOutcome succeeded(String name) {
    return new JiraReplicationRunOutcome(name, true, null);
  }

  public static JiraReplicationRunOutcome failed(String name, String message) {
    return new JiraReplicationRunOutcome(name, false, message);
  }
}
