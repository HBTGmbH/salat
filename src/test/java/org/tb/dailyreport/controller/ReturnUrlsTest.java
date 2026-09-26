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
