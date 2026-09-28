package de.hbt.salat.dailyreport.viewhelper;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.thymeleaf.context.Context;
import org.thymeleaf.context.IExpressionContext;
import org.thymeleaf.linkbuilder.StandardLinkBuilder;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;
import org.thymeleaf.templateresolver.StringTemplateResolver;
import de.hbt.salat.dailyreport.domain.MatrixData;
import de.hbt.salat.dailyreport.domain.MatrixData.DayHeader;
import de.hbt.salat.dailyreport.domain.MatrixData.FooterDay;

/**
 * „Nicht gearbeitet" hatte in der Matrix eine eigene Zeile unter Beginn, Pause und Ende, mit einem Punkt ohne Text
 * darin (#1159). Jetzt nimmt die Markierung an diesem Tag den Platz der drei Zeilen ein, die dort ohnehin leer sind:
 * eine Zelle mit {@code rowspan="3"} in der Zeile Beginn, keine in Pause und Ende. Ohne diese drei Zeilen (externer
 * Vertrag) steht sie in der Zeile GESAMT.
 */
@DisplayNameGeneration(ReplaceUnderscores.class)
class MatrixTableFragmentTest {

  private static final Pattern ROW = Pattern.compile("<tr[^>]*>(.*?)</tr>", Pattern.DOTALL);
  private static final Pattern CELL = Pattern.compile("<t[dh](\\s[^>]*)?>(.*?)</t[dh]>", Pattern.DOTALL);
  private static final Pattern ROWSPAN = Pattern.compile("rowspan=\"(\\d+)\"");
  private static final Pattern COLSPAN = Pattern.compile("colspan=\"(\\d+)\"");

  /** Montag gearbeitet, Dienstag nicht gearbeitet, Mittwoch leer. */
  private static final MatrixData MONTH = new MatrixData(
      List.of(
          new DayHeader(7, LocalDate.of(2026, 9, 7), "main.matrixoverview.weekdays.monday.text", false, false, false),
          new DayHeader(8, LocalDate.of(2026, 9, 8), "main.matrixoverview.weekdays.tuesday.text", false, false, false),
          new DayHeader(9, LocalDate.of(2026, 9, 9), "main.matrixoverview.weekdays.wednesday.text", false, false, false)),
      List.of(),
      List.of(),
      List.of(
          new FooterDay("7:30", false, false, false, false, "08:00", "0:30", "16:00", false, false, false),
          new FooterDay("0:00", true, false, false, true, null, null, null, false, false, false),
          new FooterDay("0:00", false, false, false, true, null, null, null, false, false, false)),
      "7:30", null, null, false, null, false);

  @Test
  void a_not_worked_day_takes_the_place_of_begin_break_and_end_with_one_marker() {
    var html = render(true, true);

    var begin = row(html, "Arbeitsbeginn");
    assertThat(begin).hasSize(1 + 3 + 1);
    assertThat(begin.get(2)).contains("rowspan=\"3\"").contains("ti-calendar-off");
    assertThat(row(html, "Pausendauer")).hasSize(1 + 2 + 1);
    assertThat(row(html, "Arbeitsende")).hasSize(1 + 2 + 1);

    assertThat(footer(html)).containsOnlyOnce("ti-calendar-off");
    assertThat(html).doesNotContain("●");
  }

  @Test
  void there_is_no_row_not_worked_anymore() {
    var html = render(true, true);

    assertThat(rows(html)).noneMatch(cells -> cells.getFirst().contains(">Nicht gearbeitet<"));
  }

  @Test
  void the_icon_only_marker_carries_a_tooltip_and_a_text_for_screen_readers() {
    var marker = row(render(true, true), "Arbeitsbeginn").get(2);

    assertThat(marker).contains("title=\"Nicht gearbeitet\"");
    assertThat(marker).contains("aria-hidden=\"true\"");
    assertThat(marker).containsPattern("class=\"visually-hidden\"\\s*>Nicht gearbeitet<");
  }

  @Test
  void every_row_of_the_footer_still_spans_all_columns() {
    assertThat(columnsPerRow(footer(render(true, true)))).containsOnly(1 + 3 + 1);
  }

