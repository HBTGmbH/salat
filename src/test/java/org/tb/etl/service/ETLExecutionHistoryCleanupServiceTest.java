package org.tb.etl.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.tb.common.SalatProperties;
import org.tb.common.util.ClockProvider;
import org.tb.etl.domain.ETLExecutionHistory;
import org.tb.etl.persistence.ETLExecutionHistoryRepository;

@DataJpaTest
@Import({ ETLExecutionHistoryCleanupService.class, SalatProperties.class })
@DisplayNameGeneration(ReplaceUnderscores.class)
class ETLExecutionHistoryCleanupServiceTest {

  private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 22, 3, 0);

  @Autowired
  private ETLExecutionHistoryCleanupService cleanupService;

  @Autowired
  private ETLExecutionHistoryRepository repository;

  @Autowired
  private SalatProperties salatProperties;

  @BeforeEach
  void setUp() {
    ClockProvider.useFixedClock(NOW);
    repository.deleteAll();
    salatProperties.getEtl().getHistory().setRetentionDays(14);
  }

  @AfterEach
  void tearDown() {
    ClockProvider.reset();
  }

  @Test
  void deletes_only_entries_older_than_the_retention_period() {
    save(NOW.minusDays(15));
    save(NOW.minusDays(200));
    var withinRetention = save(NOW.minusDays(13));
    var today = save(NOW);

    cleanupService.deleteExpiredExecutionHistory();

    assertThat(repository.findAll())
        .extracting(ETLExecutionHistory::getId)
        .containsExactlyInAnyOrder(withinRetention.getId(), today.getId());
  }

  @Test
  void respects_a_changed_retention_period() {
    salatProperties.getEtl().getHistory().setRetentionDays(90);
    save(NOW.minusDays(91));
    var kept = save(NOW.minusDays(89));

    cleanupService.deleteExpiredExecutionHistory();

    assertThat(repository.findAll())
        .extracting(ETLExecutionHistory::getId)
        .containsExactly(kept.getId());
  }

  @Test
  void removes_a_large_backlog_in_one_statement() {
    for (int i = 0; i < 1200; i++) {
      save(NOW.minusDays(100));
    }
    save(NOW);

    cleanupService.deleteExpiredExecutionHistory();

    assertThat(repository.count()).isEqualTo(1);
  }

  @Test
  void does_nothing_when_no_entry_is_expired() {
    save(NOW.minusDays(1));

    cleanupService.deleteExpiredExecutionHistory();

    assertThat(repository.count()).isEqualTo(1);
  }

  private ETLExecutionHistory save(LocalDateTime executedAt) {
    return repository.save(ETLExecutionHistory.builder()
        .etlId(1L)
        .etlName("test-etl")
        .executedAt(executedAt)
        .success(true)
        .message("Execute SQL: select 1")
        .build());
  }

}
