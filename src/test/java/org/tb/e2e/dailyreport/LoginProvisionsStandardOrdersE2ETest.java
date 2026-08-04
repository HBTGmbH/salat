package org.tb.e2e.dailyreport;

import static org.assertj.core.api.Assertions.assertThat;

import com.microsoft.playwright.Locator;
import java.time.Year;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.tb.common.util.ClockProvider;
import org.tb.e2e.E2EBrowser;
import org.tb.e2e.E2ETestData;
import org.tb.e2e.PlaywrightE2ETestBase;

/**
 * Verifies the standard-suborder provisioning flow end-to-end: logging in as an employee must
 * make the Urlaub/Krankheit/Fortbildung suborders (all seeded with {@code standard=true})
 * available in the "new timereport" suborder dropdown, because the login mechanism
 * (see {@code AuthorizedUserChangedListener} / {@code EmployeeorderService.generateMissingStandardOrders})
 * silently creates the missing Employeeorders.
 */
class LoginProvisionsStandardOrdersE2ETest extends PlaywrightE2ETestBase {

  @ParameterizedTest(name = "{0}")
  @EnumSource(E2EBrowser.class)
  void standard_suborders_are_offered_after_login(E2EBrowser browser) {
    String currentYear = String.valueOf(Year.now(ClockProvider.getClock()).getValue());

    runAsUser(browser, E2ETestData.EMPLOYEE_MA_SIGN, "/dailyreport/timereports/new", page -> {
      Locator suborderOptions = page.locator("#suborderId option");
      String allOptionsText = String.join("|", suborderOptions.allTextContents());

      assertThat(allOptionsText).contains(currentYear);
      assertThat(allOptionsText).contains(E2ETestData.SUBORDER_KRANKHEIT_SIGN);
      assertThat(allOptionsText).contains("FORTBILDUNG");
      assertThat(allOptionsText).contains(E2ETestData.SUBORDER_ALPHA_DEV_SIGN);
    });
  }

}
