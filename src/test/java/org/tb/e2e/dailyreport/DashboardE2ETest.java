package org.tb.e2e.dailyreport;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import java.util.regex.Pattern;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.tb.common.test.FixedClock;
import org.tb.e2e.E2EBrowser;
import org.tb.e2e.E2ETestData;
import org.tb.e2e.PlaywrightE2ETestBase;

/**
 * Verifies the dailyreport dashboard (the post-login landing page, see the
 * {@code /welcome -> /dailyreport/dashboard} legacy redirect in AGENTS.md) renders its core
 * widgets for a logged-in employee.
 */
class DashboardE2ETest extends PlaywrightE2ETestBase {

  @ParameterizedTest(name = "{0}")
  @MethodSource("org.tb.e2e.PlaywrightE2ETestBase#browsers")
  void dashboard_renders_kpi_widgets_after_login(E2EBrowser browser) {
    runAsUser(browser, E2ETestData.EMPLOYEE_MA_SIGN, "/dailyreport/dashboard", page -> {
      assertThat(page).hasURL(java.util.regex.Pattern.compile(".*/dailyreport/dashboard.*"));
      assertThat(page.locator("body")).containsText("Diese Woche");
      assertThat(page.locator("body")).containsText("Manuela Angestellt");
    });
  }

  /**
   * The legend takes its bounds from {@code OvertimeScale}, so each cell explains its own scale
   * (#1030). Asserting on the numbers is what makes the single source visible from the outside: a
   * bound moved in the code moves here, and a bound copied into a message text would not. The
   * assertions run against the opened popover, not against the hidden block it is built from —
   * otherwise the test would pass even if the explanation never reached the screen.
   */
  @ParameterizedTest(name = "{0}")
  @MethodSource("org.tb.e2e.PlaywrightE2ETestBase#browsers")
  void overtime_cells_explain_their_own_scale(E2EBrowser browser) {
    runAsUser(browser, E2ETestData.EMPLOYEE_MA_SIGN, "/dailyreport/dashboard", page -> {
      var popover = page.locator(".overtime-legend-popover");

      legendToggleOf(page, "gesamt").click();
      assertThat(popover).isVisible();
      assertThat(popover).containsText("Minus- und Überstunden zählen gleich");
      assertThat(popover).containsText("-20 bis +40 h");
      assertThat(popover).containsText("-40 bis -20 h und +40 bis +80 h");
      assertThat(popover).containsText("unter -40 h oder über +80 h");

      // the month cell carries its own bounds, not those of the total; wait for the first popover
      // to be gone, otherwise two of them match the locator while the fade-out is still running
      page.locator("h2.page-title").click();
      assertThat(popover).not().isAttached();
      legendToggleOf(page, "2026-06").click();
      assertThat(popover).containsText("-15 bis +15 h");
      assertThat(popover).containsText("unter -30 h oder über +30 h");
    });
  }

  /** The explanation stays behind the icon: no legend text sits in the card itself. */
  @ParameterizedTest(name = "{0}")
  @MethodSource("org.tb.e2e.PlaywrightE2ETestBase#browsers")
  void overtime_cells_keep_the_legend_out_of_the_card(E2EBrowser browser) {
    runAsUser(browser, E2ETestData.EMPLOYEE_MA_SIGN, "/dailyreport/dashboard", page -> {
      assertThat(cardOf(page, "gesamt").getByText("in Ordnung")).not().isVisible();
      assertThat(page.locator(".overtime-legend-popover")).not().isAttached();
    });
  }

  /**
   * The hint on working days of the previous week without a booking (#1124), for a person of its
   * own whose contract begins on Wednesday of that week: it names each remaining weekday, and each
   * day leads into the daily view of that date, for that contract. The clock stands on Monday,
   * 2026-06-15 — {@code @FixedClock} is not inherited, and without its own the method would run a
   * week and a half later, at the extension's default.
   */
  @ParameterizedTest(name = "{0}")
  @MethodSource("org.tb.e2e.PlaywrightE2ETestBase#browsers")
  @FixedClock("2026-06-15T09:00:00")
  void names_each_working_day_of_the_previous_week_without_a_booking(E2EBrowser browser) {
    runAsUser(browser, E2ETestData.EMPLOYEE_WITHOUT_BOOKINGS_SIGN, "/dailyreport/dashboard", page -> {
      var hint = page.locator("#unbooked-days-hint");
      assertThat(hint).containsText("3 Arbeitstage der Vorwoche ohne Buchung");

      // Monday and Tuesday lie before the contract, the weekend is no working day
      var days = hint.locator("a");
      assertThat(days).hasText(new String[]{
          "Mittwoch, 10.06.2026", "Donnerstag, 11.06.2026", "Freitag, 12.06.2026"});
      assertThat(days.first()).hasAttribute("href",
          Pattern.compile("^/dailyreport/daily\\?mode=daily&date=2026-06-10&fEmployeeContractId=\\d+$"));

      page.navigate(urlWithLogin(days.first().getAttribute("href"), E2ETestData.EMPLOYEE_WITHOUT_BOOKINGS_SIGN));
      assertThat(page.locator("#daily-mode-nav h3")).hasText("Mittwoch, 10. Juni 2026");
    });
  }

  /**
   * On the first day of the contract the previous week lies entirely before it: no working day is
   * missing, and the page shows no hint rather than an empty one.
   */
  @ParameterizedTest(name = "{0}")
  @MethodSource("org.tb.e2e.PlaywrightE2ETestBase#browsers")
  @FixedClock("2026-06-10T09:00:00")
  void shows_no_hint_when_no_working_day_is_missing(E2EBrowser browser) {
    runAsUser(browser, E2ETestData.EMPLOYEE_WITHOUT_BOOKINGS_SIGN, "/dailyreport/dashboard", page -> {
      assertThat(page.locator("body")).containsText("Diese Woche");
      assertThat(page.locator("#unbooked-days-hint")).not().isAttached();
    });
  }

  private static Locator cardOf(Page page, String text) {
    return page.locator(".card").filter(new Locator.FilterOptions().setHasText(text)).first();
  }

  private static Locator legendToggleOf(Page page, String cardText) {
    return cardOf(page, cardText).locator(".overtime-legend-toggle");
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("org.tb.e2e.PlaywrightE2ETestBase#browsers")
  void root_redirects_to_dashboard(E2EBrowser browser) {
    runAsUser(browser, E2ETestData.EMPLOYEE_MA_SIGN, "/welcome", page ->
        assertThat(page).hasURL(java.util.regex.Pattern.compile(".*/dailyreport/dashboard.*")));
  }

}
