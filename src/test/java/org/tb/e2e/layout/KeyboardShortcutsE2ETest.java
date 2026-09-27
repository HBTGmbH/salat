package org.tb.e2e.layout;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Request;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.regex.Pattern;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.tb.common.GlobalConstants;
import org.tb.e2e.E2EBrowser;
import org.tb.e2e.E2ETestData;
import org.tb.e2e.PlaywrightE2ETestBase;

/**
 * The keyboard shortcuts for booking (#1016): {@code ?} opens their overview, {@code i} a new
 * booking with the day of the page, {@code Ctrl+Enter} saves the booking form from any of its
 * fields. The single keys are text while the focus is in a field.
 *
 * <p>Books on days no other E2E class uses: the bookings stay in the shared database.
 */
class KeyboardShortcutsE2ETest extends PlaywrightE2ETestBase {

  private static final String MANAGER = E2ETestData.EMPLOYEE_BL_SIGN;
  private static final LocalDate DAY = LocalDate.parse("2026-06-03");
  private static final LocalDate OTHER_DAY = LocalDate.parse("2026-06-02");
  private static final String SAVE = "ControlOrMeta+Enter";

  @ParameterizedTest(name = "{0}")
  @MethodSource("org.tb.e2e.PlaywrightE2ETestBase#browsers")
  void the_question_mark_opens_the_overview_of_every_shortcut(E2EBrowser browser) {
    runAsUser(browser, MANAGER, "/dailyreport/dashboard", page -> {
      leaveFields(page);

      page.keyboard().press("?");

      Locator overview = overview(page);
      assertThat(overview).isVisible();
      // the palette's shortcut, the two single keys, saving and the arrow keys of the time field
      assertThat(overview).containsText("Befehlspalette");
      assertThat(overview.locator("kbd").filter(new Locator.FilterOptions().setHasText("K"))).hasCount(1);
      assertThat(overview.locator("kbd").filter(new Locator.FilterOptions().setHasText(Pattern.compile("^\\?$"))))
          .hasCount(1);
      assertThat(overview.locator("kbd").filter(new Locator.FilterOptions().setHasText(Pattern.compile("^i$"))))
          .hasCount(1);
      assertThat(overview).containsText("Speichern, auch mit dem Fokus im Kommentar");
      assertThat(overview).containsText("Eine Stunde vor oder zurück");
      // Bootstrap takes Esc only once the modal is in place, and then it holds the focus
      assertThat(overview).isFocused();
      // the modifier is named for the system the browser runs on, never left empty
      String modifier = overview.locator("kbd[data-platform-label]").first().textContent();
      assertTrue(modifier.equals("Ctrl") || modifier.equals("⌘"), modifier);

      page.keyboard().press("Escape");
      assertThat(overview).isHidden();
    });
  }

