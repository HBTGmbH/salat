package de.hbt.salat.e2e.layout;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import java.util.List;
import java.util.regex.Pattern;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import de.hbt.salat.e2e.E2EBrowser;
import de.hbt.salat.e2e.E2ETestData;
import de.hbt.salat.e2e.PlaywrightE2ETestBase;

/**
 * Removing an entry from a multi-select (#1149). TomSelect alone offers only the keyboard for that
 * — activate the chip, then Backspace — and on a phone that path never comes together: an entry,
 * once picked, stayed. Every chip now carries a cross that removes it with a tap or a click.
 *
 * <p>The customer filter of the booking list stands in for every {@code tomselect-multi}: the
 * cross comes from the central configuration in {@code salat.js}, and this filter is the one that
 * shows whether removing still reaches the page — it reloads the results on {@code change}.
 */
class MultiSelectRemoveE2ETest extends PlaywrightE2ETestBase {

  private static final String MANAGER = E2ETestData.EMPLOYEE_BL_SIGN;
  private static final String LIST = "/dailyreport/list";
  private static final String HBT = E2ETestData.CUSTOMER_HBT_SHORTNAME;
  private static final String CONTOSO = "CONTOSO";

  @ParameterizedTest(name = "{0}")
  @MethodSource("de.hbt.salat.e2e.PlaywrightE2ETestBase#browsers")
  void a_tap_on_the_cross_removes_exactly_that_entry(E2EBrowser browser) {
    runOnTouchDevice(browser, MANAGER, LIST, page -> {
      var contosoId = openWithHbtAndContoso(page);

      removeButton(page, HBT).tap();

      assertOnlyContosoIsLeft(page, contosoId);
      // a tap is not a request for the list, and the on-screen keyboard only opens for a text field
      assertThat(wrapper(page).locator(".ts-dropdown")).isHidden();
      assertEquals(false, page.evaluate("() => document.activeElement.matches('input, textarea')"));
    });
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("de.hbt.salat.e2e.PlaywrightE2ETestBase#browsers")
  void a_click_on_the_cross_removes_exactly_that_entry(E2EBrowser browser) {
    runAsUser(browser, MANAGER, LIST, page -> {
      var contosoId = openWithHbtAndContoso(page);

      removeButton(page, HBT).click();

      assertOnlyContosoIsLeft(page, contosoId);
      assertThat(wrapper(page).locator(".ts-dropdown")).isHidden();
    });
  }

  /**
   * WCAG 2.5.8 asks for 24 × 24 CSS pixels, more than a chip is high. The area reaches beyond the
   * visible cross instead of making the chip taller — the field keeps the height of a single-select.
   */
  @ParameterizedTest(name = "{0}")
  @MethodSource("de.hbt.salat.e2e.PlaywrightE2ETestBase#browsers")
  void the_cross_answers_on_24_by_24_pixels_and_the_field_keeps_its_height(E2EBrowser browser) {
    runOnTouchDevice(browser, MANAGER, LIST, page -> {
      openWithHbtAndContoso(page);

      Locator cross = removeButton(page, HBT);
      var box = cross.boundingBox();
      double centerX = box.x + box.width / 2;
      double centerY = box.y + box.height / 2;
      for (double dx : new double[] {-11.5, 11.5}) {
        for (double dy : new double[] {-11.5, 11.5}) {
          assertEquals(true, cross.evaluate(
              "(el, [x, y]) => el.contains(document.elementFromPoint(x, y))",
              List.of(centerX + dx, centerY + dy)), "hit at offset " + dx + "/" + dy);
        }
      }

      assertEquals(page.locator("#limit-select ~ .ts-wrapper").boundingBox().height,
          wrapper(page).boundingBox().height);
    });
  }

  /** Opens the list with both customers already in the filter, and returns the id of CONTOSO. */
  private String openWithHbtAndContoso(Page page) {
    var hbtId = customerId(page, HBT);
    var contosoId = customerId(page, CONTOSO);
    page.navigate(urlWithLogin(LIST + "?fBookingsCustomers=" + hbtId + "," + contosoId, MANAGER));
    assertThat(chips(page)).hasCount(2);
    return contosoId;
  }

  private void assertOnlyContosoIsLeft(Page page, String contosoId) {
    assertThat(chips(page)).hasCount(1);
    assertThat(chips(page).first()).containsText(CONTOSO);
    // the filter reloads on change, and the pushed address carries what is still selected
    assertThat(page).hasURL(Pattern.compile("fBookingsCustomers=" + contosoId + "(&|$)"));
  }

  private static String customerId(Page page, String shortname) {
    return (String) page.locator("#customer-select option").evaluateAll(
        "(options, text) => options.find(o => o.textContent.trim() === text).value", shortname);
  }

  private static Locator wrapper(Page page) {
    return page.locator("#customer-select ~ .ts-wrapper");
  }

  private static Locator chips(Page page) {
    return wrapper(page).locator(".ts-control > .item");
  }

  private static Locator removeButton(Page page, String shortname) {
    return chips(page).filter(new Locator.FilterOptions().setHasText(shortname))
        .getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName("Eintrag entfernen"));
  }

}
