package org.tb.e2e.dailyreport;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.WaitForSelectorState;
import java.time.LocalDate;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.tb.e2e.E2EBrowser;
import org.tb.e2e.E2ETestData;
import org.tb.e2e.PlaywrightE2ETestBase;

/**
 * The shared confirmation dialog (#1032, ADR-0027) on the two deletions of the daily view. Both used
 * to have a hand-written modal of their own; the booking now posts through a plain form and the
 * favourite through HTMX, and both describe the one dialog over {@code data-confirm-*}.
 *
 * <p>What only a browser can show: that cancelling really leaves the record alone, that confirming
 * fires the action <b>once</b> — the handler stops the submit event in the capture phase and
 * re-triggers it with {@code requestSubmit()}, so an off-by-one here would delete twice or not at
 * all — and that the dialog names the record it is about. A list of bookings on one day differs in
 * order, duration and text alone.
 *
 * <p>The second half — confirming and the action happening — hangs on the favourite. Deleting a
 * booking is a soft-delete whose {@code @SQLDelete} names the table in lower case; H2 created it as
 * {@code "Timereport"} and refuses the statement, so that path cannot be walked to its end here
 * whatever the dialog does.
 *
 * <p>Its own day (see the class comment on {@link PlaywrightE2ETestBase}): the test creates and
 * removes its bookings again, but a day shared with another class would still mix them up.
 */
class ConfirmDialogE2ETest extends PlaywrightE2ETestBase {

  private static final String EMPLOYEE = E2ETestData.EMPLOYEE_MA_SIGN;
  private static final LocalDate DAY = LocalDate.parse("2026-06-22");
  private static final String COMMENT = "E2E-Bestaetigungsdialog";
  private static final String FAVOURITE_COMMENT = "E2E-Favorit zum Loeschen";

  @ParameterizedTest(name = "{0}")
  @MethodSource("org.tb.e2e.PlaywrightE2ETestBase#browsers")
  void deleting_a_booking_asks_first_and_names_the_booking(E2EBrowser browser) {
    runAsUser(browser, EMPLOYEE, newBooking(), page -> {
      book(page, "02:15", COMMENT, false);
      openDay(page);

      Locator bookings = page.locator("#daily-bookings-area");
      assertThat(bookings).containsText(COMMENT);

      // cancelling leaves the booking where it is
      deleteButtonOf(page, COMMENT).click();
      assertThat(confirmDialog(page)).containsText(E2ETestData.SUBORDER_ALPHA_DEV_SIGN);
      assertThat(confirmDialog(page)).containsText("2:15");
      assertThat(confirmDialog(page)).containsText(COMMENT);

      // the confirming button has the focus, so Enter answers the question that was asked
      assertThat(page.locator("#confirmModalAccept")).isFocused();
      // Escape cancels, as the native popup did
      page.keyboard().press("Escape");
      page.locator("#confirmModal").waitFor(
          new Locator.WaitForOptions().setState(WaitForSelectorState.HIDDEN));
      assertThat(bookings).containsText(COMMENT);
      // and the focus is back where the click came from, not lost at the top of the document
      assertThat(deleteButtonOf(page, COMMENT)).isFocused();

      // the cancel button does the same
      deleteButtonOf(page, COMMENT).click();
      cancelAction(page);
      assertThat(bookings).containsText(COMMENT);

      // that confirming really fires the action is covered by the favourite below: the soft-delete
      // of a booking cannot complete against the H2 test database (its @SQLDelete names the table
      // in lower case, H2 created it as "Timereport"), so it would fail here for a reason that has
      // nothing to do with the dialog
    });
  }

  /**
   * The favourite is the one confirmation that posts through HTMX. HTMX registers its trigger on
   * the form element itself, so the dialog only gets its say because the handler listens in the
   * capture phase — a bubble-phase listener would let the request go out unasked.
   */
  @ParameterizedTest(name = "{0}")
  @MethodSource("org.tb.e2e.PlaywrightE2ETestBase#browsers")
  void deleting_a_favourite_asks_first_although_it_posts_through_htmx(E2EBrowser browser) {
    runAsUser(browser, EMPLOYEE, newBooking(), page -> {
      book(page, "01:30", FAVOURITE_COMMENT, true);
      openDay(page);

      Locator panel = page.locator("#daily-favourites-panel");
      assertThat(panel).containsText(FAVOURITE_COMMENT);

      favouriteDeleteButton(page).click();
      assertThat(confirmDialog(page)).containsText(FAVOURITE_COMMENT);
      assertThat(confirmDialog(page)).containsText(E2ETestData.SUBORDER_ALPHA_DEV_SIGN);
      cancelAction(page);
      assertThat(panel).containsText(FAVOURITE_COMMENT);

      favouriteDeleteButton(page).click();
      confirmAction(page);
      assertThat(page.locator("#daily-favourites-panel")).not().containsText(FAVOURITE_COMMENT);
    });
  }

  private String newBooking() {
    return "/dailyreport/timereports/new?date=" + DAY;
  }

  private void book(Page page, String duration, String comment, boolean asFavourite) {
    page.fill("#durationTime", duration);
    selectTomSelectOption(page, "suborderId", E2ETestData.SUBORDER_ALPHA_DEV_SIGN);
    page.fill("#commentField", comment);
    if (asFavourite) {
      // the second save action only appears once the panel is unfolded
      page.getByText("Als Favorit speichern").click();
      page.locator("#saveAsFavSection button[type=submit]").click();
    } else {
      page.locator("form button[type=submit]").first().click();
    }
    page.waitForLoadState();
  }

  private void openDay(Page page) {
    page.navigate(urlWithLogin("/dailyreport/daily?mode=daily&date=" + DAY, EMPLOYEE));
  }

  private Locator deleteButtonOf(Page page, String comment) {
    return page.locator("#daily-bookings-area tbody tr")
        .filter(new Locator.FilterOptions().setHasText(comment))
        .locator("form[data-confirm] button[type=submit]");
  }

  private Locator favouriteDeleteButton(Page page) {
    return page.locator("#daily-favourites-panel .list-group-item")
        .filter(new Locator.FilterOptions().setHasText(FAVOURITE_COMMENT))
        .locator("form[data-confirm] button[type=submit]");
  }

}
