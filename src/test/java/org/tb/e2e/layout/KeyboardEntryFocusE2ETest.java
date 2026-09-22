package org.tb.e2e.layout;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.microsoft.playwright.Locator;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.tb.e2e.E2EBrowser;
import org.tb.e2e.E2ETestData;
import org.tb.e2e.PlaywrightE2ETestBase;

/**
 * Covers operating the application by keyboard (#1064): where the focus starts, and that the tab
 * order is the document order.
 *
 * <p>Before #1064 {@code salat.js} handed out positive tabindex values to everything inside
 * {@code .page-body} and wrote the value of a TomSelect field onto the input inside its closed
 * dropdown. The visible {@code .ts-control} kept tabindex 0 and was therefore reached only after
 * every element carrying a positive value — on a list, after every link of the table.
 */
class KeyboardEntryFocusE2ETest extends PlaywrightE2ETestBase {

  /** The filter of the customer list starts with a plain text input. */
  @ParameterizedTest(name = "{0}")
  @MethodSource("org.tb.e2e.PlaywrightE2ETestBase#browsers")
  void the_focus_starts_in_the_first_field_of_the_filter(E2EBrowser browser) {
    runAsUser(browser, E2ETestData.EMPLOYEE_BL_SIGN, "/customers", page ->
        assertEquals("fCustomerFilter", page.evaluate("() => document.activeElement.name")));
  }

  /**
   * The filter of the employee order list starts with a select. TomSelect hides the {@code
   * <select>} itself and puts its own {@code .ts-control} next to it — that control is what the
   * focus belongs on, and it must not drop its dropdown open over the page while doing so.
   */
  @ParameterizedTest(name = "{0}")
  @MethodSource("org.tb.e2e.PlaywrightE2ETestBase#browsers")
  void a_select_takes_the_entry_focus_without_opening_its_dropdown(E2EBrowser browser) {
    runAsUser(browser, E2ETestData.EMPLOYEE_BL_SIGN, "/orders/employeeorders", page -> {
      assertEquals(true,
          page.evaluate("() => document.activeElement.classList.contains('ts-control')"));
      // the wrapper stands directly behind the field it replaces, so this names the filter field
      assertEquals("fEmployeeOrderEmployeeContractId", page.evaluate(
          "() => document.activeElement.closest('.ts-wrapper').previousElementSibling.name"));
      assertThat(page.locator(".ts-wrapper.dropdown-active")).hasCount(0);
    });
  }

  /**
   * A positive tabindex takes an element out of the document order and puts it in front of
   * everything that carries 0 — that is what pushed the selects to the end. Nothing in the
   * application hands one out any more.
   */
  @ParameterizedTest(name = "{0}")
  @MethodSource("org.tb.e2e.PlaywrightE2ETestBase#browsers")
  void no_element_carries_a_positive_tabindex(E2EBrowser browser) {
    runAsUser(browser, E2ETestData.EMPLOYEE_BL_SIGN, "/orders/employeeorders", page ->
        assertEquals(0, page.evaluate(
            "() => Array.from(document.querySelectorAll('[tabindex]'))"
                + ".filter(el => el.tabIndex > 0).length")));
  }

  /**
   * With the tab order back on the document order, the navigation stands in front of the content —
   * the skip link is what gets past it.
   */
  @ParameterizedTest(name = "{0}")
  @MethodSource("org.tb.e2e.PlaywrightE2ETestBase#browsers")
  void the_skip_link_leads_from_the_top_of_the_page_into_the_content(E2EBrowser browser) {
    runAsUser(browser, E2ETestData.EMPLOYEE_BL_SIGN, "/customers", page -> {
      assertEquals(true, page.evaluate(
          "() => document.querySelector('a[href], button, input, select, textarea')"
              + ".classList.contains('skip-link')"));

      Locator skip = page.locator(".skip-link");
      // clipped away as long as it does not carry the focus, and its full size once it does
      assertEquals("rect(0px, 0px, 0px, 0px)",
          skip.evaluate("el => getComputedStyle(el).clip"));
      skip.focus();
      assertEquals("auto", skip.evaluate("el => getComputedStyle(el).clip"));
      assertThat(skip).isVisible();

      skip.press("Enter");
      page.keyboard().press("Tab");
      assertEquals(true,
          page.evaluate("() => !!document.activeElement.closest('#page-content')"));
    });
  }
}
