package de.hbt.salat.e2e.dailyreport;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import de.hbt.salat.e2e.E2EBrowser;
import de.hbt.salat.e2e.E2ETestData;
import de.hbt.salat.e2e.PlaywrightE2ETestBase;

/**
 * The week strip of the daily view shows on large screens only (#1269). In the column next to the day
 * it was about 215 px wide at a window of 1000 px, and "Nicht gearbeitet" reached into the padding of
 * its entry; below 992 px it stood under the day. The day is changed with the buttons above it and the
 * command palette all the same.
 */
class WeekStripLargeScreensE2ETest extends PlaywrightE2ETestBase {

  /**
   * A week with a holiday on a weekday, days marked as not worked and a booking — the widest entries
   * the strip has to hold.
   */
  private static final String HOLIDAY_WEEK = "/dailyreport/daily?mode=daily&date=" + E2ETestData.HOLIDAY_ON_WEEKDAY;

  /** Per entry of the strip: what reaches past its padding. Empty when everything fits. */
  private static final String OVERFLOWS = """
      () => [...document.querySelectorAll('#daily-week-strip .list-group-item')].flatMap(item => {
        const style = getComputedStyle(item);
        const box = item.getBoundingClientRect();
        const left = box.left + parseFloat(style.paddingLeft);
        const right = box.right - parseFloat(style.paddingRight);
        return [...item.querySelectorAll('.badge, .daily-tile, .text-muted')]
          .filter(el => el.getBoundingClientRect().width > 0)
          .filter(el => el.getBoundingClientRect().left < left - 0.5 || el.getBoundingClientRect().right > right + 0.5)
          .map(el => item.getAttribute('href') + ': ' + el.textContent.replace(/\\s+/g, ' ').trim());
      })""";

  @ParameterizedTest(name = "{0}")
  @MethodSource("de.hbt.salat.e2e.PlaywrightE2ETestBase#browsers")
  void below_a_large_screen_the_week_strip_is_hidden_with_its_heading(E2EBrowser browser) {
    runAsUser(browser, E2ETestData.EMPLOYEE_REVIEWED_SIGN, HOLIDAY_WEEK, page -> {
      for (int width : new int[] {390, 1000, 1199}) {
        page.setViewportSize(width, 900);
        assertThat(weekStripCard(page)).isHidden();
      }
      // the day can still be changed
      assertThat(page.locator("[aria-label='Nächster Tag']").first()).isVisible();
    });
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("de.hbt.salat.e2e.PlaywrightE2ETestBase#browsers")
  void from_a_large_screen_on_the_week_strip_shows_and_every_entry_fits(E2EBrowser browser) {
    runAsUser(browser, E2ETestData.EMPLOYEE_REVIEWED_SIGN, HOLIDAY_WEEK, page -> {
      for (int width : new int[] {1200, 1440}) {
        page.setViewportSize(width, 900);
        assertThat(weekStripCard(page)).isVisible();
        assertThat(page.locator("#daily-week-strip .not-worked-badge").first()).isVisible();

        assertEquals(List.of(), page.evaluate(OVERFLOWS), "at " + width + " px");
      }
    });
  }

  private static Locator weekStripCard(Page page) {
    return page.locator(".card:has(> #daily-week-strip)");
  }

}
