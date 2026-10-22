package de.hbt.salat.dailyreport.viewhelper;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import de.hbt.salat.dailyreport.domain.OvertimeReport;
import de.hbt.salat.dailyreport.domain.OvertimeReportMonth;

public class OvertimeSparklineTest {

  /* Der Bericht liefert den juengsten Monat zuerst; das Gebirge laeuft von links nach rechts, der
     aelteste Monat steht also vorn. */
  @Test
  public void lists_the_month_end_balances_oldest_first_in_hours() {
    var sparkline = OvertimeSparkline.from(report(
        month(2026, 10, Duration.ofMinutes(-90)),
        month(2026, 9, Duration.ofHours(2).plusMinutes(15)),
        month(2026, 8, Duration.ZERO))).orElseThrow();

    assertThat(sparkline.values()).isEqualTo("0.00,2.25,-1.50");
    assertThat(sparkline.monthCount()).isEqualTo(3);
  }

  /* Zwoelf Monate, nicht die ganze Vertragsgeschichte: die juengere Entwicklung soll nicht von
     Jahren davor plattgedrueckt werden. */
  @Test
  public void shows_the_last_twelve_months_only() {
    var months = new ArrayList<OvertimeReportMonth>();
    for (int i = 0; i < 30; i++) {
      var yearMonth = YearMonth.of(2026, 10).minusMonths(i);
      months.add(month(yearMonth.getYear(), yearMonth.getMonthValue(), Duration.ofHours(i)));
    }

    var sparkline = OvertimeSparkline.from(new OvertimeReport(null, months)).orElseThrow();

    assertThat(sparkline.monthCount()).isEqualTo(12);
    assertThat(sparkline.values()).startsWith("11.00,").endsWith(",0.00");
    assertThat(sparkline.highestMonth()).isEqualTo("2025/11");
  }

  /* Die Textalternative nennt Hoechst- und Tiefststand mit ihrem Monat, formatiert wie die Zahl der
     Karte. */
  @Test
  public void names_highest_and_lowest_balance_with_their_month() {
    var sparkline = OvertimeSparkline.from(report(
        month(2026, 10, Duration.ofHours(4)),
        month(2026, 9, Duration.ofHours(12).plusMinutes(30)),
        month(2026, 8, Duration.ofHours(-3).minusMinutes(15)),
        month(2026, 7, Duration.ofHours(1)))).orElseThrow();

    assertThat(sparkline.highest()).isEqualTo("12:30");
    assertThat(sparkline.highestMonth()).isEqualTo("2026/09");
    assertThat(sparkline.lowest()).isEqualTo("-3:15");
    assertThat(sparkline.lowestMonth()).isEqualTo("2026/08");
  }

  /* Bei Gleichstand zaehlt der juengste Monat - er ist der, an den man sich erinnert. */
  @Test
  public void names_the_most_recent_month_on_a_tie() {
    var sparkline = OvertimeSparkline.from(report(
        month(2026, 10, Duration.ofHours(5)),
        month(2026, 9, Duration.ofHours(5)),
        month(2026, 8, Duration.ofHours(5)))).orElseThrow();

    assertThat(sparkline.highestMonth()).isEqualTo("2026/10");
    assertThat(sparkline.lowestMonth()).isEqualTo("2026/10");
  }

  /* Ein einzelner Monat, etwa im ersten Vertragsmonat, ist kein Verlauf. */
  @Test
  public void draws_nothing_for_fewer_than_two_months() {
    assertThat(OvertimeSparkline.from(report(month(2026, 10, Duration.ofHours(1))))).isEmpty();
    assertThat(OvertimeSparkline.from(report())).isEmpty();
  }

  private static OvertimeReport report(OvertimeReportMonth... months) {
    return new OvertimeReport(null, List.of(months));
  }

  private static OvertimeReportMonth month(int year, int month, Duration diffCumulative) {
    return OvertimeReportMonth.builder()
        .yearMonth(YearMonth.of(year, month))
        .diffCumulative(diffCumulative)
        .build();
  }

}
