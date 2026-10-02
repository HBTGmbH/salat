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

  /** The day no other test touches (see above), instead of the base class's day. */
  static final String NOW = "2026-08-10T14:00:00";

  private static final String EMPLOYEE = E2ETestData.EMPLOYEE_BO_SIGN;
  private static final LocalDate DAY = LocalDate.parse("2026-08-10");
  private static final String WORKINGDAY_SAVE = "/dailyreport/daily/workingday";

  @ParameterizedTest(name = "{0}")
  @MethodSource("de.hbt.salat.e2e.PlaywrightE2ETestBase#browsers")
  void the_break_accepts_duration_input(E2EBrowser browser) {
    runAsUser(browser, EMPLOYEE, "/dailyreport/daily?mode=daily&date=" + DAY, page -> {

      // two digits are minutes, as in every other duration field
      assertBreakInputStores(page, "30", "00:30");
      // and the other duration formats work too. Each value differs from the one before: an
      // unchanged value is not saved at all, so it would prove nothing
      assertBreakInputStores(page, "90m", "01:30");
      assertBreakInputStores(page, "0:45", "00:45");
      assertBreakInputStores(page, "1,5", "01:30");
      // clearing the field means no break
      assertBreakInputStores(page, "", "00:00");
    });
  }

  private void assertBreakInputStores(Page page, String typed, String stored) {
    page.navigate(urlWithLogin("/dailyreport/daily?mode=daily&date=" + DAY, EMPLOYEE));
    page.fill("#breakTime", "");
    if (!typed.isEmpty()) {
      page.locator("#breakTime").click();
      page.locator("#breakTime").pressSequentially(typed);
    }
    afterResponse(page, WORKINGDAY_SAVE, () -> page.locator("h3").first().click());

    page.navigate(urlWithLogin("/dailyreport/daily?mode=daily&date=" + DAY, EMPLOYEE));
    assertThat(page.locator("#breakTime")).hasValue(stored);
  }

}
