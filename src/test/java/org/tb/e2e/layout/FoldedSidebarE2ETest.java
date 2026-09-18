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

  /**
   * The trigger names the role instead of the sign (#1033). The sign stays in the open menu, where
   * it has always been the second time it appeared.
   */
  @ParameterizedTest(name = "{0}")
  @MethodSource("org.tb.e2e.PlaywrightE2ETestBase#browsers")
  void the_trigger_shows_the_role_badge_and_the_menu_keeps_the_sign(E2EBrowser browser) {
    runAsUser(browser, E2ETestData.EMPLOYEE_MA_SIGN, "/dailyreport/dashboard", page -> {
      Locator footer = page.locator("#salat-nav .navbar-footer");
      Locator badge = footer.locator(".nav-link-title .badge");
      assertThat(badge).hasText("Mitarbeitender");
      assertThat(footer.locator(".nav-link-title")).not().containsText(E2ETestData.EMPLOYEE_MA_SIGN);

      // Tabler positions every .badge inside a nav link absolutely as a corner dot and would push
      // a text badge past the sidebar edge; .nav-link-badge keeps it in the flow (salat.css)
      assertEquals("static", badge.evaluate("el => getComputedStyle(el).position"));
      assertEquals(true, badge.evaluate(
          "el => el.getBoundingClientRect().right <= el.closest('.navbar').getBoundingClientRect().right"));

      footer.locator("[data-bs-toggle='dropdown']").click();
      assertThat(footer.locator(".dropdown-menu")).containsText(E2ETestData.EMPLOYEE_MA_SIGN);
    });
  }

  /**
   * Folded, the user block is reduced to the picture - the role badge rides along with the
   * {@code nav-link-title} that Tabler collapses, so it must not stand next to the rail.
   */
  @ParameterizedTest(name = "{0}")
  @MethodSource("org.tb.e2e.PlaywrightE2ETestBase#browsers")
  void a_folded_sidebar_leaves_only_the_picture_in_the_user_block(E2EBrowser browser) {
    runAsUser(browser, E2ETestData.EMPLOYEE_MA_SIGN, "/dailyreport/dashboard?sidebar=folded", page -> {
      Locator footer = page.locator("#salat-nav .navbar-footer");
      // the menu below carries a second avatar, so name the one on the trigger
      assertThat(footer.locator(".nav-link > img.avatar")).isVisible();
      assertThat(footer.locator(".nav-link-title")).not().isVisible();

      // The badge keeps its own box and Playwright would call it visible - Tabler folds the title
      // around it to width 0 and clips it. So ask the page what is painted where the badge sits:
      // nothing of it is, and nothing of it reaches the rail.
      assertEquals(false, footer.locator(".nav-link-title .badge").evaluate("""
          el => {
            const box = el.getBoundingClientRect();
            const hit = document.elementFromPoint(box.x + box.width / 2, box.y + box.height / 2);
            return el === hit || el.contains(hit);
          }"""));
    });
  }
}
