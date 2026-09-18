package org.tb.e2e.dailyreport;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

import com.microsoft.playwright.Locator;
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
   * The legend takes its bounds from {@code OvertimeScale}, so each cell shows its own scale
   * (#1030). Asserting on the numbers is what makes the single source visible from the outside: a
   * bound moved in the code moves here, and a bound copied into a message text would not.
   */
  @ParameterizedTest(name = "{0}")
  @MethodSource("org.tb.e2e.PlaywrightE2ETestBase#browsers")
  void overtime_cells_explain_their_own_scale(E2EBrowser browser) {
    runAsUser(browser, E2ETestData.EMPLOYEE_MA_SIGN, "/dailyreport/dashboard", page -> {
      var total = page.locator(".card")
          .filter(new Locator.FilterOptions().setHasText("gesamt")).first();
      assertThat(total).containsText("-20 bis +40 h");
      assertThat(total).containsText("-40 bis -20 h und +40 bis +80 h");
      assertThat(total).containsText("unter -40 h oder über +80 h");
      assertThat(page.locator("body")).containsText("-15 bis +15 h");
      assertThat(page.locator("body")).containsText("Minus- und Überstunden zählen gleich");
    });
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("org.tb.e2e.PlaywrightE2ETestBase#browsers")
  void root_redirects_to_dashboard(E2EBrowser browser) {
    runAsUser(browser, E2ETestData.EMPLOYEE_MA_SIGN, "/welcome", page ->
        assertThat(page).hasURL(java.util.regex.Pattern.compile(".*/dailyreport/dashboard.*")));
  }

}
