package org.tb.dailyreport.controller;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Which return targets the booking form and its redirects accept (#1133). The targets the
 * application builds itself must keep passing; everything that leaves the application, runs script
 * or would break the redirect header is dropped.
 */
class ReturnUrlsTest {

  /** Targets as the templates and the share dialog build them (daily.html, daily-list-card.html). */
  @ParameterizedTest
  @ValueSource(strings = {
      "/dailyreport/daily",
      "/dailyreport/daily?mode=daily&date=2026-03-02",
      "/dailyreport/daily?mode=list&fMonth=3&fYear=2026",
      "/dailyreport/daily?mode=weekly",
      "/dailyreport/daily?mode=daily&date=2026-03-02#day-2026-03-02",
      "/dailyreport/daily?mode=daily&date=2026-03-02&fEmployeeContractId=42"})
  void a_target_in_the_daily_view_is_safe(String returnUrl) {
    assertThat(ReturnUrls.isSafe(returnUrl)).isTrue();
  }

  /** Die Übersichten vor der Freigabe (#760) und vor der Abnahme (#1122), wie ReviewLinks sie baut, mit Sprungmarke. */
  @ParameterizedTest
  @ValueSource(strings = {
      "/release/review",
      "/release/review?until=2026-08",
      "/release/review?until=2026-08#tr-5",
      "/release/review?until=2026-08&view=day#day-2026-08-03",
      "/release/review#review-views",
      "/acceptance/release/review?contractId=42&until=2026-08",
      "/acceptance/release/review?contractId=42&until=2026-08&view=day#day-2026-08-03",
      "/acceptance/accept/review?contractId=42&until=2026-08",
      "/acceptance/accept/review?contractId=42&until=2026-08#tr-5",
      "/acceptance/accept/review?contractId=42&until=2026-08&view=day#day-2026-08-03"})
  void an_overview_before_a_release_or_an_acceptance_is_safe_and_leads_back_to_an_overview(String returnUrl) {
    assertThat(ReturnUrls.isSafe(returnUrl)).isTrue();
    assertThat(ReturnUrls.isReviewPage(returnUrl)).isTrue();
  }

  /**
   * Eine Übersicht ist ein genauer Pfad, kein Präfix: dahinter darf nur die Abfrage oder die
   * Sprungmarke kommen.
   */
  @ParameterizedTest
  @ValueSource(strings = {
      "/release/reviewer",
      "/release/review2?until=2026-08",
      "/release/review/",
      "/release/review/../../management/employees",
      "/release/review/x?until=2026-08",
      "/Release/review?until=2026-08",
      "/release%2Freview?until=2026-08",
      "/acceptance/release/reviews",
      "/acceptance/review?contractId=42",
      "/acceptance/accept/reviews?contractId=42&until=2026-08",
      "/acceptance/accept/review/x?contractId=42",
      "/acceptance/accept?contractId=42"})
  void a_path_that_only_begins_like_an_overview_is_dropped(String returnUrl) {
    assertThat(ReturnUrls.isSafe(returnUrl)).isFalse();
    assertThat(ReturnUrls.isReviewPage(returnUrl)).isFalse();
  }

  @ParameterizedTest
  @ValueSource(strings = {
      "//release/review?until=2026-08",
      "https://evil.example.com/release/review?until=2026-08",
      "/release/review?until=2026-08\\",
      "/release/review?until=2026-08\r\nSet-Cookie: injected=1",
      "javascript:alert(1)//release/review"})
  void an_overview_is_held_to_the_same_form_as_any_other_target(String returnUrl) {
    assertThat(ReturnUrls.isSafe(returnUrl)).isFalse();
    assertThat(ReturnUrls.isReviewPage(returnUrl)).isFalse();
  }

  /** Nur eine Übersicht bekommt in der Tagesansicht einen Weg zurück (#760), nicht die Tagesansicht. */
  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {
      "/dailyreport/daily?mode=daily&date=2026-03-02",
      "/dailyreport/daily?returnUrl=/release/review"})
  void the_daily_view_is_safe_but_no_overview(String returnUrl) {
    assertThat(ReturnUrls.isReviewPage(returnUrl)).isFalse();
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {
      "javascript:alert(1)",
      "JavaScript:alert(1)",
      "data:text/html,<script>alert(1)</script>",
      "https://evil.example.com",
      "https://evil.example.com/dailyreport/daily",
      "//evil.example.com/dailyreport/daily",
      "/\\evil.example.com/dailyreport/daily",
      "dailyreport/daily",
      " /dailyreport/daily"})
  void a_target_outside_the_application_is_dropped(String returnUrl) {
    assertThat(ReturnUrls.isSafe(returnUrl)).isFalse();
  }

  @ParameterizedTest
  @ValueSource(strings = {
      "/management/employees",
      "/release",
      "/?next=/dailyreport/daily",
      "/logout#/dailyreport/daily"})
  void a_target_elsewhere_in_the_application_is_dropped(String returnUrl) {
    assertThat(ReturnUrls.isSafe(returnUrl)).isFalse();
  }

  @ParameterizedTest
  @ValueSource(strings = {
      "/dailyreport/daily\r\nSet-Cookie: injected=1",
      "/dailyreport/daily\nLocation: https://evil.example.com",
      "/dailyreport/daily\t",
      "/dailyreport/daily\u0000",
      "/dailyreport/daily\u007f",
      "/dailyreport/daily\u0085"})
  void a_target_with_a_control_character_is_dropped(String returnUrl) {
    assertThat(ReturnUrls.isSafe(returnUrl)).isFalse();
  }

  @Test
  void a_target_with_a_backslash_is_dropped() {
    // a browser reads the backslash as a slash; the prefix alone would let this one through
    assertThat(ReturnUrls.isSafe("/dailyreport/daily\\..\\..\\management")).isFalse();
  }

  @Test
  void a_safe_target_is_kept_and_an_unsafe_one_falls_back() {
    assertThat(ReturnUrls.orElse("/dailyreport/daily?mode=weekly", "/fallback"))
        .isEqualTo("/dailyreport/daily?mode=weekly");
    assertThat(ReturnUrls.orElse("javascript:alert(1)", "/fallback")).isEqualTo("/fallback");
    assertThat(ReturnUrls.orElse("https://evil.example.com", null)).isNull();
  }

}
