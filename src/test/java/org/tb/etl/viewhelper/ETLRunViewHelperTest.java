package org.tb.etl.viewhelper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.tb.etl.domain.ETLRunHistory.Status.RUNNING;
import static org.tb.etl.domain.ETLRunHistory.Status.SUCCEEDED;

import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.tb.etl.domain.ETLRunHistory;
import org.tb.etl.domain.ETLRunHistory.Status;
import org.tb.etl.domain.ETLRunHistory.Trigger;

/**
 * Die Dauer eines ETL-Laufs in der Liste (#573).
 */
@DisplayNameGeneration(ReplaceUnderscores.class)
class ETLRunViewHelperTest {

  private static final LocalDateTime START = LocalDateTime.of(2026, 9, 20, 2, 0);

  @Test
  void a_short_run_is_counted_in_seconds() {
    // DurationUtils rechnet in H:mm und machte daraus "0:00".
    assertThat(durationOf(START.plusSeconds(12))).isEqualTo("12 s");
  }

  @Test
  void a_run_of_minutes_keeps_its_seconds() {
    assertThat(durationOf(START.plusMinutes(3).plusSeconds(7))).isEqualTo("3 min 07 s");
  }

  @Test
  void a_long_run_switches_to_hours() {
    assertThat(durationOf(START.plusHours(1).plusMinutes(4).plusSeconds(30))).isEqualTo("1 h 04 min");
  }

  @Test
  void a_run_that_never_finished_has_no_duration() {
    // Die Zeit seit dem Start liefe sonst ewig weiter und saehe wie eine Dauer aus.
    var run = run(null, RUNNING);

    assertThat(ETLRunViewHelper.from(run).duration()).isNull();
    assertThat(ETLRunViewHelper.from(run).status()).isEqualTo(RUNNING);
  }

  private String durationOf(LocalDateTime finishedAt) {
    return ETLRunViewHelper.from(run(finishedAt, SUCCEEDED)).duration();
  }

  private ETLRunHistory run(LocalDateTime finishedAt, Status status) {
    return ETLRunHistory.builder()
        .startedAt(START)
        .finishedAt(finishedAt)
        .status(status)
        .triggeredBy(Trigger.SCHEDULED)
        .build();
  }

}
