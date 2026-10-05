package de.hbt.salat.jira.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static de.hbt.salat.jira.domain.JiraReplicationRun.Status.FAILED;
import static de.hbt.salat.jira.domain.JiraReplicationRun.Status.RUNNING;
import static de.hbt.salat.jira.domain.JiraReplicationRun.Trigger.MANUAL;

import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import de.hbt.salat.auth.domain.AuthorizedUser;
import de.hbt.salat.common.exception.BusinessRuleException;
import de.hbt.salat.common.exception.ErrorCode;
import de.hbt.salat.jira.domain.JiraReplicationRun;

/**
 * A replication started from the list (#1282): the row is opened on the request thread, the run
 * goes on in the background as the system.
 */
@ExtendWith(MockitoExtension.class)
@DisplayNameGeneration(ReplaceUnderscores.class)
class JiraReplicationLauncherTest {

  private static final long REPLICATION_ID = 42L;
  private static final long RUN_ID = 7L;

  @Mock
  private JiraReplicationService replicationService;

  @Mock
  private JiraReplicationRunService runService;

  @Mock
  private ObjectProvider<AuthorizedUser> authorizedUserProvider;

  @Mock
  private AuthorizedUser authorizedUser;

  @Mock
  private ThreadPoolTaskExecutor executor;

  @InjectMocks
  private JiraReplicationLauncher launcher;

  @Test
  void the_run_is_opened_before_it_is_handed_over() {
    when(runService.startRun(REPLICATION_ID, MANUAL)).thenReturn(openedRun());

    var run = launcher.startManualRun(REPLICATION_ID);

    // opened first: the page the request lands on shows it right away, and a second start sees it
    var order = inOrder(runService, executor);
    order.verify(runService).startRun(REPLICATION_ID, MANUAL);
    order.verify(executor).execute(any());
    assertThat(run.getStatus()).isEqualTo(RUNNING);
  }

  @Test
  void a_replication_that_is_still_running_is_not_handed_over_at_all() {
    when(runService.startRun(REPLICATION_ID, MANUAL))
        .thenThrow(new BusinessRuleException(ErrorCode.JI_REPLICATION_RUN_ALREADY_RUNNING, "01.10.2026 10:15:00"));

    assertThatThrownBy(() -> launcher.startManualRun(REPLICATION_ID)).isInstanceOf(BusinessRuleException.class);

    verify(executor, never()).execute(any());
  }

  @Test
  void the_background_thread_works_as_the_system_and_continues_the_opened_run() {
    when(runService.startRun(REPLICATION_ID, MANUAL)).thenReturn(openedRun());
    when(authorizedUserProvider.getObject()).thenReturn(authorizedUser);
    doAnswer(invocation -> {
      invocation.<Runnable>getArgument(0).run();
      return null;
    }).when(executor).execute(any());

    launcher.startManualRun(REPLICATION_ID);

    // without an HTTP request there is no request scope, and without initForJob no valid login
    verify(authorizedUser).initForJob();
    verify(replicationService).continueRun(RUN_ID, REPLICATION_ID);
  }

  @Test
  void a_run_that_never_reached_the_executor_does_not_look_like_one_that_is_still_going() {
    when(runService.startRun(REPLICATION_ID, MANUAL)).thenReturn(openedRun());
    doThrow(new TaskRejectedException("no free thread")).when(executor).execute(any());

    assertThatThrownBy(() -> launcher.startManualRun(REPLICATION_ID))
        .isInstanceOf(BusinessRuleException.class);

    // a row left on RUNNING would block every further start of this replication
    verify(runService).finishRun(eq(RUN_ID), eq(FAILED), anyString());
  }

  private static JiraReplicationRun openedRun() {
    return JiraReplicationRun.builder()
        .id(RUN_ID)
        .status(RUNNING)
        .triggeredBy(MANUAL)
        .build();
  }

}
