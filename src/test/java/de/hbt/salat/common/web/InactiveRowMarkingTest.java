package de.hbt.salat.common.web;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Inaktive Einträge in Listen tragen ein Badge „Inaktiv", keine getönte Zeile (#1220). {@code table-danger} und
 * {@code table-secondary} waren die beiden Tönungen, mit denen Listen inaktive Zeilen hinterlegt haben: im dunklen
 * Modus helle Flächen, auf denen Links und {@code text-muted} unter 2:1 fielen, und in einer Kartentabelle mit runden
 * Ecken mitten in der Tabelle, weil Tabler die erste und letzte Zelle jeder Zeile rundet.
 */
@DisplayNameGeneration(ReplaceUnderscores.class)
class InactiveRowMarkingTest {

  private static final Path TEMPLATES = Path.of("src/main/resources/templates");

  /** Ein öffnendes {@code <tr>} mit allen Attributen; ein {@code >} in einem Ausdruck steht in Anführungszeichen. */
  private static final Pattern TR_TAG = Pattern.compile("<tr\\b(?:[^>\"']|\"[^\"]*\"|'[^']*')*>");

  private static final Pattern INACTIVE_TINT = Pattern.compile("\\btable-(danger|secondary)\\b");

  @Test
  void no_table_row_is_tinted_the_way_inactive_rows_used_to_be() throws IOException {
    var offending = new ArrayList<String>();
    try (Stream<Path> files = Files.walk(TEMPLATES)) {
      for (Path file : files.filter(p -> p.toString().endsWith(".html")).toList()) {
        var matcher = TR_TAG.matcher(Files.readString(file, UTF_8));
        while (matcher.find()) {
          if (INACTIVE_TINT.matcher(matcher.group()).find()) {
            offending.add(TEMPLATES.relativize(file) + ": " + matcher.group());
          }
        }
      }
    }
    assertThat(offending)
        .describedAs("an inactive entry carries fragments/inactive-badge behind its name instead of a row tint")
        .isEmpty();
  }

  /** Die Bedingung je Liste ist dieselbe wie zuvor an der Tönung: das Flag beim Budgetplan, sonst ADR-0029. */
  @ParameterizedTest
  @CsvSource(delimiter = '|', value = {
      "budget/budget-list.html            | ${b.active != true}   | 1",
      "budget/pricing-list.html           | ${!p.currentlyValid}  | 1",
      "budget/flat-rate-list.html         | ${!f.currentlyValid}  | 1",
      "order/customer-order-list.html     | ${!co.currentlyValid} | 1",
      "order/sub-order-list.html          | ${!so.currentlyValid} | 1",
      "order/employee-order-list.html     | ${!eo.currentlyValid} | 1",
      "employee/employee-contract-list.html | ${!ec.currentlyValid} | 2",
  })
  void every_list_that_tinted_inactive_rows_marks_them_with_the_badge(String template, String condition, int times)
      throws IOException {
    var source = Files.readString(TEMPLATES.resolve(template), UTF_8);
    var call = "~{fragments/inactive-badge :: badge(" + condition + ")}";

    assertThat(source.split(Pattern.quote(call), -1)).hasSize(times + 1);
  }
}
