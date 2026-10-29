package de.hbt.salat.e2e.dailyreport;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.assertj.core.api.Assertions.assertThat;

import com.microsoft.playwright.Page;
import de.hbt.salat.e2e.E2EBrowser;
import de.hbt.salat.e2e.E2ETestData;
import de.hbt.salat.e2e.PlaywrightE2ETestBase;
import java.util.List;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * The chosen period of the training tab stays readable in both colour modes. A plain
 * {@code .btn.active} puts Tabler's primary on a faint tint of itself and reaches 2.75:1 in the
 * dark mode; the chosen period is filled like the sort choice on the profile page instead.
 */
class MyAccountsTrainingPeriodE2ETest extends PlaywrightE2ETestBase {

  /** WCAG contrast of the element's text against its background, translucent layers blended in. */
  private static final String CONTRAST = """
      element => {
        const context = document.createElement('canvas').getContext('2d', {willReadFrequently: true});
        const rgba = colour => {
          context.clearRect(0, 0, 1, 1);
          context.fillStyle = colour;
          context.fillRect(0, 0, 1, 1);
          const [r, g, b, a] = context.getImageData(0, 0, 1, 1).data;
          return {r, g, b, a: a / 255};
        };
        const over = (top, below) => ({r: top.r * top.a + below.r * (1 - top.a),
            g: top.g * top.a + below.g * (1 - top.a), b: top.b * top.a + below.b * (1 - top.a), a: 1});
        const layers = [];
        for (let e = element; e; e = e.parentElement) layers.unshift(rgba(getComputedStyle(e).backgroundColor));
        const background = layers.reduce((below, layer) => over(layer, below), {r: 255, g: 255, b: 255, a: 1});
        const text = over(rgba(getComputedStyle(element).color), background);
        const channel = v => (v /= 255) <= 0.03928 ? v / 12.92 : Math.pow((v + 0.055) / 1.055, 2.4);
        const luminance = c => 0.2126 * channel(c.r) + 0.7152 * channel(c.g) + 0.0722 * channel(c.b);
        const [light, dark] = [luminance(text), luminance(background)].sort((x, y) => y - x);
        return (light + 0.05) / (dark + 0.05);
      }""";

  @ParameterizedTest(name = "{0}")
  @MethodSource("de.hbt.salat.e2e.PlaywrightE2ETestBase#browsers")
  void the_chosen_period_is_readable_in_both_colour_modes(E2EBrowser browser) {
    runAsUser(browser, E2ETestData.EMPLOYEE_MA_SIGN, "/my-accounts", page -> {
      // measured in the middle of a colour transition, the values would be interpolations
      page.addStyleTag(new Page.AddStyleTagOptions().setContent("* { transition: none !important; }"));
      page.locator("#tab-training-button").click();
      var chosen = page.locator("#tab-training [aria-current=true]");
      assertThat(chosen).isVisible();

      for (var theme : List.of("light", "dark")) {
        page.evaluate("theme => toggleTheme(theme)", theme);
        assertThat(page.locator("html")).hasAttribute("data-bs-theme", theme);
        assertThat(((Number) chosen.evaluate(CONTRAST)).doubleValue()).as(theme).isGreaterThanOrEqualTo(4.5);
      }
    });
  }
}
