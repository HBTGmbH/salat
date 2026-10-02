package de.hbt.salat.e2e.dailyreport;

import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.List;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import de.hbt.salat.e2e.E2EBrowser;
import de.hbt.salat.e2e.E2ETestData;
import de.hbt.salat.e2e.PlaywrightE2ETestBase;

/**
 * The charts on "Meine Konten" tint some of their colours: the difference label above each bar and
 * the planned series. The tint is built from a Tabler token read at runtime, and since Tabler 1.6
 * those tokens are {@code oklch()}, no longer hex. A tint that cannot read its token ends up as a
 * tint of black, so a difference label must never be one.
 */
class MyAccountsChartColorsE2ETest extends PlaywrightE2ETestBase {

  @ParameterizedTest(name = "{0}")
  @MethodSource("de.hbt.salat.e2e.PlaywrightE2ETestBase#browsers")
  void difference_labels_are_tinted_with_their_token(E2EBrowser browser) {
    runAsUser(browser, E2ETestData.EMPLOYEE_MA_SIGN, "/my-accounts", page -> {
      var labels = page.locator("#workingTimeChart .apexcharts-point-annotations rect");
      labels.first().waitFor();

      // the browser resolves the fill, whatever notation ApexCharts was handed
      @SuppressWarnings("unchecked")
      List<List<Number>> fills = (List<List<Number>>) labels.evaluateAll("""
          rects => rects.map(rect => {
            const context = document.createElement('canvas').getContext('2d');
            context.fillStyle = rect.getAttribute('fill');
            context.fillRect(0, 0, 1, 1);
            return Array.from(context.getImageData(0, 0, 1, 1).data);
          })""");

      assertFalse(fills.isEmpty(), "the chart shows no difference labels");
      fills.forEach(rgba -> assertFalse(
          rgba.get(0).intValue() == 0 && rgba.get(1).intValue() == 0 && rgba.get(2).intValue() == 0,
          "a difference label is tinted black: " + rgba));
    });
  }
}
