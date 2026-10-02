package de.hbt.salat.dailyreport.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static de.hbt.salat.dailyreport.controller.TimereportController.newBookingUrl;
import static de.hbt.salat.dailyreport.controller.TimereportController.nextBookingUrl;
import static de.hbt.salat.dailyreport.controller.TimereportController.trainingDefaultOf;

import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The decisions the booking form controller makes without touching a service: the default state of
 * the training switch (#836), where "Speichern und neu" goes next (#843) and where a form prefilled by
 * the command palette starts (#1158).
 */
class TimereportControllerTest {

  private static final SuborderOption TRAINING =
      new SuborderOption(1L, "FORTBILDUNG", null, "FORTBILDUNG", "HBT", false, true, false);
  private static final SuborderOption PROJECT =
      new SuborderOption(2L, "ALPHA-DEV", null, "ALPHA-DEV", "Contoso", false, false, false);
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

  /** The field a form prefilled by the command palette starts on (#1158); anything else keeps the page's own. */
  @Test
  void the_palette_names_the_field_the_form_starts_on() {
    assertThat(TimereportController.EntryFocus.of("duration")).isEqualTo(TimereportController.EntryFocus.DURATION);
    assertThat(TimereportController.EntryFocus.of("SAVE")).isEqualTo(TimereportController.EntryFocus.SAVE);
    assertThat(TimereportController.EntryFocus.of("comment")).isEqualTo(TimereportController.EntryFocus.COMMENT);
    assertThat(TimereportController.EntryFocus.of("referenceday")).isNull();
    assertThat(TimereportController.EntryFocus.of(null)).isNull();
  }

  @Test
  void save_and_new_returns_to_an_empty_form_for_the_same_day() {
    assertThat(nextBookingUrl(DATE, null, null, null))
        .isEqualTo("/dailyreport/timereports/new?date=2026-06-18");
  }

  @Test
  void the_booked_contract_is_carried_over() {
    // a manager booking for someone else must not silently fall back to their own contract
    assertThat(nextBookingUrl(DATE, 42L, null, null))
        .isEqualTo("/dailyreport/timereports/new?date=2026-06-18&fEmployeeContractId=42");
  }

  @Test
  void an_unset_contract_is_left_out_of_the_url() {
    assertThat(nextBookingUrl(DATE, -1L, null, null)).doesNotContain("fEmployeeContractId");
    assertThat(nextBookingUrl(DATE, 0L, null, null)).doesNotContain("fEmployeeContractId");
  }

  @Test
  void the_return_target_survives_so_cancel_still_goes_back() {
    assertThat(nextBookingUrl(DATE, null, null, "/dailyreport/daily?mode=weekly"))
        .isEqualTo("/dailyreport/timereports/new?date=2026-06-18"
            + "&returnUrl=%2Fdailyreport%2Fdaily%3Fmode%3Dweekly");
  }

  /** Aus der Übersicht vor der Freigabe (#760): zurück in dieselbe Sicht an denselben Tag. */
  @Test
  void a_return_target_in_an_overview_survives_with_its_view_and_anchor() {
    assertThat(nextBookingUrl(DATE, null, null, "/release/review?until=2026-06&view=day#day-2026-06-18"))
        .isEqualTo("/dailyreport/timereports/new?date=2026-06-18"
            + "&returnUrl=%2Frelease%2Freview%3Funtil%3D2026-06%26view%3Dday%23day-2026-06-18");
  }

  /** Aus der Übersicht vor der Abnahme (#1122) ebenso. */
  @Test
  void the_acceptance_review_is_a_safe_return_target() {
    assertThat(nextBookingUrl(DATE, null, null, "/acceptance/accept/review?contractId=42&until=2026-06#tr-5"))
        .isEqualTo("/dailyreport/timereports/new?date=2026-06-18"
            + "&returnUrl=%2Facceptance%2Faccept%2Freview%3FcontractId%3D42%26until%3D2026-06%23tr-5");
  }

  /**
   * Ein Formular für einen genannten Vertrag (#760) öffnet das nächste für denselben — über das
   * Formularfeld, nicht über die gemerkte Auswahl, die eine andere Person nennen kann.
   */
  @Test
  void a_named_contract_is_carried_over_instead_of_the_remembered_one() {
    assertThat(nextBookingUrl(DATE, 99L, 42L, null))
        .isEqualTo("/dailyreport/timereports/new?date=2026-06-18&employeecontractId=42");
  }

  @Test
  void a_path_that_only_begins_like_an_overview_is_dropped() {
    assertThat(nextBookingUrl(DATE, null, null, "/release/reviewer?until=2026-06")).doesNotContain("returnUrl");
  }

  @Test
  void an_off_site_return_target_is_dropped_rather_than_carried_along() {
    assertThat(nextBookingUrl(DATE, null, null, "https://evil.example.com")).doesNotContain("returnUrl");
    assertThat(nextBookingUrl(DATE, null, null, "/management/employees")).doesNotContain("returnUrl");
  }

  /** Der Knopf der Kopfzeile (#1156) ohne Zusammenhang: das Formular für heute. */
  @Test
  void the_header_button_without_context_opens_the_plain_form() {
    assertThat(newBookingUrl(null, null, null)).isEqualTo("/dailyreport/timereports/new");
  }

  @Test
  void the_header_button_carries_the_day_and_the_way_back_into_it() {
    assertThat(newBookingUrl(DATE, null, "/dailyreport/daily?mode=daily&date=2026-06-18"))
        .isEqualTo("/dailyreport/timereports/new?date=2026-06-18"
            + "&returnUrl=%2Fdailyreport%2Fdaily%3Fmode%3Ddaily%26date%3D2026-06-18");
  }

  /** Von einer Prüfseite: deren Person als Formularfeld, nicht als Filter (ADR-0023), und kein Tag. */
  @Test
  void the_header_button_on_a_review_names_its_contract_and_leads_back() {
    assertThat(newBookingUrl(null, 42L, "/acceptance/accept/review?contractId=42&until=2026-06"))
        .isEqualTo("/dailyreport/timereports/new?employeecontractId=42"
            + "&returnUrl=%2Facceptance%2Faccept%2Freview%3FcontractId%3D42%26until%3D2026-06");
  }

  @Test
  void the_header_button_drops_an_unset_contract_and_an_unsafe_target() {
    assertThat(newBookingUrl(null, -1L, "/management/employees")).isEqualTo("/dailyreport/timereports/new");
    assertThat(newBookingUrl(null, 0L, "javascript:alert(1)")).isEqualTo("/dailyreport/timereports/new");
  }

}
