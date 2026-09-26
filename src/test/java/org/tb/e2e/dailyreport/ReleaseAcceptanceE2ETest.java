package org.tb.e2e.dailyreport;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import java.util.regex.Pattern;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.tb.e2e.E2EBrowser;
import org.tb.e2e.E2ETestData;
import org.tb.e2e.PlaywrightE2ETestBase;

/**
 * Exercises the self-release flow at {@code /release} and the People Lead acceptance flow at
 * {@code /acceptance}. The overview before a release itself is covered by
 * {@link ReleaseReviewE2ETest}.
 */
class ReleaseAcceptanceE2ETest extends PlaywrightE2ETestBase {

  /**
   * The chosen month opens the overview of the period (#760), and the release happens from there —
   * without a confirmation dialog, the overview is the question. ema is released until Friday,
   * 2026-05-29, so the period is the weekend after it. Other classes book on ema's days, so the test
   * does not assert that the overview is empty.
   */
  @ParameterizedTest(name = "{0}")
  @MethodSource("org.tb.e2e.PlaywrightE2ETestBase#browsers")
  void employee_can_self_release_bookings(E2EBrowser browser) {
    runAsUser(browser, E2ETestData.EMPLOYEE_MA_SIGN, "/release", page -> {
      page.fill("input[name=until]", "2026-05");
      page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Freigabe prüfen")).click();

      assertThat(page).hasURL(Pattern.compile(".*/release/review\\?until=2026-05$"));
      assertThat(page.locator("#review-summary")).containsText("30.05.2026 – 31.05.2026");

      page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Freigeben").setExact(true)).click();

      assertThat(page).hasURL(Pattern.compile(".*/release$"));
      assertThat(page.locator("#confirmModal")).not().isVisible();
      assertThat(page.locator(".alert-success")).containsText("Buchungen vom 30.05.2026 bis 31.05.2026 freigegeben.");
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
