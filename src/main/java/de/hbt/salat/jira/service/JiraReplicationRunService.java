package de.hbt.salat.jira.service;

import static java.util.stream.Collectors.toSet;
import static de.hbt.salat.common.exception.ErrorCode.JI_REPLICATION_RUN_ALREADY_RUNNING;
import static de.hbt.salat.common.exception.ErrorCode.JI_REPLICATION_NOT_FOUND;
import static de.hbt.salat.common.exception.ErrorCode.JI_REPLICATION_RUN_SCOPE_BUSY;
import static de.hbt.salat.common.exception.ErrorCode.JI_REPLICATION_RUN_NOT_FOUND;
import static de.hbt.salat.common.exception.ErrorCode.JI_REPLICATION_RUN_NOT_RUNNING;
import static de.hbt.salat.jira.domain.JiraReplicationRun.MESSAGE_MAX_LENGTH;
import static de.hbt.salat.jira.domain.JiraReplicationRun.Status.FAILED;
import static de.hbt.salat.jira.domain.JiraReplicationRun.Status.RUNNING;
import static de.hbt.salat.jira.domain.JiraReplicationRun.Status.SKIPPED;
import static de.hbt.salat.jira.domain.JiraReplicationRun.Status.SUCCEEDED;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import de.hbt.salat.auth.domain.Authorized;
import de.hbt.salat.common.exception.BusinessRuleException;
import de.hbt.salat.common.exception.InvalidDataException;
import de.hbt.salat.common.scheduling.RunFinisher;
import de.hbt.salat.common.util.DateTimeUtils;
import de.hbt.salat.jira.domain.JiraReplicationRun;
import de.hbt.salat.jira.domain.JiraReplicationRun.Status;
import de.hbt.salat.jira.domain.JiraReplicationRun.Trigger;
import de.hbt.salat.jira.persistence.JiraReplicationConfigRepository;
import de.hbt.salat.jira.persistence.JiraReplicationRunRepository;

