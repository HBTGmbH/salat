package org.tb.e2e.reporting;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.tb.e2e.E2EBrowser;
import org.tb.e2e.E2ETestData;
import org.tb.e2e.PlaywrightE2ETestBase;
import org.tb.reporting.domain.ReportDefinition;
import org.tb.reporting.persistence.ReportDefinitionRepository;

/**
 * A report whose SQL fails must show the database error, not a 500.
 *
 * <p>The error details moved onto the nested {@code ReportResult.ErrorInfo} in a refactoring that
 * did not update the view, and because the block only renders for a failing report the mismatch
 * stayed invisible for months (#848). This test walks that path so it cannot happen unnoticed
 * again.
 */
class ReportErrorDisplayE2ETest extends PlaywrightE2ETestBase {

  @Autowired
  private ReportDefinitionRepository reportDefinitionRepository;

  @ParameterizedTest(name = "{0}")
  @MethodSource("org.tb.e2e.PlaywrightE2ETestBase#browsers")
  void a_failing_report_shows_the_database_error_instead_of_a_500(E2EBrowser browser) {
    var broken = new ReportDefinition();
    broken.setName("E2E broken report");
    // no placeholders, so execution is not deflected to the parameter form
    broken.setSql("select * from a_table_that_does_not_exist");
    var id = reportDefinitionRepository.save(broken).getId();

    // the manager is authorized for every report, so no authorization rows are needed
    runAsUser(browser, E2ETestData.EMPLOYEE_BL_SIGN, "/reporting/reports/execute?id=" + id, page -> {

      var alert = page.locator(".alert-danger");
      assertThat(alert).isVisible();
      assertThat(alert).containsText("BadSqlGrammarException");
      // the identifier travels in the driver message, which is localised — assert on the identifier
      assertThat(alert).containsText("a_table_that_does_not_exist");
      assertThat(alert).containsText("SQLState");

      // the view offers "Show failing SQL", so the statement has to be carried into the result
      assertThat(page.locator(".alert-danger pre code"))
          .containsText("a_table_that_does_not_exist");
    });
  }

}
