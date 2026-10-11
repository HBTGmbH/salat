package de.hbt.salat.etl.viewhelper;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import de.hbt.salat.common.LocalDateRange;
import de.hbt.salat.etl.domain.ETLExecutionHistory;

/**
 * Eine Ausführung auf der Seite ihres Laufs (#1324): die Referenzperiode steht nur in der ersten
 * Zeile der Meldung und wird von dort gelesen.
 */
@DisplayNameGeneration(ReplaceUnderscores.class)
class ETLExecutionViewHelperTest {

  @Test
  void the_reference_period_is_read_from_the_first_line_as_the_service_writes_it() {
    var period = new LocalDateRange(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30));
    var message = ETLExecutionHistory.DATE_RANGE_PREFIX + period + "\nExecute took 1.2 s (12 rows affected)\n";

    var execution = ETLExecutionViewHelper.from(3, entry(message));

    assertThat(execution.position()).isEqualTo(3);
    assertThat(execution.hasPeriod()).isTrue();
    assertThat(execution.periodFrom()).isEqualTo(LocalDate.of(2026, 9, 1));
    assertThat(execution.periodUntil()).isEqualTo(LocalDate.of(2026, 9, 30));
    assertThat(execution.message()).isEqualTo(message);
  }

  @Test
  void a_message_without_a_period_leaves_it_empty() {
    assertThat(ETLExecutionViewHelper.from(1, entry("ETL execution failed: boom")).hasPeriod()).isFalse();
    assertThat(ETLExecutionViewHelper.from(1, entry(null)).hasPeriod()).isFalse();
  }

  private static ETLExecutionHistory entry(String message) {
    return ETLExecutionHistory.builder()
        .etlId(1L)
        .etlName("worked-hours")
        .executedAt(LocalDateTime.of(2026, 10, 1, 2, 1))
        .success(true)
        .message(message)
        .build();
  }

}
