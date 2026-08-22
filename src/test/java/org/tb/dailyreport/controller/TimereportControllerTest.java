package org.tb.dailyreport.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.tb.dailyreport.controller.TimereportController.nextBookingUrl;
import static org.tb.dailyreport.controller.TimereportController.trainingDefaultOf;

import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The decisions the booking form controller makes without touching a service: the default state of
 * the training switch (#836) and where "Speichern und neu" goes next (#843).
 */
class TimereportControllerTest {

  private static final SuborderOption TRAINING =
      new SuborderOption(1L, "FORTBILDUNG", "HBT", false, true);
  private static final SuborderOption PROJECT =
      new SuborderOption(2L, "ALPHA-DEV", "Contoso", false, false);
  private static final LocalDate DATE = LocalDate.parse("2026-06-18");

  @Test
  void suborder_with_the_training_flag_preselects_the_switch() {
    assertThat(trainingDefaultOf(List.of(PROJECT, TRAINING), 1L)).isTrue();
  }

  @Test
  void suborder_without_the_training_flag_leaves_the_switch_off() {
    assertThat(trainingDefaultOf(List.of(PROJECT, TRAINING), 2L)).isFalse();
  }

  @Test
  void nothing_preselected_leaves_the_switch_off() {
    assertThat(trainingDefaultOf(List.of(PROJECT, TRAINING), null)).isFalse();
  }

  @Test
  void a_suborder_outside_the_offered_options_leaves_the_switch_off() {
    // the deeplink parameter suborderId is not validated against the employee's orders here
    assertThat(trainingDefaultOf(List.of(PROJECT, TRAINING), 99L)).isFalse();
  }

  @Test
  void save_and_new_returns_to_an_empty_form_for_the_same_day() {
    assertThat(nextBookingUrl(DATE, null, null))
        .isEqualTo("/dailyreport/timereports/new?date=2026-06-18");
  }

  @Test
  void the_booked_contract_is_carried_over() {
    // a manager booking for someone else must not silently fall back to their own contract
    assertThat(nextBookingUrl(DATE, 42L, null))
        .isEqualTo("/dailyreport/timereports/new?date=2026-06-18&employeeContractId=42");
  }

  @Test
  void an_unset_contract_is_left_out_of_the_url() {
    assertThat(nextBookingUrl(DATE, -1L, null)).doesNotContain("employeeContractId");
    assertThat(nextBookingUrl(DATE, 0L, null)).doesNotContain("employeeContractId");
  }

  @Test
  void the_return_target_survives_so_cancel_still_goes_back() {
    assertThat(nextBookingUrl(DATE, null, "/dailyreport/daily?mode=weekly"))
        .isEqualTo("/dailyreport/timereports/new?date=2026-06-18"
            + "&returnUrl=%2Fdailyreport%2Fdaily%3Fmode%3Dweekly");
  }

  @Test
  void an_off_site_return_target_is_dropped_rather_than_carried_along() {
    assertThat(nextBookingUrl(DATE, null, "https://evil.example.com")).doesNotContain("returnUrl");
    assertThat(nextBookingUrl(DATE, null, "/management/employees")).doesNotContain("returnUrl");
  }

}
