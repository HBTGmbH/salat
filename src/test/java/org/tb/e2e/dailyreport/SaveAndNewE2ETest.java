package org.tb.e2e.dailyreport;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

import com.microsoft.playwright.Page;
import java.time.LocalDate;
import java.util.regex.Pattern;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.tb.common.GlobalConstants;
import org.tb.e2e.E2EBrowser;
import org.tb.e2e.E2ETestData;
import org.tb.e2e.PlaywrightE2ETestBase;

/**
 * "Speichern und neu" (#843): saving stays on the booking form for the same day instead of jumping
 * to the daily view, so several bookings can be entered in a row.
 *
 * <p>Runs as the manager on a day no other E2E class touches: the test creates bookings, and the
 * E2E database is shared (see {@link PlaywrightE2ETestBase}).
 */
class SaveAndNewE2ETest extends PlaywrightE2ETestBase {

  private static final String EMPLOYEE = E2ETestData.EMPLOYEE_BL_SIGN;
  /** In the past relative to the base class's fixed clock, and used by no other E2E class. */
  private static final LocalDate DAY = LocalDate.parse("2026-06-08");

  private static final String FIRST = "Speichern-und-neu erste Buchung";
  private static final String SECOND = "Speichern-und-neu zweite Buchung";

  @ParameterizedTest(name = "{0}")
  @MethodSource("org.tb.e2e.PlaywrightE2ETestBase#browsers")
  void save_and_new_keeps_the_form_open_for_the_next_booking(E2EBrowser browser) {
    runAsUser(browser, EMPLOYEE, "/dailyreport/timereports/new?date=" + DAY, page -> {

      fillBooking(page, "01:00", FIRST);
      page.click("button[name='saveAndNew']");
      page.waitForLoadState();

      // still on the booking form, same day, and the entry fields are empty again
      assertThat(page).hasURL(Pattern.compile("/dailyreport/timereports/new.*"));
      assertThat(page.locator("#referenceday")).hasValue(DAY.toString());
      assertThat(page.locator("#durationTime")).isEmpty();
      assertThat(page.locator("#commentField")).isEmpty();

      // the booking was saved: confirmation plus the day's bookings in the sidebar
      assertThat(page.locator(".alert-success")).containsText("Buchung erstellt");
      assertThat(page.locator("#form-sidebar")).containsText(FIRST);

      // the next booking can be entered right away
      fillBooking(page, "02:00", SECOND);
      page.click("button[name='saveAndNew']");
      page.waitForLoadState();

      assertThat(page.locator("#form-sidebar")).containsText(FIRST);
      assertThat(page.locator("#form-sidebar")).containsText(SECOND);
    });
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("org.tb.e2e.PlaywrightE2ETestBase#browsers")
  void plain_save_still_returns_to_the_daily_view(E2EBrowser browser) {
    runAsUser(browser, EMPLOYEE, "/dailyreport/timereports/new?date=" + DAY, page -> {

      fillBooking(page, "00:30", "Speichern-und-neu Kontrollfall");
      page.click("#timereportMainForm button[type=submit]");
      page.waitForLoadState();

      assertThat(page).hasURL(Pattern.compile("/dailyreport/daily.*"));
    });
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("org.tb.e2e.PlaywrightE2ETestBase#browsers")
  void the_edit_form_does_not_offer_save_and_new(E2EBrowser browser) {
    runAsUser(browser, EMPLOYEE, "/dailyreport/timereports/new?date=" + DAY, page -> {

      // an existing booking is needed to reach the edit form
      fillBooking(page, "00:45", "Speichern-und-neu Bearbeiten");
      page.click("button[name='saveAndNew']");
      page.waitForLoadState();
      assertThat(page.locator("button[name='saveAndNew']")).isVisible();

      page.navigate(urlWithLogin("/dailyreport/daily?mode=daily&date=" + DAY, EMPLOYEE));
      page.locator("a[href*='/edit']").first().click();
      page.waitForLoadState();

      assertThat(page.locator("button[name='saveAndNew']")).hasCount(0);
    });
  }

  /**
   * The training suborder is provisioned for every employee on login and, unlike Urlaub, carries no
   * entitlement the manager would first have to be granted.
   */
  private void fillBooking(Page page, String duration, String comment) {
    selectTomSelectOption(page, "suborderId", GlobalConstants.SUBRORDER_SIGN_TRAINING);
    page.fill("#durationTime", duration);
    page.fill("#commentField", comment);
  }

}
