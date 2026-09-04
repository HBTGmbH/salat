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

      // address the cell this test booked itself: the E2E suite shares one database, so other
      // test classes leave bookings of their own in the same month (see PlaywrightE2ETestBase)
      page.locator("#matrix tbody td:has(.matrix-cell-detail)")
          .filter(new Locator.FilterOptions().setHasText(LONG_COMMENT))
          .first()
          .hover();

      Locator popover = page.locator(".matrix-detail-popover");
      assertThat(popover).isVisible();
      assertThat(popover).containsText(LONG_COMMENT);

      // the description has to wrap inside the popover instead of overflowing it sideways
      Locator longDetailLine = popover.locator(".popover-body > *")
          .filter(new Locator.FilterOptions().setHasText(LONG_COMMENT))
          .first();
      assertEquals(true, longDetailLine.evaluate("el => el.scrollWidth <= el.clientWidth + 1"));
    });
  }

  /**
   * Counterpart to {@code EnglishLocaleE2ETest}: the legend labels and the month picker moved from
   * hard-coded template text (and from the native month input) into the message bundle (#823), so a
   * German browser has to keep seeing German.
   */
  @ParameterizedTest(name = "{0}")
  @MethodSource("org.tb.e2e.PlaywrightE2ETestBase#browsers")
  void legend_and_month_picker_are_german_for_a_german_browser(E2EBrowser browser) {
    runAsUser(browser, E2ETestData.EMPLOYEE_MA_SIGN, "/dailyreport/matrix", page -> {
      // the matrix card carries a second footer holding the "Rest nicht gearbeitet" action (#855)
      Locator legend = page.locator("div.card:has(#matrix) .card-footer").first();
      assertThat(legend).containsText("Sa/So");
      assertThat(legend).containsText("Feiertag");
      assertThat(legend).containsText("Heute");

      // FIXED_NOW is 2026-06-15, and the matrix opens on the current month
      assertThat(page.locator("button.dropdown-toggle[title='Monat wählen']")).hasText("Juni 2026");
    });
  }

  /**
   * The picker itself only reports the selection through its hidden "yyyy-MM" input (like the native
   * month input it replaced, see the {@code fragments/month-picker} template); the matrix page turns
   * that change event into a navigation.
   */
  @ParameterizedTest(name = "{0}")
  @MethodSource("org.tb.e2e.PlaywrightE2ETestBase#browsers")
  void picking_a_month_reports_it_as_yyyy_mm_and_navigates(E2EBrowser browser) {
    runAsUser(browser, E2ETestData.EMPLOYEE_MA_SIGN, "/dailyreport/matrix", page -> {
      assertThat(page.locator("#matrix-month")).hasValue("2026-06");

      Locator picker = page.locator("div.btn-group:has(button[title='Monat wählen'])");
      picker.locator("[data-month-label]").click();
      picker.locator("[data-month-option='9']").click();
      page.waitForLoadState();

      assertThat(page.locator("#matrix-month")).hasValue("2026-09");
      assertThat(picker.locator("[data-month-label]")).hasText("September 2026");
      assertEquals("9", urlParameter(page.url(), "month"));
      assertEquals("2026", urlParameter(page.url(), "year"));
    });
  }

  /**
   * The month arrows go through the same hidden input, including the roll-over into the next year.
   */
  @ParameterizedTest(name = "{0}")
  @MethodSource("org.tb.e2e.PlaywrightE2ETestBase#browsers")
  void month_arrows_step_month_by_month(E2EBrowser browser) {
    runAsUser(browser, E2ETestData.EMPLOYEE_MA_SIGN, "/dailyreport/matrix?month=12&year=2026", page -> {
      page.locator("[data-month-step='1']").click();
      page.waitForLoadState();

      assertThat(page.locator("#matrix-month")).hasValue("2027-01");
      assertEquals("1", urlParameter(page.url(), "month"));
      assertEquals("2027", urlParameter(page.url(), "year"));
    });
  }

  /**
   * "Current month" has to come from the application clock, not from the browser's - under
   * {@link PlaywrightE2ETestBase#FIXED_NOW} those two differ.
   */
  @ParameterizedTest(name = "{0}")
  @MethodSource("org.tb.e2e.PlaywrightE2ETestBase#browsers")
  void current_month_button_jumps_to_the_month_of_the_application_clock(E2EBrowser browser) {
    runAsUser(browser, E2ETestData.EMPLOYEE_MA_SIGN, "/dailyreport/matrix?month=12&year=2027", page -> {
      Locator picker = page.locator("div[data-month-picker]");
      picker.locator("[data-month-label]").click();
      picker.locator("[data-month-current]").click();
      page.waitForLoadState();

      assertThat(page.locator("#matrix-month")).hasValue("2026-06");
      assertEquals("6", urlParameter(page.url(), "month"));
      assertEquals("2026", urlParameter(page.url(), "year"));
    });
  }

  /**
   * The picker offers no way to empty the selection. It used to, and the matrix read that as "back
   * to the default month" — but the matrix remembers month and year as UiState, so a missing
   * request parameter is refilled from that state and emptying had no visible effect (#923).
   * "Current month" above covers the same need and names month and year explicitly.
   */
  @ParameterizedTest(name = "{0}")
  @MethodSource("org.tb.e2e.PlaywrightE2ETestBase#browsers")
  void the_picker_offers_no_way_to_empty_the_selection(E2EBrowser browser) {
    runAsUser(browser, E2ETestData.EMPLOYEE_MA_SIGN, "/dailyreport/matrix?month=12&year=2027", page -> {
      Locator picker = page.locator("div[data-month-picker]");
      picker.locator("[data-month-label]").click();

      assertThat(picker.locator("[data-month-clear]")).hasCount(0);
    });
  }

  /**
   * The year arrows are client-side only: they switch which year the twelve months refer to. Only
   * picking a month navigates.
   */
  @ParameterizedTest(name = "{0}")
  @MethodSource("org.tb.e2e.PlaywrightE2ETestBase#browsers")
  void year_arrows_switch_the_offered_year_without_leaving_the_page(E2EBrowser browser) {
    runAsUser(browser, E2ETestData.EMPLOYEE_MA_SIGN, "/dailyreport/matrix", page -> {
      Locator monthPicker = page.locator("button.dropdown-toggle[title='Monat wählen']");
      monthPicker.click();

      Locator picker = page.locator("div.btn-group:has(button[title='Monat wählen'])");
      String urlBeforeYearStep = page.url();
      picker.locator("[data-year-step='1']").click();

      assertThat(picker.locator("[data-year-label]")).hasText("2027");
      assertEquals(urlBeforeYearStep, page.url(), "stepping the year must not navigate");
      // nothing is selected yet: label, hidden value and highlight all still say June 2026
      assertThat(monthPicker).hasText("Juni 2026");
      assertThat(page.locator("#matrix-month")).hasValue("2026-06");
      assertThat(picker.locator(".dropdown-menu .btn-primary")).hasCount(0);

      picker.locator("[data-month-option='9']").click();
      page.waitForLoadState();

      assertThat(monthPicker).hasText("September 2027");
      assertEquals("9", urlParameter(page.url(), "month"));
      assertEquals("2027", urlParameter(page.url(), "year"));
    });
  }

  private static String urlParameter(String url, String name) {
    return java.net.URI.create(url).getQuery() == null ? null
        : java.util.Arrays.stream(java.net.URI.create(url).getQuery().split("&"))
            .filter(p -> p.startsWith(name + "="))
            .map(p -> p.substring(name.length() + 1))
            .findFirst().orElse(null);
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("org.tb.e2e.PlaywrightE2ETestBase#browsers")
  void people_lead_can_open_the_matrix(E2EBrowser browser) {
    runAsUser(browser, E2ETestData.EMPLOYEE_PV_SIGN, "/dailyreport/matrix", page ->
        assertThat(page.locator("#matrix")).isVisible());
  }

}
