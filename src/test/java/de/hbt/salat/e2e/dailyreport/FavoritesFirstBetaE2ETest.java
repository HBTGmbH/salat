package de.hbt.salat.e2e.dailyreport;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import de.hbt.salat.e2e.E2EBrowser;
import de.hbt.salat.e2e.E2ETestData;
import de.hbt.salat.e2e.PlaywrightE2ETestBase;

/**
 * The beta „Favoriten zuerst“ (#1442) moves the favourites above the week in the sidebar of the daily
 * view, and the week can be hidden altogether. What only a browser shows: the order of the two cards,
 * the hint that switches the beta on and reloads the page, the settings under its switch, and that
 * booking goes on without a script error when the week, and with it its out-of-band swap, is gone.
 *
 * <p>Its own day (see the class comment on {@link PlaywrightE2ETestBase}). The beta is a setting of
 * the login and stays in the shared database, so the test switches it off again at the end.
 */
class FavoritesFirstBetaE2ETest extends PlaywrightE2ETestBase {

  private static final String EMPLOYEE = E2ETestData.EMPLOYEE_MA_SIGN;
  private static final LocalDate DAY = LocalDate.parse("2026-07-22");
  private static final String DAY_PATH = "/dailyreport/daily?mode=daily&date=" + DAY;
  private static final String COMMENT = "E2E-Favorit der Beta";

  @ParameterizedTest(name = "{0}")
  @MethodSource("de.hbt.salat.e2e.PlaywrightE2ETestBase#browsers")
  void with_the_beta_the_favourites_stand_above_the_week_which_can_be_hidden(E2EBrowser browser) {
    runAsUser(browser, EMPLOYEE, "/dailyreport/timereports/new?date=" + DAY, page -> {
      page.setViewportSize(1440, 900);
      saveAsFavourite(page);
      try {
        page.navigate(urlWithLogin(DAY_PATH, EMPLOYEE));

        // without the beta: the week above, the hint and the link promote it, no badge
        assertTrue(top(weekCard(page)) < top(favouritesCard(page)), "week above the favourites without the beta");
        assertThat(betaHint(page)).isVisible();
        assertThat(page.locator("#daily-favourites-panel [data-beta-link]")).isVisible();
        assertThat(betaBadge(page)).hasCount(0);

        // "Aktivieren" switches it on and reloads the page
        betaHint(page).getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName("Aktivieren")).click();
        assertThat(betaBadge(page)).isVisible();
        assertThat(betaHint(page)).hasCount(0);
        assertThat(page.locator("#daily-favourites-panel [data-beta-link]")).hasCount(0);
        assertTrue(top(favouritesCard(page)) < top(weekCard(page)), "favourites above the week with the beta");

        // the week hidden in the settings: gone from the page, and booking goes on without an error
        page.navigate(urlWithLogin("/settings", EMPLOYEE));
        assertThat(page.locator("fieldset[data-beta-settings='favoritesfirst']")).isVisible();
        selectTomSelectOption(page, "weekStripPlacement", "Nicht anzeigen");
        save(page);

        List<String> scriptErrors = collectScriptErrors(page);
        page.navigate(urlWithLogin(DAY_PATH, EMPLOYEE));
        assertThat(page.locator("#daily-week-strip")).hasCount(0);
        int bookingsBefore = bookingsWithComment(page).count();
        favouriteInCard(page).click();
        assertThat(bookingsWithComment(page)).hasCount(bookingsBefore + 1);
        assertThat(page.locator("#daily-week-strip")).hasCount(0);
        assertEquals(List.of(), scriptErrors);
      } finally {
        switchTheBetaOff(page);
      }

      // switched off: the sidebar of before, with the week above the favourites
      page.navigate(urlWithLogin(DAY_PATH, EMPLOYEE));
      assertThat(betaBadge(page)).hasCount(0);
      assertTrue(top(weekCard(page)) < top(favouritesCard(page)), "week above the favourites again");
    });
  }

  private void saveAsFavourite(Page page) {
    page.fill("#durationTime", "00:30");
    selectTomSelectOption(page, "suborderId", E2ETestData.SUBORDER_ALPHA_DEV_SIGN);
    page.fill("#commentField", COMMENT);
    page.getByText("Als Favorit speichern").click();
    page.locator("#saveAsFavSection button[type=submit]").click();
    page.waitForLoadState();
  }

  private void switchTheBetaOff(Page page) {
    page.navigate(urlWithLogin("/settings", EMPLOYEE));
    Locator betaSwitch = page.locator("input[name='betaFeatures'][value='favoritesfirst']");
    if (betaSwitch.isChecked()) {
      betaSwitch.uncheck();
      save(page);
    }
  }

  private void save(Page page) {
    afterResponse(page, "/settings", () -> page.locator("form[action$='/settings/store'] button[type=submit]").click());
    page.waitForLoadState();
  }

  private static Locator betaHint(Page page) {
    return page.locator("[data-beta-hint='favoritesfirst']");
  }

  private static Locator betaBadge(Page page) {
    return page.locator("#daily-favourites-panel .card-title .badge");
  }

  private static Locator weekCard(Page page) {
    return page.locator(".card:has(> #daily-week-strip)");
  }

  private static Locator favouritesCard(Page page) {
    return page.locator("#daily-favourites-panel");
  }

  private static Locator favouriteInCard(Page page) {
    return page.locator("#daily-favourites-panel .offer-apply-btn")
        .filter(new Locator.FilterOptions().setHasText(COMMENT)).first();
  }

  private Locator bookingsWithComment(Page page) {
    return page.locator("#daily-bookings-area tbody tr").filter(new Locator.FilterOptions().setHasText(COMMENT));
  }

  private static double top(Locator locator) {
    locator.waitFor();
    return locator.boundingBox().y;
  }

  private static List<String> collectScriptErrors(Page page) {
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
