package org.tb.e2e.dailyreport;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static java.nio.charset.StandardCharsets.UTF_8;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import java.net.URLEncoder;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.tb.e2e.E2EBrowser;
import org.tb.e2e.E2ETestData;
import org.tb.e2e.PlaywrightE2ETestBase;

/**
 * Das Buchungsformular gibt nur eine geprüfte Rücksprungadresse aus (#1133). Eine
 * {@code javascript:}-Adresse oder eine fremde Seite im Link auf das Formular erscheint weder im
 * versteckten Feld noch im Link „Abbrechen"; „Abbrechen" führt dann in die Tagesansicht des
 * Buchungstags. Eine Adresse in die Tagesansicht kommt unverändert an.
 *
 * <p>Geprüft wird die ausgegebene Seite, weil erst das Template aus einem fehlenden Wert den
 * Rückweg in die Tagesansicht macht. Der Test öffnet nur Formulare und legt nichts an.
 */
class TimereportFormReturnUrlE2ETest extends PlaywrightE2ETestBase {

  private static final String EMPLOYEE = E2ETestData.EMPLOYEE_MA_SIGN;
  private static final LocalDate DAY = LocalDate.parse("2026-06-10");
  private static final String DAILY_VIEW_OF_THE_DAY = "/dailyreport/daily?mode=daily&date=" + DAY;

  @ParameterizedTest(name = "{0}")
  @MethodSource("org.tb.e2e.PlaywrightE2ETestBase#browsers")
  void an_unsafe_return_target_reaches_neither_the_hidden_field_nor_the_cancel_link(E2EBrowser browser) {
    runAsUser(browser, EMPLOYEE, formUrl(null), page -> {
      for (String unsafe : List.of("javascript:alert(1)", "https://evil.example.com")) {
        page.navigate(urlWithLogin(formUrl(unsafe), EMPLOYEE));

        assertThat(returnUrlField(page)).hasValue("");
        assertThat(cancelLink(page)).hasAttribute("href", DAILY_VIEW_OF_THE_DAY);
      }
    });
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("org.tb.e2e.PlaywrightE2ETestBase#browsers")
  void a_return_target_in_the_daily_view_reaches_the_form_unchanged(E2EBrowser browser) {
    var listView = "/dailyreport/daily?mode=list&fMonth=6&fYear=2026";
    runAsUser(browser, EMPLOYEE, formUrl(listView), page -> {
      assertThat(returnUrlField(page)).hasValue(listView);
      assertThat(cancelLink(page)).hasAttribute("href", listView);
    });
  }

  private static String formUrl(String returnUrl) {
    var url = "/dailyreport/timereports/new?date=" + DAY;
    return returnUrl == null ? url : url + "&returnUrl=" + URLEncoder.encode(returnUrl, UTF_8);
  }

  private static Locator returnUrlField(Page page) {
    return page.locator("input[type=hidden][name=returnUrl]");
  }

  private static Locator cancelLink(Page page) {
    return page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName("Abbrechen").setExact(true));
  }

}
