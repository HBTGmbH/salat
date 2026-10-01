package de.hbt.salat.e2e.layout;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.microsoft.playwright.Clock;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.LoadState;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.TextStyle;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import de.hbt.salat.common.GlobalConstants;
import de.hbt.salat.e2e.E2EBrowser;
import de.hbt.salat.e2e.E2ETestData;
import de.hbt.salat.common.util.ClockProvider;
import de.hbt.salat.common.util.DateUtils;
import de.hbt.salat.e2e.PlaywrightE2ETestBase;

/**
 * The command palette (#1155): opened by shortcut from any page, even with the focus in a field,
 * or from the entry in the header; it navigates to every page of the sidebar and jumps to a day.
 *
 * <p>The palette counts its days from the server's today, which the page carries. The expected
 * days are therefore computed from {@link DateUtils#today()} — the same fixed clock the server
 * renders with — and not from the clock of the machine running the browser.
 */
class CommandPaletteE2ETest extends PlaywrightE2ETestBase {

  private static final String MANAGER = E2ETestData.EMPLOYEE_BL_SIGN;
  private static final String SHORTCUT = "ControlOrMeta+k";

  static Stream<Arguments> browsersAndRoles() {
    return browsers().flatMap(browser -> Stream.of(
        E2ETestData.EMPLOYEE_MA_SIGN,
        E2ETestData.EMPLOYEE_PV_SIGN,
        E2ETestData.EMPLOYEE_BL_SIGN,
        E2ETestData.EMPLOYEE_RESTRICTED_SIGN).map(sign -> Arguments.of(browser, sign)));
  }

