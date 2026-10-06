package de.hbt.salat.jira.service;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.util.ReflectionUtils.findField;
import static org.springframework.util.ReflectionUtils.makeAccessible;
import static org.springframework.util.ReflectionUtils.setField;
import static de.hbt.salat.jira.domain.JiraReplicationRun.Trigger.SCHEDULED;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.client.RestClientException;
import de.hbt.salat.auth.domain.AuthorizedUser;
import de.hbt.salat.common.domain.AuditedEntity;
import de.hbt.salat.common.exception.BusinessRuleException;
import de.hbt.salat.common.exception.ErrorCode;
import de.hbt.salat.jira.domain.JiraReplicationConfig;

/**
 * The hourly round over all enabled replications, each with its row in the run history (#1282).
 */
@ExtendWith(MockitoExtension.class)
@DisplayNameGeneration(ReplaceUnderscores.class)
class JiraReplicationSchedulerTest {

  @Mock
  private JiraReplicationService replicationService;

  @Mock
  private JiraReplicationRunService runService;

  @Mock
  private ObjectProvider<AuthorizedUser> authorizedUserProvider;

  @Mock
  private AuthorizedUser authorizedUser;

  @InjectMocks
  private JiraReplicationScheduler scheduler;

  @BeforeEach
  void setUp() {
    when(authorizedUserProvider.getObject()).thenReturn(authorizedUser);
  }

  @Test
  void a_replication_that_is_still_running_is_recorded_as_skipped() {
    when(replicationService.getEnabledReplications()).thenReturn(List.of(config(1L, "Alpha")));
    when(replicationService.runRecorded(1L, SCHEDULED))
        .thenThrow(new BusinessRuleException(ErrorCode.JI_REPLICATION_RUN_ALREADY_RUNNING, "01.10.2026 10:15:00"));

    scheduler.runScheduled();

    verify(runService).recordSkippedRun(eq(1L), eq(SCHEDULED), anyString());
  }

  /** A run started by hand of another replication of the same scope holds this one back (#1386). */
  @Test
  void a_replication_whose_scope_is_busy_is_recorded_as_skipped() {
    when(replicationService.getEnabledReplications()).thenReturn(List.of(config(1L, "Alpha")));
    when(replicationService.runRecorded(1L, SCHEDULED))
        .thenThrow(new BusinessRuleException(ErrorCode.JI_REPLICATION_RUN_SCOPE_BUSY, "Beta", "01.10.2026 10:15:00"));

    scheduler.runScheduled();

    verify(runService).recordSkippedRun(eq(1L), eq(SCHEDULED), contains("desselben Bereichs"));
  }

  @Test
  void a_failing_replication_does_not_hold_up_the_ones_behind_it() {
    // before the run history the first failure ended the whole round
    when(replicationService.getEnabledReplications()).thenReturn(List.of(config(1L, "Alpha"), config(2L, "Beta")));
    when(replicationService.runRecorded(1L, SCHEDULED)).thenThrow(new RestClientException("connection reset"));

    scheduler.runScheduled();

    verify(replicationService).runRecorded(2L, SCHEDULED);
    verify(runService, never()).recordSkippedRun(eq(1L), eq(SCHEDULED), anyString());
  }

  @Test
  void the_round_runs_in_job_mode() {
    when(replicationService.getEnabledReplications()).thenReturn(List.of());

    scheduler.runScheduled();

    verify(authorizedUser).initForJob();
  }

  private static JiraReplicationConfig config(long id, String name) {
    var config = new JiraReplicationConfig();
    var idField = findField(AuditedEntity.class, "id");
    makeAccessible(idField);
    setField(idField, config, id);
    config.setName(name);
    return config;
  }

}
