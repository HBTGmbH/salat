package org.tb.dailyreport.viewhelper;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** Die Farbregeln der Kennzahlen im Dashboard, jede Stufe an ihrer Grenze (#1175). */
class DashboardGradesTest {

  /* Normaler Fortschritt ist neutral, auch kurz vor dem Ziel; gruen erst mit erfuelltem Soll. */
  @ParameterizedTest
  @CsvSource({
      "0,   secondary",
      "74,  secondary",
      "75,  secondary",
      "99,  secondary",
      "100, success",
      "130, success"
  })
  void grades_progress_neutral_until_the_target_is_met(int percent, String expected) {
    assertThat(DashboardGrades.progress(percent)).isEqualTo(expected);
  }

  /* Heute gebucht ist gruen, nur heute offen der Normalfall, ein fehlender Arbeitstag laesst sich
     noch nachholen, ab zweien ist es zu spaet. */
  @ParameterizedTest
  @CsvSource({
      "0,  success",
      "1,  secondary",
      "2,  warning",
      "3,  danger",
      "40, danger"
  })
  void grades_the_last_booking_by_open_working_days(int openWorkingDays, String expected) {
    assertThat(DashboardGrades.lastBooking(openWorkingDays)).isEqualTo(expected);
  }

  @Test
  void grades_an_overdue_release_red_and_a_timely_one_green() {
    var releasedUntil = LocalDate.parse("2026-08-31");

    assertThat(DashboardGrades.release(releasedUntil, true)).isEqualTo("danger");
    assertThat(DashboardGrades.release(releasedUntil, false)).isEqualTo("success");
  }

  /* Nie freigegeben heisst neuer Vertrag - Employeecontract.getReleaseWarning() warnt dann nicht. */
  @Test
  void grades_a_contract_never_released_neutral() {
    assertThat(DashboardGrades.release(null, false)).isEqualTo("secondary");
  }

  @Test
  void grades_vacation_neutral_up_to_the_budget_and_red_beyond() {
    assertThat(DashboardGrades.vacation(false)).isEqualTo("secondary");
    assertThat(DashboardGrades.vacation(true)).isEqualTo("danger");
  }
}
