package de.hbt.salat.e2e.dailyreport;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

import com.microsoft.playwright.Page;
import java.time.LocalDate;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import de.hbt.salat.common.test.FixedClock;
import de.hbt.salat.e2e.E2EBrowser;
import de.hbt.salat.e2e.E2ETestData;
import de.hbt.salat.e2e.PlaywrightE2ETestBase;

/**
 * Next to the duration the booking form shows where the booking ends (#1263): start of the day plus
 * break plus what is booked plus the duration being entered — the quitting time the daily view shows
 * after saving.
 *
 * <p>November 2026 is used because the E2E database is shared and never cleaned (#846), and the
 * two days are booked by no other test. One test walks the whole day, so that its bookings are the
 * only ones the asserted times rest on.
 */
@FixedClock(BookingEndHintE2ETest.NOW)
class BookingEndHintE2ETest extends PlaywrightE2ETestBase {

  /** After both days, so that neither is today, where the form starts with the live booking. */
  static final String NOW = "2026-11-12T18:00:00";

  private static final String EMPLOYEE = E2ETestData.EMPLOYEE_MA_SIGN;
  private static final String WORKINGDAY_SAVE = "/dailyreport/daily/workingday";
  private static final LocalDate DAY = LocalDate.parse("2026-11-10");
  private static final LocalDate NEXT_DAY = LocalDate.parse("2026-11-11");

  @ParameterizedTest(name = "{0}")
  @MethodSource("de.hbt.salat.e2e.PlaywrightE2ETestBase#browsers")
  void the_form_shows_where_the_booking_ends(E2EBrowser browser) {
    runAsUser(browser, EMPLOYEE, "/dailyreport/daily?mode=daily&date=" + DAY, page -> {
      setStart(page, DAY, "07:00");
      setStart(page, NEXT_DAY, "08:00");

      // the first booking of the day ends three hours after the start
      openBookingForm(page, DAY);
      assertThat(page.locator("#bookingEndHint")).isHidden();
      page.fill("#durationTime", "03:00");
      assertThat(page.locator("#bookingEndTime")).hasText("10:00");
      selectTomSelectOption(page, "suborderId", E2ETestData.SUBORDER_ALPHA_DEV_SIGN);
      page.click("#timereportMainForm button[type=submit]");
      page.waitForLoadState();

      // the next one continues from there and follows every entry
      openBookingForm(page, DAY);
      page.fill("#durationTime", "1:15");
      assertThat(page.locator("#bookingEndHint")).isVisible();
      assertThat(page.locator("#bookingEndTime")).hasText("11:15");

      // standby is no working time and moves nothing
      selectTomSelectOption(page, "suborderId", E2ETestData.SUBORDER_STANDBY_SIGN);
      assertThat(page.locator("#bookingEndHint")).isHidden();
      selectTomSelectOption(page, "suborderId", E2ETestData.SUBORDER_ALPHA_DEV_SIGN);
      assertThat(page.locator("#bookingEndTime")).hasText("11:15");

      // another day brings its own start along
      afterResponse(page, "/dailyreport/timereports/refresh-orders",
          () -> page.fill("#referenceday", NEXT_DAY.toString()));
      assertThat(page.locator("#bookingEndTime")).hasText("09:15");

      // past midnight there is no time of day to show
      page.fill("#durationTime", "17:00");
      assertThat(page.locator("#bookingEndHint")).isHidden();

      // an edited booking counts once, with the duration in the form
      page.navigate(urlWithLogin("/dailyreport/daily?mode=daily&date=" + DAY, EMPLOYEE));
      page.locator("a[href*='/dailyreport/timereports/'][href*='/edit']").first().click();
      page.waitForLoadState();
      assertThat(page.locator("#bookingEndTime")).hasText("10:00");
    });
  }

  private void setStart(Page page, LocalDate date, String start) {
    page.navigate(urlWithLogin("/dailyreport/daily?mode=daily&date=" + date, EMPLOYEE));
    page.fill("#startTime", start);
    afterResponse(page, WORKINGDAY_SAVE, () -> page.locator("h3").first().click());
  }

  /** In duration mode whatever the person prefers, because that is where the hint stands. */
  private void openBookingForm(Page page, LocalDate date) {
    page.navigate(urlWithLogin("/dailyreport/timereports/new?date=" + date, EMPLOYEE));
    page.click("#btnDuration");
  }
}
