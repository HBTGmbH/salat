package de.hbt.salat.jira.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import de.hbt.salat.common.SalatProperties;
import de.hbt.salat.common.util.ClockProvider;
import de.hbt.salat.jira.persistence.JiraReplicationRunRepository;

/**
 * Deletes expired rows of the run history (#1282). Every enabled replication writes one row an
 * hour, and a run history is for looking back days, not months.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class JiraReplicationRunCleanupService {

  private final JiraReplicationRunRepository runRepository;
  private final SalatProperties salatProperties;

  // between two hourly runs (:15) and after the ETL cleanup (0 45 2)
  @Scheduled(cron = "0 50 2 * * *")
  @Transactional
  public void deleteExpiredRuns() {
    int retentionDays = salatProperties.getJira().getHistory().getRetentionDays();
    var cutoff = ClockProvider.now().minusDays(retentionDays);
    int deleted = runRepository.deleteStartedBefore(cutoff);
    log.info("Deleted {} JIRA replication runs older than {} days (before {})", deleted, retentionDays, cutoff);
  }

}
