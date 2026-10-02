package de.hbt.salat.etl.viewhelper;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import de.hbt.salat.common.util.DurationUtils;
import de.hbt.salat.etl.domain.ETLRunHistory;
import de.hbt.salat.etl.domain.ETLRunHistory.Status;
import de.hbt.salat.etl.domain.ETLRunHistory.Trigger;

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
    return DurationUtils.formatElapsed(Duration.between(run.getStartedAt(), run.getFinishedAt()));
  }

}
