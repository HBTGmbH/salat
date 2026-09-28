package de.hbt.salat.e2e.layout;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import java.util.regex.Pattern;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import de.hbt.salat.e2e.E2EBrowser;
import de.hbt.salat.e2e.E2ETestData;
import de.hbt.salat.e2e.PlaywrightE2ETestBase;

/**
 * The object search of the command palette (#1157): orders, suborders, customers and persons come
 * from the server below the page's own hits, Enter opens an object, → shows its further targets and
 * ← leads back.
 *
 * <p>Who finds what is decided by the providers and tested there, per role; this class tests the
 * palette's handling of the answer. The seed has the order {@code CONTOSO-01} of the customer
 * {@code CONTOSO} with the suborder {@code ALPHA-DEV}, on which the regular employee may book.
 */
class CommandPaletteObjectsE2ETest extends PlaywrightE2ETestBase {

  private static final String MANAGER = E2ETestData.EMPLOYEE_BL_SIGN;
  private static final String EMPLOYEE = E2ETestData.EMPLOYEE_MA_SIGN;
  private static final String ORDER = E2ETestData.CUSTOMERORDER_CONTOSO_SIGN;
  private static final String SHORTCUT = "ControlOrMeta+k";

  @ParameterizedTest(name = "{0}")
  @MethodSource("de.hbt.salat.e2e.PlaywrightE2ETestBase#browsers")
  void the_objects_come_below_and_enter_opens_the_order(E2EBrowser browser) {
    runAsUser(browser, MANAGER, "/dailyreport/dashboard", page -> {
      page.keyboard().press(SHORTCUT);
      input(page).fill("contoso");

      Locator order = option(page, "CUSTOMERORDER:" + ORDER);
      assertThat(order).containsText(ORDER + " · Projekt Alpha · CONTOSO");
      assertThat(page.locator("#commandPaletteList .command-palette-group").first()).hasText("Aufträge");
      // the page has no hit for "contoso", so the first object is the one Enter opens
      assertThat(order).hasAttribute("aria-selected", "true");

      page.keyboard().press("Enter");

      page.waitForURL(Pattern.compile(".*/orders/customerorders/edit\\?id=\\d+.*"));
    });
  }

  /**
   * "de" is several hits of the page ("Dunkles Design", "Kundensegmente", …) and the suborder
   * ALPHA-DEV. The page's hits are there at once; the answer is held back until the second of them
   * is selected, and the objects arriving then go below and leave it selected — the first row would
   * be selected after a reset as well.
   */
  @ParameterizedTest(name = "{0}")
  @MethodSource("de.hbt.salat.e2e.PlaywrightE2ETestBase#browsers")
  void arriving_objects_do_not_move_the_selected_row(E2EBrowser browser) {
    runAsUser(browser, MANAGER, "/dailyreport/dashboard", page -> {
      page.evaluate("() => { const fetchNow = window.fetch; let release;"
          + " const held = new Promise(resolve => release = resolve); window.releaseObjects = release;"
          + " window.fetch = (url, options) => String(url).includes('/palette/search')"
          + "   ? held.then(() => fetchNow(url, options)) : fetchNow(url, options); }");
      page.keyboard().press(SHORTCUT);
      input(page).fill("de");
      assertThat(options(page).nth(1)).isVisible();
      page.keyboard().press("ArrowDown");
      Locator selected = page.locator("#commandPaletteList [aria-selected=true]");
      assertThat(selected).hasAttribute("data-index", "1");
      String before = selected.getAttribute("data-command-key");

      page.evaluate("() => window.releaseObjects()");
      assertThat(page.locator("#commandPaletteList [data-command-type=object]")
          .filter(new Locator.FilterOptions().setHasText(E2ETestData.SUBORDER_ALPHA_DEV_SIGN)).first()).isVisible();

      assertThat(selected).hasAttribute("data-command-key", before);
      assertEquals(input(page).getAttribute("aria-activedescendant"), selected.getAttribute("id"));
    });
  }

