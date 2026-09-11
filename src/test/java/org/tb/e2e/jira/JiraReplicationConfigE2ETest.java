package org.tb.e2e.jira;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.tb.e2e.E2EBrowser;
import org.tb.e2e.E2ETestData;
import org.tb.e2e.PlaywrightE2ETestBase;

/**
 * Maintaining the JIRA replications through the user interface (#984).
 *
 * <p>Two things here are only testable by walking the pages. The first is that the stored password
 * never comes back: it is absent from the list, from the edit form and from the message a failed run
 * produces, and each of those is a separate place it could leak from. The second is the boundary —
 * these pages carry the credentials of a foreign system, so a backoffice or a people lead must not
 * reach them by knowing the URL either.
 */
class JiraReplicationConfigE2ETest extends PlaywrightE2ETestBase {

  /** A port nothing listens on, so "run now" fails at once instead of waiting for a timeout. */
  private static final String UNREACHABLE_BASE_URL = "http://127.0.0.1:1";

  private static final String PASSWORD = "e2e-secret-token";
  private static final String REPLACEMENT_PASSWORD = "e2e-replaced-token";

  @ParameterizedTest(name = "{0}")
  @MethodSource("org.tb.e2e.PlaywrightE2ETestBase#browsers")
  void a_manager_maintains_a_replication_without_the_password_ever_coming_back(E2EBrowser browser) {
    // one name per browser: the E2E database is shared and this test deletes its own record again
    var name = "E2E Replikation " + browser;
    var renamed = name + " (geaendert)";

    runAsUser(browser, E2ETestData.EMPLOYEE_BL_SIGN, "/jira/replications", page -> {
      // every confirmation on these pages is a confirm() dialog, and Playwright dismisses those by
      // default — which would silently turn "delete" into "do nothing"
      page.onDialog(dialog -> dialog.accept());

      assertThat(page.locator("h2.page-title")).containsText("JIRA-Replikationen");

      // --- create -----------------------------------------------------------------------------
      page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName("Neue Replikation")).click();
      page.locator("#name").fill(name);
      page.locator("#customerorderSign").fill(E2ETestData.CUSTOMERORDER_CONTOSO_SIGN);
      page.locator("#baseUrl").fill(UNREACHABLE_BASE_URL);
      page.locator("#username").fill("e2e-user");
      page.locator("#password").fill(PASSWORD);
      page.locator("#jql").fill("project = CONTOSO");
      save(page);

      assertThat(rowOf(page, name)).containsText(E2ETestData.CUSTOMERORDER_CONTOSO_SIGN);
      assertThat(rowOf(page, name)).containsText(UNREACHABLE_BASE_URL);
      assertThat(rowOf(page, name)).containsText("Server");
      // never run, so the watermark says so rather than showing a date
      assertThat(rowOf(page, name)).containsText("noch nie gelaufen");
      assertPageIsFreeOf(page, PASSWORD);

      // --- edit: the field starts empty, and an empty field keeps the stored password ----------
      rowOf(page, name).getByTitle("Ändern").click();
      assertThat(page.locator("#password")).hasValue("");
      assertPageIsFreeOf(page, PASSWORD);
      page.locator("#name").fill(renamed);
      save(page);
      assertThat(rowOf(page, renamed)).isVisible();

      // --- run now: the failure comes back, with the stored password taken out of it -----------
      rowOf(page, renamed).locator("form[data-run-form] button").click();
      assertThat(page.locator(".alert-danger")).containsText("fehlgeschlagen");
      assertPageIsFreeOf(page, PASSWORD);

      // --- switch off, then on again ------------------------------------------------------------
      rowOf(page, renamed).getByTitle("Replikation ausschalten").click();
      assertThat(rowOf(page, renamed).getByTitle("Replikation einschalten")).isVisible();
      rowOf(page, renamed).getByTitle("Replikation einschalten").click();
      assertThat(rowOf(page, renamed).getByTitle("Replikation ausschalten")).isVisible();

      // --- the watermark is resettable, but there is no field to type one into ------------------
      rowOf(page, renamed).getByTitle("Ändern").click();
      assertThat(page.locator("#lastMaxUpdated")).hasCount(0);
      page.getByRole(AriaRole.BUTTON,
          new Page.GetByRoleOptions().setName("Wasserstand zurücksetzen")).click();
      assertThat(page.locator(".alert-success")).containsText("Wasserstand");

      // --- a filled field replaces the stored password, and that one does not come back either --
      rowOf(page, renamed).getByTitle("Ändern").click();
      page.locator("#password").fill(REPLACEMENT_PASSWORD);
      save(page);
      assertPageIsFreeOf(page, REPLACEMENT_PASSWORD);

      // --- delete ------------------------------------------------------------------------------
      rowOf(page, renamed).getByTitle("Löschen").click();
      assertThat(rowOf(page, name)).hasCount(0);
    });
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("org.tb.e2e.PlaywrightE2ETestBase#browsers")
  void the_menu_entry_is_there_for_the_management_and_for_nobody_else(E2EBrowser browser) {
    // it sits under its own "System" section, not under Backoffice: what the
    // application itself is configured with, as opposed to the business data of every other section
    runAsUser(browser, E2ETestData.EMPLOYEE_BL_SIGN, "/dailyreport/dashboard", page -> {
      assertThat(page.locator("a[href='#navbar-systemsettings']")).hasCount(1);
      assertThat(page.locator("a[href$='/jira/replications']")).hasCount(1);
    });

    for (var sign : new String[]{E2ETestData.EMPLOYEE_BO_SIGN, E2ETestData.EMPLOYEE_PV_SIGN,
        E2ETestData.EMPLOYEE_MA_SIGN}) {
      runAsUser(browser, sign, "/dailyreport/dashboard", page -> {
        // the whole section goes away, not just the entry inside it
        assertThat(page.locator("a[href='#navbar-systemsettings']")).hasCount(0);
        assertThat(page.locator("a[href$='/jira/replications']")).hasCount(0);
      });
    }
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("org.tb.e2e.PlaywrightE2ETestBase#browsers")
  void knowing_the_url_does_not_help_anyone_else(E2EBrowser browser) {
    for (var sign : new String[]{E2ETestData.EMPLOYEE_BO_SIGN, E2ETestData.EMPLOYEE_PV_SIGN,
        E2ETestData.EMPLOYEE_MA_SIGN, E2ETestData.EMPLOYEE_RESTRICTED_SIGN}) {
      runAsUser(browser, sign, "/dailyreport/dashboard", page -> {
        // the request is refused rather than answered with an emptied list — the error page it
        // lands on has tables of its own, so the status is what says so
        var response = page.navigate(urlWithLogin("/jira/replications", sign));
        Assertions.assertThat(response.status())
            .as("%s must not reach the replication list", sign)
            .isNotEqualTo(200);
        assertThat(page.locator("a[href$='/jira/replications/create']")).hasCount(0);
        assertThat(page.locator("h2.page-title")).not().containsText("JIRA-Replikationen");
      });
    }
  }

  private static void save(Page page) {
    page.locator("form.card button[type=submit]").first().click();
  }

  /** Re-resolved after every navigation: each action here reloads the list. */
  private static Locator rowOf(Page page, String name) {
    return page.locator("tbody tr").filter(new Locator.FilterOptions().setHasText(name));
  }

  private static void assertPageIsFreeOf(Page page, String secret) {
    Assertions.assertThat(page.content())
        .as("the rendered page must not carry the stored password")
        .doesNotContain(secret);
  }
}
