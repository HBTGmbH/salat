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
import java.util.ArrayList;
import java.util.List;
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
  private static final LocalDate THIRD_DAY = LocalDate.parse("2026-06-01");
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

  /** A ticket number that is still being typed is taken only when its field is left — or saved. */
  @ParameterizedTest(name = "{0}")
  @MethodSource("org.tb.e2e.PlaywrightE2ETestBase#browsers")
  void ctrl_enter_takes_along_a_ticket_still_being_typed(E2EBrowser browser) {
    runAsUser(browser, MANAGER, "/dailyreport/timereports/new?date=" + OTHER_DAY, page -> {
      selectTomSelectOption(page, "suborderId", GlobalConstants.SUBRORDER_SIGN_TRAINING);
      page.fill("#durationTime", "00:45");
      page.locator("#commentField").fill("Tastenkuerzel Ticket im Gange " + browser);
      page.locator("#ticketReference ~ .ts-wrapper .ts-control").click();
      page.locator("#ticketReference-ts-control").pressSequentially("EXTERN-7");

      String body = savedForm(page, () -> page.keyboard().press(SAVE));

      assertTrue(body.contains("ticketReference=EXTERN-7"), body);
      page.waitForURL(Pattern.compile(".*/dailyreport/daily.*"));
    });
  }

  /** "1,5" becomes 01:30 only when the duration field is left; saving from it has to do that. */
  @ParameterizedTest(name = "{0}")
  @MethodSource("org.tb.e2e.PlaywrightE2ETestBase#browsers")
  void ctrl_enter_takes_along_a_duration_still_being_typed(E2EBrowser browser) {
    runAsUser(browser, MANAGER, "/dailyreport/timereports/new?date=" + OTHER_DAY, page -> {
      selectTomSelectOption(page, "suborderId", GlobalConstants.SUBRORDER_SIGN_TRAINING);
      page.locator("#commentField").fill("Tastenkuerzel Dauer im Gange " + browser);
      page.fill("#durationTime", "1,5");
      assertThat(page.locator("#durationTime")).isFocused();

      String body = savedForm(page, () -> page.keyboard().press(SAVE));

      assertTrue(body.contains("durationTime=01:30"), body);
      page.waitForURL(Pattern.compile(".*/dailyreport/daily.*"));
    });
  }

  /** With the focus on the page, or beside the form, Ctrl+Enter saves the page's one form. */
  @ParameterizedTest(name = "{0}")
  @MethodSource("org.tb.e2e.PlaywrightE2ETestBase#browsers")
  void ctrl_enter_saves_with_the_focus_outside_the_form(E2EBrowser browser) {
    String comment = "Tastenkuerzel Speichern ohne Fokus im Formular " + browser;
    runAsUser(browser, MANAGER, "/dailyreport/timereports/new?date=" + THIRD_DAY, page -> {
      selectTomSelectOption(page, "suborderId", GlobalConstants.SUBRORDER_SIGN_TRAINING);
      page.fill("#durationTime", "00:15");
      page.locator("#commentField").fill(comment);
      leaveFields(page);

      page.keyboard().press(SAVE);

      page.waitForURL(Pattern.compile(".*/dailyreport/daily.*date=" + THIRD_DAY + ".*"));
      assertThat(page.locator("body")).containsText(comment);
    });
  }

  /** On a link the combination opens it in a new tab, as the browser does — the form is not saved. */
  @ParameterizedTest(name = "{0}")
  @MethodSource("org.tb.e2e.PlaywrightE2ETestBase#browsers")
  void ctrl_enter_on_a_link_keeps_what_the_browser_does(E2EBrowser browser) {
    runAsUser(browser, MANAGER, "/dailyreport/timereports/new?date=" + THIRD_DAY, page -> {
      String before = page.url();
      List<String> posts = new ArrayList<>();
      page.onRequest(request -> {
        if ("POST".equals(request.method()) && request.headers().get("hx-request") == null) posts.add(request.url());
      });
      page.locator("#timereportMainForm a.btn-secondary").focus();

      Page opened = page.context().waitForPage(() -> page.keyboard().press(SAVE));

      opened.waitForLoadState();
      assertTrue(opened.url().contains("/dailyreport/daily"), opened.url());
      assertEquals(before, page.url());
      assertEquals(List.of(), posts);
    });
  }

  /**
   * A held key sends repeats; the focus may be back in the form by then, and the form must not go
   * out twice. Checked on the handler itself: a repeat is swallowed, the first press submits.
   */
  @ParameterizedTest(name = "{0}")
  @MethodSource("org.tb.e2e.PlaywrightE2ETestBase#browsers")
  void a_held_ctrl_enter_saves_once(E2EBrowser browser) {
    runAsUser(browser, MANAGER, "/dailyreport/timereports/new?date=" + THIRD_DAY, page -> {
      page.locator("#commentField").focus();
      String press = "repeat => { const form = document.getElementById('timereportMainForm');"
          + " let submitted = false;"
          + " const onSubmit = e => { submitted = true; e.preventDefault(); };"
          + " form.addEventListener('submit', onSubmit, true);"
          + " const mac = IS_MAC;"
          + " const key = new KeyboardEvent('keydown', { key: 'Enter', ctrlKey: !mac, metaKey: mac, repeat, bubbles: true, cancelable: true });"
          + " document.getElementById('commentField').dispatchEvent(key);"
          + " form.removeEventListener('submit', onSubmit, true);"
          + " return [submitted, key.defaultPrevented]; }";

      assertEquals(List.of(false, true), page.evaluate(press, true));
      // the form is invalid here, so a real submit stops at the validation — the submit event of
      // requestSubmit comes only for a valid form; the handler reaching it is what counts
      selectTomSelectOption(page, "suborderId", GlobalConstants.SUBRORDER_SIGN_TRAINING);
      page.fill("#durationTime", "00:15");
      page.locator("#commentField").fill("Tastenkuerzel gehalten " + browser);
      assertEquals(List.of(true, true), page.evaluate(press, false));
    });
  }

  /**
   * Opened from the palette, the overview hands the focus back to where the palette came from —
   * here the booking form's order field, whose dropdown was open.
   *
   * <p>TomSelect handles the focus of a click one timer tick later, and that is when it opens the
   * dropdown. Chrome runs input sent in quick succession before that timer: without waiting, Ctrl+K
   * found the dropdown still closed, and the pending focus handling opened it only once the palette
   * had handed the focus back to the field — behind the overview, whose focus trap then lost the
   * focus to the closing dropdown (#1177).
   */
  @ParameterizedTest(name = "{0}")
  @MethodSource("org.tb.e2e.PlaywrightE2ETestBase#browsers")
  void the_overview_from_the_palette_gives_the_focus_back_to_the_field(E2EBrowser browser) {
    runAsUser(browser, MANAGER, "/dailyreport/timereports/new?date=" + THIRD_DAY, page -> {
      Locator wrapper = page.locator("#suborderId ~ .ts-wrapper");
      Locator order = wrapper.locator(".ts-control");
      Pattern open = Pattern.compile("\\bdropdown-active\\b");
      order.click();
      assertThat(wrapper).hasClass(open);
      page.keyboard().press("ControlOrMeta+k");
      page.locator("#commandPaletteInput").fill("tasten");
      assertThat(page.locator("#commandPaletteList [role=option]").first()).containsText("Tastenkürzel");
      page.keyboard().press("Enter");
      assertThat(overview(page)).isFocused();

      page.keyboard().press("Escape");

      assertThat(order).isFocused();
      assertThat(wrapper).not().hasClass(open);
    });
  }

  /**
   * Open while htmx took its history snapshot, the overview would come back from that copy shown
   * but without anything that could close it. It is dropped, and ? opens a working one.
   */
  @ParameterizedTest(name = "{0}")
  @MethodSource("org.tb.e2e.PlaywrightE2ETestBase#browsers")
  void an_overview_open_in_the_history_snapshot_does_not_come_back(E2EBrowser browser) {
    runAsUser(browser, MANAGER, "/orders/customerorders", page -> {
      leaveFields(page);
      page.keyboard().press("?");
      assertThat(overview(page)).isFocused();
      // the filter answers while the overview is open, so the snapshot of the page left carries it
      page.evaluate("() => document.querySelector('form[data-filter-form]').requestSubmit()");
      page.waitForURL(Pattern.compile(".*fCustomerOrderShowInactive=.*"));
      page.keyboard().press("Escape");
      assertThat(overview(page)).isHidden();

      page.evaluate("() => window.overviewBeforeBack = document.getElementById('shortcutHelp')");
      page.goBack();
      page.waitForFunction("() => document.getElementById('shortcutHelp') !== window.overviewBeforeBack");

      assertThat(page.locator(".modal.show")).hasCount(0);
      assertThat(page.locator(".modal-backdrop")).hasCount(0);
      assertEquals(false, page.evaluate("() => document.body.classList.contains('modal-open')"));
      leaveFields(page);
      page.keyboard().press("?");
      assertThat(overview(page)).isFocused();
      page.keyboard().press("Escape");
      assertThat(overview(page)).isHidden();
    });
  }

  /** The body of the form submission itself, not of one of the htmx updates leaving a field sets off. */
  private static String savedForm(Page page, Runnable action) {
    Request saved = page.waitForRequest(
        request -> "POST".equals(request.method()) && request.headers().get("hx-request") == null
            && request.url().matches(".*/dailyreport/timereports(\\?.*)?$"),
        action);
    return URLDecoder.decode(saved.postData(), StandardCharsets.UTF_8);
  }

  private static Locator overview(Page page) {
    return page.locator("#shortcutHelp");
  }

  /** The single keys act only outside a field; most pages put the entry focus into one. */
  private static void leaveFields(Page page) {
    page.evaluate("() => document.activeElement && document.activeElement.blur()");
  }
}
