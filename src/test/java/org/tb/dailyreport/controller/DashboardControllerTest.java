package org.tb.dailyreport.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.ui.ExtendedModelMap;
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

  /* Ohne taegliche Sollarbeitszeit gibt es keine Abweichung vom Soll und damit keinen Saldo -
     OvertimeService.calculateOvertime steigt mit einem leeren Optional aus. Beide Felder bleiben
     leer, denn an ihnen haengt die Sichtbarkeit der beiden Zellen (#1031). */
  @Test
  void a_contract_without_target_hours_fills_neither_overtime_field() {
    var model = new ExtendedModelMap();

    DashboardController.addOvertimeAttributes(model, Optional.empty());

    assertThat(model.getAttribute("overtime")).isEqualTo("");
    assertThat(model.getAttribute("monthlyOvertime")).isEqualTo("");
    assertThat(model.getAttribute("overtimeMonth")).isEqualTo("");
  }

  /* Der Vertrag beruehrt den laufenden Monat nicht: OvertimeService setzt currentMonth dann nicht.
     Der Gesamtsaldo steht trotzdem, die Monatszelle faellt weg. */
  @Test
  void a_status_without_a_current_month_fills_only_the_total() {
    var status = new OvertimeStatus();
    status.setTotal(info(-50));
    var model = new ExtendedModelMap();

    DashboardController.addOvertimeAttributes(model, Optional.of(status));

    assertThat(model.getAttribute("overtime")).isEqualTo("-50:00");
    assertThat(model.getAttribute("monthlyOvertime")).isEqualTo("");
    assertThat(model.getAttribute("overtimeMonth")).isEqualTo("");
  }

  /* Ein ausgerechnetes 0:00 ist ein Wert und bleibt sichtbar: die Unterscheidung ist "kein Wert"
     gegen "Wert ist null", nicht "Text ist 0:00". */
  @Test
  void a_month_that_really_is_balanced_keeps_its_cell() {
    var status = new OvertimeStatus();
    status.setTotal(info(0));
    status.setCurrentMonth(info(0));
    status.getCurrentMonth().setBegin(LocalDate.parse("2026-09-01"));
    var model = new ExtendedModelMap();

    DashboardController.addOvertimeAttributes(model, Optional.of(status));

    assertThat(model.getAttribute("monthlyOvertime")).isEqualTo("0:00");
    assertThat(model.getAttribute("overtimeMonth")).isEqualTo("2026-09");
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
