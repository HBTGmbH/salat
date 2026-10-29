package de.hbt.salat.e2e.achievement;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.ColorScheme;
import de.hbt.salat.achievement.domain.AchievementProfile;
import de.hbt.salat.achievement.domain.AchievementProfile.Reached;
import de.hbt.salat.achievement.service.AchievementService;
import de.hbt.salat.common.achievement.AchievementDefinition;
import de.hbt.salat.dailyreport.service.TrainingYearAchievementContributor;
import de.hbt.salat.employee.service.TenureAchievementContributor;
import de.hbt.salat.e2e.E2EBrowser;
import de.hbt.salat.e2e.E2ETestData;
import de.hbt.salat.e2e.PlaywrightE2ETestBase;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/** Real profile navigation, UiState and Bootstrap dialogs with fixed achievement data (#1553). */
class AchievementProfileE2ETest extends PlaywrightE2ETestBase {

  private static final String SIGN = E2ETestData.EMPLOYEE_MA_SIGN;
  private static final List<Reached> REACHED = List.of(
      reached(TrainingYearAchievementContributor.TRAINING_YEAR, 2, "2026-06-01"),
      reached(TenureAchievementContributor.TENURE, 5, "2026-05-01"),
      reached(TrainingYearAchievementContributor.TRAINING_YEAR, 2, "2025-06-01"));
  private static final List<AchievementProfile.Next> NEXT = List.of(
      new AchievementProfile.Next(TenureAchievementContributor.TENURE, 10, LocalDate.of(2031, 5, 1), null));

  @MockitoBean
  private AchievementService achievementService;

  @BeforeEach
  void profileData() {
    when(achievementService.getProfileOfCurrentPerson(any())).thenReturn(new AchievementProfile(REACHED, NEXT));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("de.hbt.salat.e2e.PlaywrightE2ETestBase#browsers")
  void the_keyboard_switches_to_types_and_the_next_visit_remembers_it(E2EBrowser browser) {
    runAsUser(browser, SIGN, "/profile", page -> {
      assertThat(page.locator("[aria-labelledby=achievementSortLabel] [aria-current=true]")).hasText("Zeitpunkt");
      var types = page.locator("a[href='/profile?fAchievementSort=type']");
      types.focus();
      types.press("Enter");
      assertThat(page.locator("h4[id^=achievementGroup-]")).hasText(new String[] {"Loyalist", "Fortbildungsjahr"});
      assertThat(page.locator(".achievement-tile-button[data-bs-target]")).hasCount(REACHED.size());

      page.navigate(urlWithLogin("/profile", SIGN));
      assertThat(page.locator("[aria-labelledby=achievementSortLabel] [aria-current=true]")).hasText("Typ, dann Zeitpunkt");
      page.screenshot(new Page.ScreenshotOptions().setPath(Path.of("target/achievement-profile-type-" + browser + ".png")).setFullPage(true));
      checkDialogs(page);

      page.locator("a[href='/profile?fAchievementSort=date']").click();
      assertThat(page.locator("h4[id^=achievementGroup-]")).hasCount(0);
      page.screenshot(new Page.ScreenshotOptions().setPath(Path.of("target/achievement-profile-date-" + browser + ".png")).setFullPage(true));
      checkDialogs(page);
    });
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("de.hbt.salat.e2e.PlaywrightE2ETestBase#browsers")
  void both_views_fit_a_phone_in_both_colour_modes_and_in_english(E2EBrowser browser) {
    runAsUser(browser, SIGN, "/profile?fAchievementSort=type", "en-US", page -> {
      page.setViewportSize(390, 844);
      for (var theme : List.of("light", "dark")) {
        page.emulateMedia(new Page.EmulateMediaOptions().setColorScheme(theme.equals("dark") ? ColorScheme.DARK : ColorScheme.LIGHT));
        page.evaluate("theme => toggleTheme(theme)", theme);
        for (var sort : List.of("type", "date")) {
          page.locator("a[href='/profile?fAchievementSort=" + sort + "']").click();
          assertThat(page.locator("html")).hasAttribute("data-bs-theme", theme);
          assertThat(page.locator("[aria-labelledby=achievementSortLabel] [aria-current=true]"))
              .hasText(sort.equals("type") ? "Type, then date reached" : "Date reached");
          org.assertj.core.api.Assertions.assertThat(page.evaluate(
              "() => document.documentElement.scrollWidth <= window.innerWidth")).isEqualTo(true);
          assertThat(page.locator(".achievement-tile-button[data-bs-target]")).hasCount(REACHED.size());
          if (sort.equals("type")) {
            assertThat(page.locator("h4[id^=achievementGroup-]")).hasText(new String[] {"Loyalist", "Training year"});
          }
          page.screenshot(new Page.ScreenshotOptions().setPath(Path.of(
              "target/achievement-profile-mobile-" + sort + "-" + theme + "-" + browser + ".png")).setFullPage(true));
        }
      }
    });
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("de.hbt.salat.e2e.PlaywrightE2ETestBase#browsers")
  void without_reached_medals_both_views_still_show_next(E2EBrowser browser) {
    when(achievementService.getProfileOfCurrentPerson(any())).thenReturn(new AchievementProfile(List.of(), NEXT));
    runAsUser(browser, SIGN, "/profile?fAchievementSort=type", page -> {
      for (var sort : List.of("type", "date")) {
        page.locator("a[href='/profile?fAchievementSort=" + sort + "']").click();
        assertThat(page.locator("h4[id^=achievementGroup-]")).hasCount(0);
        assertThat(page.locator(".achievement-tile-button[data-bs-target]")).hasCount(0);
        assertThat(page.locator(".achievement-tile-button[data-info-popover]")).hasCount(NEXT.size());
      }
    });
  }

  private static void checkDialogs(Page page) {
    var medals = page.locator(".achievement-tile-button[data-bs-target]");
    for (int index = 0; index < medals.count(); index++) {
      var medal = medals.nth(index);
      var rank = medal.locator(".fw-bold").innerText();
      var day = medal.locator(".small").innerText();
      var dialog = page.locator(medal.getAttribute("data-bs-target"));
      medal.click();
      assertThat(dialog).isVisible();
      assertThat(dialog.locator(".modal-body")).containsText(rank);
      assertThat(dialog.locator(".modal-body")).containsText(day);
      dialog.locator(".modal-footer button[data-bs-dismiss=modal]").click();
      assertThat(dialog).not().isVisible();
    }
  }

  private static Reached reached(AchievementDefinition definition, int level, String day) {
    return new Reached(definition, level, definition.tierOf(level), LocalDate.parse(day));
  }
}
