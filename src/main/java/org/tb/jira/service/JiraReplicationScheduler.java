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

  // Default: run daily at 02:00 local time; can be overridden by property 'salat.jira.replication.cron'
  @Scheduled(cron = "${salat.jira.replication.cron:0 0 2 * * *}")
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
