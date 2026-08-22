package org.tb.e2e.dailyreport;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.tb.e2e.E2EBrowser;
import org.tb.e2e.E2ETestData;
import org.tb.e2e.PlaywrightE2ETestBase;

/**
 * "Rest nicht gearbeitet" used to sit in the month view exactly where the button that switches into
 * that view sits in the daily view, so the click that navigated could immediately mark the whole
 * month as not worked (#855). The mode toggle now occupies that spot in both directions, and the
 * mass change moved below the day list, asks before it fires and no longer answers a GET.
 */
class FillNotWorkedGuardE2ETest extends PlaywrightE2ETestBase {

  private static final String EMPLOYEE = E2ETestData.EMPLOYEE_MA_SIGN;
  private static final String FILL_NOT_WORKED = "Rest nicht gearbeitet";
  private static final String DAILY_VIEW = "/dailyreport/daily?mode=daily";

  @ParameterizedTest(name = "{0}")
  @MethodSource("org.tb.e2e.PlaywrightE2ETestBase#browsers")
  void clicking_the_same_spot_twice_navigates_back_instead_of_filling(E2EBrowser browser) {
    runAsUser(browser, EMPLOYEE, DAILY_VIEW, page -> {
      // the click that got the user into the month view - and the one that used to trap them
      navigationCard(page).getByText("Monatsübersicht").click();
      page.waitForLoadState();
      Assertions.assertTrue(page.url().contains("mode=list"), page.url());

      navigationCard(page).getByText("Tagesansicht").click();
      page.waitForLoadState();

      Assertions.assertTrue(page.url().contains("mode=daily"), page.url());
      assertThat(page.locator(".alert-success")).hasCount(0);
    });
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("org.tb.e2e.PlaywrightE2ETestBase#browsers")
  void the_navigation_card_of_the_month_view_holds_no_mass_change(E2EBrowser browser) {
    runAsUser(browser, EMPLOYEE, listView(), page -> {
      assertThat(navigationCard(page)).not().containsText(FILL_NOT_WORKED);
      assertThat(navigationCard(page)).containsText("Tagesansicht");
      // the month view jumps to the current month, so that is what the button says
      assertThat(navigationCard(page)).containsText("Aktueller Monat");
    });
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("org.tb.e2e.PlaywrightE2ETestBase#browsers")
  void the_mass_change_sits_below_the_day_list_and_asks_first(E2EBrowser browser) {
    runAsUser(browser, EMPLOYEE, listView(), page -> {
      Locator button = page.locator("div.card:has(#daily-list) .card-footer")
          .getByText(FILL_NOT_WORKED);
      assertThat(button).isVisible();

      List<String> prompts = new ArrayList<>();
      // no handler registered means Playwright dismisses the dialog, which cancels the submit
      page.onDialog(dialog -> {
        prompts.add(dialog.message());
        dialog.dismiss();
      });

      button.click();
      page.waitForTimeout(500);

      Assertions.assertEquals(1, prompts.size(), "expected exactly one confirmation");
      Assertions.assertTrue(prompts.getFirst().contains("nur Tag für Tag"), prompts.getFirst());
      // dismissed - so the month is untouched and no success toast appeared
      assertThat(page.locator(".alert-success")).hasCount(0);
    });
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("org.tb.e2e.PlaywrightE2ETestBase#browsers")
  void the_endpoints_no_longer_answer_a_get(E2EBrowser browser) {
    runAsUser(browser, EMPLOYEE, listView(), page -> {
      assertGetIsRejected(page, "/dailyreport/daily/fill-not-worked?month=6&year=2026");
      assertGetIsRejected(page, "/dailyreport/matrix/fill-not-worked?month=6&year=2026");
    });
  }

  /**
   * A prefetched link, a bookmark or a step through the browser history must not be able to trigger
   * the change any more.
   */
  private void assertGetIsRejected(Page page, String path) {
    var response = page.navigate(urlWithLogin(path, EMPLOYEE));
    Assertions.assertNotEquals(200, response.status(), path + " still answers a GET");
  }

  private Locator navigationCard(Page page) {
    return page.locator("#daily-mode-nav");
  }

  private String listView() {
    return "/dailyreport/daily?mode=list&month=6&year=2026";
  }

}
