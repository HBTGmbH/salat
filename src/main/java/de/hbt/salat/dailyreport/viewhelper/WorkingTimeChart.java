package de.hbt.salat.dailyreport.viewhelper;

import java.time.Duration;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import de.hbt.salat.dailyreport.domain.OvertimeReportMonth;

/**
 * The working time chart of "Meine Konten": the last months in ascending order, each with its booked hours, the
 * time surcharges on top of them (#1469) and the target. The surcharges count on the time account at once, so the
 * bar shows what the account counts; the difference over it is booked + surcharges − target, as on the account.
 * Hours with one decimal.
 */
public record WorkingTimeChart(List<String> labels, List<Double> actual, List<Double> surcharge, List<Double> target) {

  /** At most {@code count} months of {@code monthsDesc}, the newest first as the overtime report sorts them. */
  public static WorkingTimeChart of(List<OvertimeReportMonth> monthsDesc, int count) {
    var labels = new ArrayList<String>();
    var actual = new ArrayList<Double>();
    var surcharge = new ArrayList<Double>();
    var target = new ArrayList<Double>();
    for (int i = Math.min(count, monthsDesc.size()) - 1; i >= 0; i--) {
      var month = monthsDesc.get(i);
      labels.add(month.getYearMonth().getMonth().getDisplayName(TextStyle.SHORT, Locale.GERMAN)
          + " '" + String.valueOf(month.getYearMonth().getYear()).substring(2));
      actual.add(hours(month.getActual()));
      surcharge.add(hours(month.getSurchargeCredit()));
      target.add(hours(month.getTarget()));
    }
    return new WorkingTimeChart(labels, actual, surcharge, target);
  }

  /** Whether any month has time surcharges: without, the chart shows no series for them. */
  public boolean hasSurcharge() {
    return surcharge.stream().anyMatch(value -> value > 0);
  }

  private static double hours(Duration duration) {
    return Math.round(duration.toMinutes() / 6.0) / 10.0;
  }
}
