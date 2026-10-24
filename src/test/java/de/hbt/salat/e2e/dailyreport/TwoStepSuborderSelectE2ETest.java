package de.hbt.salat.e2e.dailyreport;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static de.hbt.salat.e2e.E2ETestData.CUSTOMERORDER_GLOBEX_SIGN;
import static de.hbt.salat.e2e.E2ETestData.CUSTOMERORDER_INITECH_SIGN;
import static de.hbt.salat.e2e.E2ETestData.EMPLOYEE_TWO_STEP_SIGN;
import static de.hbt.salat.e2e.E2ETestData.SUBORDER_GLOBEX_CONSULT_SIGN;
import static de.hbt.salat.e2e.E2ETestData.SUBORDER_INITECH_MIGRATION_SIGN;
import static de.hbt.salat.e2e.E2ETestData.SUBORDER_INITECH_SUPPORT_SIGN;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import java.time.LocalDate;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import de.hbt.salat.e2e.E2EBrowser;
import de.hbt.salat.e2e.PlaywrightE2ETestBase;

/**
 * Den Unterauftrag in zwei Schritten wählen (#1522): erst den Auftrag, dann einen seiner Unteraufträge.
 * Welche Aufträge angeboten und welche vorgewählt werden, prüft {@code TimereportTwoStepSuborderTest};
 * hier geht es um das Filtern im Browser und darum, dass gespeichert wird, was gewählt ist.
 *
 * <p>Eine eigene Person, weil die Einstellung je Anmeldung gespeichert wird. Gebucht wird an einem Tag,
 * den keine andere E2E-Klasse ansieht, und geprüft nur die Buchung mit dem Kommentar dieses Laufs.
 */
class TwoStepSuborderSelectE2ETest extends PlaywrightE2ETestBase {

  private static final String EMPLOYEE = EMPLOYEE_TWO_STEP_SIGN;
  private static final String NEW_BOOKING = "/dailyreport/timereports/new";
  /** In the past relative to the base class's fixed clock, and used by no other E2E class. */
  private static final LocalDate DAY = LocalDate.parse("2026-06-09");

  @ParameterizedTest(name = "{0}")
  @MethodSource("de.hbt.salat.e2e.PlaywrightE2ETestBase#browsers")
  void the_suborder_is_chosen_among_those_of_the_chosen_order_and_saved(E2EBrowser browser) {
    runAsUser(browser, EMPLOYEE, "/settings", page -> {
      switchTwoStepOn(page);
      page.navigate(urlWithLogin(NEW_BOOKING + "?date=" + DAY, EMPLOYEE));
      assertThat(page.locator("#orderSelect")).not().hasValue("");

      selectTomSelectOption(page, "orderSelect", CUSTOMERORDER_INITECH_SIGN);

      // empty, with the focus and its list open, which holds the suborders of the order and nothing else
      assertThat(page.locator("#suborderId")).hasValue("");
      assertThat(focusIn(page, "suborderId")).hasCount(1);
      assertThat(suborderOptions(page)).hasCount(2);
      assertThat(suborderOptions(page).nth(0)).containsText(SUBORDER_INITECH_MIGRATION_SIGN);
      assertThat(suborderOptions(page).nth(1)).containsText(SUBORDER_INITECH_SUPPORT_SIGN);

      suborderOptions(page).filter(new Locator.FilterOptions().setHasText(SUBORDER_INITECH_MIGRATION_SIGN)).click();
      String comment = "Zweistufig gewählt " + browser;
      page.locator("#durationTime").fill("1:30");
      page.locator("#commentField").fill(comment);
      page.click("button[name='saveAndNew']");
      page.waitForLoadState();

      assertThat(page.locator("#form-sidebar .list-group-item").filter(new Locator.FilterOptions().setHasText(comment)))
          .containsText(SUBORDER_INITECH_MIGRATION_SIGN);
      // the new form preselects as every new one does, an order and one of its suborders
      String order = page.locator("#orderSelect").inputValue();
      assertThat(page.locator("#suborderId option:checked")).hasAttribute("data-parent", order);
    });
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("de.hbt.salat.e2e.PlaywrightE2ETestBase#browsers")
  void an_order_with_a_single_suborder_has_it_chosen(E2EBrowser browser) {
    runAsUser(browser, EMPLOYEE, "/settings", page -> {
      switchTwoStepOn(page);
      page.navigate(urlWithLogin(NEW_BOOKING + "?date=" + DAY, EMPLOYEE));

      selectTomSelectOption(page, "orderSelect", CUSTOMERORDER_GLOBEX_SIGN);

      assertThat(page.locator("#suborderId option:checked")).containsText(SUBORDER_GLOBEX_CONSULT_SIGN);
    });
  }

  /** The palette's {@code focus=suborder} and the keyboard: order first, then suborder. */
  @ParameterizedTest(name = "{0}")
  @MethodSource("de.hbt.salat.e2e.PlaywrightE2ETestBase#browsers")
  void the_keyboard_reaches_the_order_before_the_suborder(E2EBrowser browser) {
    runAsUser(browser, EMPLOYEE, "/settings", page -> {
      switchTwoStepOn(page);
      page.navigate(urlWithLogin(NEW_BOOKING + "?date=" + DAY + "&focus=suborder", EMPLOYEE));

      assertThat(focusIn(page, "orderSelect")).hasCount(1);
      page.keyboard().press("Tab");
      assertThat(focusIn(page, "suborderId")).hasCount(1);
    });
  }

  private void switchTwoStepOn(Page page) {
    page.locator("input[name='twoStepSuborderSelect'][type='checkbox']").check();
    page.click("button[type=submit]");
    page.waitForLoadState();
  }

  /** TomSelect focuses its control, or the search field of its open list - either way inside its wrapper. */
  private Locator focusIn(Page page, String selectId) {
    return page.locator("#" + selectId + " ~ .ts-wrapper :focus");
  }

  private Locator suborderOptions(Page page) {
    return page.locator("#suborderId ~ .ts-wrapper .ts-dropdown .option");
  }
}
