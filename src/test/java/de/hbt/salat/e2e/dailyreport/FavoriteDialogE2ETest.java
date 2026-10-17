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
  private static final LocalDate DAY_WHILE_FADING_IN = LocalDate.parse("2026-07-10");
  private static final String COMMENT_WHILE_FADING_IN = "E2E-Favorit beim Einblenden";

  @ParameterizedTest(name = "{0}")
  @MethodSource("de.hbt.salat.e2e.PlaywrightE2ETestBase#browsers")
  void a_favourite_found_in_the_dialog_is_booked_with_enter(E2EBrowser browser) {
    runAsUser(browser, EMPLOYEE, "/dailyreport/timereports/new?date=" + DAY, page -> {
      saveAsFavourite(page, COMMENT);
      page.navigate(urlWithLogin("/dailyreport/daily?mode=daily&date=" + DAY, EMPLOYEE));
      assertThat(bookingsWithComment(page, COMMENT)).hasCount(1);

      page.locator("#daily-favourites-panel a[href='#favoritesDialog']").click();
      Locator search = page.locator("#favoritesSearch");
      assertThat(search).isFocused();

      search.fill("aus dem dialog");
      // arranging a filtered list would store an order nobody saw
      assertThat(page.locator("#favoritesDialogBody [data-filter-disables]")).isDisabled();
      assertThat(page.locator("#favoritesDialogBody [data-favorite-pick]:visible")).hasCount(1);

      search.press("Enter");
      page.locator("#favoritesDialog").waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.HIDDEN));
      assertThat(bookingsWithComment(page, COMMENT)).hasCount(2);
    });
  }

  /**
   * Enter while the dialog is still fading in. Bootstrap ignores a hide during the transition: the booking was made,
   * the dialog stayed open. In Firefox the fade takes long enough for a quick Enter to land in it; stretched to a few
   * seconds here, it always does.
   */
  @ParameterizedTest(name = "{0}")
  @MethodSource("de.hbt.salat.e2e.PlaywrightE2ETestBase#browsers")
  void enter_while_the_dialog_is_still_fading_in_books_and_closes_it(E2EBrowser browser) {
    runAsUser(browser, EMPLOYEE, "/dailyreport/timereports/new?date=" + DAY_WHILE_FADING_IN, page -> {
      saveAsFavourite(page, COMMENT_WHILE_FADING_IN);
      page.navigate(urlWithLogin("/dailyreport/daily?mode=daily&date=" + DAY_WHILE_FADING_IN, EMPLOYEE));
      assertThat(bookingsWithComment(page, COMMENT_WHILE_FADING_IN)).hasCount(1);
      // the fade stretched to seconds, and the body of the dialog arriving in the middle of it: htmx focuses the
      // search field at once, and the Enter that follows lands while Bootstrap is still showing the dialog
      page.addStyleTag(new Page.AddStyleTagOptions()
          .setContent(".modal.fade .modal-dialog { transition: transform 4s linear !important; }"));
      page.route("**/favorites/dialog**", route -> {
        try {
          Thread.sleep(1_000);
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
        }
        route.resume();
      });

      page.locator("#daily-favourites-panel a[href='#favoritesDialog']").click();
      Locator search = page.locator("#favoritesSearch");
      assertThat(search).isFocused();
      search.fill("beim einblenden");
      assertThat(page.locator("#favoritesDialogBody [data-favorite-pick]:visible")).hasCount(1);
      assertThat(page.locator("#favoritesDialog .modal-dialog")).isVisible();

      search.press("Enter");
      page.locator("#favoritesDialog").waitFor(new Locator.WaitForOptions()
          .setState(WaitForSelectorState.HIDDEN).setTimeout(15_000));
      assertThat(bookingsWithComment(page, COMMENT_WHILE_FADING_IN)).hasCount(2);
    });
  }

  private void saveAsFavourite(Page page, String comment) {
    page.fill("#durationTime", "00:45");
    selectTomSelectOption(page, "suborderId", E2ETestData.SUBORDER_ALPHA_DEV_SIGN);
    page.fill("#commentField", comment);
    page.getByText("Als Favorit speichern").click();
    page.locator("#saveAsFavSection button[type=submit]").click();
    page.waitForLoadState();
  }

  private Locator bookingsWithComment(Page page, String comment) {
    return page.locator("#daily-bookings-area tbody tr").filter(new Locator.FilterOptions().setHasText(comment));
  }
}
