package de.hbt.salat.e2e.layout;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import java.util.regex.Pattern;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import de.hbt.salat.e2e.E2EBrowser;
import de.hbt.salat.e2e.E2ETestData;
import de.hbt.salat.e2e.PlaywrightE2ETestBase;
import de.hbt.salat.reporting.domain.ReportDefinition;
import de.hbt.salat.reporting.persistence.ReportDefinitionRepository;

/**
 * The command {@code report} (#1493): a report and its values typed into the palette. With every
 * value the report runs at once; with any missing its parameter page opens with what was typed, the
 * focus in the first field still open. A month without its year brings the year along, and the year
 * typed next replaces it.
 *
 * <p>The class runs on the base class's day, Thursday 2026-06-25: {@code sep} is September 2025. The
 * manager may execute every report, so no rules are needed. Report names are unique (#1333), and
 * the database is shared by every test of a run — each test names its report after itself.
 */
class CommandPaletteReportE2ETest extends PlaywrightE2ETestBase {

  private static final String SHORTCUT = "ControlOrMeta+k";

  @Autowired
  private ReportDefinitionRepository reportDefinitionRepository;

  @ParameterizedTest(name = "{0}")
  @MethodSource("de.hbt.salat.e2e.PlaywrightE2ETestBase#browsers")
  void a_month_brings_its_year_along_and_the_year_typed_next_replaces_it(E2EBrowser browser) {
    var name = "Palette Umsatz ersetzt " + browser;
    var id = report(name, "select cast(:monat as int) as monat_wert, cast(:jahr as int) as jahr_wert");

    runAsUser(browser, E2ETestData.EMPLOYEE_BL_SIGN, "/dailyreport/dashboard", page -> {
      page.keyboard().press(SHORTCUT);
      input(page).pressSequentially("report");
      assertThat(options(page).first()).hasAttribute("data-command-type", "verb");
      page.keyboard().press("Tab");
      assertThat(param(page)).hasText("Report?");

      input(page).pressSequentially("umsatz ersetzt");
      assertThat(options(page).first()).containsText(name);
      assertThat(options(page).first()).containsText("Parameter: monat, jahr");
      page.keyboard().press("Tab");
      assertThat(chips(page)).hasText(new String[] {"report", name});
      assertThat(param(page)).hasText("monat?");

      input(page).pressSequentially("sep ");
      assertThat(chips(page)).hasText(new String[] {"report", name, "monat September"});
      assertThat(param(page)).hasText("jahr?");
      assertThat(previewTitle(page)).hasText("Report ausführen");
      assertThat(previewValues(page)).hasText(name + " · monat September · jahr 2025");

      input(page).pressSequentially("2024");
      assertThat(previewValues(page)).hasText(name + " · monat September · jahr 2024");
      page.keyboard().press("Enter");

      page.waitForURL(Pattern.compile(".*/reporting/reports/execute\\?.*"));
      assertTrue(page.url().contains("id=" + id), page.url());
      assertTrue(page.url().contains("monat=number%2C9"), page.url());
      assertTrue(page.url().contains("jahr=number%2C2024"), page.url());
      assertThat(page.locator("table")).containsText("2024");
    });
  }

  /** In one go, without a year: the one the month brought along, and the report runs. */
  @ParameterizedTest(name = "{0}")
  @MethodSource("de.hbt.salat.e2e.PlaywrightE2ETestBase#browsers")
  void without_a_year_typed_the_report_runs_with_the_one_the_month_brought_along(E2EBrowser browser) {
    var name = "Palette Umsatz abgeleitet " + browser;
    var id = report(name, "select cast(:monat as int) as monat_wert, cast(:jahr as int) as jahr_wert");

    runAsUser(browser, E2ETestData.EMPLOYEE_BL_SIGN, "/dailyreport/dashboard", page -> {
      page.keyboard().press(SHORTCUT);
      input(page).fill("report umsatz abgeleitet");
      assertThat(options(page).first()).containsText(name);
      page.keyboard().press("Tab");

      input(page).pressSequentially("sep");
      assertThat(previewValues(page)).hasText(name + " · monat September · jahr 2025");
      page.keyboard().press("Enter");

      page.waitForURL(Pattern.compile(".*/reporting/reports/execute\\?.*"));
      assertTrue(page.url().contains("id=" + id), page.url());
      assertTrue(page.url().contains("monat=number%2C9"), page.url());
      assertTrue(page.url().contains("jahr=number%2C2025"), page.url());
      assertThat(page.locator("table")).containsText("2025");
    });
  }

