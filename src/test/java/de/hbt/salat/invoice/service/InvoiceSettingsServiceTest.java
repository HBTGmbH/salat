package de.hbt.salat.invoice.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class InvoiceSettingsServiceTest {

  private static final Pattern RULE = Pattern.compile("([^{}]+)\\{([^}]*)}");

  /**
   * The heading row of a group consists of several cells since it carries the subtotal (#217); a
   * background on the cells restarts in each of them, so a template paints it on the row (#1566).
   */
  @Test
  void aBackgroundOfTheGroupHeadingIsPaintedOnTheRow() {
    var settings = new InvoiceSettingsService().getAllSettings();

    assertThat(settings).isNotEmpty();
    for (var setting : settings) {
      var rules = RULE.matcher(setting.getCustomCss().replaceAll("/\\*.*?\\*/", ""));
      while (rules.find()) {
        if (rules.group(2).contains("background")) {
          assertThat(rules.group(1).strip()).as(setting.getName()).isEqualTo("tr.invoice_suborder_row");
        }
      }
    }
  }
}
