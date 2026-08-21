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
 * The daily view used to show the contract's daily working time as the target of every day, so a
 * Saturday claimed a target of 8 hours and a progress bar that can never fill (#857). On a day
 * without a target the booked value and the quitting time remain - both follow from what has been
 * booked rather than from a target.
 */
@FixedClock(DailyTargetOnNonWorkingDaysE2ETest.NOW)
class DailyTargetOnNonWorkingDaysE2ETest extends PlaywrightE2ETestBase {

  /** Explicit because {@code @FixedClock} is not inherited from the base class. */
  static final String NOW = "2026-10-20T14:00:00";

  private static final String EMPLOYEE = E2ETestData.EMPLOYEE_MA_SIGN;
  /** October 2026 is the month the seeded public holidays live in, see E2ETestData. */
  private static final LocalDate SATURDAY = LocalDate.parse("2026-10-17");
  private static final LocalDate WEEKDAY = LocalDate.parse("2026-10-06");
  private static final String COMMENT = "E2E-Wochenendarbeit";

  @ParameterizedTest(name = "{0}")
  @MethodSource("org.tb.e2e.PlaywrightE2ETestBase#browsers")
  void a_saturday_has_no_target_and_no_progress_bar(E2EBrowser browser) {
    runAsUser(browser, EMPLOYEE, dailyView(SATURDAY), page -> assertNoTargetShown(page));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("org.tb.e2e.PlaywrightE2ETestBase#browsers")
  void a_public_holiday_on_a_weekday_has_no_target_either(E2EBrowser browser) {
    runAsUser(browser, EMPLOYEE, dailyView(E2ETestData.HOLIDAY_ON_WEEKDAY),
        page -> assertNoTargetShown(page));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("org.tb.e2e.PlaywrightE2ETestBase#browsers")
  void a_public_holiday_on_a_weekend_behaves_like_a_weekend(E2EBrowser browser) {
    // the holiday must not be subtracted a second time on a day that has no target anyway
    runAsUser(browser, EMPLOYEE, dailyView(E2ETestData.HOLIDAY_ON_WEEKEND),
        page -> assertNoTargetShown(page));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("org.tb.e2e.PlaywrightE2ETestBase#browsers")
  void a_normal_weekday_still_shows_target_bar_and_quitting_time(E2EBrowser browser) {
    runAsUser(browser, EMPLOYEE, dailyView(WEEKDAY), page -> {
      var progress = progress(page);
      assertThat(progress).containsText("8:00 h");
      assertThat(progress).containsText("Feierabend");
      assertThat(progress.locator(".progress-bar")).hasCount(1);
    });
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("org.tb.e2e.PlaywrightE2ETestBase#browsers")
  void a_booking_on_a_saturday_stays_visible_after_an_out_of_band_refresh(E2EBrowser browser) {
    runAsUser(browser, EMPLOYEE, "/dailyreport/timereports/new?date=" + SATURDAY, page -> {
      page.fill("#durationTime", "02:00");
      selectTomSelectOption(page, "suborderId", E2ETestData.SUBORDER_ALPHA_DEV_SIGN);
      page.fill("#commentField", COMMENT);
      page.click("#timereportMainForm button[type=submit]");
      page.waitForLoadState();

      page.navigate(urlWithLogin(dailyView(SATURDAY), EMPLOYEE));
      assertThat(progress(page)).containsText("2:00 h");
      assertNoTargetShown(page);

      // the inline duration edit answers with an out-of-band refresh of the progress block, which
      // must arrive without target, bar and quitting time just like the initial render
      editDurationInline(page, "03:00");

      assertThat(progress(page)).containsText("3:00 h");
      assertNoTargetShown(page);
    });
  }

  /**
   * The booked value and the quitting time stay, the target is gone - and the progress track stays
   * as an empty hint so the block keeps the height it has on a working day.
   */
  private void assertNoTargetShown(Page page) {
    var progress = progress(page);
    assertThat(progress).isVisible();
    assertThat(progress).containsText("Gebucht");
    assertThat(progress).not().containsText("von");
    assertThat(progress).containsText("Feierabend");
    assertThat(progress.locator(".progress")).hasCount(1);
    assertThat(progress.locator(".progress-bar")).hasCount(0);
  }

  private Locator progress(Page page) {
    return page.locator("#daily-progress");
  }

  private String dailyView(LocalDate date) {
    return "/dailyreport/daily?mode=daily&date=" + date;
  }

  /** The row of the booking this test created - the E2E database is shared (#846). */
  private void editDurationInline(Page page, String value) {
    Locator row = page.locator("#daily-bookings-area tr")
        .filter(new Locator.FilterOptions().setHasText(COMMENT))
        .first();
    row.locator("span.inline-edit-display").click();
    Locator input = row.locator("input[name=duration]");
    input.fill(value);
    // Enter blurs the field, which is what triggers the hx-post
    input.press("Enter");
    page.waitForLoadState();
    page.waitForTimeout(1500);
  }

}
