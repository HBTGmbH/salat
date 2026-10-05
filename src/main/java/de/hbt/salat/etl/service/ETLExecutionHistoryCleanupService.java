package de.hbt.salat.etl.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import de.hbt.salat.common.SalatProperties;
import de.hbt.salat.common.util.ClockProvider;
import de.hbt.salat.etl.persistence.ETLExecutionHistoryRepository;
import de.hbt.salat.etl.persistence.ETLRunHistoryRepository;

/**
 * Löscht abgelaufene Einträge aus {@code etl_execution_history} und {@code etl_run_history}.
 *
 * <p>Die Tabelle wuchs unbegrenzt: jeder nächtliche ETL-Lauf schreibt eine Zeile pro Definition
 * und Referenzperiode. Seit #1357 trägt die Zeile im {@code message}-Feld nur noch Zeitraum, Dauer
 * und Zeilenzahl je Teil (rund 250 Zeichen), im Fehlerfall dazu die gescheiterte Anweisung; vorher
 * stand dort der SQL-Text jeder Anweisung (1,5 bis 20 KB). Die Laufhistorie (#573) kommt mit
 * derselben Frist mit — eine Zeile pro Lauf, die ohne Aufräumen genauso stehenbliebe.
 *
 * <p>Das Löschen findet die abgelaufenen Zeilen über den Index auf {@code executed_at}; ohne ihn
 * ging es die ganze Tabelle durch.
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