  @Test
  void without_begin_break_and_end_the_marker_sits_in_the_total_row() {
    var html = render(false, true);

    assertThat(rows(html)).noneMatch(cells -> cells.getFirst().contains(">Arbeitsbeginn<"));
    var total = row(html, "GESAMT");
    assertThat(total.get(2)).contains("ti-calendar-off");
    assertThat(total.get(1)).doesNotContain("ti-calendar-off").contains("7:30");
  }

  @Test
  void the_aggregated_matrix_shows_no_marker_and_keeps_every_cell() {
    var html = render(true, false);

    assertThat(html).doesNotContain("ti-calendar-off");
    assertThat(row(html, "Pausendauer")).hasSize(1 + 3 + 1);
  }

  private static String render(boolean showBeginBreakEnd, boolean showNotWorked) {
    var fragments = new ClassLoaderTemplateResolver();
    fragments.setPrefix("templates/");
    fragments.setSuffix(".html");
    fragments.setCharacterEncoding("UTF-8");
    fragments.setCheckExistence(true);
    fragments.setOrder(1);
    var caller = new StringTemplateResolver();
    caller.setOrder(2);

    var messageSource = new ResourceBundleMessageSource();
    messageSource.setBasename("de/hbt/salat/web/MessageResources");
    messageSource.setDefaultEncoding("UTF-8");
    messageSource.setFallbackToSystemLocale(false);

    var engine = new SpringTemplateEngine();
    engine.addTemplateResolver(fragments);
    engine.addTemplateResolver(caller);
    engine.setTemplateEngineMessageSource(messageSource);
    // Die Tagesspalten tragen einen kontextrelativen Link; ausserhalb einer Web-Anfrage gibt es keinen Kontextpfad.
    engine.setLinkBuilder(new StandardLinkBuilder() {
      @Override
      protected String computeContextPath(IExpressionContext context, String base, Map<String, Object> parameters) {
        return "";
      }
    });

    var context = new Context(Locale.GERMANY, Map.of(
        "matrixData", MONTH, "showBeginBreakEnd", showBeginBreakEnd, "showNotWorked", showNotWorked));
    return engine.process(
        "<th:block th:replace=\"~{fragments/matrix-table :: matrixTable(${matrixData}, ${showBeginBreakEnd}, ${showNotWorked})}\"></th:block>",
        context);
  }

  private static String footer(String html) {
    return html.substring(html.indexOf("<tfoot>"), html.indexOf("</tfoot>"));
  }

  /** Die Zellen jeder Tabellenzeile, als Quelltext mit ihren Attributen. */
  private static List<List<String>> rows(String html) {
    var rows = new ArrayList<List<String>>();
    Matcher row = ROW.matcher(html);
    while (row.find()) {
      var cells = new ArrayList<String>();
      Matcher cell = CELL.matcher(row.group(1));
      while (cell.find()) {
        cells.add(cell.group());
      }
      rows.add(cells);
    }
    return rows;
  }

  private static List<String> row(String html, String label) {
    return rows(html).stream()
        .filter(cells -> cells.getFirst().contains(">" + label + "<"))
        .findFirst()
        .orElseThrow(() -> new AssertionError("keine Zeile " + label));
  }

  /** Die Breite jeder Zeile im Raster: ihre eigenen Zellen plus die, in die eine Zelle darueber hineinreicht. */
  private static List<Integer> columnsPerRow(String table) {
    var widths = new ArrayList<Integer>();
    var reachingDown = new ArrayList<Integer>();
    for (var cells : rows(table)) {
      int width = (int) reachingDown.stream().filter(rest -> rest > 0).count();
      reachingDown.replaceAll(rest -> rest - 1);
      for (var cell : cells) {
        width += span(COLSPAN, cell);
        int rowspan = span(ROWSPAN, cell);
        if (rowspan > 1) {
          reachingDown.add(rowspan - 1);
        }
      }
      widths.add(width);
    }
    return widths;
  }

  private static int span(Pattern attribute, String cell) {
    Matcher match = attribute.matcher(cell.substring(0, cell.indexOf('>')));
    return match.find() ? Integer.parseInt(match.group(1)) : 1;
  }
}
