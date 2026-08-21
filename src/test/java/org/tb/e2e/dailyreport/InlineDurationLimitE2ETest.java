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
 * The inline edit of the duration in the daily view used to bypass the 24 hour limit of the
 * booking form, so 55:30 could be stored for a single booking (#825). Both a duration beyond the
 * day and an unreadable input must now be rejected with an error toast, leaving the booking
 * untouched.
 */
class InlineDurationLimitE2ETest extends PlaywrightE2ETestBase {

  private static final LocalDate BOOKING_DATE = LocalDate.parse("2026-06-26");
  private static final String COMMENT = "E2E-Dauergrenze";

  @ParameterizedTest(name = "{0}")
  @MethodSource("org.tb.e2e.PlaywrightE2ETestBase#browsers")
  void inline_editing_the_duration_beyond_a_day_is_rejected(E2EBrowser browser) {
    runAsUser(browser, E2ETestData.EMPLOYEE_MA_SIGN, newBookingPath(), page -> {
      bookTwoHours(page);
      openDailyView(page);

      editDurationInline(page, "5530");

      assertThat(page.locator(".toast-error")).containsText("24:00");
      assertThat(durationBadge(page)).hasText("2:00");
    });
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("org.tb.e2e.PlaywrightE2ETestBase#browsers")
  void inline_editing_the_duration_with_an_unreadable_value_is_rejected(E2EBrowser browser) {
    runAsUser(browser, E2ETestData.EMPLOYEE_MA_SIGN, newBookingPath(), page -> {
      bookTwoHours(page);
      openDailyView(page);

      // an unreadable value used to be discarded silently, keeping the stored duration
      editDurationInline(page, "abc");

      assertThat(page.locator(".toast-error")).isVisible();
      assertThat(durationBadge(page)).hasText("2:00");
    });
  }

  private String newBookingPath() {
    return "/dailyreport/timereports/new?date=" + BOOKING_DATE;
  }

  private void bookTwoHours(Page page) {
    page.fill("#durationTime", "02:00");
    selectTomSelectOption(page, "suborderId", E2ETestData.SUBORDER_ALPHA_DEV_SIGN);
    page.fill("#commentField", COMMENT);
    page.click("button[type=submit]");
    page.waitForLoadState();
  }

  private void openDailyView(Page page) {
    page.navigate(urlWithLogin("/dailyreport/daily?mode=daily&date=" + BOOKING_DATE,
        E2ETestData.EMPLOYEE_MA_SIGN));
  }

  /** The row of the booking this test created - the E2E database is shared (#846). */
  private Locator bookingRow(Page page) {
    return page.locator("#daily-bookings-area tr")
        .filter(new Locator.FilterOptions().setHasText(COMMENT))
        .first();
  }

  /** The duration is the only inline-editable value rendered as a badge. */
  private Locator durationBadge(Page page) {
    return bookingRow(page).locator("span.inline-edit-display");
  }

  private void editDurationInline(Page page, String value) {
    durationBadge(page).click();
    Locator input = bookingRow(page).locator("input[name=duration]");
    input.fill(value);
    // Enter blurs the field, which is what triggers the hx-post
    input.press("Enter");
    page.waitForLoadState();
  }

}
