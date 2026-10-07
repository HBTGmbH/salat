package de.hbt.salat.e2e.dailyreport;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.WaitForSelectorState;
import java.time.LocalDate;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import de.hbt.salat.e2e.E2EBrowser;
import de.hbt.salat.e2e.E2ETestData;
import de.hbt.salat.e2e.PlaywrightE2ETestBase;

/**
 * Picking a favourite in the dialog "Favoriten" (#1414). What only a browser can show: the dialog
 * belongs to the favourites module and knows neither the day nor the endpoint; its buttons submit the
 * page's form {@code favoritesApplyForm} through {@code form=}. That the search field has the focus,
 * that Enter takes the only hit, that the dialog closes and the day shows the new booking hangs on
 * markup, salat.js and htmx working together. Who may apply which favourite is a question of the
 * service and tested there.
 *
 * <p>Its own day (see the class comment on {@link PlaywrightE2ETestBase}).
 */
class FavoriteDialogE2ETest extends PlaywrightE2ETestBase {

  private static final String EMPLOYEE = E2ETestData.EMPLOYEE_MA_SIGN;
  private static final LocalDate DAY = LocalDate.parse("2026-07-09");
  private static final String COMMENT = "E2E-Favorit aus dem Dialog";

  @ParameterizedTest(name = "{0}")
  @MethodSource("de.hbt.salat.e2e.PlaywrightE2ETestBase#browsers")
  void a_favourite_found_in_the_dialog_is_booked_with_enter(E2EBrowser browser) {
    runAsUser(browser, EMPLOYEE, "/dailyreport/timereports/new?date=" + DAY, page -> {
      saveAsFavourite(page);
      page.navigate(urlWithLogin("/dailyreport/daily?mode=daily&date=" + DAY, EMPLOYEE));
      assertThat(bookingsWithComment(page)).hasCount(1);

      page.locator("#daily-favourites-panel .card-actions button").click();
      Locator search = page.locator("#favoritesSearch");
      assertThat(search).isFocused();

      search.fill("aus dem dialog");
      // arranging a filtered list would store an order nobody saw
      assertThat(page.locator("#favoritesDialogBody [data-filter-disables]")).isDisabled();
      assertThat(page.locator("#favoritesDialogBody [data-favorite-pick]:visible")).hasCount(1);

      search.press("Enter");
      page.locator("#favoritesDialog").waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.HIDDEN));
      assertThat(bookingsWithComment(page)).hasCount(2);
    });
  }

  private void saveAsFavourite(Page page) {
    page.fill("#durationTime", "00:45");
    selectTomSelectOption(page, "suborderId", E2ETestData.SUBORDER_ALPHA_DEV_SIGN);
    page.fill("#commentField", COMMENT);
    page.getByText("Als Favorit speichern").click();
    page.locator("#saveAsFavSection button[type=submit]").click();
    page.waitForLoadState();
  }

  private Locator bookingsWithComment(Page page) {
    return page.locator("#daily-bookings-area tbody tr").filter(new Locator.FilterOptions().setHasText(COMMENT));
  }
}
