package de.hbt.salat.jira.viewhelper;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
import de.hbt.salat.common.util.DurationUtils;
import de.hbt.salat.jira.domain.JiraReplicationRun;
import de.hbt.salat.jira.domain.JiraReplicationRun.Status;
import de.hbt.salat.jira.domain.JiraReplicationRun.Trigger;

/**
 * A replication run as the list shows it (#1282).
 *
 * @param replicationName the name of the replication, looked up by its id — the row keeps only that
 * @param duration the duration of the run, formatted — {@code null} as long as there is no end. For
 *     a crashed run the time since its start would go on growing and look like a duration; that it
 *     never ended is what the status says.
 */
public record JiraReplicationRunViewHelper(Long id, String replicationName, LocalDateTime startedAt,
                                           LocalDateTime finishedAt, Status status, Trigger triggeredBy,
                                           String message, String duration) {

  public static JiraReplicationRunViewHelper from(JiraReplicationRun run, Map<Long, String> namesById) {
    return new JiraReplicationRunViewHelper(run.getId(), namesById.get(run.getReplicationId()),
        run.getStartedAt(), run.getFinishedAt(), run.getStatus(), run.getTriggeredBy(), run.getMessage(),
        durationOf(run));
  }

  private static String durationOf(JiraReplicationRun run) {
    if (run.getStartedAt() == null || run.getFinishedAt() == null) {
      return null;
    }
    return DurationUtils.formatElapsed(Duration.between(run.getStartedAt(), run.getFinishedAt()));
  }

}
