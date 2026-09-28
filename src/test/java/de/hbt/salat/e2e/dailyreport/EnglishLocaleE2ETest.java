package de.hbt.salat.e2e.dailyreport;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

import com.microsoft.playwright.Locator;
import java.time.LocalDate;
import java.util.regex.Pattern;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import de.hbt.salat.e2e.E2EBrowser;
import de.hbt.salat.e2e.E2ETestData;
import de.hbt.salat.e2e.PlaywrightE2ETestBase;

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
  @MethodSource("de.hbt.salat.e2e.PlaywrightE2ETestBase#browsers")
  void sidebar_is_translated(E2EBrowser browser) {
    runAsUser(browser, E2ETestData.EMPLOYEE_MA_SIGN, "/dailyreport/matrix", ENGLISH, page -> {
      assertThat(page.locator("html")).hasAttribute("lang", "en");

      Locator sidebar = page.locator("#salat-nav");
      assertThat(sidebar).containsText("Logged in as");
      assertThat(sidebar).not().containsText("Angemeldet als");
    });
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("de.hbt.salat.e2e.PlaywrightE2ETestBase#browsers")
  void matrix_navigation_and_legend_are_translated(E2EBrowser browser) {
    runAsUser(browser, E2ETestData.EMPLOYEE_MA_SIGN,
        "/dailyreport/matrix?fMonth=" + BOOKING_DATE.getMonthValue() + "&fYear=" + BOOKING_DATE.getYear(),
        ENGLISH, page -> {

      // the month picker replaced the native <input type="month">, which Chrome rendered as
      // "März 2026" no matter which language the application was running in
      Locator monthPicker = page.locator("button.dropdown-toggle[title='Select month']");
      assertThat(monthPicker).hasText("June 2026");

      Locator monthNavigation = page.locator("div.card:has(button.dropdown-toggle)").first();
      // the button jumps to the current month, not to today - it is labelled accordingly (#855)
      assertThat(monthNavigation).containsText("Current month");
      assertThat(monthNavigation).not().containsText("Aktueller Monat");

      // the picker itself lists the months in English, too
      monthPicker.click();
      Locator monthMenu = page.locator("div.btn-group:has(button[title='Select month']) .dropdown-menu");
      assertThat(monthMenu).containsText("December");
      assertThat(monthMenu).not().containsText("Dezember");

      // the matrix card carries a second footer holding the "Rest nicht gearbeitet" action (#855)
      Locator legend = page.locator("div.card:has(#matrix) .card-footer").first();
      assertThat(legend).containsText("Sa/Su");
      assertThat(legend).containsText("Public holiday");
      assertThat(legend).containsText("Today");
      assertThat(legend).not().containsText("Feiertag");
    });
  }

  /**
   * The matrix card on the dashboard (#878, before it the hours-by-order card): title, column
   * headers and the booked suborder in English. The test books its own hours for
   * {@link #BOOKING_DATE} so the table holds a row it can rely on.
   */
  @ParameterizedTest(name = "{0}")
  @MethodSource("de.hbt.salat.e2e.PlaywrightE2ETestBase#browsers")
  void dashboard_matrix_is_translated(E2EBrowser browser) {
    runAsUser(browser, E2ETestData.EMPLOYEE_MA_SIGN,
        "/dailyreport/timereports/new?date=" + BOOKING_DATE, ENGLISH, page -> {

      page.fill("#durationTime", "04:00");
      selectTomSelectOption(page, "suborderId", E2ETestData.SUBORDER_GLOBEX_CONSULT_SIGN);
      page.fill("#commentField", "E2E-English-Locale-Testbuchung");
      page.click("button[type=submit]");
      page.waitForLoadState();

      page.navigate(urlWithLogin("/dailyreport/dashboard", E2ETestData.EMPLOYEE_MA_SIGN));

      Locator card = page.locator("#dashboard-matrix");
      assertThat(card.locator(".card-title")).containsText("Matrix overview");
      assertThat(card.locator(".card-title")).containsText("June 2026");
      // Tabler renders table headers with text-transform: uppercase, so match case-insensitively
      Locator header = card.locator("#matrix thead");
      assertThat(header).containsText(Pattern.compile("order", Pattern.CASE_INSENSITIVE));
      assertThat(header).containsText(Pattern.compile("sum", Pattern.CASE_INSENSITIVE));
      assertThat(header).not().containsText(Pattern.compile("auftrag", Pattern.CASE_INSENSITIVE));
      assertThat(card.locator("#matrix tbody")).containsText(E2ETestData.SUBORDER_GLOBEX_CONSULT_SIGN);
    });
  }

}
