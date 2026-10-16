package de.hbt.salat.dailyreport.viewhelper;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.YearMonth;
import java.util.List;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import de.hbt.salat.dailyreport.domain.OvertimeReportMonth;

/**
 * The working time chart of "Meine Konten" (#1469): the last months oldest first, the time surcharges as a series
 * of their own to stack on the booked hours - they count on the time account at once.
 */
@DisplayNameGeneration(ReplaceUnderscores.class)
class WorkingTimeChartTest {

  @Test
  void the_months_come_oldest_first_with_booked_hours_surcharges_and_target() {
    var chart = WorkingTimeChart.of(List.of(
        month(YearMonth.of(2026, 9), 150, 90, 168),
        month(YearMonth.of(2026, 8), 160, 0, 160)), 6);

    assertThat(chart.labels()).containsExactly("Aug. '26", "Sept. '26");
    assertThat(chart.actual()).containsExactly(160.0, 150.0);
    assertThat(chart.surcharge()).containsExactly(0.0, 1.5);
    assertThat(chart.target()).containsExactly(160.0, 168.0);
    assertThat(chart.hasSurcharge()).isTrue();
  }

  @Test
  void only_the_newest_months_up_to_the_count() {
    var chart = WorkingTimeChart.of(List.of(
        month(YearMonth.of(2026, 9), 1, 0, 1),
        month(YearMonth.of(2026, 8), 1, 0, 1),
        month(YearMonth.of(2026, 7), 1, 0, 1)), 2);

    assertThat(chart.labels()).containsExactly("Aug. '26", "Sept. '26");
    assertThat(chart.hasSurcharge()).isFalse();
  }

  private static OvertimeReportMonth month(YearMonth yearMonth, int actualHours, int surchargeMinutes, int targetHours) {
    return OvertimeReportMonth.builder()
        .yearMonth(yearMonth)
        .actual(Duration.ofHours(actualHours))
        .surchargeCredit(Duration.ofMinutes(surchargeMinutes))
        .target(Duration.ofHours(targetHours))
        .build();
  }
}