  /** The entry in the footer opens it as well, and Esc hands the focus back to that entry. */
  @ParameterizedTest(name = "{0}")
  @MethodSource("org.tb.e2e.PlaywrightE2ETestBase#browsers")
  void the_footer_entry_opens_it_and_gets_the_focus_back(E2EBrowser browser) {
    runAsUser(browser, MANAGER, "/dailyreport/dashboard", page -> {
      Locator entry = page.locator("[data-shortcut-help-open]");
      entry.click();
      assertThat(overview(page)).isFocused();

      page.keyboard().press("Escape");

      assertThat(overview(page)).isHidden();
      assertThat(entry).isFocused();
    });
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("org.tb.e2e.PlaywrightE2ETestBase#browsers")
  void the_palette_offers_the_overview(E2EBrowser browser) {
    runAsUser(browser, MANAGER, "/dailyreport/dashboard", page -> {
      page.keyboard().press("ControlOrMeta+k");
      page.locator("#commandPaletteInput").fill("tasten");
      assertThat(page.locator("#commandPaletteList [role=option]").first()).containsText("Tastenkürzel");

      page.keyboard().press("Enter");

      assertThat(overview(page)).isVisible();
    });
  }

  /** The customer list puts the focus into its filter field: there both single keys are text. */
  @ParameterizedTest(name = "{0}")
  @MethodSource("org.tb.e2e.PlaywrightE2ETestBase#browsers")
  void in_a_field_the_single_keys_are_text(E2EBrowser browser) {
    runAsUser(browser, MANAGER, "/customers", page -> {
      Locator filter = page.locator("[name=fCustomerFilter]");
      assertThat(filter).isFocused();

      page.keyboard().press("?");
      page.keyboard().press("i");

      assertThat(filter).hasValue("?i");
      assertThat(overview(page)).isHidden();
      assertThat(page).hasURL(Pattern.compile(".*/customers.*"));
    });
  }

  /** i goes where the header button goes: the form for the day of the daily view, and back to it. */
  @ParameterizedTest(name = "{0}")
  @MethodSource("org.tb.e2e.PlaywrightE2ETestBase#browsers")
  void i_opens_a_new_booking_with_the_day_of_the_page(E2EBrowser browser) {
    runAsUser(browser, MANAGER, "/dailyreport/daily?mode=daily&date=" + DAY, page -> {
      String target = (String) page.evaluate("() => document.getElementById('header-new-booking').href");
      assertTrue(target.contains("date=" + DAY), target);
      leaveFields(page);

      page.keyboard().press("i");

      page.waitForURL(target);
      assertThat(page.locator("#referenceday")).hasValue(DAY.toString());
    });
  }

  /** The booking form has no header button, so there i has nothing to follow. */
  @ParameterizedTest(name = "{0}")
  @MethodSource("org.tb.e2e.PlaywrightE2ETestBase#browsers")
  void i_does_nothing_on_the_booking_form(E2EBrowser browser) {
    runAsUser(browser, MANAGER, "/dailyreport/timereports/new?date=" + DAY, page -> {
      String before = page.url();
      leaveFields(page);

      page.keyboard().press("i");
      page.waitForTimeout(300);

      assertEquals(before, page.url());
    });
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("org.tb.e2e.PlaywrightE2ETestBase#browsers")
  void ctrl_enter_saves_the_booking_from_the_comment(E2EBrowser browser) {
    String comment = "Tastenkuerzel Speichern aus dem Kommentar " + browser;
    runAsUser(browser, MANAGER, "/dailyreport/timereports/new?date=" + DAY, page -> {
      selectTomSelectOption(page, "suborderId", GlobalConstants.SUBRORDER_SIGN_TRAINING);
      page.fill("#durationTime", "00:30");
      page.locator("#commentField").fill(comment);
      assertThat(page.locator("#commentField")).isFocused();

      page.keyboard().press(SAVE);

      page.waitForURL(Pattern.compile(".*/dailyreport/daily.*date=" + DAY + ".*"));
      assertThat(page.locator("body")).containsText(comment);
    });
  }

  /**
   * What is typed but not yet taken goes along: the ticket field keeps its text until it loses the
   * focus, and the duration is put into its form on blur. Both are in the request that saves.
   */
  @ParameterizedTest(name = "{0}")
  @MethodSource("org.tb.e2e.PlaywrightE2ETestBase#browsers")
  void ctrl_enter_takes_along_what_is_still_being_typed(E2EBrowser browser) {
    runAsUser(browser, MANAGER, "/dailyreport/timereports/new?date=" + OTHER_DAY, page -> {
      selectTomSelectOption(page, "suborderId", GlobalConstants.SUBRORDER_SIGN_TRAINING);
      page.fill("#durationTime", "1,5");
      page.locator("#commentField").fill("Tastenkuerzel Eingabe im Gange " + browser);
      page.locator("#ticketReference ~ .ts-wrapper .ts-control").click();
      page.locator("#ticketReference-ts-control").pressSequentially("EXTERN-7");

      // the form itself, not one of the htmx updates the leaving of a field sets off
      Request saved = page.waitForRequest(
          request -> "POST".equals(request.method()) && request.headers().get("hx-request") == null
              && request.url().matches(".*/dailyreport/timereports(\\?.*)?$"),
          () -> page.keyboard().press(SAVE));

      String body = URLDecoder.decode(saved.postData(), StandardCharsets.UTF_8);
      assertTrue(body.contains("ticketReference=EXTERN-7"), body);
      assertTrue(body.contains("durationTime=01:30"), body);
      page.waitForURL(Pattern.compile(".*/dailyreport/daily.*"));
    });
  }

  private static Locator overview(Page page) {
    return page.locator("#shortcutHelp");
  }

  /** The single keys act only outside a field; most pages put the entry focus into one. */
  private static void leaveFields(Page page) {
    page.evaluate("() => document.activeElement && document.activeElement.blur()");
  }
}
