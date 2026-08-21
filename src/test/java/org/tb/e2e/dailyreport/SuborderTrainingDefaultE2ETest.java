package org.tb.e2e.dailyreport;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.tb.common.GlobalConstants.SUBRORDER_SIGN_TRAINING;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.tb.e2e.E2EBrowser;
import org.tb.e2e.E2ETestData;
import org.tb.e2e.PlaywrightE2ETestBase;

/**
 * A suborder can carry a default flag for project based training ({@code Suborder.trainingFlag}).
 * Choosing such a suborder in the booking form must switch "Fortbildung" on - the evaluation was
 * lost with the Struts module and is back in the Thymeleaf flow (#836).
 */
class SuborderTrainingDefaultE2ETest extends PlaywrightE2ETestBase {

  @ParameterizedTest(name = "{0}")
  @MethodSource("org.tb.e2e.PlaywrightE2ETestBase#browsers")
  void choosing_a_suborder_with_the_training_flag_switches_fortbildung_on(E2EBrowser browser) {
    runAsUser(browser, E2ETestData.EMPLOYEE_MA_SIGN, newBookingPath(), page -> {
      // start from a suborder without the flag, so the switch is off no matter which suborder
      // the form would preselect on its own
      openBookingFormFor(page, suborderIdOf(page, E2ETestData.SUBORDER_ALPHA_DEV_SIGN));
      assertThat(trainingSwitch(page)).not().isChecked();

      selectTomSelectOption(page, "suborderId", SUBRORDER_SIGN_TRAINING);

      assertThat(trainingSwitch(page)).isChecked();
    });
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("org.tb.e2e.PlaywrightE2ETestBase#browsers")
  void a_preselected_suborder_with_the_training_flag_renders_the_switch_on(E2EBrowser browser) {
    runAsUser(browser, E2ETestData.EMPLOYEE_MA_SIGN, newBookingPath(), page -> {
      openBookingFormFor(page, suborderIdOf(page, SUBRORDER_SIGN_TRAINING));

      assertThat(trainingSwitch(page)).isChecked();
    });
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("org.tb.e2e.PlaywrightE2ETestBase#browsers")
  void an_explicit_training_parameter_wins_over_the_suborder_default(E2EBrowser browser) {
    runAsUser(browser, E2ETestData.EMPLOYEE_MA_SIGN, newBookingPath(), page -> {
      // the share-with-colleagues deeplink carries the training state of the shared booking and
      // must not be overruled by the default flag of its suborder
      String trainingSuborderId = suborderIdOf(page, SUBRORDER_SIGN_TRAINING);
      page.navigate(urlWithLogin(newBookingPath() + "?suborderId=" + trainingSuborderId
          + "&training=false", E2ETestData.EMPLOYEE_MA_SIGN));

      assertThat(trainingSwitch(page)).not().isChecked();
    });
  }

  private String newBookingPath() {
    return "/dailyreport/timereports/new";
  }

  private Locator trainingSwitch(Page page) {
    return page.locator("#trainingSwitch");
  }

  /** Reads the option value of a suborder from the rendered dropdown, keyed by its sign. */
  private String suborderIdOf(Page page, String sign) {
    Object value = page.evaluate(
        "sign => Array.from(document.querySelectorAll('#suborderId option'))"
            + ".find(o => o.textContent.includes(sign)).value", sign);
    return String.valueOf(value);
  }

  private void openBookingFormFor(Page page, String suborderId) {
    page.navigate(urlWithLogin(newBookingPath() + "?suborderId=" + suborderId,
        E2ETestData.EMPLOYEE_MA_SIGN));
  }

}
