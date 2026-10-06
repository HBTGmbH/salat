package de.hbt.salat.jira.service;

import java.util.List;

/**
 * What one replication run did (#1282) — the line it leaves in the run history.
 *
 * @param fetched the issues JIRA answered with
 * @param written the tickets inserted or updated
 * @param failed the issues that could not be stored; the watermark does not move past them (#841)
 * @param skipped the issues another replication maintains in the same scope (#1386) — left to it,
 *     and worth a look at the JQL of the two
 * @param skippedTickets the first {@value #SKIPPED_NAMED} of them, each with the replication that
 *     maintains it
 * @param worklogSyncError the reason the worklog sync failed after the replication, with the
 *     password taken out — {@code null} when it ran or had nothing to do
 */
public record JiraReplicationResult(int fetched, int written, int failed, int skipped, List<String> skippedTickets,
    String worklogSyncError) {

  /** How many skipped tickets the message names; the run history keeps 4000 characters. */
  public static final int SKIPPED_NAMED = 10;

  public JiraReplicationResult {
    skippedTickets = skippedTickets == null ? List.of() : List.copyOf(skippedTickets);
  }

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
    if (skipped > 0) {
      summary.append(" %d übersprungen — eine andere Replikation pflegt sie im selben Bereich, die JQL überschneidet sich: %s%s."
          .formatted(skipped, String.join(", ", skippedTickets), skipped > skippedTickets.size() ? ", …" : ""));
    }
    if (worklogSyncError != null) {
      summary.append(" Worklog-Abgleich fehlgeschlagen: ").append(worklogSyncError);
    }
    return summary.toString();
  }

}
