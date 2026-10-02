package de.hbt.salat.e2e.dailyreport;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import de.hbt.salat.e2e.E2EBrowser;
import de.hbt.salat.e2e.E2ETestData;
import de.hbt.salat.e2e.PlaywrightE2ETestBase;

/**
 * "Nicht gearbeitet" stands level with the date tile on a day without bookings (#1268). It used to sit
 * 4 px above: an empty row of cards hung below it, and the margin between the two still counted in a
 * cell that centres its content.
 */
class DailyListNotWorkedBadgeE2ETest extends PlaywrightE2ETestBase {

  /** Every working day of September 2026 of this person but one is marked as not worked. */
  private static final String MONTH_VIEW = "/dailyreport/daily?mode=list&fMonth=9&fYear=2026";

  /** Per day with the badge and no booking: how far the badge's centre is off the tile's, if more than 1 px. */
  private static final String OFF_CENTRE = """
      () => [...document.querySelectorAll('#daily-list tbody tr')].flatMap(row => {
        const badge = row.querySelector('.daily-day-content .not-worked-badge');
        if (!badge || row.querySelector('[id^="tr-card-"]')) return [];
        const tile = row.querySelector('.daily-tile').getBoundingClientRect();
        const box = badge.getBoundingClientRect();
        const off = (box.top + box.height / 2) - (tile.top + tile.height / 2);
        return Math.abs(off) > 1 ? [row.querySelector('.daily-tile').textContent.replace(/\\s+/g, ' ').trim() + ': ' + off + 'px'] : [];
      })""";

  @ParameterizedTest(name = "{0}")
  @MethodSource("de.hbt.salat.e2e.PlaywrightE2ETestBase#browsers")
  void the_badge_of_a_day_without_bookings_is_level_with_the_date_tile(E2EBrowser browser) {
    runAsUser(browser, E2ETestData.EMPLOYEE_REVIEWED_SIGN, MONTH_VIEW, page -> {
      assertThat(page.locator("#daily-list .not-worked-badge").first()).isVisible();

      assertEquals(List.of(), page.evaluate(OFF_CENTRE));
    });
  }

}
