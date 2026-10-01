package de.hbt.salat.e2e.layout;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import java.util.regex.Pattern;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import de.hbt.salat.e2e.E2EBrowser;
import de.hbt.salat.e2e.E2ETestData;
import de.hbt.salat.e2e.PlaywrightE2ETestBase;

/**
 * The bar above the header (#1231): every booking page shows only the data of the selected
 * contract, and for a person with several the bar says so, names the period and switches to the
 * other contract. The selector beside the header buttons stays reachable below {@code md}, where it
 * used to be hidden.
 */
class ContractBarE2ETest extends PlaywrightE2ETestBase {

  private static final String PERSON = E2ETestData.EMPLOYEE_TWO_CONTRACTS_SIGN;

  @ParameterizedTest(name = "{0}")
  @MethodSource("de.hbt.salat.e2e.PlaywrightE2ETestBase#browsers")
  void the_bar_names_the_contract_and_switches_to_the_other_one(E2EBrowser browser) {
    runAsUser(browser, PERSON, "/dailyreport/daily", page -> {
      Locator bar = bar(page);
      assertThat(bar).isVisible();
      assertThat(bar).hasAttribute("aria-label", "Gewählter Vertrag");
      assertThat(bar).containsText("Zora Zweivertrag");
      assertThat(bar).containsText("Vertrag ab 01.01.2026, unbefristet");
      assertThat(bar).containsText("Angezeigt werden nur Daten dieses Vertrags.");

      bar.getByRole(AriaRole.BUTTON,
          new Locator.GetByRoleOptions().setName("01.01.2020 – 31.12.2025")).click();

      page.waitForURL(Pattern.compile(".*fEmployeeContractId=\\d+.*"));
      assertThat(bar(page)).containsText("Vertrag 01.01.2020 – 31.12.2025");
      assertThat(bar(page).locator("[data-select-contract]")).hasText("ab 01.01.2026, unbefristet");
    });
  }

  /**
   * A booking form opened for a named contract (#760) — the dashboard and the review pages link it
   * that way — follows a switch in the header too. The named contract used to win over the new
   * selection, so the form went on offering the suborders of the old one.
   */
  @ParameterizedTest(name = "{0}")
  @MethodSource("de.hbt.salat.e2e.PlaywrightE2ETestBase#browsers")
  void a_form_opened_for_a_named_contract_follows_the_switch(E2EBrowser browser) {
    runAsUser(browser, PERSON, "/dailyreport/timereports/new", page -> {
      String current = page.locator("#globalEmployeeContractId").inputValue();
      page.navigate(urlWithLogin("/dailyreport/timereports/new?employeecontractId=" + current, PERSON));
      assertThat(page.locator("input[type=hidden][name=employeecontractId]")).hasValue(current);

      bar(page).getByRole(AriaRole.BUTTON,
          new Locator.GetByRoleOptions().setName("01.01.2020 – 31.12.2025")).click();

      page.waitForURL(Pattern.compile(".*fEmployeeContractId=\\d+.*"));
      assertThat(bar(page)).containsText("Vertrag 01.01.2020 – 31.12.2025");
      assertThat(page.locator("input[type=hidden][name=employeecontractId]")).hasCount(0);
    });
  }

  /** One contract, nothing to say: the selector alone, without the bar. */
  @ParameterizedTest(name = "{0}")
  @MethodSource("de.hbt.salat.e2e.PlaywrightE2ETestBase#browsers")
  void a_person_with_one_contract_gets_no_bar(E2EBrowser browser) {
    runAsUser(browser, E2ETestData.EMPLOYEE_BL_SIGN, "/dailyreport/daily", page -> {
      assertThat(page.locator("#globalEmployeeContractId")).hasCount(1);
      assertThat(bar(page)).hasCount(0);
    });
  }

  /** Outside the booking pages the selection has no effect, so there is no bar either. */
  @ParameterizedTest(name = "{0}")
  @MethodSource("de.hbt.salat.e2e.PlaywrightE2ETestBase#browsers")
  void outside_the_booking_pages_there_is_no_bar(E2EBrowser browser) {
    runAsUser(browser, PERSON, "/settings", page -> assertThat(bar(page)).hasCount(0));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("de.hbt.salat.e2e.PlaywrightE2ETestBase#browsers")
  void on_a_phone_the_selector_gets_a_line_of_its_own(E2EBrowser browser) {
    runAsUser(browser, PERSON, "/dailyreport/daily", page -> {
      page.setViewportSize(360, 740);
      Locator selector = page.locator("header.page-header .header-contract-selector .ts-control");
      assertThat(selector).isVisible();
      assertThat(bar(page).locator("[data-select-contract]")).isVisible();

      // the title above it stays whole: the toolbar wraps below instead of squeezing it
      double titleBottom = ((Number) page.locator("h2.page-title")
          .evaluate("el => el.getBoundingClientRect().bottom")).doubleValue();
      double selectorTop = ((Number) selector.evaluate("el => el.getBoundingClientRect().top")).doubleValue();
      assertTrue(selectorTop >= titleBottom,
          "selector " + selectorTop + " below title " + titleBottom);
    });
  }

  private static Locator bar(Page page) {
    return page.locator(".contract-bar");
  }
}
