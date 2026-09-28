package org.tb.dailyreport.viewhelper;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.tb.employee.domain.Employeecontract;

public class OvertimeScaleTest {

  /* Die Gesamtskala ist bewusst unsymmetrisch: gruen von -20 h bis +40 h, gelb bis -40 h bzw. +80 h,
     darueber hinaus rot. Eine Stufe beginnt jenseits ihrer Grenze, auf die Minute genau: +40:00 ist
     noch gruen, +40:01 gelb - so, wie die Legende "ueber +40 h" sagt. Bis #1175 schnitt die Skala
     auf volle Stunden ab, und +40:59 stand noch gruen da. */
  @ParameterizedTest
  @CsvSource({
      "0,     0,  success",
      "-19,   0,  success",
      "-20,   0,  success",
      "-20,  -1,  warning",
      "-25,   0,  warning",
      "-40,   0,  warning",
      "-40,  -1,  danger",
      "-50,   0,  danger",
      "-85,   0,  danger",
      "40,    0,  success",
      "40,    1,  warning",
      "50,    0,  warning",
      "80,    0,  warning",
      "80,    1,  danger",
      "85,    0,  danger"
  })
  public void grades_the_total_on_both_sides(long hours, long minutes, String expected) {
    assertThat(OvertimeScale.TOTAL.colorClass(duration(hours, minutes))).isEqualTo(expected);
  }

  /* Die Monatsskala ist symmetrisch: gruen innerhalb +/-15 h, rot jenseits +/-30 h. */
  @ParameterizedTest
  @CsvSource({
      "0,   success",
      "-15, success",
      "-16, warning",
      "-30, warning",
      "-31, danger",
      "15,  success",
      "16,  warning",
      "30,  warning",
      "31,  danger"
  })
  public void grades_the_month_on_both_sides(long hours, String expected) {
    assertThat(OvertimeScale.CURRENT_MONTH.colorClass(duration(hours, 0))).isEqualTo(expected);
  }

  @Test
  public void grades_a_missing_saldo_as_unremarkable() {
    assertThat(OvertimeScale.TOTAL.colorClass(null)).isEqualTo("success");
  }

  /* Die Legende zeigt ihre Grenzen mit Vorzeichen - das ist in der Karte der Hinweis darauf, dass
     die Skala in beide Richtungen gilt. */
  @Test
  public void labels_every_bound_with_its_sign() {
    assertThat(OvertimeScale.TOTAL.dangerBelowLabel()).isEqualTo("-40");
    assertThat(OvertimeScale.TOTAL.warningBelowLabel()).isEqualTo("-20");
    assertThat(OvertimeScale.TOTAL.warningAboveLabel()).isEqualTo("+40");
    assertThat(OvertimeScale.TOTAL.dangerAboveLabel()).isEqualTo("+80");
  }

  /* Die Legende jeder Zelle zeigt ihre eigenen Schwellen, nicht die der Nachbarzelle. */
  @Test
  public void labels_the_month_with_its_own_bounds() {
    assertThat(OvertimeScale.CURRENT_MONTH.warningBelowLabel()).isEqualTo("-15");
    assertThat(OvertimeScale.CURRENT_MONTH.warningAboveLabel()).isEqualTo("+15");
    assertThat(OvertimeScale.CURRENT_MONTH.dangerBelowLabel()).isEqualTo("-30");
    assertThat(OvertimeScale.CURRENT_MONTH.dangerAboveLabel()).isEqualTo("+30");
  }

  /* Die Schwellen gelten fuer 40 Wochenstunden und werden anteilig umgerechnet (#1175). */
  @Test
  public void scales_the_bounds_to_the_weekly_working_time() {
    assertThat(OvertimeScale.TOTAL.scaledTo(Duration.ofHours(20)))
        .isEqualTo(new OvertimeScale(-20, -10, 20, 40));
    assertThat(OvertimeScale.CURRENT_MONTH.scaledTo(Duration.ofHours(20)))
        .isEqualTo(new OvertimeScale(-15, -8, 8, 15));
    assertThat(OvertimeScale.TOTAL.scaledTo(Duration.ofHours(40))).isEqualTo(OvertimeScale.TOTAL);
  }

  /* Gerundet wird auf volle Stunden, eine halbe vom Nullpunkt weg: so bleibt die Monatsskala
     symmetrisch, und die Legende nennt keine Minuten. 38,5 Wochenstunden ergeben -19,25 / +38,5 /
     +77 bzw. +-14,4375 / +-28,875 h. */
  @Test
  public void rounds_the_scaled_bounds_to_full_hours() {
    var weekly = Duration.ofHours(38).plusMinutes(30);
    assertThat(OvertimeScale.TOTAL.scaledTo(weekly)).isEqualTo(new OvertimeScale(-39, -19, 39, 77));
    assertThat(OvertimeScale.CURRENT_MONTH.scaledTo(weekly)).isEqualTo(new OvertimeScale(-29, -14, 14, 29));
  }

  @Test
  public void takes_the_weekly_working_time_from_five_days_of_the_contract() {
    var contract = new Employeecontract();
    contract.setDailyWorkingTime(Duration.ofHours(6));

    assertThat(OvertimeScale.TOTAL.forContract(contract)).isEqualTo(new OvertimeScale(-30, -15, 30, 60));
  }

  private static Duration duration(long hours, long minutes) {
    return Duration.ofHours(hours).plusMinutes(minutes);
  }

}
