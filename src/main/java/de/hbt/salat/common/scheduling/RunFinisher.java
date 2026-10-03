package de.hbt.salat.common.scheduling;

import static org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW;

import java.time.Duration;
import java.time.Instant;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import de.hbt.salat.common.SalatProperties;

/**
 * Writes the outcome of a run into its {@code RUNNING} row — for the ETL run and the JIRA replication
 * alike (#1300, → ADR-0028).
 *
 * <p><b>That row is the lock.</b> A run that comes to its end but cannot write it leaves the row on
 * {@code RUNNING}, and every further start of the same run is refused until somebody marks it
 * finished by hand. That is right for a run whose process died — nobody knows its outcome. It is wrong
 * for a run that knows its outcome and only met a database that was briefly unreachable, as on a
 * restart of the database server in the middle of the nightly ETL run.
 *
 * <p>Two things therefore happen here:
 *
 * <ul>
 *   <li><b>Every attempt writes in a new transaction</b> of its own, never in one that might still
 *       hold a connection of the failed run.
 *   <li><b>A connection error is retried</b>, with a growing delay and for at most
 *       {@code salat.runs.finish-retry-max}. This is not the age threshold rejected in ADR-0028: the
 *       process <em>knows</em> the run is over and does not guess. The threshold only limits how long
 *       it keeps trying to say so. Past it, the run's id goes to the log at error level, and the row
 *       stays for "mark as finished", just as after a crash.
 * </ul>
 *
 * <p><b>A retry never waits on a thread.</b> The nightly ETL run runs on the scheduler's thread, and
 * that pool has a single thread shared by every {@code @Scheduled} job. Waiting there for up to an
 * hour would hold up the cleanup and the hourly JIRA replication as well. Each retry is therefore a
 * delayed task of its own on the {@link TaskScheduler}; between two attempts no thread is taken. An
 * attempt itself can block for as long as the pool waits for a connection — bounded per attempt, not
 * by the length of the retry.
 *
 * <p>The write passed in must not depend on the caller's thread: a retry runs on the scheduler's
 * thread, without a request scope and without {@code AuthorizedUser}. Repository calls are fine, a
 * call through a service guarded by {@code @Authorized} is not. The write must also read the row
 * afresh on every attempt — it may have been marked finished by hand in the meantime.
 */
@Slf4j
@Component
public class RunFinisher {

  /** Delay before the first retry; it doubles from attempt to attempt. */
  static final Duration FIRST_RETRY_DELAY = Duration.ofSeconds(5);

  /** Upper bound of the delay between two attempts. */
  static final Duration MAX_RETRY_DELAY = Duration.ofMinutes(5);

  private final TaskScheduler taskScheduler;
  private final TransactionTemplate newTransaction;
  private final SalatProperties salatProperties;

  public RunFinisher(TaskScheduler taskScheduler, PlatformTransactionManager transactionManager,
                     SalatProperties salatProperties) {
    this.taskScheduler = taskScheduler;
    this.newTransaction = new TransactionTemplate(transactionManager);
    this.newTransaction.setPropagationBehavior(PROPAGATION_REQUIRES_NEW);
    this.salatProperties = salatProperties;
  }

  /**
   * Writes the outcome now, or — if the database is unreachable — later.
   *
   * <p>Returns as soon as the first attempt has succeeded or the next one is planned. Any error other
   * than a connection error is passed on to the caller as before.
   *
   * @param run names the run in the log, e.g. {@code "ETL run 7"}
   * @param write writes the outcome; runs inside a new transaction
   */
  public void finish(String run, Runnable write) {
    var maxDuration = salatProperties.getRuns().getFinishRetryMax();
    var deadline = taskScheduler.getClock().instant().plus(maxDuration);
    attempt(run, write, deadline, FIRST_RETRY_DELAY);
  }

  private void attempt(String run, Runnable write, Instant deadline, Duration delay) {
    try {
      newTransaction.executeWithoutResult(status -> write.run());
    } catch (TransientDataAccessException | DataAccessResourceFailureException
             | CannotCreateTransactionException e) {
      planRetry(run, write, deadline, delay, e);
    }
  }

  private void planRetry(String run, Runnable write, Instant deadline, Duration delay, Exception cause) {
    var now = taskScheduler.getClock().instant();
    if (!now.isBefore(deadline)) {
      log.error("{}: outcome could not be written until {}, its row stays RUNNING and has to be marked "
          + "finished by hand", run, deadline, cause);
      return;
    }
    var next = now.plus(delay);
    if (next.isAfter(deadline)) {
      next = deadline;
    }
    log.warn("{}: outcome could not be written, next attempt at {}", run, next, cause);
    var doubled = delay.multipliedBy(2);
    var nextDelay = doubled.compareTo(MAX_RETRY_DELAY) < 0 ? doubled : MAX_RETRY_DELAY;
    taskScheduler.schedule(() -> retry(run, write, deadline, nextDelay), next);
  }

  private void retry(String run, Runnable write, Instant deadline, Duration delay) {
    try {
      attempt(run, write, deadline, delay);
    } catch (RuntimeException e) {
      // There is no caller left to pass it to; on the scheduler's thread it would end without a word.
      log.error("{}: outcome could not be written, its row stays RUNNING and has to be marked "
          + "finished by hand", run, e);
    }
  }

}
