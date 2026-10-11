package de.hbt.salat.jira.service;

import static de.hbt.salat.jira.domain.JiraReplicationRun.Status.FAILED;
import static de.hbt.salat.jira.domain.JiraReplicationRun.Status.INCOMPLETE;
import static de.hbt.salat.jira.domain.JiraReplicationRun.Status.SUCCEEDED;

import java.util.List;
import de.hbt.salat.jira.domain.JiraReplicationRun.Status;

/**
 * What one replication run did (#1282) — the line it leaves in the run history.
 *
 * @param fetched the issues JIRA answered with
 * @param written the tickets inserted or updated
 * @param failed the issues that could not be stored; the watermark does not move past them (#841),
 *     and the run is incomplete (#1325)
 * @param skipped the issues another replication maintains in the same scope (#1386) — left to it,
 *     and worth a look at the JQL of the two
 * @param skippedTickets the first {@value #SKIPPED_NAMED} of them, each with the replication that
 *     maintains it
 * @param worklogSyncError the reason the worklog sync failed after the replication, with the
 *     password taken out — {@code null} when it ran or had nothing to do
 * @param failedWorklogs the single worklogs the sync could not write or delete (#1325)
 */
public record JiraReplicationResult(int fetched, int written, int failed, int skipped, List<String> skippedTickets,
    String worklogSyncError, int failedWorklogs) {

  /** How many skipped tickets the message names; the run history keeps 4000 characters. */
  public static final int SKIPPED_NAMED = 10;

  public JiraReplicationResult {
    skippedTickets = skippedTickets == null ? List.of() : List.copyOf(skippedTickets);
  }

  /**
   * How the run ends (#1325): failed when the worklog sync broke off, incomplete when single issues
   * could not be stored or single worklogs could not be written, otherwise succeeded. Only the status
   * follows from the issues not processed; the watermark is held back by them as before (#841).
   */
  public Status status() {
    if (worklogSyncError != null) {
      return FAILED;
    }
    return failed > 0 || failedWorklogs > 0 ? INCOMPLETE : SUCCEEDED;
  }

  /** The message of the run, German like every message stored in a run history. */
  public String summary() {
    var summary = new StringBuilder("%d Tickets geholt, %d geschrieben.".formatted(fetched, written));
    var remarks = ticketRemarks(failed, skipped, skippedTickets);
    if (remarks != null) {
      summary.append(' ').append(remarks);
    }
    if (worklogSyncError != null) {
      summary.append(" Worklog-Abgleich fehlgeschlagen: ").append(worklogSyncError);
    }
    if (failedWorklogs > 0) {
      summary.append(" %d Worklogs nicht abgeglichen.".formatted(failedWorklogs));
    }
    return summary.toString();
  }

  /**
   * What is to be said about the tickets beyond their numbers — the issues not processed and the ones
   * left to another replication; {@code null} when there is nothing. Also the message of the step
   * „Tickets lesen" (#1325).
   */
  static String ticketRemarks(int failed, int skipped, List<String> skippedTickets) {
    var remarks = new StringBuilder();
    if (failed > 0) {
      remarks.append("%d nicht verarbeitet — der Wasserstand rückt nicht über sie hinaus."
          .formatted(failed));
    }
    if (skipped > 0) {
      if (!remarks.isEmpty()) {
        remarks.append(' ');
      }
      remarks.append("%d übersprungen — eine andere Replikation pflegt sie im selben Bereich, die JQL überschneidet sich: %s%s."
          .formatted(skipped, String.join(", ", skippedTickets), skipped > skippedTickets.size() ? ", …" : ""));
    }
    return remarks.isEmpty() ? null : remarks.toString();
  }

}
