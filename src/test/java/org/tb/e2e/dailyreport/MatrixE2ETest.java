package org.tb.e2e.dailyreport;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.microsoft.playwright.Locator;
import java.time.LocalDate;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.tb.e2e.E2EBrowser;
import org.tb.e2e.E2ETestData;
import org.tb.e2e.PlaywrightE2ETestBase;

/**
 * Verifies the matrix overview renders for an employee and reflects their own bookings.
 */
class MatrixE2ETest extends PlaywrightE2ETestBase {

  private static final LocalDate BOOKING_DATE = LocalDate.parse("2026-06-17");
  private static final String LONG_COMMENT =
      "Sehr ausfuehrliche Beschreibung der Taetigkeit, die deutlich breiter ist als das Popover "
          + "und daher umgebrochen werden muss, damit nichts abgeschnitten wird.";

  @ParameterizedTest(name = "{0}")
  @MethodSource("org.tb.e2e.PlaywrightE2ETestBase#browsers")
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

  /**
   * The detail lines used to be {@code <pre>} blocks, which Tabler styles with
   * {@code overflow: auto} and no line wrapping, so long task descriptions were cut off behind a
   * scrollbar that cannot be reached without dismissing the hover popover (#818).
   */
  @ParameterizedTest(name = "{0}")
  @MethodSource("org.tb.e2e.PlaywrightE2ETestBase#browsers")
  void cell_detail_popover_shows_the_complete_task_description(E2EBrowser browser) {
    runAsUser(browser, E2ETestData.EMPLOYEE_MA_SIGN,
        "/dailyreport/timereports/new?date=" + BOOKING_DATE, page -> {

      page.fill("#durationTime", "03:00");
      selectTomSelectOption(page, "suborderId", E2ETestData.SUBORDER_GLOBEX_CONSULT_SIGN);
      page.fill("#commentField", LONG_COMMENT);
      page.click("button[type=submit]");
      page.waitForLoadState();

      page.navigate(urlWithLogin(
          "/dailyreport/matrix?month=" + BOOKING_DATE.getMonthValue() + "&year=" + BOOKING_DATE.getYear(),
          E2ETestData.EMPLOYEE_MA_SIGN));

      page.locator("#matrix tbody td:has(.matrix-cell-detail)").first().hover();

      Locator popover = page.locator(".matrix-detail-popover");
      assertThat(popover).isVisible();
      assertThat(popover).containsText(LONG_COMMENT);

      // the description has to wrap inside the popover instead of overflowing it sideways
      assertEquals(true, popover.locator(".popover-body > *").first()
          .evaluate("el => el.scrollWidth <= el.clientWidth + 1"));
    });
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("org.tb.e2e.PlaywrightE2ETestBase#browsers")
  void people_lead_can_open_the_matrix(E2EBrowser browser) {
    runAsUser(browser, E2ETestData.EMPLOYEE_PV_SIGN, "/dailyreport/matrix", page ->
        assertThat(page.locator("#matrix")).isVisible());
  }

}
