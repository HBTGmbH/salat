package org.tb.e2e.dailyreport;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

import java.time.LocalDate;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.tb.e2e.E2EBrowser;
import org.tb.e2e.E2ETestData;
import org.tb.e2e.PlaywrightE2ETestBase;

/**
 * Verifies the matrix overview renders for an employee and reflects their own bookings.
 */
class MatrixE2ETest extends PlaywrightE2ETestBase {

  private static final LocalDate BOOKING_DATE = LocalDate.parse("2026-06-17");

  @ParameterizedTest(name = "{0}")
  @EnumSource(E2EBrowser.class)
  void matrix_shows_a_booking_made_by_the_employee(E2EBrowser browser) {
    runAsUser(browser, E2ETestData.EMPLOYEE_MA_SIGN,
        "/dailyreport/timereports/new?date=" + BOOKING_DATE, page -> {

      page.fill("#durationTime", "03:00");
      selectTomSelectOption(page, "suborderId", E2ETestData.SUBORDER_GLOBEX_CONSULT_SIGN);
      page.fill("#commentField", "E2E-Matrix-Testbuchung");
      page.click("button[type=submit]");
      page.waitForLoadState();

      page.navigate(urlWithLogin(
          "/dailyreport/matrix?month=" + BOOKING_DATE.getMonthValue() + "&year=" + BOOKING_DATE.getYear(),
          E2ETestData.EMPLOYEE_MA_SIGN));

      assertThat(page.locator("#matrix")).isVisible();
      assertThat(page.locator("#matrix")).containsText(E2ETestData.SUBORDER_GLOBEX_CONSULT_SIGN);
    });
  }

  @ParameterizedTest(name = "{0}")
  @EnumSource(E2EBrowser.class)
  void people_lead_can_open_the_matrix(E2EBrowser browser) {
    runAsUser(browser, E2ETestData.EMPLOYEE_PV_SIGN, "/dailyreport/matrix", page ->
        assertThat(page.locator("#matrix")).isVisible());
  }

}
