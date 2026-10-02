package de.hbt.salat.jira.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;
import static de.hbt.salat.jira.domain.JiraReplicationRun.Status.FAILED;
import static de.hbt.salat.jira.domain.JiraReplicationRun.Status.RUNNING;
import static de.hbt.salat.jira.domain.JiraReplicationRun.Status.SKIPPED;
import static de.hbt.salat.jira.domain.JiraReplicationRun.Status.SUCCEEDED;
import static de.hbt.salat.jira.domain.JiraReplicationRun.Trigger.MANUAL;
import static de.hbt.salat.jira.domain.JiraReplicationRun.Trigger.SCHEDULED;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import de.hbt.salat.auth.domain.AuthorizedUser;
import de.hbt.salat.common.exception.AuthorizationException;
import de.hbt.salat.common.exception.BusinessRuleException;
import de.hbt.salat.common.exception.ErrorCode;
import de.hbt.salat.jira.domain.JiraReplicationRun;
import de.hbt.salat.jira.persistence.JiraReplicationRunRepository;

/**
 * The run history of the replications and the lock it is at the same time (#1282).
 */
@SpringBootTest
@DisplayNameGeneration(ReplaceUnderscores.class)
class JiraReplicationRunServiceTest {

  private static final long ALPHA = 9001L;
  private static final long BETA = 9002L;

  @MockitoBean
  private AuthorizedUser authorizedUser;

  @Autowired
  private JiraReplicationRunService runService;

  @Autowired
  private JiraReplicationRunRepository runRepository;

  @BeforeEach
  void setUp() {
    when(authorizedUser.isAuthenticated()).thenReturn(true);
    when(authorizedUser.isManager()).thenReturn(true);
  }

  @AfterEach
  void tearDown() {
    runRepository.deleteByReplicationId(ALPHA);
    runRepository.deleteByReplicationId(BETA);
  }

  @Test
  void a_running_replication_does_not_start_a_second_time() {
    runService.startRun(ALPHA, MANUAL);

    assertThatThrownBy(() -> runService.startRun(ALPHA, SCHEDULED))
        .isInstanceOf(BusinessRuleException.class)
        .satisfies(ex -> assertThat(((BusinessRuleException) ex).getMessages())
            .extracting(message -> message.getErrorCode())
            .containsExactly(ErrorCode.JI_REPLICATION_RUN_ALREADY_RUNNING));
  }

  @Test
  void another_replication_starts_meanwhile() {
    // two replications write into different scopes and get along
    runService.startRun(ALPHA, MANUAL);

    assertThat(runService.startRun(BETA, MANUAL).getStatus()).isEqualTo(RUNNING);
    assertThat(runService.getRunningReplicationIds()).contains(ALPHA, BETA);
  }

  @Test
  void a_finished_run_frees_its_replication() {
    var run = runService.startRun(ALPHA, MANUAL);
    runService.finishRun(run.getId(), SUCCEEDED, "3 Tickets geholt, 1 geschrieben.");

    assertThat(runService.startRun(ALPHA, MANUAL).getStatus()).isEqualTo(RUNNING);
    var finished = runRepository.findById(run.getId()).orElseThrow();
    assertThat(finished.getStatus()).isEqualTo(SUCCEEDED);
    assertThat(finished.getFinishedAt()).isNotNull();
  }

  @Test
  void a_run_marked_finished_by_hand_keeps_that_and_gets_its_outcome_appended() {
    var run = runService.startRun(ALPHA, MANUAL);
    runService.markFinished(run.getId());

    runService.finishRun(run.getId(), SUCCEEDED, "3 Tickets geholt, 1 geschrieben.");

    var stored = runRepository.findById(run.getId()).orElseThrow();
    assertThat(stored.getStatus()).isEqualTo(FAILED);
    assertThat(stored.getMessage())
        .startsWith("Von Hand als beendet markiert.")
        .endsWith("Der Lauf kam danach noch zu Ende: 3 Tickets geholt, 1 geschrieben.");
  }

  @Test
  void only_a_running_run_can_be_marked_finished() {
    var run = runService.startRun(ALPHA, MANUAL);
    runService.finishRun(run.getId(), SUCCEEDED, "fertig");

    assertThatThrownBy(() -> runService.markFinished(run.getId()))
        .isInstanceOf(BusinessRuleException.class);
  }

  @Test
  void the_failed_filter_keeps_running_and_skipped_runs() {
    // a crashed run stands on RUNNING forever - hiding it would hide what the list is for
    var succeeded = runService.startRun(ALPHA, SCHEDULED);
    runService.finishRun(succeeded.getId(), SUCCEEDED, "fertig");
    var running = runService.startRun(ALPHA, MANUAL);
    runService.recordSkippedRun(ALPHA, SCHEDULED, "Übersprungen");

    assertThat(runService.getLatestRuns(100, true))
        .filteredOn(run -> run.getReplicationId() == ALPHA)
        .extracting(JiraReplicationRun::getStatus)
        .containsExactlyInAnyOrder(RUNNING, SKIPPED)
        .doesNotContain(SUCCEEDED);
    assertThat(runService.getLatestRuns(100, false))
        .extracting(JiraReplicationRun::getId)
        .contains(succeeded.getId(), running.getId());
  }

  @Test
  void the_history_is_for_the_management() {
    when(authorizedUser.isManager()).thenReturn(false);

    assertThatThrownBy(() -> runService.getLatestRuns(100, false)).isInstanceOf(AuthorizationException.class);
    assertThatThrownBy(() -> runService.markFinished(1L)).isInstanceOf(AuthorizationException.class);
    // the lock as well: the scheduled run gets through as the job user, which is a manager
    assertThatThrownBy(() -> runService.startRun(ALPHA, MANUAL)).isInstanceOf(AuthorizationException.class);
  }

}
