package org.tb.e2e.layout;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

import com.microsoft.playwright.Locator;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.tb.e2e.E2EBrowser;
import org.tb.e2e.E2ETestData;
import org.tb.e2e.PlaywrightE2ETestBase;

/**
 * Covers the folded sidebar that replaced the hand-built one with Tabler 1.5 (#1019).
 *
 * <p>The folded state lives in {@code localStorage['tabler-sidebar']}; {@code tabler-theme.js}
 * writes it back onto {@code <html>} as {@code data-bs-sidebar} before the first paint. Since the
 * same loader also reads a {@code ?sidebar=} parameter, the folded state can be forced through the
 * URL without touching localStorage - which keeps the tests independent of each other.
 *
 * <p>No test here depends on hovering. The Playwright default viewport of 1280x720 is above the
 * {@code md} breakpoint, so the folding is active.
 */
class FoldedSidebarE2ETest extends PlaywrightE2ETestBase {

  private static final String PIN = "#salat-nav [data-bs-toggle='sidebar-folded']";
  private static final String OPEN_SECTION_MENU = "#salat-nav .navbar-collapse .dropdown-menu.show";

  @ParameterizedTest(name = "{0}")
  @MethodSource("org.tb.e2e.PlaywrightE2ETestBase#browsers")
  void the_pin_folds_the_sidebar_and_remembers_it(E2EBrowser browser) {
    runAsUser(browser, E2ETestData.EMPLOYEE_MA_SIGN, "/dailyreport/dashboard", page -> {
      // unfolded is the default: no attribute, and the section of the current page stands open
      assertThat(page.locator("html[data-bs-sidebar]")).hasCount(0);
      assertThat(page.locator(OPEN_SECTION_MENU)).hasCount(1);

      // the pin is opacity:0 until the sidebar is hovered, which for Playwright still counts as
      // visible and clickable - a non-empty bounding box and no visibility:hidden
      Locator pin = page.locator(PIN);
      pin.click();

      assertThat(page.locator("html")).hasAttribute("data-bs-sidebar", "folded-hover");
      assertThat(pin).hasAttribute("aria-pressed", "true");

      // Tabler finds the open section through aria-expanded on the toggle - which the template
      // renders server-side. Without it the menu would stay behind as a panel next to the rail.
      assertThat(page.locator(OPEN_SECTION_MENU)).hasCount(0);
    });
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("org.tb.e2e.PlaywrightE2ETestBase#browsers")
  void a_folded_sidebar_starts_with_every_section_menu_closed(E2EBrowser browser) {
    runAsUser(browser, E2ETestData.EMPLOYEE_MA_SIGN, "/dailyreport/dashboard?sidebar=folded-hover",
        page -> {
      assertThat(page.locator("html")).hasAttribute("data-bs-sidebar", "folded-hover");
      assertThat(page.locator(OPEN_SECTION_MENU)).hasCount(0);

      // the section toggle of the current page keeps its state marker, it is only no longer open
      assertThat(page.locator("#salat-nav a[href='#navbar-timereports']"))
          .hasAttribute("aria-expanded", "false");
    });
  }

  /**
   * The user block moved out of the scrolling menu into the {@code navbar-footer} zone. It has to
   * stay inside {@code #salat-nav}, because {@code EnglishLocaleE2ETest} asserts its label there.
   */
  @ParameterizedTest(name = "{0}")
  @MethodSource("org.tb.e2e.PlaywrightE2ETestBase#browsers")
  void the_user_block_sits_in_the_sidebar_footer(E2EBrowser browser) {
    runAsUser(browser, E2ETestData.EMPLOYEE_MA_SIGN, "/dailyreport/dashboard", page -> {
      Locator footer = page.locator("#salat-nav .navbar-footer");
      assertThat(footer).hasCount(1);
      assertThat(footer).containsText("Angemeldet als");

      // the probe at the end of the page swaps title and target of exactly this link when the
      // address has no gravatar - it finds it by id, so the id has to survive the move
      assertThat(page.locator("#salat-nav #gravatar-sidebar-link")).hasCount(1);

      footer.locator("[data-bs-toggle='dropdown']").click();
      assertThat(footer.locator(".dropdown-menu")).isVisible();
    });
  }
}
