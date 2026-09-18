package org.tb.dailyreport.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.tb.dailyreport.domain.OvertimeStatus;
import org.tb.dailyreport.domain.OvertimeStatus.OvertimeStatusInfo;

public class DashboardControllerTest {

  @Test
  public void grades_a_negative_total_on_the_negative_side_of_the_scale() {
    assertThat(DashboardController.overtimeColorClass(status(-25, 0))).isEqualTo("warning");
    assertThat(DashboardController.overtimeColorClass(status(-50, 0))).isEqualTo("danger");
  }

  @Test
  public void leaves_a_positive_total_where_it_was() {
    assertThat(DashboardController.overtimeColorClass(status(10, 0))).isEqualTo("success");
    assertThat(DashboardController.overtimeColorClass(status(50, 0))).isEqualTo("warning");
    assertThat(DashboardController.overtimeColorClass(status(85, 0))).isEqualTo("danger");
  }

  /* Der Pfeil zeigt nach unten, sobald die Dauer negativ ist. Die Farbe kommt aus derselben Dauer -
     ein Minussaldo kann damit nicht mehr gruen neben einem abwaertsgerichteten Pfeil stehen, wenn
     die Skala ihn bemaengelt (#1030). */
  @Test
  public void keeps_arrow_and_colour_from_contradicting_each_other() {
    var status = status(-25, 0);

    assertThat(status.get().getTotal().isNegative()).isTrue();
    assertThat(DashboardController.overtimeColorClass(status)).isNotEqualTo("success");
  }

  /* Die Monatszelle wurde mit dem Vorzeichen des Gesamtsaldos bewertet. Bei gegenlaeufigen
     Vorzeichen zeigt sich das: ein Monat von -20 h ist gelb, gleich wie der Gesamtsaldo steht. */
  @Test
  public void grades_the_month_without_looking_at_the_total() {
    assertThat(DashboardController.monthlyOvertimeColorClass(status(60, -20))).isEqualTo("warning");
    assertThat(DashboardController.monthlyOvertimeColorClass(status(-60, -20))).isEqualTo("warning");
    assertThat(DashboardController.monthlyOvertimeColorClass(status(-60, 20))).isEqualTo("warning");
    assertThat(DashboardController.monthlyOvertimeColorClass(status(-60, 35))).isEqualTo("danger");
    assertThat(DashboardController.monthlyOvertimeColorClass(status(60, -35))).isEqualTo("danger");
  }

  @Test
  public void grades_a_missing_status_as_unremarkable() {
    assertThat(DashboardController.overtimeColorClass(Optional.empty())).isEqualTo("success");
    assertThat(DashboardController.monthlyOvertimeColorClass(Optional.empty())).isEqualTo("success");
  }

  @Test
  public void grades_a_status_without_a_current_month_as_unremarkable() {
    var status = new OvertimeStatus();
    status.setTotal(info(-50));

    assertThat(DashboardController.monthlyOvertimeColorClass(Optional.of(status))).isEqualTo("success");
  }

  private static Optional<OvertimeStatus> status(long totalHours, long monthHours) {
    var status = new OvertimeStatus();
    status.setTotal(info(totalHours));
    status.setCurrentMonth(info(monthHours));
    return Optional.of(status);
  }

  /* Wie OvertimeService.toStatusInfo: die Dauer traegt das Vorzeichen, negative ist nur der Merker
     fuer die Pfeilrichtung. */
  private static OvertimeStatusInfo info(long hours) {
    var info = new OvertimeStatusInfo();
    var duration = Duration.ofHours(hours);
    info.setDuration(duration);
    info.setNegative(duration.isNegative());
    return info;
  }

}
