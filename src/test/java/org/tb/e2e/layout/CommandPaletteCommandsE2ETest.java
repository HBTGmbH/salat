package org.tb.e2e.layout;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.tb.e2e.E2EBrowser;
import org.tb.e2e.E2ETestData;
import org.tb.e2e.PlaywrightE2ETestBase;

/**
 * Commands with parameters in the command palette (#1158): the readers of month and duration the
 * browser runs itself, and {@code buchen} with day, suborder, duration and comment up to the
 * prefilled booking form, whose focus then waits on "Speichern".
 *
 * <p>The class runs on the base class's day, Thursday 2026-06-25; the readers take their today as an
 * argument and are asked across a change of month and of year. Which values the server offers is
 * the providers' business and tested there, the last working day across a public holiday included.
 */
class CommandPaletteCommandsE2ETest extends PlaywrightE2ETestBase {

  private static final String SHORTCUT = "ControlOrMeta+k";
  /** The complete order sign, as the form and the chips show it. */
  private static final String ALPHA = E2ETestData.CUSTOMERORDER_CONTOSO_SIGN + "/" + E2ETestData.SUBORDER_ALPHA_DEV_SIGN;

  @ParameterizedTest(name = "{0}")
  @MethodSource("org.tb.e2e.PlaywrightE2ETestBase#browsers")
  void the_month_reader_counts_back_to_the_most_recent_month(E2EBrowser browser) {
    runAsUser(browser, E2ETestData.EMPLOYEE_MA_SIGN, "/dailyreport/dashboard", page -> {
      var expected = new LinkedHashMap<String, String>();
      // [typed, today] -> month
      expected.put("9|2026-09-28", "2026-09");
      expected.put("9/2026|2026-09-28", "2026-09");
      expected.put("9.26|2026-09-28", "2026-09");
      expected.put("2026-9|2026-09-28", "2026-09");
      expected.put("sep|2026-09-28", "2026-09");
      expected.put("September|2026-09-28", "2026-09");
      expected.put("mär|2026-09-28", "2026-03");
      // a month still to come this year is last year's
      expected.put("okt|2026-09-28", "2025-10");
      // change of month and of year
      expected.put("letzter|2026-10-01", "2026-09");
      expected.put("letzter|2027-01-10", "2026-12");
      expected.put("dez|2027-01-10", "2026-12");
      expected.put("12|2027-01-10", "2026-12");
      expected.put("1|2027-01-10", "2027-01");
      // nothing a month could be read from
      expected.put("13|2026-09-28", "");
      expected.put("ma|2026-09-28", "");
      expected.put("13/2026|2026-09-28", "");
      expected.put("xyz|2026-09-28", "");
      expected.forEach((input, month) -> assertEquals(month, page.evaluate(
          "([typed, today]) => paletteParseMonth(typed, today, paletteMonthVocabulary("
              + "document.getElementById('commandPalette'))).map(m => m.ym).join()",
          List.of(input.split("\\|"))), input));

      assertEquals("September 2026", page.evaluate("() => paletteFormatMonth('2026-09', 'de')"));
    });
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("org.tb.e2e.PlaywrightE2ETestBase#browsers")
  void the_duration_reader_takes_the_notations_of_the_time_field(E2EBrowser browser) {
    runAsUser(browser, E2ETestData.EMPLOYEE_MA_SIGN, "/dailyreport/dashboard", page -> {
      Map<String, Object> expected = new LinkedHashMap<>();
      expected.put("1:30", 90);
      expected.put("1h30", 90);
      expected.put("90m", 90);
      expected.put("1,5", 90);
      expected.put("1.5", 90);
      expected.put("2", 120);
      expected.put("24:00", 1440);
      expected.put("24:15", null);
      expected.put("0", null);
      expected.put("Review", null);
      expected.forEach((typed, minutes) -> assertEquals(minutes,
          page.evaluate("typed => paletteParseDuration(typed)", typed), typed));

      assertEquals("1:30", page.evaluate("() => paletteFormatDuration(90)"));
      assertEquals("0:15", page.evaluate("() => paletteFormatDuration(15)"));
    });
  }

  /** The day reader of #1155, now also for the day of a command: back across a change of month. */
  @ParameterizedTest(name = "{0}")
  @MethodSource("org.tb.e2e.PlaywrightE2ETestBase#browsers")
  void the_day_reader_goes_back_across_a_change_of_month(E2EBrowser browser) {
    runAsUser(browser, E2ETestData.EMPLOYEE_MA_SIGN, "/dailyreport/dashboard", page -> {
      String vocabulary = "paletteVocabulary(document.getElementById('commandPalette'))";
      assertEquals("2026-09-30", page.evaluate("() => paletteParseDay('gestern', '2026-10-01', " + vocabulary + ")[0].iso"));
      assertEquals("2026-09-25", page.evaluate("() => paletteParseDay('fr', '2026-10-01', " + vocabulary + ")[0].iso"));
    });
  }

  /**
   * Step by step, as the issue describes it: the command, the day, the suborder, the duration — each
   * taken with Tab and standing as a chip —, then the comment. The preview says what Enter does, and
   * Enter opens the form with all four filled in and the focus on "Speichern". Nothing is saved.
   */
  @ParameterizedTest(name = "{0}")
  @MethodSource("org.tb.e2e.PlaywrightE2ETestBase#browsers")
  void buchen_with_all_four_parameters_opens_the_prefilled_form(E2EBrowser browser) {
    runAsUser(browser, E2ETestData.EMPLOYEE_MA_SIGN, "/dailyreport/dashboard", page -> {
      page.keyboard().press(SHORTCUT);
      input(page).pressSequentially("buchen");
      assertThat(options(page).first()).hasAttribute("data-command-type", "verb");
      page.keyboard().press("Tab");
      assertThat(param(page)).hasText("Tag?");

      input(page).pressSequentially("gestern");
      page.keyboard().press("Tab");
      assertThat(param(page)).hasText("Unterauftrag?");

      input(page).pressSequentially("alpha");
      assertThat(options(page).first()).containsText(ALPHA);
      page.keyboard().press("Tab");
      assertThat(param(page)).hasText("Dauer?");

      input(page).pressSequentially("1,5");
      page.keyboard().press("Tab");
      assertThat(chips(page)).hasText(new String[] {"buchen", "Mi 24.06.", ALPHA, "1:30"});

      input(page).pressSequentially("Review Release");
      assertThat(page.locator("#commandPalettePreview [data-part=title]")).hasText("Buchungsformular öffnen");
      assertThat(page.locator("#commandPalettePreview [data-part=values]"))
          .hasText("Mi 24.06.2026 · " + ALPHA + " Entwicklung · 1:30 · „Review Release“");

      page.keyboard().press("Enter");

      page.waitForURL(Pattern.compile(".*/dailyreport/timereports/new\\?.*"));
      assertTrue(page.url().contains("date=2026-06-24"), page.url());
      assertTrue(page.url().contains("duration=1%3A30"), page.url());
      assertTrue(page.url().contains("focus=save"), page.url());
      assertThat(page.locator("[name=referenceday]")).hasValue("2026-06-24");
      assertThat(page.locator("[name=durationTime]")).hasValue("1:30");
      assertThat(page.locator("#commentField")).hasValue("Review Release");
      assertEquals(ALPHA + " · Entwicklung",
          page.evaluate("() => document.getElementById('suborderId').selectedOptions[0].text"));
      assertThat(page.locator("button[data-submit-shortcut]")).isFocused();
    });
  }

  /** In one go: every word that means exactly one value becomes a chip, the rest is the comment. */
  @ParameterizedTest(name = "{0}")
  @MethodSource("org.tb.e2e.PlaywrightE2ETestBase#browsers")
  void buchen_in_one_go_reads_the_words_in_the_order_of_the_parameters(E2EBrowser browser) {
    runAsUser(browser, E2ETestData.EMPLOYEE_MA_SIGN, "/dailyreport/dashboard", page -> {
      page.keyboard().press(SHORTCUT);
      input(page).fill("buchen gestern alpha 1:30 Review");

      assertThat(chips(page)).hasText(new String[] {"buchen", "Mi 24.06.", ALPHA, "1:30"});
      assertThat(input(page)).hasValue("Review");

      page.keyboard().press("Enter");

      page.waitForURL(Pattern.compile(".*/dailyreport/timereports/new\\?.*comment=Review.*"));
      assertThat(page.locator("#commentField")).hasValue("Review");
    });
  }

  /** Without a day the form is for today; Backspace in the empty field gives the chips back one by one. */
  @ParameterizedTest(name = "{0}")
  @MethodSource("org.tb.e2e.PlaywrightE2ETestBase#browsers")
  void backspace_gives_the_last_chip_back_and_a_missing_day_is_today(E2EBrowser browser) {
    runAsUser(browser, E2ETestData.EMPLOYEE_MA_SIGN, "/dailyreport/dashboard", page -> {
      page.keyboard().press(SHORTCUT);
      input(page).fill("buchen alpha ");
      assertThat(chips(page)).hasText(new String[] {"buchen", ALPHA});
      assertThat(page.locator("#commandPalettePreview [data-part=values]")).containsText("Do 25.06.2026");

      page.keyboard().press("Backspace");
      assertThat(chips(page)).hasText(new String[] {"buchen"});
      assertThat(param(page)).hasText("Tag?");

      page.keyboard().press("Backspace");
      assertThat(page.locator("#commandPaletteChips")).isHidden();
      assertThat(input(page)).hasValue("buchen");
    });
  }

  /**
   * Chips are not cut short: where the line is too narrow for them and the field, the field moves
   * below the chips. On a wide screen the palette grows while a command is entered, and shrinks back.
   */
  @ParameterizedTest(name = "{0}")
  @MethodSource("org.tb.e2e.PlaywrightE2ETestBase#browsers")
  void chips_are_not_cut_short_and_the_field_moves_below_them(E2EBrowser browser) {
    runAsUser(browser, E2ETestData.EMPLOYEE_MA_SIGN, "/dailyreport/dashboard", page -> {
      page.setViewportSize(1400, 900);
      page.keyboard().press(SHORTCUT);
      int listWidth = paletteWidth(page);
      input(page).fill("buchen gestern alpha 1:30 ");
      assertThat(chips(page)).hasCount(4);
      assertTrue(paletteWidth(page) > listWidth, "wider while a command is entered");

      page.setViewportSize(480, 900);
      assertEquals(Boolean.FALSE, page.evaluate("() => Array.from(document.querySelectorAll('#commandPaletteChips button'))"
          + ".some(chip => chip.scrollWidth > chip.clientWidth)"));
      assertEquals(Boolean.TRUE, page.evaluate("() => document.getElementById('commandPaletteInput').getBoundingClientRect().top"
          + " >= document.querySelector('#commandPaletteChips button').getBoundingClientRect().bottom"));

      page.setViewportSize(1400, 900);
      // duration, suborder, day, then the command itself
      for (int press = 0; press < 4; press++) {
        page.keyboard().press("Backspace");
      }
      assertThat(page.locator("#commandPaletteChips")).isHidden();
      assertEquals(listWidth, paletteWidth(page));
    });
  }

  private static int paletteWidth(Page page) {
    return ((Number) page.evaluate("() => document.getElementById('commandPalette').offsetWidth")).intValue();
  }

  private static Locator input(Page page) {
    return page.locator("#commandPaletteInput");
  }

  private static Locator options(Page page) {
    return page.locator("#commandPaletteList [role=option]");
  }

  private static Locator param(Page page) {
    return page.locator("#commandPaletteParam");
  }

  private static Locator chips(Page page) {
    return page.locator("#commandPaletteChips [data-palette-chip]");
  }
}
