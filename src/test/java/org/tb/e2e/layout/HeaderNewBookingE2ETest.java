package org.tb.e2e.layout;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import java.time.LocalDate;
import java.util.regex.Pattern;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.tb.e2e.E2EBrowser;
import org.tb.e2e.E2ETestData;
import org.tb.e2e.PlaywrightE2ETestBase;

/**
 * Der Knopf „Neue Buchung" in der Kopfzeile (#1156): er nimmt den Tag der Einzelübersicht und den
 * Rückweg einer Prüfseite mit, öffnet von allen anderen Seiten das Formular für heute, fehlt auf dem
 * Formular selbst und ist im schmalen Fenster ohne das Menü zu erreichen.
 *
 * <p>Der Test öffnet nur Formulare und legt nichts an; wohin „Abbrechen" führt, ist dasselbe Ziel
 * wie nach dem Speichern ({@code ReturnUrls}).
 */
class HeaderNewBookingE2ETest extends PlaywrightE2ETestBase {

  private static final String EMPLOYEE = E2ETestData.EMPLOYEE_MA_SIGN;
  private static final LocalDate PAST_DAY = LocalDate.parse("2026-06-10");
  private static final String DAILY_VIEW_OF_THE_DAY = "/dailyreport/daily?mode=daily&date=" + PAST_DAY;

  @ParameterizedTest(name = "{0}")
  @MethodSource("org.tb.e2e.PlaywrightE2ETestBase#browsers")
  void from_a_past_day_it_opens_the_form_for_that_day_and_leads_back_into_it(E2EBrowser browser) {
    runAsUser(browser, EMPLOYEE, DAILY_VIEW_OF_THE_DAY, page -> {
      headerButton(page).click();

      assertThat(page).hasURL(Pattern.compile(".*/dailyreport/timereports/new\\?date=" + PAST_DAY + "&.*"));
      assertThat(page.locator("#referenceday")).hasValue(PAST_DAY.toString());
      assertThat(cancelLink(page)).hasAttribute("href", DAILY_VIEW_OF_THE_DAY);
      assertThat(headerButton(page)).hasCount(0);
    });
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("org.tb.e2e.PlaywrightE2ETestBase#browsers")
  void from_a_review_page_cancelling_leads_back_to_it(E2EBrowser browser) {
    var review = "/release/review?until=" + E2ETestData.REVIEWED_MONTH;
    runAsUser(browser, E2ETestData.EMPLOYEE_REVIEWED_SIGN, review, page -> {
      headerButton(page).click();

      assertThat(page).hasURL(Pattern.compile(".*/dailyreport/timereports/new\\?.*"));
      assertThat(cancelLink(page)).hasAttribute("href", review);
    });
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("org.tb.e2e.PlaywrightE2ETestBase#browsers")
  void from_any_other_page_it_opens_the_form_for_today(E2EBrowser browser) {
    runAsUser(browser, EMPLOYEE, "/dailyreport/dashboard", page -> {
      assertThat(headerButton(page)).hasAttribute("href", "/dailyreport/timereports/new");
      assertThat(headerButton(page)).hasAttribute("title", "Neue Buchung");
    });
  }

  /** Auf dem Smartphone liegt die Sidebar hinter dem Menü; der Knopf steht trotzdem in der Kopfzeile. */
  @ParameterizedTest(name = "{0}")
  @MethodSource("org.tb.e2e.PlaywrightE2ETestBase#browsers")
  void in_a_narrow_window_it_is_there_without_opening_the_menu(E2EBrowser browser) {
    runAsUser(browser, EMPLOYEE, "/dailyreport/dashboard", page -> {
      page.setViewportSize(390, 844);

      assertThat(headerButton(page)).isVisible();
      assertThat(headerButton(page).locator("span")).isHidden();
    });
  }

  private static Locator headerButton(Page page) {
    return page.locator("header.page-header #header-new-booking");
  }

  private static Locator cancelLink(Page page) {
    return page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName("Abbrechen").setExact(true));
  }

}
