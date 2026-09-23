package org.tb.etl.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.tb.common.SalatProperties;
import org.tb.common.util.ClockProvider;
import org.tb.etl.persistence.ETLExecutionHistoryRepository;
import org.tb.etl.persistence.ETLRunHistoryRepository;

/**
 * Löscht abgelaufene Einträge aus {@code etl_execution_history} und {@code etl_run_history}.
 *
 * <p>Die Tabelle wuchs unbegrenzt: jeder nächtliche ETL-Lauf schreibt eine Zeile pro Definition
 * und Referenzperiode, jede mit dem vollständigen SQL-Text im {@code message}-Feld (~6 KB). Die
 * Laufhistorie (#573) kommt mit derselben Frist mit — eine Zeile pro Lauf, die ohne Aufräumen
 * genauso stehenbliebe.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ETLExecutionHistoryCleanupService {

  private final ETLExecutionHistoryRepository repository;
  private final ETLRunHistoryRepository runHistoryRepository;
  private final SalatProperties salatProperties;

  // läuft nach dem nächtlichen ETL (0 0 2) und dem Notification-Cleanup (0 30 2)
  @Scheduled(cron = "0 45 2 * * *")
  @Transactional
  public void deleteExpiredExecutionHistory() {
    int retentionDays = salatProperties.getEtl().getHistory().getRetentionDays();
    var cutoff = ClockProvider.now().minusDays(retentionDays);
    int deleted = repository.deleteExecutedBefore(cutoff);
    int deletedRuns = runHistoryRepository.deleteStartedBefore(cutoff);
    log.info("Deleted {} ETL execution history entries and {} ETL run history entries older than {} days (before {})",
        deleted, deletedRuns, retentionDays, cutoff);
  }

}
