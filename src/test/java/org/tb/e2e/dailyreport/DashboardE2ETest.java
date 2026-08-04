package org.tb.e2e.dailyreport;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

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

  @ParameterizedTest(name = "{0}")
  @MethodSource("org.tb.e2e.PlaywrightE2ETestBase#browsers")
  void root_redirects_to_dashboard(E2EBrowser browser) {
    runAsUser(browser, E2ETestData.EMPLOYEE_MA_SIGN, "/welcome", page ->
        assertThat(page).hasURL(java.util.regex.Pattern.compile(".*/dailyreport/dashboard.*")));
  }

}
