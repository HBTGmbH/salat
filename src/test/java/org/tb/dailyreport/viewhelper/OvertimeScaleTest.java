package org.tb.dailyreport.viewhelper;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

public class OvertimeScaleTest {

  /* Die Gesamtskala ist bewusst unsymmetrisch: gruen von -20 h bis +40 h, gelb bis -40 h bzw. +80 h,
     darueber hinaus rot. Angeschnittene Stunden zaehlen zur kleineren Stufe, weil toHours() zur Null
     hin abschneidet - deshalb stehen -40:59 und +80:59 hier noch in Gelb. */
  @ParameterizedTest
  @CsvSource({
      "0,     0,  success",
      "-19,   0,  success",
      "-20,   0,  success",
      "-20, -59,  success",
      "-21,   0,  warning",
      "-25,   0,  warning",
      "-40,   0,  warning",
      "-40, -59,  warning",
      "-41,   0,  danger",
      "-50,   0,  danger",
      "-85,   0,  danger",
      "40,    0,  success",
      "40,   59,  success",
      "41,    0,  warning",
      "50,    0,  warning",
      "80,    0,  warning",
      "80,   59,  warning",
      "81,    0,  danger",
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

  private static Duration duration(long hours, long minutes) {
    return Duration.ofHours(hours).plusMinutes(minutes);
  }

}
