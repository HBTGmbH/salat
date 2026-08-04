package org.tb.e2e.dailyreport;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

import java.time.LocalDate;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.tb.e2e.E2EBrowser;
import org.tb.e2e.E2ETestData;
import org.tb.e2e.PlaywrightE2ETestBase;

/**
 * Books a working day and a timereport entry as a regular employee, then verifies both show up
 * in the daily view, exercising the core dailyreport booking flow through the real UI.
 */
class DailyBookingE2ETest extends PlaywrightE2ETestBase {

  private static final LocalDate BOOKING_DATE = LocalDate.parse("2026-06-15");

  @ParameterizedTest(name = "{0}")
  @MethodSource("org.tb.e2e.PlaywrightE2ETestBase#browsers")
  void booking_a_project_timereport_shows_up_in_the_daily_view(E2EBrowser browser) {
    runAsUser(browser, E2ETestData.EMPLOYEE_MA_SIGN,
        "/dailyreport/timereports/new?date=" + BOOKING_DATE, page -> {

      page.fill("#durationTime", "02:00");
      selectTomSelectOption(page, "suborderId", E2ETestData.SUBORDER_ALPHA_DEV_SIGN);
      page.fill("#commentField", "E2E-Testbuchung");
      page.click("button[type=submit]");
      page.waitForLoadState();

      page.navigate(urlWithLogin("/dailyreport/daily?mode=daily&date=" + BOOKING_DATE, E2ETestData.EMPLOYEE_MA_SIGN));

      assertThat(page.locator("#daily-bookings-area")).containsText(E2ETestData.SUBORDER_ALPHA_DEV_SIGN);
      assertThat(page.locator("#daily-bookings-area")).containsText("2:00");
    });
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("org.tb.e2e.PlaywrightE2ETestBase#browsers")
  void booking_on_the_vacation_suborder_shows_up_as_urlaub(E2EBrowser browser) {
    runAsUser(browser, E2ETestData.EMPLOYEE_MA_SIGN,
        "/dailyreport/timereports/new?date=" + BOOKING_DATE.plusDays(1), page -> {

      page.fill("#durationTime", "08:00");
      selectTomSelectOption(page, "suborderId", "URLAUB");
      page.click("button[type=submit]");
      page.waitForLoadState();

      page.navigate(urlWithLogin(
          "/dailyreport/daily?mode=daily&date=" + BOOKING_DATE.plusDays(1), E2ETestData.EMPLOYEE_MA_SIGN));

      assertThat(page.locator("#daily-bookings-area")).containsText("URLAUB");
    });
  }

}
