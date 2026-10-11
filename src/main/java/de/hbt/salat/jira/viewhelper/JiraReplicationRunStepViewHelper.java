package de.hbt.salat.jira.viewhelper;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import de.hbt.salat.jira.domain.JiraReplicationRun.Status;
import de.hbt.salat.jira.domain.JiraReplicationRunStep;
import de.hbt.salat.jira.domain.JiraReplicationRunStep.Step;

/**
 * A step of a replication run as the page of the run shows it (#1325).
 *
 * @param counters the counters the step has, in the order the page names them
 * @param worklogsFrom first day of the period the worklogs were compared over, {@code null} for
 *     reading the tickets and for a sync that had nothing to work on
 * @param messagePreview see {@link JiraReplicationRunViewHelper#messagePreview()}
 */
public record JiraReplicationRunStepViewHelper(Step step, LocalDateTime startedAt, LocalDateTime finishedAt,
                                               String duration, Status status, List<Counter> counters,
                                               LocalDate worklogsFrom, LocalDate worklogsUntil, String message,
                                               String messagePreview) {

  /**
   * One counter of a step.
   *
   * @param key the last part of its message key, {@code main.jira.replication.run.step.counter.<key>}
   * @param failure whether it counts single cases that went wrong — shown as such once it is above zero
   */
  public record Counter(String key, int value, boolean failure) {

    /** Whether the page marks it: a failure that happened. */
    public boolean isNoticeable() {
      return failure && value > 0;
    }

  }

  public JiraReplicationRunStepViewHelper {
    counters = List.copyOf(counters);
  }

  public static JiraReplicationRunStepViewHelper from(JiraReplicationRunStep step) {
    return new JiraReplicationRunStepViewHelper(step.getStep(), step.getStartedAt(), step.getFinishedAt(),
        JiraReplicationRunViewHelper.durationOf(step.getStartedAt(), step.getFinishedAt()), step.getStatus(),
        countersOf(step), step.getWorklogsFrom(), step.getWorklogsUntil(), step.getMessage(),
        JiraReplicationRunViewHelper.previewOf(step.getMessage()));
  }

  /** The counters that are set; a step that broke off, or had nothing to work on, has none. */
  private static List<Counter> countersOf(JiraReplicationRunStep step) {
    var counters = new ArrayList<Counter>();
    add(counters, "ticketsFetched", step.getTicketsFetched(), false);
    add(counters, "ticketsWritten", step.getTicketsWritten(), false);
    add(counters, "ticketsFailed", step.getTicketsFailed(), true);
    add(counters, "ticketsSkipped", step.getTicketsSkipped(), false);
    add(counters, "ticketsRemoved", step.getTicketsRemoved(), false);
    add(counters, "parentChainsResolved", step.getParentChainsResolved(), false);
    add(counters, "worklogsCreated", step.getWorklogsCreated(), false);
    add(counters, "worklogsUpdated", step.getWorklogsUpdated(), false);
    add(counters, "worklogsDeleted", step.getWorklogsDeleted(), false);
    add(counters, "worklogsUnchanged", step.getWorklogsUnchanged(), false);
    add(counters, "worklogsKept", step.getWorklogsKept(), false);
    add(counters, "worklogsFailed", step.getWorklogsFailed(), true);
    return counters;
  }

  private static void add(List<Counter> counters, String key, Integer value, boolean failure) {
    if (value != null) {
      counters.add(new Counter(key, value, failure));
    }
  }

}
