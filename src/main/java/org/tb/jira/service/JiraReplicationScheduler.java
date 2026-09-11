package org.tb.jira.service;

import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.tb.jira.domain.JiraReplicationConfig;

@Slf4j
@Service
@RequiredArgsConstructor
public class JiraReplicationScheduler {

  private final JiraReplicationService replicationService;

  // Default: hourly at :15 local time; can be overridden by property 'salat.jira.replication.cron'.
  // Since #982 the replicated tickets are offered as suggestions while booking, so a ticket created
  // in the morning has to be bookable the same day — once a night was the wrong granularity for
  // that. Not on the hour: the ETL runs at 02:00 and there is no reason to meet it there.
  @Scheduled(cron = "${salat.jira.replication.cron:0 15 * * * *}")
  public void runAll() {
    log.info("Scheduled JIRA replication start");
    List<JiraReplicationConfig> enabled = replicationService.getEnabledReplications();
    if (enabled.isEmpty()) {
      log.info("No enabled JIRA replications configured.");
    }
    enabled.stream()
        .map(cfg -> cfg.getId())
        .forEach(replicationService::runReplication);
    log.info("Scheduled JIRA replication finished");
  }

}
