package org.tb.e2e.layout;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import java.util.regex.Pattern;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.tb.e2e.E2EBrowser;
import org.tb.e2e.E2ETestData;
import org.tb.e2e.PlaywrightE2ETestBase;

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
  @MethodSource("org.tb.e2e.PlaywrightE2ETestBase#browsers")
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
   * "con" is a page of the sidebar ("Controlling") and several objects. The page's hits are there at
   * once and one of them is selected; the objects arriving later go below and leave it selected.
   */
  @ParameterizedTest(name = "{0}")
  @MethodSource("org.tb.e2e.PlaywrightE2ETestBase#browsers")
  void arriving_objects_do_not_move_the_selected_row(E2EBrowser browser) {
    runAsUser(browser, MANAGER, "/dailyreport/dashboard", page -> {
      page.keyboard().press(SHORTCUT);
      input(page).fill("con");
      Locator selected = page.locator("#commandPaletteList [aria-selected=true]");
      String before = selected.getAttribute("data-command-key");
      assertEquals("nav", selected.getAttribute("data-command-type"));

      assertThat(option(page, "CUSTOMERORDER:" + ORDER)).isVisible();

      assertEquals(before, selected.getAttribute("data-command-key"));
      assertEquals(input(page).getAttribute("aria-activedescendant"), selected.getAttribute("id"));
    });
  }

  /** → with the cursor at the end shows the targets of the object, ← leads back to the hits. */
  @ParameterizedTest(name = "{0}")
  @MethodSource("org.tb.e2e.PlaywrightE2ETestBase#browsers")
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
  @MethodSource("org.tb.e2e.PlaywrightE2ETestBase#browsers")
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
  @MethodSource("org.tb.e2e.PlaywrightE2ETestBase#browsers")
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
