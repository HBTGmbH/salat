package org.tb.jira.service;

import static org.springframework.web.context.request.RequestContextHolder.resetRequestAttributes;
import static org.springframework.web.context.request.RequestContextHolder.setRequestAttributes;

import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.tb.auth.domain.AuthorizedUser;
import org.tb.common.scheduling.SchedulerRequestAttributes;
import org.tb.jira.domain.JiraReplicationConfig;

@Slf4j
@Service
@RequiredArgsConstructor
public class JiraReplicationScheduler {

  private final JiraReplicationService replicationService;
  private final ObjectProvider<AuthorizedUser> authorizedUserProvider;

  // Default: hourly at :15 local time; can be overridden by property 'salat.jira.replication.cron'.
  // Since #982 the replicated tickets are offered as suggestions while booking, so a ticket created
  // in the morning has to be bookable the same day — once a night was the wrong granularity for
  // that. Not on the hour: the ETL runs at 02:00 and there is no reason to meet it there.
  @Scheduled(cron = "${salat.jira.replication.cron:0 15 * * * *}")
  public void runAll() {
    log.info("Scheduled JIRA replication start");
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
      enabled.stream()
          .map(JiraReplicationConfig::getId)
          .forEach(replicationService::runReplication);
    } catch (Exception e) {
      log.error("Scheduled JIRA replication failed", e);
    } finally {
      // No bean is destroyed by hand: resetRequestAttributes() drops the whole scope with the
      // bean inside it. Why destroyScopedBean("authorizedUser") must not come back here is
      // written down on SchedulerRequestAttributes (#1084).
      resetRequestAttributes();
    }
    log.info("Scheduled JIRA replication finished");
  }

}
