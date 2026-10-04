package de.hbt.salat.e2e.layout;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import de.hbt.salat.e2e.E2EBrowser;
import de.hbt.salat.e2e.E2ETestData;
import de.hbt.salat.e2e.PlaywrightE2ETestBase;

/**
 * The message at the top of the page (#1355): icon and text side by side. Tabler 1.6 dropped
 * {@code display: flex} from {@code .alert}, and the messages of {@code layout/base.html} relied on it - the
 * text slipped under the icon.
 *
 * <p>Books on a day no other E2E class uses: the bookings stay in the shared database.
 */
class PageMessageLayoutE2ETest extends PlaywrightE2ETestBase {

  private static final LocalDate DAY = LocalDate.parse("2026-07-21");

  @ParameterizedTest(name = "{0}")
  @MethodSource("de.hbt.salat.e2e.PlaywrightE2ETestBase#browsers")
  void the_success_message_shows_its_text_beside_the_icon(E2EBrowser browser) {
    runAsUser(browser, E2ETestData.EMPLOYEE_MA_SIGN, "/dailyreport/timereports/new?date=" + DAY, page -> {
      selectTomSelectOption(page, "suborderId", E2ETestData.SUBORDER_ALPHA_DEV_SIGN);
      page.fill("#durationTime", "00:15");
      page.fill("#commentField", "Meldung " + browser);
      page.click("#timereportMainForm button[data-submit-shortcut]");

      var message = page.locator(".alert-success").first();
      assertThat(message).isVisible();
      var icon = message.locator(".icon").first().boundingBox();
      var title = message.locator(".alert-title").first().boundingBox();

      // side by side: the title starts right of the icon, and the two share a line
      assertTrue(title.x >= icon.x + icon.width, "title starts left of the icon's right edge: " + title.x);
      assertTrue(title.y < icon.y + icon.height, "title starts below the icon: " + title.y);
    });
  }
}