  /** The customer list puts the entry focus into its filter field, the palette's usual origin. */
  @ParameterizedTest(name = "{0}")
  @MethodSource("de.hbt.salat.e2e.PlaywrightE2ETestBase#browsers")
  void the_shortcut_opens_it_from_a_field_and_escape_hands_the_focus_back(E2EBrowser browser) {
    runAsUser(browser, MANAGER, "/customers", page -> {
      assertEquals("fCustomerFilter", page.evaluate("() => document.activeElement.name"));

      page.keyboard().press(SHORTCUT);

      assertThat(palette(page)).isVisible();
      assertEquals("commandPaletteInput", page.evaluate("() => document.activeElement.id"));
      // the letter typed right after the shortcut must land in the palette, not in the filter
      page.keyboard().type("m");
      assertThat(input(page)).hasValue("m");
      assertThat(page.locator("[name=fCustomerFilter]")).hasValue("");

      page.keyboard().press("Escape");

      assertThat(palette(page)).isHidden();
      assertThat(page.locator("[name=fCustomerFilter]")).isFocused();
    });
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("de.hbt.salat.e2e.PlaywrightE2ETestBase#browsers")
  void the_entry_in_the_header_opens_it(E2EBrowser browser) {
    runAsUser(browser, MANAGER, "/dailyreport/dashboard", page -> {
      page.locator("#header-command-palette").click();

      assertThat(palette(page)).isVisible();
      assertEquals("commandPaletteInput", page.evaluate("() => document.activeElement.id"));
    });
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("de.hbt.salat.e2e.PlaywrightE2ETestBase#browsers")
  void a_tap_opens_it_and_a_tap_on_a_hit_runs_it(E2EBrowser browser) {
    runOnTouchDevice(browser, MANAGER, "/dailyreport/dashboard", page -> {
      page.locator("#header-command-palette").tap();
      input(page).fill("mat");

      options(page).first().tap();

      page.waitForURL(Pattern.compile(".*/dailyreport/matrix.*"));
    });
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("de.hbt.salat.e2e.PlaywrightE2ETestBase#browsers")
  void three_letters_and_enter_lead_to_a_page(E2EBrowser browser) {
    runAsUser(browser, MANAGER, "/dailyreport/dashboard", page -> {
      page.keyboard().press(SHORTCUT);
      input(page).fill("mat");

      assertThat(options(page).first()).containsText("Matrixübersicht");
      assertThat(options(page).first().locator("mark")).hasText("Mat");
      page.keyboard().press("Enter");

      page.waitForURL(Pattern.compile(".*/dailyreport/matrix.*"));
    });
  }

  /** "fr" is a page and a day at once; the arrow key picks the day, and the line names it. */
  @ParameterizedTest(name = "{0}")
  @MethodSource("de.hbt.salat.e2e.PlaywrightE2ETestBase#browsers")
  void a_day_jump_names_the_resolved_day_and_opens_its_daily_view(E2EBrowser browser) {
    runAsUser(browser, MANAGER, "/dailyreport/dashboard", page -> {
      page.keyboard().press(SHORTCUT);
      input(page).fill("fr");

      LocalDate friday = DateUtils.today().with(TemporalAdjusters.previousOrSame(DayOfWeek.FRIDAY));
      assertThat(options(page).first()).containsText("Freigabe");
      assertThat(options(page).nth(1)).containsText("Einzelübersicht · " + shown(friday));
      page.keyboard().press("ArrowDown");
      assertThat(options(page).nth(1)).hasAttribute("aria-selected", "true");
      assertEquals(options(page).nth(1).getAttribute("id"),
          input(page).getAttribute("aria-activedescendant"));

      page.keyboard().press("Enter");

      page.waitForURL(Pattern.compile(".*/dailyreport/daily\\?mode=daily&date=" + friday + "$"));
    });
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("de.hbt.salat.e2e.PlaywrightE2ETestBase#browsers")
  void the_words_for_a_day_resolve_against_the_servers_today(E2EBrowser browser) {
    runAsUser(browser, MANAGER, "/dailyreport/dashboard", page -> {
      LocalDate today = DateUtils.today();
      assertEquals(today.toString(), page.evaluate("() => paletteToday(document.getElementById('commandPalette'))"));
      page.keyboard().press(SHORTCUT);
      Map<String, LocalDate> days = new LinkedHashMap<>();
      days.put("heute", today);
      days.put("gestern", today.minusDays(1));
      days.put("vorgestern", today.minusDays(2));
      days.put("morgen", today.plusDays(1));
      days.put("mo", today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)));
      days.put("sonntag", today.with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY)));
      days.put("12.9", LocalDate.of(today.getYear(), 9, 12));
      days.put("12.9.2025", LocalDate.of(2025, 9, 12));
      days.forEach((typed, day) -> {
        input(page).fill(typed);
        assertThat(options(page).filter(new Locator.FilterOptions().setHasText("Einzelübersicht · " + shown(day))))
            .hasCount(1);
      });
      input(page).fill("31.2.");
      assertThat(page.locator("[data-command-type=day]")).hasCount(0);

      // "modus" is a further word of the colour mode, but nothing visible of it matches — the day
      // the issue names for "mo" comes first
      input(page).fill("mo");
      assertThat(options(page).first()).hasAttribute("data-command-type", "day");
      @SuppressWarnings("unchecked")
      List<String> keys = (List<String>) page.evaluate(
          "() => Array.from(document.querySelectorAll('#commandPaletteList [role=option]')).map(o => o.dataset.commandKey)");
      assertTrue(keys.indexOf("/my-accounts") < keys.indexOf("theme-dark"),
          "letters in order on the label rank before a further word: " + keys);
    });
  }

