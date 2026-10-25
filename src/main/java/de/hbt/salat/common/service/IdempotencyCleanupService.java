package de.hbt.salat.common.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import de.hbt.salat.common.persistence.IdempotencyEntryRepository;
import de.hbt.salat.common.util.ClockProvider;

/**
 * Forgets idempotency keys after a week (#1532, concept 6.5): a caller repeats a call within minutes, not days, and
 * the table holds transaction data nobody needs afterwards.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IdempotencyCleanupService {

  static final int RETENTION_DAYS = 7;

  private final IdempotencyEntryRepository repository;

  @Scheduled(cron = "0 40 2 * * *")
  @Transactional
  public void deleteExpiredKeys() {
    var cutoff = ClockProvider.now().minusDays(RETENTION_DAYS);
    int deleted = repository.deleteCreatedBefore(cutoff);
    log.info("Deleted {} idempotency keys older than {} days", deleted, RETENTION_DAYS);
  }
}
