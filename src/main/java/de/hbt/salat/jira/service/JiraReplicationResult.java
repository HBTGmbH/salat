package de.hbt.salat.jira.service;

/**
 * What one replication run did (#1282) — the line it leaves in the run history.
 *
 * @param fetched the issues JIRA answered with
 * @param written the tickets inserted or updated
 * @param failed the issues that could not be stored; the watermark does not move past them (#841)
 * @param worklogSyncError the reason the worklog sync failed after the replication, with the
 *     password taken out — {@code null} when it ran or had nothing to do
 */
public record JiraReplicationResult(int fetched, int written, int failed, String worklogSyncError) {

  /** Clean only when every issue was stored and the worklogs went through as well. */
  public boolean succeeded() {
    return failed == 0 && worklogSyncError == null;
  }

  /** The message of the run, German like every message stored in a run history. */
  public String summary() {
    var summary = new StringBuilder("%d Tickets geholt, %d geschrieben.".formatted(fetched, written));
    if (failed > 0) {
      summary.append(" %d nicht verarbeitet — der Wasserstand rückt nicht über sie hinaus."
          .formatted(failed));
    }
    if (worklogSyncError != null) {
      summary.append(" Worklog-Abgleich fehlgeschlagen: ").append(worklogSyncError);
    }
    return summary.toString();
  }

}
