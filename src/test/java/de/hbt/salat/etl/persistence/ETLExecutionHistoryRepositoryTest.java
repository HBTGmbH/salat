package de.hbt.salat.etl.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import de.hbt.salat.etl.domain.ETLExecutionHistory;
import de.hbt.salat.etl.domain.ETLRunHistory;
import de.hbt.salat.etl.domain.ETLRunHistory.Status;
import de.hbt.salat.etl.domain.ETLRunHistory.Trigger;

/**
 * Die Ausführungen eines ETL-Laufs (#1324): nur die eigenen, in der Reihenfolge, in der sie liefen.
 */
@DataJpaTest
@DisplayNameGeneration(ReplaceUnderscores.class)
class ETLExecutionHistoryRepositoryTest {

  private static final LocalDateTime NIGHT = LocalDateTime.of(2026, 10, 1, 2, 0);

  @Autowired
  private ETLExecutionHistoryRepository repository;

  @Autowired
  private ETLRunHistoryRepository runHistoryRepository;

  @Test
  void the_executions_of_a_run_are_read_in_the_order_they_ran() {
    var run = saveRun(NIGHT);
    var other = saveRun(NIGHT.minusDays(1));
    var first = save(NIGHT.plusSeconds(10), run);
    save(NIGHT.minusDays(1).plusSeconds(10), other);
    // dieselbe Sekunde: die Reihenfolge des Schreibens entscheidet, nicht der Zeitpunkt
    var second = save(NIGHT.plusSeconds(20), run);
    var third = save(NIGHT.plusSeconds(20), run);
    save(NIGHT.plusSeconds(30), null);

    assertThat(repository.findByRunInOrderOfExecution(run.getId()))
        .extracting(ETLExecutionHistory::getId)
        .containsExactly(first.getId(), second.getId(), third.getId());
  }

  @Test
  void a_run_without_executions_has_none() {
    var run = saveRun(NIGHT);
    save(NIGHT.plusSeconds(10), null);

    assertThat(repository.findByRunInOrderOfExecution(run.getId())).isEmpty();
  }

  /**
   * So schreibt {@code ETLService}: ohne umspannende Transaktion, jede Zeile für sich. Der Lauf, den
   * die Ausführung nennt, ist dabei längst losgelöst — beim Weg über die Oberfläche kommt er sogar
   * aus einem anderen Thread. Der Verweis muss trotzdem als {@code run_id} ankommen.
   */
  @Test
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  void a_run_written_in_a_transaction_of_its_own_is_referenced_by_the_execution() {
    var run = saveRun(NIGHT);
    try {
      var execution = save(NIGHT.plusSeconds(10), run);

      assertThat(repository.findByRunInOrderOfExecution(run.getId()))
          .extracting(ETLExecutionHistory::getId)
          .containsExactly(execution.getId());
    } finally {
      // ohne Transaktion rollt nichts zurück; der Lauf nimmt seine Ausführung mit (ON DELETE CASCADE)
      runHistoryRepository.deleteById(run.getId());
    }
    assertThat(repository.findByRunInOrderOfExecution(run.getId())).isEmpty();
  }

  private ETLRunHistory saveRun(LocalDateTime startedAt) {
    return runHistoryRepository.save(ETLRunHistory.builder()
        .startedAt(startedAt)
        .finishedAt(startedAt.plusMinutes(3))
        .status(Status.SUCCEEDED)
        .triggeredBy(Trigger.SCHEDULED)
        .build());
  }

  private ETLExecutionHistory save(LocalDateTime executedAt, ETLRunHistory run) {
    return repository.save(ETLExecutionHistory.builder()
        .run(run)
        .etlId(1L)
        .etlName("test-etl")
        .executedAt(executedAt)
        .success(true)
        .message("Date Range: 2026-09-01 - 2026-09-30\n")
        .build());
  }

}