  /**
   * The recent entries show up on an empty input, the latest first. A day jump is remembered as the
   * expression that was typed: a week later, the same entry names the day before that later day.
   */
  @ParameterizedTest(name = "{0}")
  @MethodSource("de.hbt.salat.e2e.PlaywrightE2ETestBase#browsers")
  void an_empty_input_offers_the_recently_used_commands_resolved_anew(E2EBrowser browser) {
    runAsUser(browser, MANAGER, "/dailyreport/dashboard", page -> {
      LocalDate today = DateUtils.today();
      runFromPalette(page, "mat");
      page.waitForURL(Pattern.compile(".*/dailyreport/matrix.*"));
      runFromPalette(page, "gestern");
      page.waitForURL(Pattern.compile(".*date=" + today.minusDays(1) + "$"));

      page.keyboard().press(SHORTCUT);

      assertThat(palette(page).locator(".command-palette-group")).hasText("Zuletzt verwendet");
      assertThat(options(page)).hasCount(2);
      assertThat(options(page).first()).containsText("Einzelübersicht · gestern");
      assertThat(options(page).first()).containsText(shown(today.minusDays(1)));
      assertThat(options(page).nth(1)).containsText("Matrixübersicht");

      // the server's day moves on a week; FixedClockExtension puts the clock back after the test
      LocalDate weekLater = today.plusDays(7);
      ZoneId zone = ZoneId.of(GlobalConstants.DEFAULT_TIMEZONE_ID);
      ClockProvider.setClock(java.time.Clock.fixed(weekLater.atTime(9, 0).atZone(zone).toInstant(), zone));
      page.navigate(urlWithLogin("/dailyreport/dashboard", MANAGER));
      page.keyboard().press(SHORTCUT);

      assertThat(options(page).first()).containsText("Einzelübersicht · gestern");
      assertThat(options(page).first()).containsText(shown(weekLater.minusDays(1)));
    });
  }

  /**
   * The server's day is taken when the page loads and carried forward with the browser's clock: a
   * tab left open past midnight counts from the new day, not from the one it was rendered on.
   */
  @ParameterizedTest(name = "{0}")
  @MethodSource("de.hbt.salat.e2e.PlaywrightE2ETestBase#browsers")
  void a_tab_left_open_past_midnight_counts_from_the_new_day(E2EBrowser browser) {
    runAsUser(browser, MANAGER, "/dailyreport/dashboard", page -> {
      LocalDate today = DateUtils.today();
      page.clock().install(new Clock.InstallOptions().setTime(System.currentTimeMillis()));
      page.clock().fastForward("24:00:00");

      page.keyboard().press(SHORTCUT);
      input(page).fill("heute");

      assertThat(options(page).first()).containsText("Einzelübersicht · " + shown(today.plusDays(1)));
    });
  }

  /**
   * The page's own hits come without a request: pages are read from the sidebar, days in the
   * browser, and they are there before any answer could be. Only the business objects are asked
   * for, at the object search (#1157) — and nothing else.
   */
  @ParameterizedTest(name = "{0}")
  @MethodSource("de.hbt.salat.e2e.PlaywrightE2ETestBase#browsers")
  void its_own_hits_send_no_request_and_the_objects_only_the_search(E2EBrowser browser) {
    runAsUser(browser, MANAGER, "/dailyreport/dashboard", page -> {
      page.waitForLoadState(LoadState.NETWORKIDLE);
      List<String> requests = new ArrayList<>();
      page.onRequest(request -> requests.add(request.url()));

      page.keyboard().press(SHORTCUT);
      for (String typed : List.of("mat", "fr", "gestern", "12.9.", "dunkel", "")) {
        input(page).fill(typed);
        // there at once, before the object search has even been asked
        assertTrue(options(page).count() > 0, typed);
      }
      input(page).fill("contoso");
      assertThat(page.locator("#commandPaletteList [data-command-type=object]").first()).isVisible();
      page.keyboard().press("Escape");

      assertTrue(requests.stream().allMatch(url -> url.contains("/palette/search?q=")), requests::toString);
    });
  }

