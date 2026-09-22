package org.tb.e2e.dailyreport;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.tb.e2e.E2EBrowser;
import org.tb.e2e.E2ETestData;
import org.tb.e2e.PlaywrightE2ETestBase;

/**
 * Exercises the self-release flow at {@code /release} and the People Lead acceptance flow at
 * {@code /acceptance}.
 */
class ReleaseAcceptanceE2ETest extends PlaywrightE2ETestBase {

  @ParameterizedTest(name = "{0}")
  @MethodSource("org.tb.e2e.PlaywrightE2ETestBase#browsers")
  void employee_can_self_release_bookings(E2EBrowser browser) {
    runAsUser(browser, E2ETestData.EMPLOYEE_MA_SIGN, "/release", page -> {
      page.fill("input[name=selfReleaseDate]", "2026-05");
      page.click("button[type=submit]");

      // the shared dialog (#1032) names who is releasing and up to when — the month is what was
      // just typed into the form, so only the dialog can carry it
      assertThat(confirmDialog(page)).containsText("2026-05");
      confirmAction(page);
      page.waitForLoadState();

      assertThat(page.locator("body")).containsText("2026-05-31");
    });
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("org.tb.e2e.PlaywrightE2ETestBase#browsers")
  void people_lead_can_accept_team_bookings(E2EBrowser browser) {
    runAsUser(browser, E2ETestData.EMPLOYEE_PV_SIGN, "/acceptance", page -> {
      assertThat(page.locator("body")).containsText(E2ETestData.EMPLOYEE_MA_SIGN);
    });
  }

}
