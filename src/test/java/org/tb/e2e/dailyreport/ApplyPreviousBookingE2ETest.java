package org.tb.e2e.dailyreport;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import java.time.LocalDate;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.tb.e2e.E2EBrowser;
import org.tb.e2e.E2ETestData;
import org.tb.e2e.PlaywrightE2ETestBase;

/**
 * Taking over a booking of an earlier day (#1017). Whoever sits on the same task for several days
 * had to leaf back through the days to read order, text and ticket off the earlier booking, then
 * jump forward again and type them in once more.
 *
 * <p>What only a browser can show: that the offer really carries all four values across the day
 * boundary, and that one click turns it into a booking on the day in view without a page load —
 * the dropdown posts through HTMX and swaps the bookings area, which includes the dropdown itself.
 *
 * <p>Its own pair of days: the offer spans <em>every</em> suborder of the fortnight before the day
 * in view, so a day another test class books on would push its bookings into this dropdown and,
 * with only five entries on offer, could crowd this test's entry out of it. The fortnight before
 * {@link #DAY} is free of other classes' days for exactly that reason.
 */
class ApplyPreviousBookingE2ETest extends PlaywrightE2ETestBase {

  private static final String EMPLOYEE = E2ETestData.EMPLOYEE_MA_SIGN;
  private static final LocalDate EARLIER_DAY = LocalDate.parse("2026-07-28");
  private static final LocalDate DAY = LocalDate.parse("2026-07-29");
  private static final String COMMENT = "E2E-Uebernahme aus dem Vortag";
  private static final String TICKET = "UEBERNAHME-1";

  @ParameterizedTest(name = "{0}")
  @MethodSource("org.tb.e2e.PlaywrightE2ETestBase#browsers")
  void a_booking_of_an_earlier_day_is_offered_and_one_click_books_it_again(E2EBrowser browser) {
    runAsUser(browser, EMPLOYEE, "/dailyreport/timereports/new?date=" + EARLIER_DAY, page -> {
      bookOnTheEarlierDay(page);

      page.navigate(urlWithLogin("/dailyreport/daily?mode=daily&date=" + DAY, EMPLOYEE));
      // counted rather than asserted away: a second browser in the same run books this day again,
      // and what the click has to prove is that it adds one, not that the day was empty before
      Locator rows = bookedRows(page);
      int before = rows.count();

      // the offer names all four values the new booking is made of
      page.getByText("Vorherige übernehmen").click();
      Locator offer = offerWithTheComment(page);
      assertThat(offer).containsText(COMMENT);
      assertThat(offer).containsText(E2ETestData.SUBORDER_ALPHA_DEV_SIGN);
      assertThat(offer).containsText(TICKET);
      assertThat(offer).containsText("2:15");

      offer.locator("button[type=submit]").click();

      // and one click is all it takes - no page load, no form
      assertThat(rows).hasCount(before + 1);
      assertThat(rows.last()).containsText(TICKET);
      assertThat(rows.last()).containsText("2:15");
    });
  }

  private void bookOnTheEarlierDay(Page page) {
    page.fill("#durationTime", "02:15");
    selectTomSelectOption(page, "suborderId", E2ETestData.SUBORDER_ALPHA_DEV_SIGN);
    page.locator("#ticketReference ~ .ts-wrapper .ts-control").click();
    page.locator("#ticketReference-ts-control").pressSequentially(TICKET);
    // leaving the field is what turns the typed text into the value
    page.locator("#commentField").click();
    page.fill("#commentField", COMMENT);
    page.locator("form button[type=submit]").first().click();
    page.waitForLoadState();
  }

  private Locator bookedRows(Page page) {
    return page.locator("#daily-bookings-area tbody tr")
        .filter(new Locator.FilterOptions().setHasText(COMMENT));
  }

  private Locator offerWithTheComment(Page page) {
    return page.locator("#daily-bookings-area .dropdown-menu li")
        .filter(new Locator.FilterOptions().setHasText(COMMENT))
        .first();
  }

}
