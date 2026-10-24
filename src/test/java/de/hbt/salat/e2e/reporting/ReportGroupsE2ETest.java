package de.hbt.salat.e2e.reporting;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import de.hbt.salat.e2e.E2EBrowser;
import de.hbt.salat.e2e.E2ETestData;
import de.hbt.salat.e2e.PlaywrightE2ETestBase;
import de.hbt.salat.reporting.domain.ReportDefinition;
import de.hbt.salat.reporting.domain.ReportGroup;
import de.hbt.salat.reporting.persistence.ReportDefinitionRepository;
import de.hbt.salat.reporting.persistence.ReportGroupRepository;

/**
 * The report list by group (#1523): each group opens and closes by its heading, all of them at once by
 * the two buttons, by mouse and by keyboard, and what is closed stays closed on the next visit. A group
 * of its own per run, named after the browser — the E2E database is shared, and the assertions look at
 * that group only.
 */
class ReportGroupsE2ETest extends PlaywrightE2ETestBase {

  private static final String LIST = "/reporting/reports";

  @Autowired
  private ReportGroupRepository reportGroupRepository;

  @Autowired
  private ReportDefinitionRepository reportDefinitionRepository;

  @ParameterizedTest(name = "{0}")
  @MethodSource("de.hbt.salat.e2e.PlaywrightE2ETestBase#browsers")
  void a_group_closes_and_opens_and_stays_as_it_was_left(E2EBrowser browser) {
    var groupId = groupWithReport("E2E Gruppe " + browser, "E2E Report " + browser);

    runAsUser(browser, E2ETestData.EMPLOYEE_BL_SIGN, LIST, page -> {
      var toggle = toggle(page, groupId);
      assertThat(toggle).hasAttribute("aria-expanded", "true");
      assertThat(toggle).containsText("1");
      assertThat(body(page, groupId)).isVisible();

      toggle.click();
      assertThat(toggle).hasAttribute("aria-expanded", "false");
      assertThat(body(page, groupId)).isHidden();

      page.reload();
      assertThat(toggle(page, groupId)).hasAttribute("aria-expanded", "false");
      assertThat(body(page, groupId)).isHidden();

      // the keyboard opens it again
      toggle(page, groupId).focus();
      page.keyboard().press("Enter");
      assertThat(toggle(page, groupId)).hasAttribute("aria-expanded", "true");
      assertThat(body(page, groupId)).isVisible();
    });
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("de.hbt.salat.e2e.PlaywrightE2ETestBase#browsers")
  void all_groups_close_and_open_at_once(E2EBrowser browser) {
    var groupId = groupWithReport("E2E Alle " + browser, "E2E Alle Report " + browser);

    runAsUser(browser, E2ETestData.EMPLOYEE_BL_SIGN, LIST, page -> {
      page.locator("[data-report-groups-expand=false]").click();
      assertThat(page.locator(".report-group-toggle[aria-expanded=true]")).hasCount(0);
      assertThat(body(page, groupId)).isHidden();

      page.locator("[data-report-groups-expand=true]").click();
      assertThat(page.locator(".report-group-toggle[aria-expanded=false]")).hasCount(0);
      assertThat(body(page, groupId)).isVisible();
    });
  }

  /** A filter opens every group it leaves, whatever was closed before. */
  @ParameterizedTest(name = "{0}")
  @MethodSource("de.hbt.salat.e2e.PlaywrightE2ETestBase#browsers")
  void the_free_text_finds_a_report_by_its_group_and_opens_the_group(E2EBrowser browser) {
    var groupName = "E2E Filter " + browser;
    var groupId = groupWithReport(groupName, "E2E Gefiltert " + browser);

    runAsUser(browser, E2ETestData.EMPLOYEE_BL_SIGN, LIST, page -> {
      toggle(page, groupId).click();
      assertThat(body(page, groupId)).isHidden();

      page.navigate(urlWithLogin(LIST + "?fReportFilter=" + groupName.replace(' ', '+'), E2ETestData.EMPLOYEE_BL_SIGN));

      assertThat(page.locator("[data-report-group]")).hasCount(1);
      assertThat(body(page, groupId)).isVisible();
      assertThat(body(page, groupId)).containsText("E2E Gefiltert " + browser);

      // the filter is remembered (UiState); clear it for the next test
      page.navigate(urlWithLogin(LIST + "?fReportFilter=", E2ETestData.EMPLOYEE_BL_SIGN));
    });
  }

  private long groupWithReport(String groupName, String title) {
    var group = new ReportGroup();
    group.setName(groupName);
    group = reportGroupRepository.save(group);
    var report = new ReportDefinition();
    report.setName(title);
    report.setSql("select 1");
    report.setGroup(group);
    reportDefinitionRepository.save(report);
    return group.getId();
  }

  private static Locator toggle(Page page, long groupId) {
    return page.locator("[data-report-group='" + groupId + "'] .report-group-toggle");
  }

  private static Locator body(Page page, long groupId) {
    return page.locator("#report-group-" + groupId);
  }
}