  /**
   * An object of the last answer is selected while the next one is on its way, and that one fails:
   * the objects go, and the selection falls back to the first hit of the page instead of to none.
   * The trailing space makes it a new query the old objects still match.
   */
  @ParameterizedTest(name = "{0}")
  @MethodSource("de.hbt.salat.e2e.PlaywrightE2ETestBase#browsers")
  void a_selected_object_gone_with_a_failed_answer_leaves_the_first_hit_selected(E2EBrowser browser) {
    runAsUser(browser, MANAGER, "/dailyreport/dashboard", page -> {
      page.keyboard().press(SHORTCUT);
      input(page).fill("de");
      Locator objects = page.locator("#commandPaletteList [data-command-type=object]");
      assertThat(objects.first()).isVisible();
      page.evaluate("() => { const fetchNow = window.fetch; let fail;"
          + " const failed = new Promise((resolve, reject) => fail = reject);"
          + " window.failObjects = () => fail(new TypeError('offline'));"
          + " window.fetch = (url, options) => String(url).includes('/palette/search')"
          + "   ? failed : fetchNow(url, options); }");

      input(page).fill("de ");
      Locator selected = page.locator("#commandPaletteList [aria-selected=true]");
      for (var i = 0; i < 10 && !"object".equals(selected.getAttribute("data-command-type")); i++) {
        page.keyboard().press("ArrowDown");
      }
      assertThat(selected).hasAttribute("data-command-type", "object");

      page.evaluate("() => window.failObjects()");

      assertThat(objects).hasCount(0);
      assertThat(selected).hasAttribute("data-index", "0");
      assertEquals(selected.getAttribute("id"), input(page).getAttribute("aria-activedescendant"));
    });
  }

  /**
   * A held key goes into the targets or out of them once. Held to empty the filter, Backspace stays
   * in the targets; held to leave them, it does not go on deleting the query that was put back.
   */
  @ParameterizedTest(name = "{0}")
  @MethodSource("de.hbt.salat.e2e.PlaywrightE2ETestBase#browsers")
  void a_held_key_changes_between_hits_and_targets_only_once(E2EBrowser browser) {
    runAsUser(browser, MANAGER, "/dailyreport/dashboard", page -> {
      page.keyboard().press(SHORTCUT);
      input(page).fill("contoso");
      assertThat(option(page, "CUSTOMERORDER:" + ORDER)).hasAttribute("aria-selected", "true");
      page.keyboard().press("ArrowRight");
      page.keyboard().type("co");

      // a second keyboard().down() of the same key is a repeat, as a held key sends it
      for (var i = 0; i < 4; i++) {
        page.keyboard().down("Backspace");
      }
      page.keyboard().up("Backspace");
      assertThat(input(page)).hasValue("");
      assertThat(page.locator("#commandPaletteCrumb")).isVisible();

      for (var i = 0; i < 4; i++) {
        page.keyboard().down("Backspace");
      }
      page.keyboard().up("Backspace");
      assertThat(page.locator("#commandPaletteCrumb")).isHidden();
      assertThat(input(page)).hasValue("contoso");
    });
  }