  /**
   * On a fresh browser there is nothing recent yet, and the empty input lists every page there is.
   * That list must be exactly the sidebar the login sees — no entry the role does not get.
   */
  @ParameterizedTest(name = "{0} {1}")
  @MethodSource("browsersAndRoles")
  void it_offers_every_sidebar_entry_of_the_role_and_no_other(E2EBrowser browser, String sign) {
    runAsUser(browser, sign, "/dailyreport/dashboard", page -> {
      @SuppressWarnings("unchecked")
      List<String> sidebar = (List<String>) page.evaluate(
          "() => Array.from(document.querySelectorAll('#sidebar-menu .dropdown-item[href]'))"
              + ".map(a => a.getAttribute('href'))");

      page.keyboard().press(SHORTCUT);

      assertThat(palette(page).locator(".command-palette-group").first()).hasText("Seiten");
      assertThat(palette(page).locator(".command-palette-group").nth(1)).hasText("Einstellungen");
      @SuppressWarnings("unchecked")
      List<String> offered = (List<String>) page.evaluate(
          "() => Array.from(document.querySelectorAll('#commandPaletteList [data-command-type=nav]'))"
              + ".map(o => o.dataset.commandKey)");
      assertEquals(sidebar, offered);
      assertTrue(sidebar.contains("/dailyreport/daily"), "every role has the daily view");
    });
  }

