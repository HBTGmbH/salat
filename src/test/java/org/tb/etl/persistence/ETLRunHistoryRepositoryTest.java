package org.tb.etl.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.tb.etl.domain.ETLRunHistory.Status.FAILED;
import static org.tb.etl.domain.ETLRunHistory.Status.RUNNING;
import static org.tb.etl.domain.ETLRunHistory.Status.SUCCEEDED;

import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.data.domain.PageRequest;
import org.tb.etl.domain.ETLRunHistory;
import org.tb.etl.domain.ETLRunHistory.Status;
import org.tb.etl.domain.ETLRunHistory.Trigger;

/**
 * Die Liste der ETL-Läufe (#573): die jüngsten zuerst, höchstens so viele wie angefragt, und auf
 * Wunsch nur die, die nicht sauber zu Ende kamen.
 *
 * <p>Dazu die eine Abfrage, auf der die Sperre aus #1071 steht: solange sie einen Lauf findet, darf
 * kein zweiter starten.
 */
@DataJpaTest
@DisplayNameGeneration(ReplaceUnderscores.class)
class ETLRunHistoryRepositoryTest {

  private static final LocalDateTime NIGHT = LocalDateTime.of(2026, 9, 20, 2, 0);

  @Autowired
  private ETLRunHistoryRepository repository;

  @Test
  void answers_with_the_most_recent_runs_first() {
    save(NIGHT, SUCCEEDED);
    var yesterday = save(NIGHT.plusDays(1), SUCCEEDED);
    var today = save(NIGHT.plusDays(2), FAILED);

    var runs = repository.findByOrderByStartedAtDesc(PageRequest.of(0, 2));

    assertThat(runs).extracting(ETLRunHistory::getId)
        .containsExactly(today.getId(), yesterday.getId());
  }

  @Test
  void keeps_a_run_that_never_finished_among_the_conspicuous_ones() {
    // Genau dieser Fall ist der Grund für die Tabelle: abgestürzt, deshalb für immer RUNNING.
    var crashed = save(NIGHT, RUNNING);
    var failed = save(NIGHT.plusDays(1), FAILED);
    save(NIGHT.plusDays(2), SUCCEEDED);

    var runs = repository.findByStatusNotOrderByStartedAtDesc(SUCCEEDED, PageRequest.of(0, 100));

    assertThat(runs).extracting(ETLRunHistory::getId)
        .containsExactly(failed.getId(), crashed.getId());
  }

  @Test
  void finds_the_run_that_is_still_going() {
    // Die RUNNING-Zeile ist die Sperre (#1071): sie muss auch dann gefunden werden, wenn juengere
    // Laeufe daneben stehen — denn nur solange sie steht, darf kein zweiter Lauf starten.
    var running = save(NIGHT, RUNNING);
    save(NIGHT.minusDays(1), SUCCEEDED);
    save(NIGHT.plusDays(1), FAILED);

    assertThat(repository.findFirstByStatusOrderByStartedAtDesc(RUNNING))
        .map(ETLRunHistory::getId)
        .contains(running.getId());
  }

  @Test
  void finds_nothing_while_no_run_is_going() {
    save(NIGHT, SUCCEEDED);
    save(NIGHT.plusDays(1), FAILED);

    assertThat(repository.findFirstByStatusOrderByStartedAtDesc(RUNNING)).isEmpty();
  }

  private ETLRunHistory save(LocalDateTime startedAt, Status status) {
    return repository.save(ETLRunHistory.builder()
        .startedAt(startedAt)
        .finishedAt(status == RUNNING ? null : startedAt.plusMinutes(4))
        .status(status)
        .triggeredBy(Trigger.SCHEDULED)
        .message("2 Definition(en) ausgeführt")
        .build());
  }

}
