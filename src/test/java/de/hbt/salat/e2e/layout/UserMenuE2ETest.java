package de.hbt.salat.e2e.layout;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.util.AopTestUtils;
import org.springframework.test.util.ReflectionTestUtils;
import de.hbt.salat.auth.domain.AccessLevel;
import de.hbt.salat.auth.domain.AuthorizationRule;
import de.hbt.salat.auth.persistence.AuthorizationRuleRepository;
import de.hbt.salat.auth.service.AuthService;
import de.hbt.salat.e2e.E2EBrowser;
import de.hbt.salat.e2e.E2ETestData;
import de.hbt.salat.e2e.PlaywrightE2ETestBase;

/**
 * The user menu at the bottom left (#1231): the settings moved there from the header, the menu
 * shows the real login sign instead of a second picture, and the command palette offers every one
 * of its functions although the closed menu hides them — but only what the menu would show.
 *
 * <p>The login switch needs a rule that grants it. It goes to the backoffice login, which the other
 * palette tests do not use, and only for the regular employee.
 */
class UserMenuE2ETest extends PlaywrightE2ETestBase {

  private static final String SHORTCUT = "ControlOrMeta+k";
  private static final String SWITCHER = E2ETestData.EMPLOYEE_BO_SIGN;
  private static final String RULE_NAME = "E2E Benutzerwechsel (#1231)";

  @Autowired
  private AuthorizationRuleRepository authorizationRuleRepository;
  @Autowired
  private AuthService authService;

