package org.tb.etl.viewhelper;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.tb.etl.domain.ETLRunHistory;
import org.tb.etl.domain.ETLRunHistory.Status;
import org.tb.etl.domain.ETLRunHistory.Trigger;

/**
 * Ein ETL-Lauf, wie ihn die Liste zeigt (#573).
 *
 * @param duration die Dauer des Laufs, fertig formatiert — {@code null}, solange kein Endzeitpunkt
 *     dasteht. Für einen abgestürzten Lauf bliebe die Zeit seit dem Start ewig weiterlaufen und
 *     sähe nach einer Dauer aus; dass er nicht zu Ende kam, sagt der Status.
 */
public record ETLRunViewHelper(Long id, LocalDateTime startedAt, LocalDateTime finishedAt,
                               Status status, Trigger triggeredBy, LocalDate dateFrom,
                               LocalDate dateUntil, String message, String duration) {

  public static ETLRunViewHelper from(ETLRunHistory run) {
    return new ETLRunViewHelper(run.getId(), run.getStartedAt(), run.getFinishedAt(),
        run.getStatus(), run.getTriggeredBy(), run.getDateFrom(), run.getDateUntil(),
        run.getMessage(), durationOf(run));
  }

  private static String durationOf(ETLRunHistory run) {
    if (run.getStartedAt() == null || run.getFinishedAt() == null) {
      return null;
    }
    return format(Duration.between(run.getStartedAt(), run.getFinishedAt()));
  }

  /**
   * Ein ETL-Lauf dauert Sekunden bis Minuten. {@code DurationUtils.format} rechnet in {@code H:mm}
   * und machte daraus „0:00"; die Einheiten hier sind SI und in beiden Sprachen dieselben.
   */
  private static String format(Duration duration) {
    long seconds = Math.max(duration.toSeconds(), 0);
    if (seconds < 60) {
      return "%d s".formatted(seconds);
    }
    if (seconds < 3600) {
      return "%d min %02d s".formatted(seconds / 60, seconds % 60);
    }
    return "%d h %02d min".formatted(seconds / 3600, (seconds % 3600) / 60);
  }

}
