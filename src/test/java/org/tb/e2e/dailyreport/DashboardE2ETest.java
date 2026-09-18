package org.tb.e2e.dailyreport;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
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