/**
 * The run history of the replications (#1282), modelled on the ETL's (→ ADR-0028).
 *
 * <p><b>The {@code RUNNING} row is the lock</b>, as with the ETL — but per scope, not for all
 * replications at once. Two replications of different scopes get along; the same one twice would
 * write the same tickets from two threads, and so would two of the same scope (#1386): each derives
 * top-level key and inherited fields of every ticket of the scope, whoever maintains it.
 *
 * <p>Management only, the scheduled run included: the job user is a manager
 * ({@code AuthorizedUser#initForJob}, → ADR-0006).
 *
 * <p><b>Deliberately without a class-level {@code @Transactional}</b>, unlike the pattern in
 * AGENTS.md: {@link #startRun} checks and writes inside a {@code synchronized} section, and a
 * surrounding transaction would commit the new row only after the section was left — a second
 * request could slip through in between and not see it. Without one, {@code save} commits on the
 * spot. The methods that read or change several rows carry their own annotation.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Authorized(requiresManager = true)
public class JiraReplicationRunService {

  private static final DateTimeFormatter STARTED_AT_FORMAT =
      DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss");

  private final JiraReplicationRunRepository runRepository;
  private final JiraReplicationConfigRepository configRepository;
  private final RunFinisher runFinisher;

  /**
   * Opens a run and is the lock at the same time: checking and writing form one section, two
   * simultaneous requests would otherwise both get past the check.
   *
   * <p><b>Assumes a single instance of the application</b>, as the ETL lock and every
   * {@code @Scheduled} job of the application do already.
   *
   * @throws BusinessRuleException when this replication is already running, or another one of the
   *     same scope
   */
  public synchronized JiraReplicationRun startRun(long replicationId, Trigger trigger) {
    runRepository.findFirstByReplicationIdAndStatusOrderByStartedAtDesc(replicationId, RUNNING)
        .ifPresent(running -> {
          throw new BusinessRuleException(JI_REPLICATION_RUN_ALREADY_RUNNING,
              running.getStartedAt().format(STARTED_AT_FORMAT));
        });
    // Loaded, not a reference: without a surrounding transaction a proxy could not tell its scope.
    var replication = configRepository.findById(replicationId)
        .orElseThrow(() -> new InvalidDataException(JI_REPLICATION_NOT_FOUND));
    runRepository.findInScopeByStatus(replication.getCustomerorderId(), replication.getSuborderId(), RUNNING)
        .stream().findFirst()
        .ifPresent(running -> {
          throw new BusinessRuleException(JI_REPLICATION_RUN_SCOPE_BUSY, running.getReplication().getName(),
              running.getStartedAt().format(STARTED_AT_FORMAT));
        });
    return runRepository.save(JiraReplicationRun.builder()
        .replication(replication)
        .startedAt(DateTimeUtils.now())
        .status(RUNNING)
        .triggeredBy(trigger)
        .build());
  }

  /** Records that a run did not even begin, because the same replication was still running. */
  public void recordSkippedRun(long replicationId, Trigger trigger, String message) {
    var now = DateTimeUtils.now();
    runRepository.save(JiraReplicationRun.builder()
        .replication(configRepository.getReferenceById(replicationId))
        .startedAt(now)
        .finishedAt(now)
        .status(SKIPPED)
        .triggeredBy(trigger)
        .message(shortened(message))
        .build());
  }

  /**
   * Writes the outcome into the run's row — without overwriting what was changed by hand in the
   * meantime. Between start and end lies the whole run, and {@link #markFinished} may have declared
   * it crashed while it was in fact still going. The row is read afresh for that reason; if it no
   * longer stands on {@code RUNNING}, that decision stays and the actual outcome is appended.
   *
   * <p>A row that is gone altogether belongs to a replication deleted during its run — there is
   * nothing left to report to.
   *
   * <p>Written through {@link RunFinisher} (#1300), which also brings its own transaction: if the
   * database is unreachable right now, the end is written later instead of leaving the row as a lock.
   * That is why this method carries no {@code @Transactional} — a transaction opened around it would
   * fail on the same broken connection before the write was even reached. The retry runs on the
   * scheduler's thread, so the write goes straight to the repository, never through a guarded method.
   */
  public void finishRun(long runId, Status status, String message) {
    var finishedAt = DateTimeUtils.now();
    runFinisher.finish("JIRA replication run " + runId, () -> {
      var run = runRepository.findById(runId).orElse(null);
      if (run == null) {
        log.info("JIRA replication run {} no longer exists - its replication was deleted meanwhile", runId);
        return;
      }
      if (run.getStatus() != RUNNING) {
        log.warn("JIRA replication run {} was marked finished by hand while it was still running", runId);
        run.setMessage(shortened("%s\nDer Lauf kam danach noch zu Ende: %s".formatted(run.getMessage(), message)));
        return;
      }
      run.setFinishedAt(finishedAt);
      run.setStatus(status);
      run.setMessage(shortened(message));
    });
  }

  /**
   * Sets a run that only stands on {@code RUNNING} because of a crash to finished. Such a row would
   * block every further start of its replication for good. As with the ETL, there is no reset at
   * start-up and no age threshold — see {@code ETLRunHistoryService#markFinished}. The run itself is
   * not stopped by this; the confirmation says so.
   */
  @Transactional
  public void markFinished(long runId) {
    var run = runRepository.findById(runId)
        .orElseThrow(() -> new InvalidDataException(JI_REPLICATION_RUN_NOT_FOUND));
    if (run.getStatus() != RUNNING) {
      // catches the double click as well as two simultaneous ones: the second one sees FAILED
      throw new BusinessRuleException(JI_REPLICATION_RUN_NOT_RUNNING);
    }
    run.setStatus(FAILED);
    run.setFinishedAt(DateTimeUtils.now());
    run.setMessage(shortened(withNote(run.getMessage(), "Von Hand als beendet markiert.")));
  }

  /**
   * The latest runs, newest first.
   *
   * @param failedOnly only the runs that did not end cleanly. {@code RUNNING} is part of it: a
   *     crashed run stays there forever, and hiding it would hide exactly the case the table is for.
   */
  @Transactional(readOnly = true)
  public List<JiraReplicationRun> getLatestRuns(int limit, boolean failedOnly) {
    var page = PageRequest.of(0, limit);
    return failedOnly
        ? runRepository.findByStatusNotOrderByStartedAtDesc(SUCCEEDED, page)
        : runRepository.findByOrderByStartedAtDesc(page);
  }

  /**
   * The replications running right now — for the list, which offers no start button for them. Not
   * the lock: between this answer and the click the state can change, {@link #startRun} decides.
   */
  @Transactional(readOnly = true)
  public Set<Long> getRunningReplicationIds() {
    return runRepository.findByStatus(RUNNING).stream()
        .map(run -> run.getReplication().getId())
        .collect(toSet());
  }

  /** The runs of a replication that is being deleted — they have nothing left to refer to. */
  @Transactional
  public void deleteRunsOf(long replicationId) {
    runRepository.deleteByReplicationId(replicationId);
  }

  private static String withNote(String message, String note) {
    return message == null || message.isBlank() ? note : message + "\n" + note;
  }

  static String shortened(String message) {
    if (message == null) return null;
    return message.length() > MESSAGE_MAX_LENGTH ? message.substring(0, MESSAGE_MAX_LENGTH) : message;
  }

}
