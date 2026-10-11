package de.hbt.salat.jira.viewhelper;

import java.time.Duration;
import java.time.LocalDateTime;
import de.hbt.salat.common.util.DurationUtils;
import de.hbt.salat.jira.domain.JiraReplicationRun;
import de.hbt.salat.jira.domain.JiraReplicationRun.Status;
import de.hbt.salat.jira.domain.JiraReplicationRun.Trigger;

/**
 * A replication run as the list and its own page show it (#1282, #1325).
 *
 * @param replicationName the name of the replication the run belongs to
 * @param duration the duration of the run, formatted — {@code null} as long as there is no end. For
 *     a crashed run the time since its start would go on growing and look like a duration; that it
 *     never ended is what the status says.
 * @param messagePreview the beginning of a long message, which the page of the run shows folded —
 *     {@code null} for a short one, which it shows as it is
 */
public record JiraReplicationRunViewHelper(Long id, String replicationName, LocalDateTime startedAt,
                                           LocalDateTime finishedAt, Status status, Trigger triggeredBy,
                                           String message, String messagePreview, String duration) {

  /** Up to this length, and on a single line, a message is shown unfolded. */
  static final int MESSAGE_PREVIEW_LENGTH = 160;

  public static JiraReplicationRunViewHelper from(JiraReplicationRun run) {
    return new JiraReplicationRunViewHelper(run.getId(), run.getReplication().getName(),
        run.getStartedAt(), run.getFinishedAt(), run.getStatus(), run.getTriggeredBy(), run.getMessage(),
        previewOf(run.getMessage()), durationOf(run.getStartedAt(), run.getFinishedAt()));
  }

  static String durationOf(LocalDateTime startedAt, LocalDateTime finishedAt) {
    if (startedAt == null || finishedAt == null) {
      return null;
    }
    return DurationUtils.formatElapsed(Duration.between(startedAt, finishedAt));
  }

  /**
   * The beginning of a message too long to be shown unfolded: an aborted run carries the text of the
   * exception, a run with many skipped tickets names them all, and a run marked finished by hand adds
   * a line.
   */
  static String previewOf(String message) {
    if (message == null) {
      return null;
    }
    var firstLine = message.lines().findFirst().orElse("");
    boolean fitsUnfolded = message.length() <= MESSAGE_PREVIEW_LENGTH && firstLine.length() == message.length();
    if (fitsUnfolded) {
      return null;
    }
    var preview = firstLine.length() > MESSAGE_PREVIEW_LENGTH ? firstLine.substring(0, MESSAGE_PREVIEW_LENGTH) : firstLine;
    return preview.strip() + " …";
  }

}
