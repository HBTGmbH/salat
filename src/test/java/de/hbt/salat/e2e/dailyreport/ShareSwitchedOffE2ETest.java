package de.hbt.salat.e2e.dailyreport;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Request;
import com.microsoft.playwright.Route;
import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import de.hbt.salat.e2e.E2EBrowser;
import de.hbt.salat.e2e.E2ETestData;
import de.hbt.salat.e2e.PlaywrightE2ETestBase;

/**
 * Switching "Mit Kollegen teilen" off again takes the choice of colleagues out of the booking form
 * (#1196). The choice is a required field; left behind hidden in the form, it made the browser
 * refuse to submit without saying why.
 *
 * <p>{@code erb} is the one seeded colleague with an employee order on {@code ALPHA-DEV}. The class
 * books, on a day no other E2E class touches, because the E2E database is shared (see
 * {@link PlaywrightE2ETestBase}).
 */
class ShareSwitchedOffE2ETest extends PlaywrightE2ETestBase {

  private static final String EMPLOYEE = E2ETestData.EMPLOYEE_MA_SIGN;
  private static final String COLLEAGUE = "| " + E2ETestData.EMPLOYEE_STRAY_SIGN;
  /** In the past relative to the base class's fixed clock, and used by no other E2E class. */
  private static final LocalDate DAY = LocalDate.parse("2026-06-11");
  private static final String FORM_PATH = "/dailyreport/timereports/new?date=" + DAY;

  @ParameterizedTest(name = "{0}")
  @MethodSource("de.hbt.salat.e2e.PlaywrightE2ETestBase#browsers")
  void switched_on_and_off_again_the_booking_is_saved(E2EBrowser browser) {
    runAsUser(browser, EMPLOYEE, FORM_PATH, page -> {
      fillBooking(page, "Teilen aus, ohne Auswahl");

      afterResponse(page, "/share-recipients", () -> page.check("#shareToggle"));
      page.uncheck("#shareToggle");

      assertSavedWithoutSharing(page, "Teilen aus, ohne Auswahl");
    });
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("de.hbt.salat.e2e.PlaywrightE2ETestBase#browsers")
  void colleagues_chosen_before_switching_off_are_not_sent(E2EBrowser browser) {
    runAsUser(browser, EMPLOYEE, FORM_PATH, page -> {
      fillBooking(page, "Teilen aus, mit Auswahl");

      afterResponse(page, "/share-recipients", () -> page.check("#shareToggle"));
      page.locator("#shareRecipientsContainer .ts-control").click();
      page.locator("#shareRecipientsContainer .ts-dropdown .option").filter(
          new Locator.FilterOptions().setHasText(COLLEAGUE)).first().click();
      page.uncheck("#shareToggle");

      assertSavedWithoutSharing(page, "Teilen aus, mit Auswahl");
    });
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("de.hbt.salat.e2e.PlaywrightE2ETestBase#browsers")
  void a_late_answer_does_not_bring_the_colleagues_back(E2EBrowser browser) {
    runAsUser(browser, EMPLOYEE, FORM_PATH, page -> {
      fillBooking(page, "Teilen aus, Antwort verspaetet");

      // hold the answer until sharing is switched off again
      AtomicReference<Route> held = new AtomicReference<>();
      page.route("**/share-recipients**", held::set);
      page.check("#shareToggle");
      page.waitForCondition(() -> held.get() != null);
      page.uncheck("#shareToggle");

      // braces, so evaluate does not wait for the promise before the answer is even released
      page.evaluate("() => { window.shareSwapDone = new Promise(done => document"
          + ".getElementById('shareRecipientsContainer')"
          + ".addEventListener('htmx:finally:swap', done, {once: true})); }");
      held.get().resume();
      page.evaluate("() => window.shareSwapDone.then(() => true)");

      assertThat(page.locator("#shareRecipientsContainer select")).hasCount(0);
      assertSavedWithoutSharing(page, "Teilen aus, Antwort verspaetet");
    });
  }

  private void fillBooking(Page page, String comment) {
    selectTomSelectOption(page, "suborderId", E2ETestData.SUBORDER_ALPHA_DEV_SIGN);
    page.fill("#durationTime", "01:00");
    page.fill("#commentField", comment);
  }

  private void assertSavedWithoutSharing(Page page, String comment) {
    Request post = page.waitForRequest(
        r -> "POST".equals(r.method()) && r.url().contains("/dailyreport/timereports"),
        () -> page.click("#timereportMainForm button[type=submit]:not([name])"));
    String body = post.postData();
    assertFalse(body.contains("recipientUserIds"), "form data: " + body);
    assertFalse(body.contains("shareWithColleagues"), "form data: " + body);

    page.waitForLoadState();
    assertThat(page).hasURL(Pattern.compile("/dailyreport/daily.*"));
    assertThat(page.locator("body")).containsText(comment);
  }

}
