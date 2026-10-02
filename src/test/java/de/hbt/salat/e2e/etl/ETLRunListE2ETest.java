package de.hbt.salat.e2e.etl;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import java.util.regex.Pattern;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import de.hbt.salat.e2e.E2EBrowser;
import de.hbt.salat.e2e.E2ETestData;
import de.hbt.salat.e2e.PlaywrightE2ETestBase;

/**
 * The refresh button in the head of the ETL run list (#1286). A run started from the list goes on in
 * the background and stands as "running" until the list is loaded again; the button is that reload,
 * and it must not lose the filter the list was showing. Only a browser shows both: that the link
 * really reloads, and that the remembered filter comes back without being in the link.
 */
class ETLRunListE2ETest extends PlaywrightE2ETestBase {

  @ParameterizedTest(name = "{0}")
  @MethodSource("de.hbt.salat.e2e.PlaywrightE2ETestBase#browsers")
  void refreshing_the_list_keeps_its_filter(E2EBrowser browser) {
    runAsUser(browser, E2ETestData.EMPLOYEE_BL_SIGN, "/etl/runs", page -> {
      var failedOnly = page.getByLabel("Nur Fehlerhafte");
      if (failedOnly.isChecked()) {
        failedOnly.click(); // the filter is remembered per login, start from a known state
        page.waitForURL("**fEtlRunFailedOnly=false**");
      }
      failedOnly.click();
      page.waitForURL("**fEtlRunFailedOnly=true**");

      page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName("Aktualisieren")).click();

      // the link carries no filter of its own - the UiState filter fills in the remembered one
      assertThat(page).hasURL(Pattern.compile(".*/etl/runs$"));
      assertThat(page.getByLabel("Nur Fehlerhafte")).isChecked();

      // and leaves the switch as it was found, the E2E database is shared
      page.getByLabel("Nur Fehlerhafte").click();
      page.waitForURL("**fEtlRunFailedOnly=false**");
    });
  }

}
