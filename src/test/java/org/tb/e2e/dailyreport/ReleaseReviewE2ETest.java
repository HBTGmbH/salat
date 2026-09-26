package org.tb.e2e.dailyreport;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import java.time.LocalDate;
import java.util.regex.Pattern;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.tb.common.test.FixedClock;
import org.tb.e2e.E2EBrowser;
import org.tb.e2e.E2ETestData;
import org.tb.e2e.PlaywrightE2ETestBase;

/**
 * The overview before a release (#760): the month is chosen on {@code /release}, the overview
 * shows the bookings of the period — by order first, by day on request — with the findings at
 * their day, and the release happens from there, without a confirmation dialog.
 *
 * <p>Every case runs on people of its own, seeded with their bookings by {@link E2ETestData}, so
 * no other class sees their periods and ema's June stays untouched. The first four cases only look
 * at {@link E2ETestData#EMPLOYEE_REVIEWED_SIGN}'s September and October; the last one books,
 * edits and releases, on a person of its own per browser, because a period can be released only
 * once. The clock stands after all of these periods — at the class default the months would lie
 * in the future.
 */
@FixedClock("2026-12-01T09:00:00")
class ReleaseReviewE2ETest extends PlaywrightE2ETestBase {

  private static final String REVIEWED = E2ETestData.EMPLOYEE_REVIEWED_SIGN;
  private static final String ALPHA_DEV = E2ETestData.CUSTOMERORDER_CONTOSO_SIGN + "/"
      + E2ETestData.SUBORDER_ALPHA_DEV_SIGN;
  private static final String GLOBEX_CONSULT = E2ETestData.CUSTOMERORDER_GLOBEX_SIGN + "/"
      + E2ETestData.SUBORDER_GLOBEX_CONSULT_SIGN;
  private static final String MISSING_BOOKING_TEXT = "04.09.2026: Es fehlen Buchungen für den Arbeitstag.";

