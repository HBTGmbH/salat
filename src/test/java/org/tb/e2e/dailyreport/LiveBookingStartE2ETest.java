package org.tb.e2e.dailyreport;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

import com.microsoft.playwright.Page;
import java.time.LocalDate;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.tb.common.test.FixedClock;
import org.tb.e2e.E2EBrowser;
import org.tb.e2e.E2ETestData;
import org.tb.e2e.PlaywrightE2ETestBase;

/**
 * The booking form prefills begin/end from the start of the working day (#851).
 *
 * <p>Walks the whole lifecycle of one day in one test on purpose: the interesting case is the state
 * <em>before</em> a working day row exists, and a second test could not rely on that state still
 * being there — the E2E tests share one database (#846).
 *
 * <p>Uses the backoffice employee because the other dailyreport tests book as {@code ema} on the
 * fixed clock's today, which would create the very row this test needs to be absent.
 */
@FixedClock(LiveBookingStartE2ETest.NOW)
class LiveBookingStartE2ETest extends PlaywrightE2ETestBase {

  /**
   * Set explicitly rather than relying on the base class: {@code @FixedClock} is not
   * {@code @Inherited}, so a subclass without its own annotation silently gets the extension's
   * default instead of the base class's value.
   */
  static final String NOW = "2026-06-25T10:15:30";

  private static final String EMPLOYEE = E2ETestData.EMPLOYEE_BO_SIGN;
  /** What {@link #NOW} makes "today"; no other E2E test books on this date. */
  private static final LocalDate TODAY = LocalDate.parse("2026-06-25");

  @ParameterizedTest(name = "{0}")
  @MethodSource("org.tb.e2e.PlaywrightE2ETestBase#browsers")
  void the_form_prefills_begin_from_the_start_of_the_working_day(E2EBrowser browser) {
    runAsUser(browser, EMPLOYEE, "/settings", page -> {

      // the default work day start is 09:00, which equals the fixed clock's "now" — no elapsed time
      // to book, so move it earlier to get a meaningful live booking
      page.fill("#workDayStart", "08:00");
      page.click("button[type=submit]");
      page.waitForLoadState();

      // (1) first booking of the day: no working day row yet, so the configured start applies.
      // The daily view has always shown it; the form used to ignore it and stay in duration mode.
      page.navigate(urlWithLogin("/dailyreport/daily?mode=daily&date=" + TODAY, EMPLOYEE));
      assertThat(page.locator("#startTime")).hasValue("08:00");

      openBookingForm(page, TODAY);
      assertThat(page.locator("#durationModeField")).hasValue("beginEnd");
      assertThat(page.locator("#beginEndSection")).isVisible();
      assertThat(page.locator("#beginTimeInput")).hasValue("08:00");

      // (2) a stored start wins over the setting
      page.navigate(urlWithLogin("/dailyreport/daily?mode=daily&date=" + TODAY, EMPLOYEE));
      page.fill("#startTime", "07:00");
      page.locator("h3").first().click();
      page.waitForTimeout(1200);

      openBookingForm(page, TODAY);
      assertThat(page.locator("#beginTimeInput")).hasValue("07:00");

      // (3) a day marked as not worked has no starting point
      page.navigate(urlWithLogin("/dailyreport/daily?mode=daily&date=" + TODAY, EMPLOYEE));
      page.locator("#notWorked").check();
      page.waitForTimeout(1200);

      openBookingForm(page, TODAY);
      assertThat(page.locator("#durationModeField")).hasValue("duration");
      assertThat(page.locator("#beginEndSection")).isHidden();

      // (4) the prefill is for today only — a past day stays in duration mode
      page.navigate(urlWithLogin("/dailyreport/daily?mode=daily&date=" + TODAY, EMPLOYEE));
      page.locator("#notWorked").uncheck();
      page.waitForTimeout(1200);

      openBookingForm(page, TODAY.minusDays(3));
      assertThat(page.locator("#durationModeField")).hasValue("duration");
      assertThat(page.locator("#beginEndSection")).isHidden();
    });
  }

  private void openBookingForm(Page page, LocalDate date) {
    page.navigate(urlWithLogin("/dailyreport/timereports/new?date=" + date, EMPLOYEE));
  }

}