  @BeforeAll
  void grantTheLoginSwitch() {
    if (authorizationRuleRepository.findAllByNameIgnoreCase(RULE_NAME).isEmpty()) {
      var rule = new AuthorizationRule();
      rule.setName(RULE_NAME);
      rule.setCategory("EMPLOYEE");
      rule.setGranteeId(Set.of(SWITCHER));
      rule.setObjectId(Set.of(E2ETestData.EMPLOYEE_MA_SIGN));
      rule.setAccessLevels(Set.of(AccessLevel.LOGIN));
      rule.setValidFrom(LocalDate.of(2020, 1, 1));
      authorizationRuleRepository.save(rule);
    }
    // the rules are cached; clearCache() asks for a manager, which a test thread is not
    Object target = AopTestUtils.getTargetObject(authService);
    ReflectionTestUtils.setField(target, "lastCacheUpdate", 0L);
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("de.hbt.salat.e2e.PlaywrightE2ETestBase#browsers")
  void the_settings_moved_from_the_header_into_the_menu(E2EBrowser browser) {
    runAsUser(browser, E2ETestData.EMPLOYEE_MA_SIGN, "/dailyreport/dashboard", page -> {
      assertThat(page.locator("header.page-header a[href$='/settings']")).hasCount(0);
      assertThat(page.locator("header.page-header [data-palette-command^='theme']")).hasCount(0);

      userMenuToggle(page).click();
      Locator menu = menu(page);
      assertThat(menu).isVisible();
      assertThat(menu.locator("img")).hasCount(0);
      assertThat(menu.locator(".dropdown-item-text")).containsText("Angemeldet als " + E2ETestData.EMPLOYEE_MA_SIGN);
      assertThat(menu.locator("a.dropdown-item[href$='/settings']")).isVisible();
      assertThat(menu.locator(".dropdown-item:visible")).containsText(
          new String[] {"Benutzereinstellungen", "Dunkles Design aktivieren", "Abmelden"});

      menu.locator("a.dropdown-item[href$='/settings']").click();
      page.waitForURL("**/settings");
      // the gravatar link moved along: the probe at the end of the page swaps text and target of
      // exactly this link when the address has no gravatar, and it finds it by id
      assertThat(page.locator("#gravatar-link")).hasCount(1);
    });
  }

  /** Marker and "Neu" stand until the menu has been opened once, and then stay away. */
  @ParameterizedTest(name = "{0}")
  @MethodSource("de.hbt.salat.e2e.PlaywrightE2ETestBase#browsers")
  void the_hint_on_the_change_goes_once_the_menu_was_opened(E2EBrowser browser) {
    runAsUser(browser, E2ETestData.EMPLOYEE_MA_SIGN, "/dailyreport/dashboard", page -> {
      Locator marker = page.locator("#user-menu-toggle .user-menu-marker");
      Locator badge = menu(page).locator(".badge[data-user-menu-news]");
      assertThat(marker).isVisible();

      userMenuToggle(page).click();
      assertThat(marker).isHidden();
      assertThat(badge).isVisible();
      assertThat(badge).hasText("Neu");

      page.reload();
      assertThat(marker).isHidden();
      userMenuToggle(page).click();
      assertThat(menu(page)).isVisible();
      assertThat(badge).isHidden();
    });
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("de.hbt.salat.e2e.PlaywrightE2ETestBase#browsers")
  void the_palette_offers_the_functions_of_the_closed_menu(E2EBrowser browser) {
    runAsUser(browser, E2ETestData.EMPLOYEE_MA_SIGN, "/dailyreport/dashboard", page -> {
      assertThat(menu(page)).isHidden();
      page.keyboard().press(SHORTCUT);

      List<String> keys = commandKeys(page, "");
      assertTrue(keys.containsAll(List.of("settings", "theme-dark", "logout")), keys.toString());
      // not the mode already in force, and no switch without the right to it
      assertTrue(!keys.contains("theme-light") && !keys.contains("login-switch"), keys.toString());

      input(page).fill("abmelden");
      assertThat(options(page).first()).hasAttribute("data-command-key", "logout");
      input(page).fill("einstellungen");
      page.keyboard().press("Enter");
      page.waitForURL("**/settings");
    });
  }

  /**
   * Whoever may switch finds every person by name or sign and switches with Enter; while the switch
   * runs, the palette offers its end and nothing else of it. None of it is remembered.
   */
  @ParameterizedTest(name = "{0}")
  @MethodSource("de.hbt.salat.e2e.PlaywrightE2ETestBase#browsers")
  void the_login_switch_runs_from_the_palette_and_is_not_remembered(E2EBrowser browser) {
    runAsUser(browser, SWITCHER, "/dailyreport/dashboard", page -> {
      page.keyboard().press(SHORTCUT);
      assertTrue(commandKeys(page, "wechseln").contains("login-switch"));
      assertThat(page.locator("#commandPaletteList [data-command-type=login]")).hasCount(1);
      // not on an empty input, where it would push the pages away
      input(page).fill("");
      assertThat(options(page).first()).isVisible();
      assertThat(page.locator("#commandPaletteList [data-command-type=login]")).hasCount(0);

      input(page).fill("angestellt");
      Locator person = page.locator("#commandPaletteList [data-command-type=login]");
      assertThat(person).hasCount(1);
      assertThat(person).containsText("Benutzer wechseln zu Manuela Angestellt (" + E2ETestData.EMPLOYEE_MA_SIGN + ")");
      input(page).fill(E2ETestData.EMPLOYEE_MA_SIGN);
      assertThat(person).hasCount(1);
      // letters in order do not find it: "buch" would otherwise run through "Benutzer wechseln"
      input(page).fill("buch");
      assertThat(person).hasCount(0);

      input(page).fill(E2ETestData.EMPLOYEE_MA_SIGN);
      person.click();

      page.waitForLoadState();
      assertThat(menu(page).locator(".dropdown-item-text"))
          .containsText("Handelt als Manuela Angestellt (" + E2ETestData.EMPLOYEE_MA_SIGN + ")");
      page.keyboard().press(SHORTCUT);
      List<String> keys = commandKeys(page, "wechs");
      assertEquals(List.of("login-switch-exit"), keys);
      page.keyboard().press("Enter");

      page.waitForLoadState();
      assertThat(menu(page).locator(".dropdown-item-text")).not().containsText("Handelt als");
      assertEquals(null, page.evaluate("() => localStorage.getItem('salat-command-palette-recent')"));
    });
  }

  private static Locator userMenuToggle(Page page) {
    return page.locator("#user-menu-toggle");
  }

  private static Locator menu(Page page) {
    return page.locator("#salat-nav .navbar-footer [data-palette-menu]");
  }

  private static Locator input(Page page) {
    return page.locator("#commandPaletteInput");
  }

  private static Locator options(Page page) {
    return page.locator("#commandPaletteList [role=option]");
  }

  @SuppressWarnings("unchecked")
  private static List<String> commandKeys(Page page, String typed) {
    input(page).fill(typed);
    assertThat(options(page).first()).isVisible();
    return (List<String>) page.evaluate("() => Array.from(document.querySelectorAll("
        + "'#commandPaletteList [data-command-type=cmd], #commandPaletteList [data-command-type=login]'))"
        + ".map(o => o.dataset.commandKey)");
  }
}