  @ParameterizedTest(name = "{0}")
  @MethodSource("org.tb.e2e.PlaywrightE2ETestBase#browsers")
  void the_overview_lists_the_bookings_by_order_with_their_sum_and_full_comment(E2EBrowser browser) {
    runAsUser(browser, REVIEWED, "/release", page -> {
      page.fill("#release-until", E2ETestData.REVIEWED_MONTH);
      page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Freigabe prüfen")).click();

      assertThat(page).hasURL(Pattern.compile(".*/release/review\\?until=2026-10$"));
      assertThat(page.locator("#review-summary")).containsText(E2ETestData.EMPLOYEE_REVIEWED_NAME + " | " + REVIEWED);
      assertThat(page.locator("#review-summary")).containsText("01.09.2026 – 31.10.2026");
      // booked working time of the period: 4 + 2 + 6 + 3 + 5 hours
      assertThat(page.locator("#review-summary")).containsText("20:00");

      // by order is the default view
      assertThat(viewLink(page, "Nach Auftrag")).hasAttribute("aria-current", "page");
      assertThat(orderGroup(page, ALPHA_DEV)).containsText("3 Buchungen");
      assertThat(orderGroup(page, ALPHA_DEV)).containsText("13:00");
      assertThat(orderGroup(page, GLOBEX_CONSULT)).containsText("2 Buchungen");
      assertThat(orderGroup(page, GLOBEX_CONSULT)).containsText("7:00");

      // the comment is not cut off and keeps its line breaks
      Locator comment = orderGroup(page, ALPHA_DEV).getByText("Schnittstelle zum Abrechnungssystem");
      assertThat(comment).isVisible();
      assertEquals(E2ETestData.REVIEWED_LONG_COMMENT, comment.innerText());
    });
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("org.tb.e2e.PlaywrightE2ETestBase#browsers")
  void the_day_view_shows_the_finding_at_its_day_and_the_release_waits_for_it(E2EBrowser browser) {
    runAsUser(browser, REVIEWED, reviewUrl(), page -> {
      viewLink(page, "Nach Tag").click();

      assertThat(page).hasURL(Pattern.compile(".*/release/review\\?until=2026-10&view=day#review-views$"));
      assertThat(viewLink(page, "Nach Tag")).hasAttribute("aria-current", "page");
      assertThat(heading(page, "September 2026")).isVisible();
      assertThat(heading(page, "Oktober 2026")).isVisible();

      // the finding stands at its day, which offers to book it
      Locator missingDay = day(page, E2ETestData.REVIEWED_DAY_WITHOUT_BOOKING);
      assertThat(missingDay.locator(".text-danger")).hasText(MISSING_BOOKING_TEXT);
      assertThat(missingDay).containsText("Keine Buchung");
      assertThat(missingDay.getByRole(AriaRole.LINK, new Locator.GetByRoleOptions().setName("Buchung anlegen: Fr. 04.09.2026")))
          .isVisible();
      // neither a short day, nor a public holiday, nor a day not worked is a finding
      assertThat(day(page, E2ETestData.REVIEWED_SHORT_DAY)).containsText("3:00");
      assertThat(day(page, E2ETestData.REVIEWED_SHORT_DAY).locator(".text-danger")).hasCount(0);
      assertThat(day(page, E2ETestData.HOLIDAY_ON_WEEKDAY)).containsText("E2E-Feiertag am Werktag");
      assertThat(day(page, E2ETestData.HOLIDAY_ON_WEEKDAY).locator(".text-danger")).hasCount(0);
      assertThat(day(page, E2ETestData.REVIEWED_NOT_WORKED_DAY)).containsText("Nicht gearbeitet");
      assertThat(day(page, E2ETestData.REVIEWED_NOT_WORKED_DAY)).not().containsText("Keine Buchung");

      // the summary above both views leads to the day
      assertThat(page.locator("#review-findings")).containsText("Ein Befund an einem Tag");
      assertThat(page.locator("#review-findings a")).hasAttribute("href",
          Pattern.compile(".*/release/review\\?until=2026-10&view=day#day-2026-09-04$"));

      // the button waits for the finding and says why
      Locator release = releaseButton(page);
      assertThat(release).isDisabled();
      assertThat(release).hasAttribute("aria-describedby", "review-action-blocked");
      assertThat(page.locator("#review-action-blocked"))
          .containsText("Freigeben ist möglich, sobald keine Befunde mehr offen sind.");
    });
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("org.tb.e2e.PlaywrightE2ETestBase#browsers")
  void the_daily_view_of_a_day_leads_back_to_the_overview(E2EBrowser browser) {
    runAsUser(browser, REVIEWED, reviewUrl() + "&view=day", page -> {
      day(page, E2ETestData.REVIEWED_DAY_WITHOUT_BOOKING)
          .getByRole(AriaRole.LINK, new Locator.GetByRoleOptions().setName("Fr. 04.09.2026").setExact(true))
          .click();

      assertThat(page).hasURL(Pattern.compile(".*/dailyreport/daily\\?.*date=2026-09-04.*"));
      Locator back = page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName("Zurück zur Übersicht"));
      back.click();

      assertThat(page).hasURL(Pattern.compile(".*/release/review\\?until=2026-10&view=day#day-2026-09-04$"));
      assertThat(day(page, E2ETestData.REVIEWED_DAY_WITHOUT_BOOKING)).containsText(MISSING_BOOKING_TEXT);
    });
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("org.tb.e2e.PlaywrightE2ETestBase#browsers")
  void a_people_lead_sees_the_same_overview_without_edit_or_create_links(E2EBrowser browser) {
    runAsUser(browser, E2ETestData.EMPLOYEE_PV_SIGN, "/acceptance", page -> {
      selectTomSelectOption(page, "employee-contract-select", E2ETestData.EMPLOYEE_REVIEWED_NAME);
      assertThat(page).hasURL(Pattern.compile(".*/acceptance\\?.*fAcceptanceEmployeeContractId=\\d+.*"));
      page.fill("#acceptance-release-until", E2ETestData.REVIEWED_MONTH);
      page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Freigabe prüfen")).click();

      assertThat(page).hasURL(Pattern.compile(".*/acceptance/release/review\\?contractId=\\d+&until=2026-10$"));
      assertThat(page.locator("#review-summary")).containsText(E2ETestData.EMPLOYEE_REVIEWED_NAME + " | " + REVIEWED);
      assertThat(page.locator("#review-summary")).containsText("01.09.2026 – 31.10.2026");
      assertThat(orderGroup(page, ALPHA_DEV)).containsText("13:00");
      assertThat(orderGroup(page, ALPHA_DEV).getByText("Schnittstelle zum Abrechnungssystem")).isVisible();
      // a people lead may not change open bookings, so there is nothing to edit
      assertThat(page.locator("a[href*='/edit']")).hasCount(0);

      viewLink(page, "Nach Tag").click();
      assertThat(page).hasURL(Pattern.compile(".*/acceptance/release/review\\?contractId=\\d+&until=2026-10&view=day#review-views$"));
      Locator missingDay = day(page, E2ETestData.REVIEWED_DAY_WITHOUT_BOOKING);
      assertThat(missingDay.locator(".text-danger")).hasText(MISSING_BOOKING_TEXT);
      assertThat(missingDay).containsText("Keine Buchung");
      // nor to create: the create links carry the contract, the menu's plain form link does not
      assertThat(page.locator("a[href*='/timereports/new?']")).hasCount(0);
      assertThat(page.locator("a[href*='/edit']")).hasCount(0);
      assertThat(releaseButton(page)).isDisabled();
    });
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("org.tb.e2e.PlaywrightE2ETestBase#browsers")
  void the_missing_day_is_booked_a_booking_edited_and_the_period_released_from_the_overview(E2EBrowser browser) {
    String employee = E2ETestData.releasingEmployeeSign(browser);
    LocalDate missingDay = E2ETestData.RELEASING_DAY_WITHOUT_BOOKING;
    String addedComment = "Nachgetragen aus der Übersicht";
    String editedComment = E2ETestData.RELEASING_EDITED_COMMENT + " und abgestimmt";

    runAsUser(browser, employee, "/release", page -> {
      page.fill("#release-until", E2ETestData.RELEASING_MONTH);
      page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Freigabe prüfen")).click();
      assertThat(page).hasURL(Pattern.compile(".*/release/review\\?until=2026-11$"));
      assertThat(page.locator("#review-summary")).containsText("23.11.2026 – 30.11.2026");
      assertThat(releaseButton(page)).isDisabled();

      // book the missing day from the day view: the form opens for that day and returns to it
      viewLink(page, "Nach Tag").click();
      day(page, missingDay)
          .getByRole(AriaRole.LINK, new Locator.GetByRoleOptions().setName("Buchung anlegen: Mi. 25.11.2026"))
          .click();
      assertThat(page).hasURL(Pattern.compile(".*/dailyreport/timereports/new\\?.*"));
      assertThat(page.locator("#referenceday")).hasValue(missingDay.toString());
      selectTomSelectOption(page, "suborderId", E2ETestData.SUBORDER_ALPHA_DEV_SIGN);
      page.fill("#durationTime", "4:00");
      page.fill("#commentField", addedComment);
      page.click("#timereportMainForm button[type=submit]");

      assertThat(page).hasURL(Pattern.compile(".*/release/review\\?until=2026-11&view=day#day-2026-11-25$"));
      assertThat(page.locator(".alert-success")).containsText("Buchung erstellt.");
      assertThat(day(page, missingDay)).containsText(addedComment);
      assertThat(day(page, missingDay)).not().containsText("Keine Buchung");
      assertThat(day(page, missingDay).locator(".text-danger")).hasCount(0);
      assertThat(page.locator("#review-findings")).hasCount(0);
      assertThat(releaseButton(page)).isEnabled();

      // edit a booking from the order view: back in the order view, at the booking
      viewLink(page, "Nach Auftrag").click();
      page.locator("tr").filter(new Locator.FilterOptions().setHasText(E2ETestData.RELEASING_EDITED_COMMENT))
          .locator("a[href*='/edit']")
          .click();
      assertThat(page).hasURL(Pattern.compile(".*/dailyreport/timereports/\\d+/edit\\?.*"));
      page.fill("#commentField", editedComment);
      page.click("#timereportMainForm button[type=submit]");

      assertThat(page).hasURL(Pattern.compile(".*/release/review\\?until=2026-11#tr-\\d+$"));
      assertThat(page.locator(".alert-success")).containsText("Buchung gespeichert.");
      assertThat(viewLink(page, "Nach Auftrag")).hasAttribute("aria-current", "page");
      assertThat(orderGroup(page, ALPHA_DEV)).containsText(editedComment);

      // release without a dialog: the toast names the period that was released
      releaseButton(page).click();

      assertThat(page).hasURL(Pattern.compile(".*/release$"));
      assertThat(page.locator("#confirmModal")).not().isVisible();
      assertThat(page.locator(".alert-success")).containsText("Buchungen vom 23.11.2026 bis 30.11.2026 freigegeben.");
      assertThat(page.locator("body")).containsText("2026-11-30");
    });
  }

  private static String reviewUrl() {
    return "/release/review?until=" + E2ETestData.REVIEWED_MONTH;
  }

  private static Locator viewLink(Page page, String name) {
    return page.locator("#review-views").getByRole(AriaRole.LINK, new Locator.GetByRoleOptions().setName(name));
  }

  private static Locator orderGroup(Page page, String completeOrderSign) {
    return page.locator(".card").filter(new Locator.FilterOptions()
        .setHas(page.locator("h3.card-title", new Page.LocatorOptions().setHasText(completeOrderSign))));
  }

  private static Locator heading(Page page, String name) {
    return page.getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setName(name).setExact(true));
  }

  private static Locator day(Page page, LocalDate date) {
    return page.locator("#day-" + date);
  }

  private static Locator releaseButton(Page page) {
    return page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Freigeben").setExact(true));
  }

}
