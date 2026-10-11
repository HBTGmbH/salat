package de.hbt.salat.jira.service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * What one worklog sync did (#1325) — the step „Worklogs schreiben" of a replication run, with the
 * counters that used to reach the log only.
 *
 * @param from the first day compared — {@code null} when the sync had nothing to work on
 * @param until the last day compared, the day of the run — {@code null} likewise
 * @param skippedReason why the sync, switched on, had nothing to work on — {@code null} when it ran
 * @param failed the worklogs that could not be written or deleted; their rows stay as they were, so the
 *     next run tries again
 * @param failedWorklogs the first {@value #FAILED_NAMED} of them, as ticket and day
 */
public record JiraWorklogSyncResult(LocalDate from, LocalDate until, String skippedReason, int created,
                                    int updated, int deleted, int unchanged, int kept, int failed,
                                    List<String> failedWorklogs) {

  /** How many failed worklogs the message names; the step keeps 4000 characters. */
  public static final int FAILED_NAMED = 10;

  static final DateTimeFormatter DAY_FORMAT = DateTimeFormatter.ofPattern("dd.MM.yyyy");

  public JiraWorklogSyncResult {
    failedWorklogs = failedWorklogs == null ? List.of() : List.copyOf(failedWorklogs);
  }

  /** Switched on, but nothing to work on — said in German, like every message of the run history. */
  static JiraWorklogSyncResult skipped(String reason) {
    return new JiraWorklogSyncResult(null, null, reason, 0, 0, 0, 0, 0, 0, List.of());
  }

  public boolean isSkipped() {
    return skippedReason != null;
  }

  /** The message of the step: the reason it had nothing to do, the worklogs it missed, or nothing. */
  public String message() {
    if (isSkipped()) {
      return skippedReason;
    }
    if (failed == 0) {
      return null;
    }
    return "%d Worklogs konnten nicht abgeglichen werden, der nächste Lauf versucht es erneut: %s%s."
        .formatted(failed, String.join(", ", failedWorklogs), failed > failedWorklogs.size() ? ", …" : "");
  }

  /** How a failed worklog is named in the message. */
  static String nameOf(String issueKey, LocalDate workDate) {
    return issueKey + " am " + workDate.format(DAY_FORMAT);
  }

}
