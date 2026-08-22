package org.tb.e2e.dailyreport;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

import com.microsoft.playwright.Page;
import java.time.LocalDate;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.tb.common.GlobalConstants;
import org.tb.common.test.FixedClock;
import org.tb.e2e.E2EBrowser;
import org.tb.e2e.E2ETestData;
import org.tb.e2e.PlaywrightE2ETestBase;

/**
 * The booking form remembers how the user enters their time (#844): either the mode of their last
 * booking, or a fixed personal default chosen in the settings.
 *
 * <p>Runs as the people lead: the entry mode is stored per user, and every other booking-form E2E
 * test books as {@code ema} while assuming the duration field is the visible one.
 */
@FixedClock(DurationInputModeE2ETest.NOW)
class DurationInputModeE2ETest extends PlaywrightE2ETestBase {

  /**
   * Set explicitly rather than relying on the base class: {@code @FixedClock} is not
   * {@code @Inherited}, so a subclass without its own annotation silently gets the extension's
   * default instead of the base class's value.
   */
  static final String NOW = "2026-06-25T10:15:30";

  private static final String EMPLOYEE = E2ETestData.EMPLOYEE_PV_SIGN;
  /** What {@link #NOW} makes "today"; the only date here that can carry a live booking. */
  private static final LocalDate TODAY = LocalDate.parse("2026-06-25");
  private static final LocalDate PAST = LocalDate.parse("2026-06-15");
  private static final LocalDate ANOTHER_PAST = LocalDate.parse("2026-06-16");
  private static final LocalDate THIRD_PAST = LocalDate.parse("2026-06-17");

  private static final String MODE_REMEMBER = "Letzte Eingabe merken";
  private static final String MODE_DURATION = "Immer Dauer";
  private static final String MODE_BEGIN_END = "Immer Beginn / Ende";

  @ParameterizedTest(name = "{0}")
  @MethodSource("org.tb.e2e.PlaywrightE2ETestBase#browsers")
  void a_fixed_personal_default_decides_the_entry_mode(E2EBrowser browser) {
    runAsUser(browser, EMPLOYEE, "/settings", page -> {

      // the work day starts before the fixed clock's "now", so today offers a live booking
      page.fill("#workDayStart", "08:00");
      page.click("button[type=submit]");
      page.waitForLoadState();

      // (1) "always begin / end" applies even on a day that has no live booking at all
      setDurationInputMode(page, MODE_BEGIN_END);
      openBookingForm(page, PAST);
      assertThat(page.locator("#durationModeField")).hasValue("beginEnd");
      assertThat(page.locator("#beginEndSection")).isVisible();
      assertThat(page.locator("#durationSection")).isHidden();

      // (2) "always duration" wins over the live booking's begin/end suggestion — this is the
      // complaint of #844: switching the mode back on every single booking
      setDurationInputMode(page, MODE_DURATION);
      openBookingForm(page, TODAY);
      assertThat(page.locator("#durationModeField")).hasValue("duration");
      assertThat(page.locator("#durationSection")).isVisible();
      assertThat(page.locator("#beginEndSection")).isHidden();
      // the suggestion itself is not lost: switching over by hand shows it prefilled (#851)
      page.click("#btnBeginEnd");
      assertThat(page.locator("#beginTimeInput")).hasValue("08:00");
    });
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("org.tb.e2e.PlaywrightE2ETestBase#browsers")
  void the_mode_of_the_last_booking_is_remembered(E2EBrowser browser) {
    runAsUser(browser, EMPLOYEE, "/settings", page -> {

      setDurationInputMode(page, MODE_REMEMBER);

      // book once with begin/end ...
      openBookingForm(page, PAST);
      page.click("#btnBeginEnd");
      page.fill("#beginTimeInput", "09:00");
      page.fill("#endTimeInput", "11:00");
      submit(page);

      // ... and the next booking form opens in that mode
      openBookingForm(page, ANOTHER_PAST);
      assertThat(page.locator("#durationModeField")).hasValue("beginEnd");
      assertThat(page.locator("#beginEndSection")).isVisible();

      // it follows the user back, too
      page.click("#btnDuration");
      page.fill("#durationTime", "02:00");
      submit(page);

      openBookingForm(page, THIRD_PAST);
      assertThat(page.locator("#durationModeField")).hasValue("duration");
      assertThat(page.locator("#durationSection")).isVisible();
      assertThat(page.locator("#beginEndSection")).isHidden();
    });
  }

  private void openBookingForm(Page page, LocalDate date) {
    page.navigate(urlWithLogin("/dailyreport/timereports/new?date=" + date, EMPLOYEE));
  }

  /**
   * Books on the training suborder: it is provisioned for every employee on login and, unlike
   * Urlaub, carries no entitlement of its own that the people lead would first have to be granted.
   */
  private void submit(Page page) {
    selectTomSelectOption(page, "suborderId", GlobalConstants.SUBRORDER_SIGN_TRAINING);
    page.click("#timereportMainForm button[type=submit]");
    page.waitForLoadState();
  }

  /**
   * Switches the personal default and verifies it stuck — a silently unauthenticated POST would
   * otherwise leave the test asserting against the wrong variant.
   */
  private void setDurationInputMode(Page page, String optionText) {
    page.navigate(urlWithLogin("/settings", EMPLOYEE));
    selectTomSelectOption(page, "durationInputMode", optionText);
    page.click("button[type=submit]");
    page.waitForLoadState();
    // the redirect after saving drops the dev-login query parameter, so navigate back explicitly
    page.navigate(urlWithLogin("/settings", EMPLOYEE));
    assertThat(page.locator("#durationInputMode ~ .ts-wrapper .ts-control")).containsText(optionText);
  }

}
