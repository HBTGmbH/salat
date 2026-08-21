package org.tb.e2e.dailyreport;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.microsoft.playwright.Page;
import java.time.LocalDate;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.tb.common.test.FixedClock;
import org.tb.e2e.E2EBrowser;
import org.tb.e2e.E2ETestData;
import org.tb.e2e.PlaywrightE2ETestBase;

/**
 * The progress line in the daily view: durations carry their unit, and the line is there from the
 * first visit on rather than only once something has been stored (#831).
 */
@FixedClock(DailyProgressUnitE2ETest.NOW)
class DailyProgressUnitE2ETest extends PlaywrightE2ETestBase {

  /** Explicit because {@code @FixedClock} is not inherited from the base class. */
  static final String NOW = "2026-07-15T14:00:00";

  private static final String EMPLOYEE = E2ETestData.EMPLOYEE_MA_SIGN;
  /** Untouched by the other E2E tests, and in the past so the live booking prefill stays out. */
  private static final LocalDate UNTOUCHED_DAY = LocalDate.parse("2026-07-13");
  private static final LocalDate BOOKED_DAY = LocalDate.parse("2026-07-14");

  @ParameterizedTest(name = "{0}")
  @MethodSource("org.tb.e2e.PlaywrightE2ETestBase#browsers")
  void a_day_without_anything_stored_still_shows_target_progress_and_quitting_time(E2EBrowser browser) {
    runAsUser(browser, EMPLOYEE, "/dailyreport/daily?mode=daily&date=" + UNTOUCHED_DAY, page -> {

      // no working day row, no booking — the target used to be read through that row, so the line
      // showed "von —" with an empty bar and no quitting time at all
      var progress = page.locator("#daily-progress");
      assertThat(progress).isVisible();
      assertThat(progress).containsText("0:00 h");
      assertThat(progress).containsText("8:00 h");
      assertThat(progress).containsText("Feierabend");
      assertThat(progress.locator(".progress-bar")).hasCount(1);
    });
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("org.tb.e2e.PlaywrightE2ETestBase#browsers")
  void the_progress_line_looks_the_same_after_an_out_of_band_refresh(E2EBrowser browser) {
    runAsUser(browser, EMPLOYEE, "/dailyreport/timereports/new?date=" + BOOKED_DAY, page -> {

      page.fill("#durationTime", "02:00");
      selectTomSelectOption(page, "suborderId", E2ETestData.SUBORDER_ALPHA_DEV_SIGN);
      page.click("#timereportMainForm button[type=submit]");
      page.waitForLoadState();

      page.navigate(urlWithLogin("/dailyreport/daily?mode=daily&date=" + BOOKED_DAY, EMPLOYEE));
      assertThat(page.locator("#daily-progress")).containsText("2:00 h");
      var initial = structureOf(page);

      // an inline duration edit answers with an out-of-band refresh of this very block, which used
      // to be a copy of the markup above and could drift from it unnoticed
      var wrap = page.locator("#daily-bookings-area .inline-edit-wrap:has(input[name='duration'])").first();
      wrap.locator(".inline-edit-display").click();
      wrap.locator(".inline-edit-input").fill("03:00");
      page.locator("h3").first().click();
      page.waitForTimeout(1500);

      assertThat(page.locator("#daily-progress")).containsText("3:00 h");
      assertEquals(initial, structureOf(page),
          "labels and units must be identical after the refresh; only the values may move");
    });
  }

  /** The rendered line with all clock and duration values masked out, so only the wording remains. */
  private String structureOf(Page page) {
    return page.locator("#daily-progress").innerText().replaceAll("\\d{1,2}:\\d{2}", "#");
  }

}
