package org.tb.e2e.dailyreport;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.tb.e2e.E2EBrowser;
import org.tb.e2e.E2ETestData;
import org.tb.e2e.PlaywrightE2ETestBase;

/**
 * Covers the new time and duration input (#830) in both states of its opt-in beta flag.
 *
 * <p>The flag is persisted per user, so every test sets it explicitly through the settings form
 * first instead of relying on the state a previous test left behind.
 */
class TimeInputBetaE2ETest extends PlaywrightE2ETestBase {

  private static final String EMPLOYEE = E2ETestData.EMPLOYEE_MA_SIGN;
  private static final String STEPPER_INCREASE = "#durationTime + button";
  private static final String STEPPER_DECREASE = "#durationSection .input-group > button:first-child";

  @ParameterizedTest(name = "{0}")
  @MethodSource("org.tb.e2e.PlaywrightE2ETestBase#browsers")
  void without_the_beta_the_field_stays_plain_but_accepts_flexible_input(E2EBrowser browser) {
    var date = LocalDate.parse("2026-06-18");
    runAsUser(browser, EMPLOYEE, "/settings", page -> {

      setBeta(page, false);
      openNewBooking(page, date);

      // classic field: no stepper, but the in-context beta hint is offered
      assertThat(page.locator(STEPPER_INCREASE)).hasCount(0);
      assertThat(page.getByText("Schnelleingabe testen")).isVisible();

      // "2h30" is normalised to HH:MM on blur and accepted by the server
      page.fill("#durationTime", "2h30");
      page.locator("#commentField").click();
      assertThat(page.locator("#durationTime")).hasValue("02:30");

      book(page, date);
      assertThat(page.locator("#daily-bookings-area")).containsText("2:30");
    });
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("org.tb.e2e.PlaywrightE2ETestBase#browsers")
  void the_beta_adds_a_snapping_stepper_and_additive_chips(E2EBrowser browser) {
    runAsUser(browser, EMPLOYEE, "/settings", page -> {

      setBeta(page, true);
      openNewBooking(page, LocalDate.parse("2026-06-19"));

      // the hint gives way to the beta badge, which carries the Slack feedback channel
      assertThat(page.getByText("Schnelleingabe testen")).hasCount(0);
      assertThat(page.locator("span.badge[title*='#salat']")).isVisible();

      // empty field: the first step lands on the grid
      page.click(STEPPER_INCREASE);
      assertThat(page.locator("#durationTime")).hasValue("00:15");

      // an off-grid value snaps onto the grid first, then moves in full steps
      page.fill("#durationTime", "08:07");
      page.click(STEPPER_INCREASE);
      assertThat(page.locator("#durationTime")).hasValue("08:15");
      page.click(STEPPER_INCREASE);
      assertThat(page.locator("#durationTime")).hasValue("08:30");
      page.click(STEPPER_DECREASE);
      assertThat(page.locator("#durationTime")).hasValue("08:15");
      page.click(STEPPER_DECREASE);
      assertThat(page.locator("#durationTime")).hasValue("08:00");

      // chips add up, the reset chip clears the field again
      page.fill("#durationTime", "01:00");
      chip(page, "+30").click();
      chip(page, "+30").click();
      assertThat(page.locator("#durationTime")).hasValue("02:00");
      chip(page, "+1h").click();
      assertThat(page.locator("#durationTime")).hasValue("03:00");
      page.locator("#durationChips button[aria-label='Dauer zurücksetzen']").click();
      assertThat(page.locator("#durationTime")).isEmpty();

      // the chips belong to the duration field and must not linger in begin/end mode
      page.click("#btnBeginEnd");
      assertThat(page.locator("#durationChips")).isHidden();
      page.click("#btnDuration");
      assertThat(page.locator("#durationChips")).isVisible();
    });
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("org.tb.e2e.PlaywrightE2ETestBase#browsers")
  void an_off_grid_duration_can_still_be_typed_and_saved_with_the_beta_on(E2EBrowser browser) {
    var date = LocalDate.parse("2026-06-22");
    runAsUser(browser, EMPLOYEE, "/settings", page -> {

      setBeta(page, true);
      openNewBooking(page, date);

      page.fill("#durationTime", "01:07");
      book(page, date);

      assertThat(page.locator("#daily-bookings-area")).containsText("1:07");
    });
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("org.tb.e2e.PlaywrightE2ETestBase#browsers")
  void the_beta_turns_the_workingday_times_into_typeable_fields(E2EBrowser browser) {
    runAsUser(browser, EMPLOYEE, "/settings", page -> {

      setBeta(page, false);
      page.navigate(urlWithLogin("/dailyreport/daily?mode=daily&date=2026-06-24", EMPLOYEE));
      // classic: the native picker stays
      assertThat(page.locator("#startTime")).hasAttribute("type", "time");

      setBeta(page, true);
      page.navigate(urlWithLogin("/dailyreport/daily?mode=daily&date=2026-06-24", EMPLOYEE));

      // beta: a text field, so a phone shows the numeric keypad instead of the OS wheel
      assertThat(page.locator("#startTime")).hasAttribute("type", "text");

      // sized by character count rather than by a pinned pixel value, so the width follows the
      // font size (12px on the desktop, 16px below the md breakpoint) instead of fighting it
      assertThat(page.locator("#startTime")).hasAttribute("size", "5");
      assertEquals(false, page.locator("#startTime").evaluate(
          "el => { el.value = '08:30'; return el.scrollWidth > el.clientWidth + 1; }"),
          "an HH:MM value must fit without scrolling");

      List<String> saves = new ArrayList<>();
      page.onRequest(request -> {
        if (request.url().contains("/dailyreport/daily/workingday")) {
          saves.add(request.url());
        }
      });

      // typing must not save: the response swaps the form out of the page and would take the
      // caret with it while the user is still entering digits
      page.fill("#startTime", "");
      page.locator("#startTime").click();
      page.locator("#startTime").pressSequentially("0745", new Locator.PressSequentiallyOptions().setDelay(80));
      assertThat(page.locator("#startTime")).hasValue("07:45");

      // the stepper snaps on the grid, saves nothing yet and leaves the focus in the field
      page.click("#start-field .input-group > button:last-child");
      assertThat(page.locator("#startTime")).hasValue("08:00");
      page.waitForTimeout(1200);
      assertEquals(0, saves.size(), "neither typing nor stepping may trigger a save");
      assertEquals("startTime", page.evaluate("() => document.activeElement.id"));

      // moving on to the next field saves once and leaves the caret there: the response must not
      // swap the form back when only a time changed
      page.locator("#breakTime").click();
      page.waitForTimeout(1200);
      assertEquals(1, saves.size(), "leaving the field must save exactly once");
      assertEquals("breakTime", page.evaluate("() => document.activeElement.id"),
          "the save must not pull the focus out of the next field");
      page.navigate(urlWithLogin("/dailyreport/daily?mode=daily&date=2026-06-24", EMPLOYEE));
      assertThat(page.locator("#startTime")).hasValue("08:00");
    });
  }

  /**
   * The counterpart to the test above: a changed not-worked flag adds or removes the time fields, so
   * here the form does have to be swapped back.
   */
  @ParameterizedTest(name = "{0}")
  @MethodSource("org.tb.e2e.PlaywrightE2ETestBase#browsers")
  void toggling_not_worked_still_swaps_the_form_and_hides_the_time_fields(E2EBrowser browser) {
    runAsUser(browser, EMPLOYEE, "/settings", page -> {

      setBeta(page, true);
      page.navigate(urlWithLogin("/dailyreport/daily?mode=daily&date=2026-06-29", EMPLOYEE));
      assertThat(page.locator("#start-field")).isVisible();

      page.locator("#notWorked").check();
      page.waitForTimeout(1200);
      assertThat(page.locator("#start-field")).hasCount(0);
      assertThat(page.locator("#break-field")).hasCount(0);

      page.locator("#notWorked").uncheck();
      page.waitForTimeout(1200);
      assertThat(page.locator("#start-field")).isVisible();
      // and the field is enhanced again after the swap
      assertThat(page.locator("#start-field .input-group")).isVisible();
    });
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("org.tb.e2e.PlaywrightE2ETestBase#browsers")
  void the_hint_on_the_daily_page_activates_the_feature(E2EBrowser browser) {
    runAsUser(browser, EMPLOYEE, "/settings", page -> {

      setBeta(page, false);
      page.navigate(urlWithLogin("/dailyreport/daily?mode=daily&date=2026-06-23", EMPLOYEE));

      assertThat(page.locator("#beta-timeinput-hint")).isVisible();
      page.locator("#beta-timeinput-hint").getByText("Aktivieren").click();

      // the HX-Refresh response reloads the page: hint gone, workingday fields enhanced
      page.waitForSelector("#start-field .input-group");
      assertThat(page.locator("#beta-timeinput-hint")).hasCount(0);
      assertThat(page.locator("#break-field .input-group")).isVisible();
    });
  }

  private void openNewBooking(Page page, LocalDate date) {
    page.navigate(urlWithLogin("/dailyreport/timereports/new?date=" + date, EMPLOYEE));
  }

  private void book(Page page, LocalDate date) {
    selectTomSelectOption(page, "suborderId", E2ETestData.SUBORDER_ALPHA_DEV_SIGN);
    page.click("#timereportMainForm button[type=submit]");
    page.waitForLoadState();
    page.navigate(urlWithLogin("/dailyreport/daily?mode=daily&date=" + date, EMPLOYEE));
  }

  private Locator chip(Page page, String label) {
    return page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(label));
  }

  /**
   * Switches the beta flag and verifies it stuck — a silently unauthenticated POST would otherwise
   * leave the tests asserting against the wrong variant.
   */
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
    // the redirect after saving drops the dev-login query parameter, so navigate back explicitly
    page.navigate(urlWithLogin("/settings", EMPLOYEE));
    if (enabled) {
      assertThat(checkbox).isChecked();
    } else {
      assertThat(checkbox).not().isChecked();
    }
  }

}
