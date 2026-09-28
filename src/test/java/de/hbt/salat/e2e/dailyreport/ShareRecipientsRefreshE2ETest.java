package de.hbt.salat.e2e.dailyreport;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import de.hbt.salat.e2e.E2EBrowser;
import de.hbt.salat.e2e.E2ETestData;
import de.hbt.salat.e2e.PlaywrightE2ETestBase;

/**
 * With "Mit Kollegen teilen" switched on, the list of colleagues follows the suborder and the date
 * of the booking form (#1184) — also after a change of the date, which replaces the suborder select
 * together with everything hanging on it. Without sharing, nothing is loaded.
 *
 * <p>Which colleagues are offered depends on the suborder: of the seeded people, only
 * {@code erb} has an employee order on {@code ALPHA-DEV} and none on {@code GLOBEX-CONSULT}. The
 * form is never saved, so the class books nothing.
 */
class ShareRecipientsRefreshE2ETest extends PlaywrightE2ETestBase {

  private static final String EMPLOYEE = E2ETestData.EMPLOYEE_MA_SIGN;
  private static final String COLLEAGUE_ON_ALPHA_ONLY = "| " + E2ETestData.EMPLOYEE_STRAY_SIGN;
  private static final String FORM_PATH = "/dailyreport/timereports/new?date=2026-06-25";
  private static final String OTHER_DAY = "2026-06-24";

  @ParameterizedTest(name = "{0}")
  @MethodSource("de.hbt.salat.e2e.PlaywrightE2ETestBase#browsers")
  void the_recipients_follow_suborder_and_date_also_after_the_suborders_were_replaced(E2EBrowser browser) {
    runAsUser(browser, EMPLOYEE, FORM_PATH, page -> {
      List<String> errors = collectScriptErrors(page);
      List<String> recipientRequests = collectRecipientRequests(page);
      afterResponse(page, "/refresh-sidebar",
          () -> selectTomSelectOption(page, "suborderId", E2ETestData.SUBORDER_GLOBEX_CONSULT_SIGN));

      afterResponse(page, "/share-recipients", () -> page.check("#shareToggle"));
      assertThat(colleagueOnAlphaOnly(page)).hasCount(0);

      afterResponse(page, "/share-recipients",
          () -> selectTomSelectOption(page, "suborderId", E2ETestData.SUBORDER_ALPHA_DEV_SIGN));
      assertThat(colleagueOnAlphaOnly(page)).hasCount(1);

      // the date change replaces #orders-suborders-wrapper, the suborder select included
      afterResponse(page, "/share-recipients", () -> page.fill("#referenceday", OTHER_DAY));
      assertThat(colleagueOnAlphaOnly(page)).hasCount(1);

      afterResponse(page, "/share-recipients",
          () -> selectTomSelectOption(page, "suborderId", E2ETestData.SUBORDER_GLOBEX_CONSULT_SIGN));
      assertThat(colleagueOnAlphaOnly(page)).hasCount(0);

      // one request per change: loading the list must not trigger the next one (07f9f838)
      assertEquals(4, recipientRequests.size(), "requests for the recipients: " + recipientRequests);
      assertEquals(List.of(), errors, "the form must not write errors to the console");
    });
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("de.hbt.salat.e2e.PlaywrightE2ETestBase#browsers")
  void without_sharing_no_recipients_are_loaded(E2EBrowser browser) {
    runAsUser(browser, EMPLOYEE, FORM_PATH, page -> {
      List<String> errors = collectScriptErrors(page);
      List<String> recipientRequests = collectRecipientRequests(page);

      afterResponse(page, "/refresh-sidebar",
          () -> selectTomSelectOption(page, "suborderId", E2ETestData.SUBORDER_GLOBEX_CONSULT_SIGN));
      afterResponse(page, "/refresh-orders", () -> page.fill("#referenceday", OTHER_DAY));
      afterResponse(page, "/refresh-sidebar",
          () -> selectTomSelectOption(page, "suborderId", E2ETestData.SUBORDER_ALPHA_DEV_SIGN));

      // switching sharing on is the one request; anything the changes above had sent after their
      // own response would have been sent before it
      afterResponse(page, "/share-recipients", () -> page.check("#shareToggle"));
      assertEquals(1, recipientRequests.size(), "requests for the recipients: " + recipientRequests);
      assertEquals(List.of(), errors, "the form must not write errors to the console");
    });
  }

  private Locator colleagueOnAlphaOnly(Page page) {
    return page.locator("#shareRecipientsContainer option")
        .filter(new Locator.FilterOptions().setHasText(COLLEAGUE_ON_ALPHA_ONLY));
  }

  private List<String> collectRecipientRequests(Page page) {
    List<String> requests = new ArrayList<>();
    page.onRequest(request -> {
      if (request.url().contains("/share-recipients")) {
        requests.add(request.url());
      }
    });
    return requests;
  }

  private List<String> collectScriptErrors(Page page) {
    List<String> errors = new ArrayList<>();
    page.onPageError(errors::add);
    page.onConsoleMessage(message -> {
      if ("error".equals(message.type())) {
        errors.add(message.text());
      }
    });
    return errors;
  }

}
