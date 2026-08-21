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
 * The break is a duration, not a time of day (#833).
 *
 * <p>It used to be read with time-of-day rules, where two digits are an hour: typing {@code 30} for
 * half an hour meant "30 o'clock", which is invalid, so the server silently stored no break at all.
 *
 * <p>Uses the backoffice employee and a day no other test touches, because the assertions depend on
 * the stored working day of that day.
 */
@FixedClock(BreakAsDurationE2ETest.NOW)
class BreakAsDurationE2ETest extends PlaywrightE2ETestBase {

  /** Explicit because {@code @FixedClock} is not inherited from the base class. */
  static final String NOW = "2026-08-10T14:00:00";

  private static final String EMPLOYEE = E2ETestData.EMPLOYEE_BO_SIGN;
  private static final LocalDate DAY = LocalDate.parse("2026-08-10");

  @ParameterizedTest(name = "{0}")
  @MethodSource("org.tb.e2e.PlaywrightE2ETestBase#browsers")
  void the_break_accepts_duration_input(E2EBrowser browser) {
    runAsUser(browser, EMPLOYEE, "/settings", page -> {

      setBeta(page, true);

      // two digits are minutes, as in every other duration field
      assertBreakInputStores(page, "30", "00:30");
      // and the other duration formats work too
      assertBreakInputStores(page, "90m", "01:30");
      assertBreakInputStores(page, "1,5", "01:30");
      assertBreakInputStores(page, "0:45", "00:45");
      // clearing the field means no break
      assertBreakInputStores(page, "", "00:00");
    });
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("org.tb.e2e.PlaywrightE2ETestBase#browsers")
  void the_classic_break_field_keeps_the_native_picker(E2EBrowser browser) {
    runAsUser(browser, EMPLOYEE, "/settings", page -> {

      setBeta(page, false);
      page.navigate(urlWithLogin("/dailyreport/daily?mode=daily&date=" + DAY, EMPLOYEE));

      // without the beta the browser control stays, and the duration mask must not interfere with it
      assertThat(page.locator("#breakTime")).hasAttribute("type", "time");
      page.fill("#breakTime", "00:45");
      page.locator("h3").first().click();
      page.waitForTimeout(1200);
      page.navigate(urlWithLogin("/dailyreport/daily?mode=daily&date=" + DAY, EMPLOYEE));
      assertThat(page.locator("#breakTime")).hasValue("00:45");
    });
  }

  private void assertBreakInputStores(Page page, String typed, String stored) {
    page.navigate(urlWithLogin("/dailyreport/daily?mode=daily&date=" + DAY, EMPLOYEE));
    page.fill("#breakTime", "");
    if (!typed.isEmpty()) {
      page.locator("#breakTime").click();
      page.locator("#breakTime").pressSequentially(typed);
    }
    page.locator("h3").first().click();
    page.waitForTimeout(1200);

    page.navigate(urlWithLogin("/dailyreport/daily?mode=daily&date=" + DAY, EMPLOYEE));
    assertThat(page.locator("#breakTime")).hasValue(stored);
  }

  private void setBeta(Page page, boolean enabled) {
    page.navigate(urlWithLogin("/settings", EMPLOYEE));
    var checkbox = page.locator("input[name='betaFeatures'][value='timeinput']");
    if (enabled) {
      checkbox.check();
    } else {
      checkbox.uncheck();
    }
    page.click("button[type=submit]");
    page.waitForLoadState();
  }

}
