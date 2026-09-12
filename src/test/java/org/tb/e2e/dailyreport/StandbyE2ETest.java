package org.tb.e2e.dailyreport;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import java.time.LocalDate;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.tb.common.test.FixedClock;
import org.tb.e2e.E2EBrowser;
import org.tb.e2e.E2ETestData;
import org.tb.e2e.PlaywrightE2ETestBase;

/**
 * Standby is booked like any other time but is no working time (#463): the daily view marks it and
 * leaves it out of the day's total, and the matrix lists it below the sum row, with its own row
 * total but with no share in any sum.
 *
 * <p>August 2026 is used because the E2E database is shared and never cleaned: the month is
 * untouched apart from {@code BreakAsDurationE2ETest} on the 10th, so the sums asserted here are
 * this test's own.
 */
@FixedClock(StandbyE2ETest.NOW)
class StandbyE2ETest extends PlaywrightE2ETestBase {

  /** Explicit because {@code @FixedClock} is not inherited from the base class. */
  static final String NOW = "2026-08-12T18:00:00";

  private static final String EMPLOYEE = E2ETestData.EMPLOYEE_MA_SIGN;
  /**
   * One day per test: the shared database keeps every booking, so two tests on one day add up.
   * Both are off the fixed today, where the booking form starts in live mode and offers no
   * duration field.
   */
  private static final LocalDate DAILY_VIEW_DAY = LocalDate.parse("2026-08-11");
  private static final LocalDate MATRIX_DAY = LocalDate.parse("2026-08-13");

  @ParameterizedTest(name = "{0}")
  @MethodSource("org.tb.e2e.PlaywrightE2ETestBase#browsers")
  void the_daily_view_marks_standby_and_leaves_it_out_of_the_day_total(E2EBrowser browser) {
    runAsUser(browser, EMPLOYEE, "/dailyreport/daily?mode=daily&date=" + DAILY_VIEW_DAY, page -> {
      book(page, DAILY_VIEW_DAY, E2ETestData.SUBORDER_ALPHA_DEV_SIGN, "03:00");
      book(page, DAILY_VIEW_DAY, E2ETestData.SUBORDER_STANDBY_SIGN, "04:00");

      page.navigate(urlWithLogin("/dailyreport/daily?mode=daily&date=" + DAILY_VIEW_DAY, EMPLOYEE));

      // both bookings are listed, and only the standby one carries the marker
      Locator standbyRow = page.locator("#daily-bookings-area tr")
          .filter(new Locator.FilterOptions().setHasText(E2ETestData.SUBORDER_STANDBY_SIGN));
      assertThat(standbyRow.locator("[data-standby-badge]")).hasCount(1);
      Locator workedRow = page.locator("#daily-bookings-area tr")
          .filter(new Locator.FilterOptions().setHasText(E2ETestData.SUBORDER_ALPHA_DEV_SIGN));
      assertThat(workedRow.locator("[data-standby-badge]")).hasCount(0);

      // ... but only the three worked hours are counted
      assertThat(page.locator("#daily-progress")).containsText("3:00 h");
    });
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("org.tb.e2e.PlaywrightE2ETestBase#browsers")
  void the_matrix_shows_standby_below_the_sum_row_without_counting_it(E2EBrowser browser) {
    runAsUser(browser, EMPLOYEE, "/dailyreport/daily?mode=daily&date=" + MATRIX_DAY, page -> {
      book(page, MATRIX_DAY, E2ETestData.SUBORDER_ALPHA_DEV_SIGN, "03:00");
      book(page, MATRIX_DAY, E2ETestData.SUBORDER_STANDBY_SIGN, "04:00");

      page.navigate(urlWithLogin(
          "/dailyreport/matrix?month=" + MATRIX_DAY.getMonthValue() + "&year=" + MATRIX_DAY.getYear(), EMPLOYEE));

      // the worked order stands above the sum row, the standby order below it
      assertThat(page.locator("#matrix tbody")).containsText(E2ETestData.SUBORDER_ALPHA_DEV_SIGN);
      assertThat(page.locator("#matrix tbody")).not().containsText(E2ETestData.SUBORDER_STANDBY_SIGN);
      Locator standbyRow = page.locator("#matrix tfoot tr")
          .filter(new Locator.FilterOptions().setHasText(E2ETestData.SUBORDER_STANDBY_SIGN));
      assertThat(standbyRow).hasCount(1);

      assertThat(standbyRow.locator("[data-standby-badge]")).hasCount(1);
      // it keeps its own cells - the row total spans the whole month, so the cell of the day this
      // test booked is what can be asserted on a shared database
      assertThat(standbyRow.locator("td").nth(MATRIX_DAY.getDayOfMonth())).containsText("4:00");
      // ... while neither the day of the booking nor the month total knows about those four hours
      assertThat(dayCellOfSumRow(page)).hasText("3:00");
    });
  }

  /** The cell of the booked day in the GESAMT row — the first footer row. */
  private Locator dayCellOfSumRow(Page page) {
    return page.locator("#matrix tfoot tr").first().locator("td").nth(MATRIX_DAY.getDayOfMonth());
  }

  private void book(Page page, LocalDate date, String suborderSign, String duration) {
    page.navigate(urlWithLogin("/dailyreport/timereports/new?date=" + date, EMPLOYEE));
    page.fill("#durationTime", duration);
    selectTomSelectOption(page, "suborderId", suborderSign);
    page.click("#timereportMainForm button[type=submit]");
    page.waitForLoadState();
  }

}
