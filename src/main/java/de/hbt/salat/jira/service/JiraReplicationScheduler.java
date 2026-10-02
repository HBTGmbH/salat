package de.hbt.salat.jira.service;

import static org.springframework.web.context.request.RequestContextHolder.resetRequestAttributes;
import static org.springframework.web.context.request.RequestContextHolder.setRequestAttributes;
import static de.hbt.salat.common.exception.ErrorCode.JI_REPLICATION_RUN_ALREADY_RUNNING;
import static de.hbt.salat.jira.domain.JiraReplicationRun.Trigger.SCHEDULED;

import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import de.hbt.salat.auth.domain.AuthorizedUser;
import de.hbt.salat.common.exception.BusinessRuleException;
import de.hbt.salat.common.scheduling.SchedulerRequestAttributes;
import de.hbt.salat.jira.domain.JiraReplicationConfig;
import de.hbt.salat.jira.domain.JiraReplicationRun.Trigger;

@Slf4j
@Service
@RequiredArgsConstructor
public class JiraReplicationScheduler {

  private final JiraReplicationService replicationService;
  private final JiraReplicationRunService runService;
  private final ObjectProvider<AuthorizedUser> authorizedUserProvider;

  // Default: hourly at :15 local time; can be overridden by property 'salat.jira.replication.cron'.
  // Since #982 the replicated tickets are offered as suggestions while booking, so a ticket created
  // in the morning has to be bookable the same day — once a night was the wrong granularity for
  // that. Not on the hour: the ETL runs at 02:00 and there is no reason to meet it there.
  @Scheduled(cron = "${salat.jira.replication.cron:0 15 * * * *}")
  public void runScheduled() {
    runAll(SCHEDULED);
  }

  /**
   * Runs every enabled replication, one after another, each with its row in the run history
   * (#1282). One that fails is in its row and in the log and does not hold up the ones behind it —
   * before the history the first failure ended the whole round.
   */
  public void runAll(Trigger trigger) {
    log.info("JIRA replication of all enabled configs start, trigger={}", trigger);
    // A scheduled run has no HTTP request and therefore no SecurityContext, so the request-scoped
    // AuthorizedUser has to be put into job mode (→ ADR-0006). Needed since #1007: the worklog sync
    // resolves the scope through CustomerorderService and SuborderService, both @Authorized.
    setRequestAttributes(new SchedulerRequestAttributes(), true);
    try {
      AuthorizedUser systemUser = authorizedUserProvider.getObject();
      systemUser.initForJob();
      List<JiraReplicationConfig> enabled = replicationService.getEnabledReplications();
      if (enabled.isEmpty()) {
        log.info("No enabled JIRA replications configured.");
      }
      enabled.forEach(cfg -> runOne(cfg, trigger));
    } catch (Exception e) {
      log.error("JIRA replication of all enabled configs failed", e);
    } finally {
      // No bean is destroyed by hand: resetRequestAttributes() drops the whole scope with the
      // bean inside it. Why destroyScopedBean("authorizedUser") must not come back here is
      // written down on SchedulerRequestAttributes (#1084).
      resetRequestAttributes();
    }
    log.info("JIRA replication of all enabled configs finished");
  }

  private void runOne(JiraReplicationConfig cfg, Trigger trigger) {
    try {
      replicationService.runRecorded(cfg.getId(), trigger);
    } catch (BusinessRuleException ex) {
      boolean stillRunning = ex.getMessages().stream()
          .anyMatch(m -> m.getErrorCode() == JI_REPLICATION_RUN_ALREADY_RUNNING);
      if (!stillRunning) {
        log.error("JIRA replication {} failed", cfg.getName(), ex);
        return;
      }
      // A run started by hand is still going. The same replication twice would write the same
      // tickets from two threads; the gap belongs where it is looked for, in the list.
      log.warn("JIRA replication {} skipped, it is still running", cfg.getName());
      runService.recordSkippedRun(cfg.getId(), trigger,
          "Übersprungen: zum Startzeitpunkt lief diese Replikation noch.");
    } catch (Exception ex) {
      // already written into the run's row by JiraReplicationService.continueRun
      log.error("JIRA replication {} failed", cfg.getName(), ex);
    }
  }

}
