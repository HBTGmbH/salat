package de.hbt.salat.jira.service;

import static org.springframework.web.context.request.RequestContextHolder.resetRequestAttributes;
import static org.springframework.web.context.request.RequestContextHolder.setRequestAttributes;
import static de.hbt.salat.common.exception.ErrorCode.JI_REPLICATION_RUN_EXECUTOR_BUSY;
import static de.hbt.salat.jira.configuration.JiraExecutorConfiguration.JIRA_REPLICATION_TASK_EXECUTOR;
import static de.hbt.salat.jira.domain.JiraReplicationRun.Status.FAILED;
import static de.hbt.salat.jira.domain.JiraReplicationRun.Trigger.MANUAL;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;
import de.hbt.salat.auth.domain.Authorized;
import de.hbt.salat.auth.domain.AuthorizedUser;
import de.hbt.salat.common.exception.BusinessRuleException;
import de.hbt.salat.common.scheduling.SchedulerRequestAttributes;
import de.hbt.salat.jira.domain.JiraReplicationRun;

/**
 * Starts a replication from the list (#1282), the way {@code ETLRunLauncher} starts an ETL run: the
 * row in the run history is opened on the request thread, the run itself goes on in the background,
 * and the list shows its outcome.
 *
 * <p><b>The background thread is also the fix of #1282.</b> On the request thread the run used the
 * request's EntityManager (Open Session in View). Under the {@code NOT_SUPPORTED} it ran with then,
 * reads came from that EntityManager's cache, while every {@code save} opened a fresh one — a
 * ticket written by {@code upsertIfChanged} stayed in the cache with its old version, and saving
 * its parent chain afterwards failed the version check. The scheduled run never had a request's
 * EntityManager, and on a thread of its own the manual run has none either: every read gets a fresh
 * EntityManager and sees the current version.
 *
 * <p>Everything that concerns the person asking — the manager check, the lock — is decided on the
 * request thread before the hand-over. In the background the run is {@code SYSTEM}; a permission
 * check there would always pass ({@link AuthorizedUser#initForJob}).
 */
@Slf4j
@Service
@Authorized(requiresManager = true)
public class JiraReplicationLauncher {

  private final JiraReplicationService replicationService;
  private final JiraReplicationRunService runService;
  private final ObjectProvider<AuthorizedUser> authorizedUserProvider;
  private final ThreadPoolTaskExecutor executor;

  /** By hand rather than {@code @RequiredArgsConstructor}, for the {@link Qualifier} on the executor. */
  public JiraReplicationLauncher(JiraReplicationService replicationService,
                                 JiraReplicationRunService runService,
                                 ObjectProvider<AuthorizedUser> authorizedUserProvider,
                                 @Qualifier(JIRA_REPLICATION_TASK_EXECUTOR) ThreadPoolTaskExecutor executor) {
    this.replicationService = replicationService;
    this.runService = runService;
    this.authorizedUserProvider = authorizedUserProvider;
    this.executor = executor;
  }

  /**
   * Starts a run and returns as soon as it is open — it stands as {@code RUNNING} in the list from
   * now on.
   *
   * @throws BusinessRuleException when the replication is still running, or when every thread is
   *     taken
   */
  public JiraReplicationRun startManualRun(long replicationId) {
    var run = runService.startRun(replicationId, MANUAL);
    try {
      executor.execute(() -> runInBackground(run.getId(), replicationId));
    } catch (TaskRejectedException e) {
      // The row must not stay on "running" and block every further start, and the person asking
      // needs a message rather than an error page.
      log.error("Manual JIRA replication could not be handed to the executor", e);
      runService.finishRun(run.getId(), FAILED,
          "Der Lauf konnte nicht gestartet werden: kein freier Ausführungsthread.");
      throw new BusinessRuleException(JI_REPLICATION_RUN_EXECUTOR_BUSY, e);
    }
    return run;
  }

  private void runInBackground(long runId, long replicationId) {
    // Without an HTTP request there is no request scope; the request-scoped AuthorizedUser needs the
    // same preparation as in a scheduled job (→ ADR-0006).
    setRequestAttributes(new SchedulerRequestAttributes(), true);
    try {
      authorizedUserProvider.getObject().initForJob();
      replicationService.continueRun(runId, replicationId);
    } catch (Exception e) {
      // The run keeps its failure in its own row; the exception would otherwise end in the thread
      // without a word anywhere.
      log.error("Manually started JIRA replication failed: id={}", replicationId, e);
    } finally {
      // Nothing is destroyed by hand - see SchedulerRequestAttributes (#1084).
      resetRequestAttributes();
    }
  }

}
