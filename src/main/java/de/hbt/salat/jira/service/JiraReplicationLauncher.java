package de.hbt.salat.jira.service;

import static org.springframework.web.context.request.RequestContextHolder.resetRequestAttributes;
import static org.springframework.web.context.request.RequestContextHolder.setRequestAttributes;
import static de.hbt.salat.jira.configuration.JiraExecutorConfiguration.JIRA_REPLICATION_TASK_EXECUTOR;

import java.util.concurrent.ExecutionException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;
import de.hbt.salat.auth.domain.AuthorizedUser;
import de.hbt.salat.common.scheduling.SchedulerRequestAttributes;

/**
 * Runs a replication started by hand the way the scheduler runs it: on a thread of its own, in job
 * mode (→ ADR-0006), and waits for it (#1282).
 *
 * <p><b>Why not on the request thread.</b> The request holds an EntityManager for its whole
 * duration (Open Session in View), and {@code JiraReplicationConfigService.runNow} suspends the
 * transaction with {@code NOT_SUPPORTED}, which leaves an empty transaction scope with active
 * synchronisation behind. The first repository read without a transaction of its own — the derived
 * finders of {@code JiraTicketRepository} are such reads — registers the request's EntityManager
 * with that scope. From then on every repository write ({@code save}, {@code saveAll}) suspends it
 * and opens an EntityManager of its own, while every read keeps coming from the request's one and
 * its first-level cache. A ticket written by {@code upsertIfChanged} therefore stays there with its
 * old version, {@code findByScopeSign} hands exactly that instance back, and merging it in
 * {@code resolveParentChains} fails the version check. The scheduled run has neither the
 * request's EntityManager nor the empty scope: every read gets a fresh EntityManager and sees the
 * current version. A thread of its own gives the manual run the same footing.
 *
 * <p>Everything that concerns the person asking — the manager check, which config — is decided on
 * the request thread before the hand-over, as in {@code ETLRunLauncher}. In the background the run
 * is {@code SYSTEM}; a permission check there would always pass.
 *
 * <p>Unlike the ETL run it waits: a replication keeps no run history the page could point at, so
 * the outcome is what the request has to report.
 */
@Service
public class JiraReplicationLauncher {

  private final JiraReplicationService replicationService;
  private final ObjectProvider<AuthorizedUser> authorizedUserProvider;
  private final ThreadPoolTaskExecutor executor;

  /** By hand rather than {@code @RequiredArgsConstructor}, for the {@link Qualifier} on the executor. */
  public JiraReplicationLauncher(JiraReplicationService replicationService,
                                 ObjectProvider<AuthorizedUser> authorizedUserProvider,
                                 @Qualifier(JIRA_REPLICATION_TASK_EXECUTOR) ThreadPoolTaskExecutor executor) {
    this.replicationService = replicationService;
    this.authorizedUserProvider = authorizedUserProvider;
    this.executor = executor;
  }

  /** Runs the replication and returns once it is done; its failure surfaces here unchanged. */
  public void runAndWait(long replicationId) throws InterruptedException {
    try {
      executor.submit(() -> runAsJob(replicationId)).get();
    } catch (ExecutionException ex) {
      if (ex.getCause() instanceof RuntimeException runtimeException) throw runtimeException;
      throw new IllegalStateException(ex.getCause());
    }
  }

  private void runAsJob(long replicationId) {
    setRequestAttributes(new SchedulerRequestAttributes(), true);
    try {
      authorizedUserProvider.getObject().initForJob();
      replicationService.runReplication(replicationId);
    } finally {
      // Nothing is destroyed by hand - see SchedulerRequestAttributes (#1084).
      resetRequestAttributes();
    }
  }

}
