package de.hbt.salat.e2e.dailyreport;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.microsoft.playwright.Page;
import com.microsoft.playwright.Request;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import de.hbt.salat.common.GlobalConstants;
import de.hbt.salat.e2e.E2EBrowser;
import de.hbt.salat.e2e.E2ETestData;
import de.hbt.salat.e2e.PlaywrightE2ETestBase;

/**
 * The booking form goes out once (#1239): every request that reaches the server creates a booking
 * of its own, so a second click while the first save is still on its way booked twice.
 *
 * <p>Nothing reaches the server — the recorder of {@link #recordSubmits(Page)} holds every submit
 * back — so the class books nothing and needs no day of its own.
 */
class TimereportFormSubmitOnceE2ETest extends PlaywrightE2ETestBase {

  private static final String EMPLOYEE = E2ETestData.EMPLOYEE_BL_SIGN;
  private static final LocalDate DAY = LocalDate.parse("2026-06-09");

  @ParameterizedTest(name = "{0}")
  @MethodSource("de.hbt.salat.e2e.PlaywrightE2ETestBase#browsers")
  void a_double_click_on_save_sends_the_booking_once(E2EBrowser browser) {
    runAsUser(browser, EMPLOYEE, "/dailyreport/timereports/new?date=" + DAY, page -> {
      fillBooking(page);
      List<Request> posted = blockBookingRequests(page);
      recordSubmits(page);

      page.locator("#timereportMainForm button[type=submit]").first().dblclick();

      assertEquals(1, submitsGoingOut(page), "submits that would go out: " + submits(page));
      assertEquals(List.of(), posted);
    });
  }

  /** "Speichern und neu" (#843) is a second button of the same form, and the shortcut (#1016) a third way in. */
  @ParameterizedTest(name = "{0}")
  @MethodSource("de.hbt.salat.e2e.PlaywrightE2ETestBase#browsers")
  void the_shortcut_after_save_and_new_sends_nothing_more(E2EBrowser browser) {
    runAsUser(browser, EMPLOYEE, "/dailyreport/timereports/new?date=" + DAY, page -> {
      fillBooking(page);
      List<Request> posted = blockBookingRequests(page);
      recordSubmits(page);

      page.click("button[name='saveAndNew']");
      page.locator("#commentField").press("ControlOrMeta+Enter");

      assertEquals(1, submitsGoingOut(page), "submits that would go out: " + submits(page));
      assertEquals(List.of(), posted);
    });
  }

  /**
   * How many of the recorded submits would have reached the server. The second click on a locked
   * button may not even become a submit — {@code .btn.disabled} takes the pointer events — so the
   * test counts what goes out instead of expecting a prevented second submit.
   */
  private static long submitsGoingOut(Page page) {
    return submits(page).stream().filter(prevented -> !prevented).count();
  }

  private void fillBooking(Page page) {
    selectTomSelectOption(page, "suborderId", GlobalConstants.SUBRORDER_SIGN_TRAINING);
    page.fill("#durationTime", "01:00");
    page.fill("#commentField", "Einmal-absenden Buchung");
  }

  /**
   * A guard, not part of what is tested: should a save get past the recorder after all, it is
   * aborted before it reaches the server and shows up in the returned list.
   */
  private static List<Request> blockBookingRequests(Page page) {
    List<Request> posted = new ArrayList<>();
    page.route(Pattern.compile(".*/dailyreport/timereports(\\?.*)?$"), route -> {
      if ("POST".equals(route.request().method())) {
        posted.add(route.request());
        route.abort();
      } else {
        route.resume();
      }
    });
    return posted;
  }

}