  /**
   * A value missing: Enter opens the parameter page with what was typed, the focus in the first
   * field the palette left open. Nothing runs with a value the palette chose itself.
   */
  @ParameterizedTest(name = "{0}")
  @MethodSource("de.hbt.salat.e2e.PlaywrightE2ETestBase#browsers")
  void with_a_value_missing_enter_opens_the_parameter_page_at_the_first_open_field(E2EBrowser browser) {
    var name = "Palette Stunden teilweise " + browser;
    var id = report(name, "select cast(:von as date) as von_wert, cast(:person as varchar) as person_wert");

    runAsUser(browser, E2ETestData.EMPLOYEE_BL_SIGN, "/dailyreport/dashboard", page -> {
      page.keyboard().press(SHORTCUT);
      input(page).fill("report stunden teilweise");
      assertThat(options(page).first()).containsText(name);
      page.keyboard().press("Tab");
      assertThat(param(page)).hasText("von?");

      input(page).pressSequentially("gestern ");
      assertThat(chips(page)).hasText(new String[] {"report", name, "von Mi 24.06."});
      assertThat(param(page)).hasText("person?");
      assertThat(previewTitle(page)).hasText("Parameterseite des Reports öffnen");
      page.keyboard().press("Enter");

      page.waitForURL(Pattern.compile(".*/reporting/reports/execute\\?.*"));
      assertTrue(page.url().contains("id=" + id), page.url());
      assertTrue(page.url().contains("von=date%2C2026-06-24"), page.url());
      assertThat(page.locator("[name='parameters[0].value']")).hasValue("2026-06-24");
      assertThat(page.locator("[name='parameters[1].name']")).hasValue("person");
      assertThat(page.locator("[name='parameters[1].value']")).isFocused();
    });
  }

  /**
   * Whoever looks for a report types its name, not the command first: Tab on the report the search
   * found takes it into the command, and its values are typed next.
   */
  @ParameterizedTest(name = "{0}")
  @MethodSource("de.hbt.salat.e2e.PlaywrightE2ETestBase#browsers")
  void tab_on_a_report_the_search_found_takes_it_into_the_command(E2EBrowser browser) {
    var name = "Palette Umsatz gefunden " + browser;
    var id = report(name, "select cast(:monat as int) as monat_wert, cast(:jahr as int) as jahr_wert");

    runAsUser(browser, E2ETestData.EMPLOYEE_BL_SIGN, "/dailyreport/dashboard", page -> {
      page.keyboard().press(SHORTCUT);
      input(page).pressSequentially("umsatz gefunden");
      var found = page.locator("#commandPaletteList [role=option][data-command-type=object]", new Page.LocatorOptions().setHasText(name));
      assertThat(found).hasCount(1);
      assertThat(found).hasAttribute("aria-selected", "true");

      page.keyboard().press("Tab");

      assertThat(chips(page)).hasText(new String[] {"report", name});
      assertThat(param(page)).hasText("monat?");
      assertThat(input(page)).isFocused();
      assertThat(input(page)).hasValue("");

      input(page).pressSequentially("sep");
      page.keyboard().press("Enter");

      page.waitForURL(Pattern.compile(".*/reporting/reports/execute\\?.*"));
      assertTrue(page.url().contains("id=" + id), page.url());
      assertTrue(page.url().contains("monat=number%2C9"), page.url());
      assertTrue(page.url().contains("jahr=number%2C2025"), page.url());
    });
  }

  private long report(String name, String sql) {
    var report = new ReportDefinition();
    report.setName(name);
    report.setSql(sql);
    return reportDefinitionRepository.save(report).getId();
  }

  private static Locator input(Page page) {
    return page.locator("#commandPaletteInput");
  }

  private static Locator options(Page page) {
    return page.locator("#commandPaletteList [role=option]");
  }

  private static Locator param(Page page) {
    return page.locator("#commandPaletteParam");
  }

  private static Locator chips(Page page) {
    return page.locator("#commandPaletteChips [data-palette-chip]");
  }

  private static Locator previewTitle(Page page) {
    return page.locator("#commandPalettePreview [data-part=title]");
  }

  private static Locator previewValues(Page page) {
    return page.locator("#commandPalettePreview [data-part=values]");
  }
}