  /** → with the cursor at the end shows the targets of the object, ← leads back to the hits. */
  @ParameterizedTest(name = "{0}")
  @MethodSource("de.hbt.salat.e2e.PlaywrightE2ETestBase#browsers")
  void arrow_right_shows_the_targets_and_the_controlling_opens_evaluated(E2EBrowser browser) {
    runAsUser(browser, MANAGER, "/dailyreport/dashboard", page -> {
      page.keyboard().press(SHORTCUT);
      input(page).fill("contoso");
      assertThat(option(page, "CUSTOMERORDER:" + ORDER)).hasAttribute("aria-selected", "true");

      page.keyboard().press("ArrowRight");

      assertThat(page.locator("#commandPaletteCrumb")).containsText("contoso");
      assertThat(page.locator("#commandPaletteCrumb")).containsText(ORDER);
      // the budget follows only where the order has plans, which is not the seed's business here
      assertThat(options(page).nth(0)).hasText("Auftrag öffnen");
      assertThat(options(page).nth(1)).hasText("Unteraufträge");
      assertThat(options(page).nth(2)).hasText("Controlling, ausgewertet");

      page.keyboard().press("ArrowLeft");
      assertThat(page.locator("#commandPaletteCrumb")).isHidden();
      assertThat(input(page)).hasValue("contoso");
      assertThat(option(page, "CUSTOMERORDER:" + ORDER)).hasAttribute("aria-selected", "true");

      page.keyboard().press("ArrowRight");
      page.keyboard().press("ArrowDown");
      page.keyboard().press("ArrowDown");
      page.keyboard().press("Enter");

      page.waitForURL(Pattern.compile(".*/budget/controlling\\?fCustomerOrderSign=" + ORDER + "&evaluate=true.*"));
    });
  }

  /** The regular employee may book on the suborder today: its targets offer the booking form. */
  @ParameterizedTest(name = "{0}")
  @MethodSource("de.hbt.salat.e2e.PlaywrightE2ETestBase#browsers")
  void a_bookable_suborder_offers_the_booking_form(E2EBrowser browser) {
    runAsUser(browser, EMPLOYEE, "/dailyreport/dashboard", page -> {
      page.keyboard().press(SHORTCUT);
      input(page).fill("alpha");
      Locator suborder = page.locator("#commandPaletteList [data-command-type=object]")
          .filter(new Locator.FilterOptions().setHasText(ORDER + "/" + E2ETestData.SUBORDER_ALPHA_DEV_SIGN)).first();
      suborder.locator("[data-palette-more]").click();

      Locator book = options(page).filter(new Locator.FilterOptions()
          .setHasText("Buchen auf " + ORDER + "/" + E2ETestData.SUBORDER_ALPHA_DEV_SIGN));
      assertThat(book).hasCount(1);
      book.click();

      page.waitForURL(Pattern.compile(".*/dailyreport/timereports/new\\?suborderId=\\d+&employeecontractId=\\d+.*"));
      assertThat(page.locator("#suborderId")).not().hasValue("");
    });
  }

  /**
   * An answer the next keystroke has overtaken is dropped. The answer for "co" is held back until the
   * one for "contoso" is shown; "co" would also bring the suborder of GLOBEX, "contoso" does not.
   */
  @ParameterizedTest(name = "{0}")
  @MethodSource("de.hbt.salat.e2e.PlaywrightE2ETestBase#browsers")
  void an_overtaken_answer_is_dropped(E2EBrowser browser) {
    runAsUser(browser, MANAGER, "/dailyreport/dashboard", page -> {
      page.evaluate("() => { const fetchNow = window.fetch; window.fetch = (url, options) =>"
          + " /[?&]q=co$/.test(String(url))"
          + "   ? new Promise(resolve => setTimeout(() => resolve(fetchNow(url, options)), 1500))"
          + "   : fetchNow(url, options); }");
      page.keyboard().press(SHORTCUT);
      input(page).fill("co");
      page.waitForTimeout(400);
      input(page).fill("contoso");
      assertThat(option(page, "CUSTOMERORDER:" + ORDER)).isVisible();

      page.waitForTimeout(2000);

      assertThat(page.locator("#commandPaletteList [data-command-type=object]")
          .filter(new Locator.FilterOptions().setHasText("GLOBEX"))).hasCount(0);
      assertThat(option(page, "CUSTOMERORDER:" + ORDER)).isVisible();
    });
  }

  private static Locator input(Page page) {
    return page.locator("#commandPaletteInput");
  }

  private static Locator options(Page page) {
    return page.locator("#commandPaletteList [role=option]");
  }

  private static Locator option(Page page, String key) {
    return page.locator("#commandPaletteList [role=option][data-command-key=\"" + key + "\"]");
  }
}
