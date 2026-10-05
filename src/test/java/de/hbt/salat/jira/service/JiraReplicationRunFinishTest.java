package de.hbt.salat.jira.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static de.hbt.salat.jira.domain.JiraReplicationRun.Status.FAILED;
import static de.hbt.salat.jira.domain.JiraReplicationRun.Status.RUNNING;
import static de.hbt.salat.jira.domain.JiraReplicationRun.Trigger.SCHEDULED;

import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.CannotCreateTransactionException;
import de.hbt.salat.common.scheduling.ManualTaskScheduler;
import de.hbt.salat.common.scheduling.RunFinisher;
import de.hbt.salat.jira.domain.JiraReplicationRun;
import de.hbt.salat.jira.persistence.JiraReplicationRunRepository;

/**
 * Writing the outcome of a replication run while the database is briefly unreachable (#1300) — the
 * same case as in {@code ETLServiceTest}, for the second run that holds its {@code RUNNING} row as
 * its lock.
 */
@ExtendWith(MockitoExtension.class)
@DisplayNameGeneration(ReplaceUnderscores.class)
class JiraReplicationRunFinishTest {

  private final ManualTaskScheduler taskScheduler =
      new ManualTaskScheduler(Instant.parse("2026-10-03T00:01:00Z"));

  @Spy
  private RunFinisher runFinisher = taskScheduler.runFinisher(Duration.ofMinutes(60));

  @Mock
  private JiraReplicationRunRepository runRepository;

  @InjectMocks
  private JiraReplicationRunService runService;

  @Test
  void a_run_whose_end_cannot_be_written_at_first_writes_it_once_the_database_is_back() {
    var run = runningRun(11L);
    when(runRepository.findById(11L))
        .thenThrow(new CannotCreateTransactionException("Could not open JPA EntityManager for transaction",
            new SQLException("Connection is closed")))
        .thenReturn(Optional.of(run));

    runService.finishRun(11L, FAILED, "Abgebrochen: Server shutdown in progress");
    taskScheduler.runAllPlanned();

    assertThat(run.getStatus()).isEqualTo(FAILED);
    assertThat(run.getFinishedAt()).isNotNull();
    assertThat(run.getMessage()).isEqualTo("Abgebrochen: Server shutdown in progress");
  }

  @Test
  void a_run_marked_finished_by_hand_meanwhile_keeps_that_when_its_end_is_written_late() {
    var markedAt = LocalDateTime.of(2026, 10, 3, 2, 30, 0);
    var run = runningRun(11L);
    run.setStatus(FAILED);
    run.setFinishedAt(markedAt);
    run.setMessage("Von Hand als beendet markiert.");
    when(runRepository.findById(11L))
        .thenThrow(new CannotCreateTransactionException("Connection is closed"))
        .thenReturn(Optional.of(run));

    runService.finishRun(11L, FAILED, "Abgebrochen: Server shutdown in progress");
    taskScheduler.runAllPlanned();

    assertThat(run.getStatus()).isEqualTo(FAILED);
    assertThat(run.getFinishedAt()).isEqualTo(markedAt);
    assertThat(run.getMessage())
        .startsWith("Von Hand als beendet markiert.")
        .contains("Der Lauf kam danach noch zu Ende: Abgebrochen: Server shutdown in progress");
  }

  private static JiraReplicationRun runningRun(long id) {
    return JiraReplicationRun.builder()
        .id(id)
        .startedAt(LocalDateTime.of(2026, 10, 3, 2, 0, 0))
        .status(RUNNING)
        .triggeredBy(SCHEDULED)
        .build();
  }

}