  /**
   * "Neue Buchung" goes where the header button goes, which carries the page's day and way back
   * (#1156) — not to the sidebar link, which always opens today's form.
   */
  @ParameterizedTest(name = "{0}")
  @MethodSource("de.hbt.salat.e2e.PlaywrightE2ETestBase#browsers")
  void new_booking_takes_the_target_of_the_header_button(E2EBrowser browser) {
    runAsUser(browser, MANAGER, "/dailyreport/daily?mode=daily&date=2026-06-10", page -> {
      String headerTarget = (String) page.evaluate("() => document.getElementById('header-new-booking').href");
      assertTrue(headerTarget.contains("date=2026-06-10"), headerTarget);

      runFromPalette(page, "neue buchung");

      page.waitForURL(headerTarget);
    });
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("de.hbt.salat.e2e.PlaywrightE2ETestBase#browsers")
  void the_colour_mode_is_switched_from_the_palette(E2EBrowser browser) {
    runAsUser(browser, MANAGER, "/dailyreport/dashboard", page -> {
      page.keyboard().press(SHORTCUT);
      input(page).fill("hell");
      // the mode that is already on is not offered
      assertThat(options(page)).hasCount(0);
      assertThat(page.locator("#commandPaletteEmpty")).isVisible();

      input(page).fill("dunkel");
      page.keyboard().press("Enter");

      assertThat(page.locator("html")).hasAttribute("data-bs-theme", "dark");
      assertThat(palette(page)).isHidden();
    });
  }

  /** The readers behind the hits, called directly: they are plain functions of salat.js. */
  @ParameterizedTest(name = "{0}")
  @MethodSource("de.hbt.salat.e2e.PlaywrightE2ETestBase#browsers")
  void the_day_reader_and_the_ranking(E2EBrowser browser) {
    runAsUser(browser, MANAGER, "/dailyreport/dashboard", page -> {
      String vocabulary = "{ offsets: [['heute', 0], ['gestern', -1], ['vorgestern', -2], ['morgen', 1]],"
          + " weekdays: ['Montag','Dienstag','Mittwoch','Donnerstag','Freitag','Samstag','Sonntag'] }";
      // Monday, 2026-09-28 — the examples of the issue
      Map.of(
          "gestern", "2026-09-27",
          "vorgestern", "2026-09-26",
          "morgen", "2026-09-29",
          "fr", "2026-09-25",
          "mo", "2026-09-28",
          "12.9", "2026-09-12",
          "12.9.2025", "2025-09-12",
          "1.1.27", "2027-01-01",
          "2026-02-28", "2026-02-28").forEach((typed, expected) -> assertEquals(expected, page.evaluate(
          "([typed, vocabulary]) => paletteParseDay(typed, '2026-09-28', eval('(' + vocabulary + ')'))"
              + ".map(d => d.iso).join()", List.of(typed, vocabulary)), typed));
      // a month change and a year change backwards
      assertEquals("2026-02-27", page.evaluate(
          "v => paletteParseDay('fr', '2026-03-02', eval('(' + v + ')'))[0].iso", vocabulary));
      assertEquals("2025-12-31", page.evaluate(
          "v => paletteParseDay('gestern', '2026-01-01', eval('(' + v + ')'))[0].iso", vocabulary));
      for (String nothing : List.of("31.2.", "29.2.2026", "m", "he", "xyz", "0.1.", "1.1.0500", "0999-12-31")) {
        assertEquals("", page.evaluate(
            "([typed, v]) => paletteParseDay(typed, '2026-09-28', eval('(' + v + ')')).map(d => d.iso).join()",
            List.of(nothing, vocabulary)), nothing);
      }

      // word start before a hit inside a word before letters in order
      assertEquals(List.of(3, 2, 1, 0), page.evaluate(
          "() => [paletteMatch('Matrixübersicht', 'mat'), paletteMatch('Matrixübersicht', 'uber'),"
              + " paletteMatch('Matrixübersicht', 'mxs'), paletteMatch('Matrixübersicht', 'xyz')]"
              + ".map(m => m ? m.tier : 0)"));
      assertEquals(List.of(List.of(6, 10)), page.evaluate(
          "() => paletteMatch('Matrixübersicht', 'uber').ranges"));
      assertEquals(3, page.evaluate("() => paletteMatch('Neue Buchung', 'buch').tier"));
    });
  }

  /** On a phone there is no Esc; the close button is the way out without running a command. */
  @ParameterizedTest(name = "{0}")
  @MethodSource("de.hbt.salat.e2e.PlaywrightE2ETestBase#browsers")
  void the_close_button_closes_it_on_a_touch_device(E2EBrowser browser) {
    runOnTouchDevice(browser, MANAGER, "/dailyreport/dashboard", page -> {
      page.locator("#header-command-palette").tap();
      assertThat(palette(page)).isVisible();

      palette(page).locator("[data-command-palette-close]").tap();

      assertThat(palette(page)).isHidden();
    });
  }

  /**
   * Selecting the typed text with the mouse and letting go beyond the edge is no click on the
   * backdrop, although the browser delivers it to the dialog; a real one closes it.
   */
  @ParameterizedTest(name = "{0}")
  @MethodSource("de.hbt.salat.e2e.PlaywrightE2ETestBase#browsers")
  void only_a_press_on_the_backdrop_closes_it(E2EBrowser browser) {
    runAsUser(browser, MANAGER, "/dailyreport/dashboard", page -> {
      page.keyboard().press(SHORTCUT);
      input(page).fill("matrix");
      var box = input(page).boundingBox();

      page.mouse().move(box.x + box.width - 5, box.y + box.height / 2);
      page.mouse().down();
      page.mouse().move(box.x + 5, 5);
      page.mouse().up();
      assertThat(palette(page)).isVisible();

      page.mouse().click(5, 5);
      assertThat(palette(page)).isHidden();
    });
  }

  /**
   * With the dropdown of a TomSelect field open, the focus sits on the dropdown's search input,
   * which closes along with it. Esc hands the focus back to the field's control instead.
   */
  @ParameterizedTest(name = "{0}")
  @MethodSource("de.hbt.salat.e2e.PlaywrightE2ETestBase#browsers")
  void escape_hands_the_focus_back_to_a_tomselect_field(E2EBrowser browser) {
    runAsUser(browser, MANAGER, "/orders/employeeorders", page -> {
      page.locator(".ts-wrapper .ts-control").first().click();
      assertThat(page.locator(".ts-wrapper.dropdown-active")).hasCount(1);

      page.keyboard().press(SHORTCUT);
      page.keyboard().press("Escape");

      assertThat(palette(page)).isHidden();
      // the dialog fires "close" as a task of its own, after it is gone from the screen
      assertThat(page.locator(".ts-wrapper .ts-control").first()).isFocused();
      assertThat(page.locator(".ts-wrapper.dropdown-active")).hasCount(0);
    });
  }

  /**
   * The list views push their filter into the history, and htmx restores such a page from a copy of
   * its markup. The palette of the restored page has to work like the first one.
   */
  @ParameterizedTest(name = "{0}")
  @MethodSource("de.hbt.salat.e2e.PlaywrightE2ETestBase#browsers")
  void it_works_on_a_page_restored_from_the_history(E2EBrowser browser) {
    runAsUser(browser, MANAGER, "/orders/customerorders", page -> {
      page.keyboard().press(SHORTCUT);
      page.keyboard().press("Escape");
      page.locator("#advanced-toggle").check();
      Locator filter = page.locator("[name=fCustomerOrderFilter]");
      filter.fill("E2E");
      filter.press("Enter");
      page.waitForURL(Pattern.compile(".*fCustomerOrderFilter=E2E.*"));

      page.evaluate("() => window.paletteBeforeBack = document.getElementById('commandPalette')");
      page.goBack();
      // the address changes before htmx has put the copy in place; the new dialog is the sign
      page.waitForFunction("() => document.getElementById('commandPalette') !== window.paletteBeforeBack");
      page.keyboard().press(SHORTCUT);
      input(page).fill("mat");

      assertThat(options(page).first()).containsText("Matrixübersicht");
      page.keyboard().press("Enter");
      page.waitForURL(Pattern.compile(".*/dailyreport/matrix.*"));
    });
  }

  /**
   * Open while htmx took its snapshot, the palette comes back from that copy as an open dialog that
   * is no modal and has no listeners. It is dropped, and the header entry opens a working palette.
   */
  @ParameterizedTest(name = "{0}")
  @MethodSource("de.hbt.salat.e2e.PlaywrightE2ETestBase#browsers")
  void a_palette_open_in_the_history_snapshot_does_not_come_back(E2EBrowser browser) {
    runAsUser(browser, MANAGER, "/orders/customerorders", page -> {
      page.keyboard().press(SHORTCUT);
      // the filter answers while the palette is open, so the snapshot of the page left carries it
      page.evaluate("() => document.querySelector('form[data-filter-form]').requestSubmit()");
      page.waitForURL(Pattern.compile(".*fCustomerOrderShowInactive=.*"));
      page.keyboard().press("Escape");

      page.evaluate("() => window.paletteBeforeBack = document.getElementById('commandPalette')");
      page.goBack();
      page.waitForFunction("() => document.getElementById('commandPalette') !== window.paletteBeforeBack");

      // the copy is in place before it is cleaned up: hx-history-cache fires the restore event, on
      // which the palette is dropped, one animation frame after the swap (#1241). A single read can
      // fall in between, so wait for the dialog to close
      assertThat(page.locator("#commandPalette")).not().hasAttribute("open", Pattern.compile(".*"));
      page.locator("#header-command-palette").click();
      assertEquals(true, page.evaluate("() => document.getElementById('commandPalette').matches(':modal')"));
      input(page).fill("mat");
      assertThat(options(page).first()).containsText("Matrixübersicht");
    });
  }

  /** How a hit names a day: "Fr 12.06.2026", the weekday without its trailing dot. */
  private static String shown(LocalDate day) {
    return day.getDayOfWeek().getDisplayName(TextStyle.SHORT, Locale.GERMAN).replace(".", "")
        + " " + String.format("%02d.%02d.%d", day.getDayOfMonth(), day.getMonthValue(), day.getYear());
  }

  private static Locator palette(Page page) {
    return page.locator("#commandPalette");
  }

  private static Locator input(Page page) {
    return page.locator("#commandPaletteInput");
  }

  private static Locator options(Page page) {
    return page.locator("#commandPaletteList [role=option]");
  }

  private static void runFromPalette(Page page, String typed) {
    page.keyboard().press(SHORTCUT);
    input(page).fill(typed);
    // the hits are there before the next key could be typed; waiting for them keeps Enter honest
    assertThat(options(page).first()).isVisible();
    page.keyboard().press("Enter");
  }
}
