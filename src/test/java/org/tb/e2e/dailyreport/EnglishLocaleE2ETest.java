package org.tb.e2e.dailyreport;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

import com.microsoft.playwright.Locator;
import java.time.LocalDate;
import java.util.regex.Pattern;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.tb.e2e.E2EBrowser;
import org.tb.e2e.E2ETestData;
import org.tb.e2e.PlaywrightE2ETestBase;

/**
 * Verifies that the screens reported in #823 are fully translated when the browser asks for
 * English. The labels asserted here used to be hard-coded German text in the templates: the
 * sidebar's "logged in as", the matrix month navigation and legend, and the headers of the
 * dashboard's hours-by-order table.
 */
class EnglishLocaleE2ETest extends PlaywrightE2ETestBase {

  // an own day, so the bookings of other test classes cannot satisfy the assertions for us
  private static final LocalDate BOOKING_DATE = LocalDate.parse("2026-06-23");
  private static final String ENGLISH = "en-US";

  @ParameterizedTest(name = "{0}")
  @MethodSource("org.tb.e2e.PlaywrightE2ETestBase#browsers")
  void sidebar_is_translated(E2EBrowser browser) {
    runAsUser(browser, E2ETestData.EMPLOYEE_MA_SIGN, "/dailyreport/matrix", ENGLISH, page -> {
      assertThat(page.locator("html")).hasAttribute("lang", "en");

      Locator sidebar = page.locator("#salat-nav");
      assertThat(sidebar).containsText("Logged in as");
      assertThat(sidebar).not().containsText("Angemeldet als");
    });
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("org.tb.e2e.PlaywrightE2ETestBase#browsers")
  void matrix_navigation_and_legend_are_translated(E2EBrowser browser) {
    runAsUser(browser, E2ETestData.EMPLOYEE_MA_SIGN,
        "/dailyreport/matrix?month=" + BOOKING_DATE.getMonthValue() + "&year=" + BOOKING_DATE.getYear(),
        ENGLISH, page -> {

      Locator monthNavigation = page.locator("div.card:has(#matrix-month-input)");
      assertThat(monthNavigation).containsText("Today");
      assertThat(monthNavigation).not().containsText("Heute");

      Locator legend = page.locator("div.card:has(#matrix) .card-footer");
      assertThat(legend).containsText("Sa/Su");
      assertThat(legend).containsText("Public holiday");
      assertThat(legend).containsText("Today");
      assertThat(legend).not().containsText("Feiertag");
    });
  }

  /**
   * The hours-by-order card on the dashboard only renders when the current month holds at least one
   * booking, so the test books its own hours for {@link #BOOKING_DATE} first.
   */
  @ParameterizedTest(name = "{0}")
  @MethodSource("org.tb.e2e.PlaywrightE2ETestBase#browsers")
  void dashboard_order_hours_table_is_translated(E2EBrowser browser) {
    runAsUser(browser, E2ETestData.EMPLOYEE_MA_SIGN,
        "/dailyreport/timereports/new?date=" + BOOKING_DATE, ENGLISH, page -> {

      page.fill("#durationTime", "04:00");
      selectTomSelectOption(page, "suborderId", E2ETestData.SUBORDER_GLOBEX_CONSULT_SIGN);
      page.fill("#commentField", "E2E-English-Locale-Testbuchung");
      page.click("button[type=submit]");
      page.waitForLoadState();

      page.navigate(urlWithLogin("/dailyreport/dashboard", E2ETestData.EMPLOYEE_MA_SIGN));

      Locator orderHoursCard = page.locator("div.card")
          .filter(new Locator.FilterOptions().setHasText("Hours by Order"))
          .first();
      // Tabler renders table headers with text-transform: uppercase, so match case-insensitively
      Locator header = orderHoursCard.locator("thead");
      assertThat(header).containsText(Pattern.compile("order", Pattern.CASE_INSENSITIVE));
      assertThat(header).containsText(Pattern.compile("hours", Pattern.CASE_INSENSITIVE));
      assertThat(header).not().containsText(Pattern.compile("auftrag", Pattern.CASE_INSENSITIVE));
      assertThat(header).not().containsText(Pattern.compile("stunden", Pattern.CASE_INSENSITIVE));
    });
  }

}
