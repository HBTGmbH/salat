package de.hbt.salat.jira.viewhelper;

import static org.assertj.core.api.Assertions.assertThat;
import static de.hbt.salat.jira.domain.JiraReplicationRun.Status.INCOMPLETE;
import static de.hbt.salat.jira.domain.JiraReplicationRun.Status.SUCCEEDED;
import static de.hbt.salat.jira.domain.JiraReplicationRunStep.Step.READ_TICKETS;
import static de.hbt.salat.jira.domain.JiraReplicationRunStep.Step.WRITE_WORKLOGS;

import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import de.hbt.salat.jira.domain.JiraReplicationRunStep;
import de.hbt.salat.jira.viewhelper.JiraReplicationRunStepViewHelper.Counter;

/** A step as the page of a run shows it (#1325). */
@DisplayNameGeneration(ReplaceUnderscores.class)
class JiraReplicationRunStepViewHelperTest {

  private static final LocalDateTime START = LocalDateTime.of(2026, 10, 11, 10, 15, 0);

  @Test
  void a_step_shows_only_its_own_counters_in_their_order() {
    var step = JiraReplicationRunStep.builder()
        .step(READ_TICKETS).status(SUCCEEDED).startedAt(START).finishedAt(START.plusSeconds(3))
        .ticketsFetched(12).ticketsWritten(3).ticketsFailed(0).ticketsSkipped(0).ticketsRemoved(1)
        .parentChainsResolved(5)
        .build();

    var view = JiraReplicationRunStepViewHelper.from(step);

    assertThat(view.counters()).extracting(Counter::key).containsExactly(
        "ticketsFetched", "ticketsWritten", "ticketsFailed", "ticketsSkipped", "ticketsRemoved", "parentChainsResolved");
    assertThat(view.counters()).noneMatch(Counter::isNoticeable);
    assertThat(view.duration()).isNotNull();
  }

  @Test
  void failed_worklogs_stand_out() {
    var step = JiraReplicationRunStep.builder()
        .step(WRITE_WORKLOGS).status(INCOMPLETE).startedAt(START).finishedAt(START.plusSeconds(3))
        .worklogsCreated(2).worklogsUpdated(0).worklogsDeleted(0).worklogsUnchanged(4).worklogsKept(0)
        .worklogsFailed(1)
        .build();

    var view = JiraReplicationRunStepViewHelper.from(step);

    assertThat(view.counters()).filteredOn(Counter::isNoticeable).extracting(Counter::key)
        .containsExactly("worklogsFailed");
  }

  @Test
  void a_step_that_broke_off_has_no_counters() {
    var step = JiraReplicationRunStep.builder()
        .step(READ_TICKETS).status(SUCCEEDED).startedAt(START).finishedAt(START)
        .message("401 Unauthorized")
        .build();

    var view = JiraReplicationRunStepViewHelper.from(step);

    assertThat(view.counters()).isEmpty();
    assertThat(view.messagePreview()).isNull();
  }

  @Test
  void a_long_message_is_folded_behind_its_beginning() {
    var longMessage = "x".repeat(JiraReplicationRunViewHelper.MESSAGE_PREVIEW_LENGTH + 1);

    assertThat(JiraReplicationRunViewHelper.previewOf(longMessage))
        .isEqualTo("x".repeat(JiraReplicationRunViewHelper.MESSAGE_PREVIEW_LENGTH) + " …");
  }

  @Test
  void a_message_of_several_lines_is_folded_behind_its_first() {
    assertThat(JiraReplicationRunViewHelper.previewOf("Abgebrochen.\nVon Hand als beendet markiert."))
        .isEqualTo("Abgebrochen. …");
  }

  @Test
  void a_short_message_is_shown_as_it_is() {
    assertThat(JiraReplicationRunViewHelper.previewOf("12 Tickets geholt, 3 geschrieben.")).isNull();
    assertThat(JiraReplicationRunViewHelper.previewOf(null)).isNull();
  }

}
