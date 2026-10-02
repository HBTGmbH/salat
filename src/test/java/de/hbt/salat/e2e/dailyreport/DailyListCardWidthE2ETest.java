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
 * The booking cards of the month view fit their cell on a phone (#1267). A card used to keep its
 * minimum width and grow with its longest line, so a long order line pushed it past the right edge of
 * the cell and the badge with the hours was cut off. On a wide screen the cards stay as they were.
 */
class DailyListCardWidthE2ETest extends PlaywrightE2ETestBase {

  /** September 2026 of this person holds bookings with long lines and a comment over several lines. */
  private static final String MONTH_VIEW = "/dailyreport/daily?mode=list&fMonth=9&fYear=2026";

  /**
   * What sticks out, per card: past the content box of its cell, past the visible edge of the list
   * (the cell itself grows and the table scrolls sideways instead), or the hours badge past the card.
   * Empty when everything fits.
   */
  private static final String OVERFLOWS = """
      () => [...document.querySelectorAll('#daily-list [id^="tr-card-"]')].flatMap(card => {
        const cell = card.closest('td');
        const visible = card.closest('.table-responsive').getBoundingClientRect().right;
        const cellRight = cell.getBoundingClientRect().right - parseFloat(getComputedStyle(cell).paddingRight);
        const cardRight = card.getBoundingClientRect().right;
        const badge = card.querySelector('.badge');
        const found = [];
        if (cardRight > cellRight + 0.5) found.push(card.id + ' past its cell by ' + (cardRight - cellRight) + 'px');
        if (cardRight > visible + 0.5) found.push(card.id + ' past the list by ' + (cardRight - visible) + 'px');
        if (badge && badge.getBoundingClientRect().right > Math.min(cardRight, visible) + 0.5) {
          found.push(card.id + ': hours cut off');
        }
        return found;
      })""";

  @ParameterizedTest(name = "{0}")
  @MethodSource("de.hbt.salat.e2e.PlaywrightE2ETestBase#browsers")
  void on_a_narrow_phone_no_card_sticks_out_of_the_list(E2EBrowser browser) {
    assertFitsAt(browser, 360);
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("de.hbt.salat.e2e.PlaywrightE2ETestBase#browsers")
  void on_a_common_phone_no_card_sticks_out_of_the_list(E2EBrowser browser) {
    assertFitsAt(browser, 390);
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("de.hbt.salat.e2e.PlaywrightE2ETestBase#browsers")
  void on_a_wide_screen_the_cards_keep_their_width(E2EBrowser browser) {
    runAsUser(browser, E2ETestData.EMPLOYEE_REVIEWED_SIGN, MONTH_VIEW, page -> {
      page.setViewportSize(1440, 900);
      assertThat(cards(page).first()).isVisible();

      @SuppressWarnings("unchecked")
      var widths = (List<Number>) page.evaluate(
          "() => [...document.querySelectorAll('#daily-list [id^=\"tr-card-\"]')].map(c => c.getBoundingClientRect().width)");
      widths.forEach(width -> {
        var w = width.doubleValue();
        if (w < 199.5 || w > 320.5) {
          throw new AssertionError("card width " + w + " outside 200-320 px");
        }
      });
    });
  }

  private void assertFitsAt(E2EBrowser browser, int width) {
    runAsUser(browser, E2ETestData.EMPLOYEE_REVIEWED_SIGN, MONTH_VIEW, page -> {
      page.setViewportSize(width, 800);
      assertThat(cards(page).first()).isVisible();

      assertEquals(List.of(), page.evaluate(OVERFLOWS));
    });
  }

  private static Locator cards(Page page) {
    return page.locator("#daily-list [id^=\"tr-card-\"]");
  }

}
