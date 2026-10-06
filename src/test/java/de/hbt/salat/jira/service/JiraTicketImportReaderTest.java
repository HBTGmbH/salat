package de.hbt.salat.jira.service;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import de.hbt.salat.jira.domain.JiraImportColumn;
import de.hbt.salat.jira.domain.JiraImportTarget;

/**
 * Reading a ticket file (#1386): what the headings suggest, CSV in both separators, Excel, and the
 * ways a parent and a date are written.
 */
@DisplayNameGeneration(ReplaceUnderscores.class)
class JiraTicketImportReaderTest {

  @Test
  void the_headings_suggest_the_columns_of_jira_ticket() {
    var file = JiraTicketFile.read("""
        Issue key,Issue id,Summary,Issue Type,Labels,Labels,Parent,Parent id,Parent summary,Created,Updated,Sprint
        ABC-1,1,Eins,Epic,a,,,,,,,
        """.getBytes(UTF_8));

    assertThat(JiraTicketImportReader.preview(file).suggested()).extracting(JiraImportColumn::target).containsExactly(
        JiraImportTarget.KEY, JiraImportTarget.ID, JiraImportTarget.SUMMARY, JiraImportTarget.ISSUE_TYPE,
        JiraImportTarget.LABELS, JiraImportTarget.LABELS, JiraImportTarget.PARENT, JiraImportTarget.PARENT,
        JiraImportTarget.IGNORE, JiraImportTarget.CREATED, JiraImportTarget.UPDATED, JiraImportTarget.IGNORE);
  }

  /** A field taking one column is suggested once; a second heading for it is left to the user. */
  @Test
  void a_second_heading_for_a_single_field_is_not_suggested() {
    var file = JiraTicketFile.read("Schlüssel;Key\nABC-1;ABC-1\n".getBytes(UTF_8));

    assertThat(JiraTicketImportReader.preview(file).suggested()).extracting(JiraImportColumn::target)
        .containsExactly(JiraImportTarget.KEY, JiraImportTarget.IGNORE);
  }

  /** Each column shows its first different values, as far down as it takes; all rows are counted. */
  @Test
  void the_preview_shows_different_values_per_column_and_counts_all_rows() {
    var file = JiraTicketFile.read("\uFEFFKey;Labels;Team\nA-1;alpha;\n\nA-2;alpha;\nA-3;beta;\nA-4;alpha;\nA-5;;\nA-6;gamma;Blau\nA-7;;\n"
        .getBytes(UTF_8));

    var preview = JiraTicketImportReader.preview(file);

    assertThat(preview.headings()).containsExactly("Key", "Labels", "Team");
    assertThat(preview.rowCount()).isEqualTo(7);
    assertThat(preview.sampleValues().get(0)).containsExactly("A-1", "A-2", "A-3", "A-4", "A-5");
    assertThat(preview.sampleValues().get(1)).containsExactly("alpha", "beta", "gamma");
    assertThat(preview.sampleValues().get(2)).containsExactly("Blau");
  }


  /** JIRA names a parent by id or by key; the first parent column that names a known ticket counts. */
  @Test
  void a_parent_is_read_by_key_or_by_id() {
    var file = JiraTicketFile.read("""
        Key,Id,Parent,Parent link
        A-1,11,,
        A-2,12,11,
        A-3,13,99,A-2
        A-4,14,,Titel mit Leerzeichen
        A-5,15,77,
        """.getBytes(UTF_8));
    var mapping = List.of(column(JiraImportTarget.KEY), column(JiraImportTarget.ID),
        column(JiraImportTarget.PARENT), column(JiraImportTarget.PARENT));

    var tickets = JiraTicketImportReader.read(file, mapping, Map.of(77L, "SCOPE-7"));

    assertThat(tickets).extracting(JiraTicketImportReader.Ticket::parentKey)
        .containsExactly(null, "A-1", "A-2", null, "SCOPE-7");
  }

  @Test
  void dates_are_read_in_the_usual_ways() {
    assertThat(JiraTicketImportReader.parseDate("2026-06-25T15:05")).contains(LocalDateTime.of(2026, 6, 25, 15, 5));
    assertThat(JiraTicketImportReader.parseDate("25.06.2026 15:05")).contains(LocalDateTime.of(2026, 6, 25, 15, 5));
    assertThat(JiraTicketImportReader.parseDate("25/jun/26 3:05 pm")).contains(LocalDateTime.of(2026, 6, 25, 15, 5));
    assertThat(JiraTicketImportReader.parseDate("25.06.2026")).contains(LocalDateTime.of(2026, 6, 25, 0, 0));
    assertThat(JiraTicketImportReader.parseDate("gestern")).isEmpty();
  }

  @Test
  void an_excel_workbook_is_read_with_its_dates_and_numbers() throws IOException {
    byte[] content;
    try (var workbook = new XSSFWorkbook(); var out = new ByteArrayOutputStream()) {
      var sheet = workbook.createSheet();
      var heading = sheet.createRow(0);
      heading.createCell(0).setCellValue("Issue key");
      heading.createCell(1).setCellValue("Issue id");
      heading.createCell(2).setCellValue("Created");
      var row = sheet.createRow(1);
      row.createCell(0).setCellValue("ABC-1");
      row.createCell(1).setCellValue(10001);
      var created = row.createCell(2);
      created.setCellValue(LocalDateTime.of(2026, 6, 25, 15, 5));
      var style = workbook.createCellStyle();
      style.setDataFormat(workbook.getCreationHelper().createDataFormat().getFormat("dd.mm.yyyy hh:mm"));
      created.setCellStyle(style);
      workbook.write(out);
      content = out.toByteArray();
    }

    var file = JiraTicketFile.read(content);
    var tickets = JiraTicketImportReader.read(file, JiraTicketImportReader.preview(file).suggested(), Map.of());

    assertThat(tickets).singleElement().satisfies(ticket -> {
      assertThat(ticket.key()).isEqualTo("ABC-1");
      assertThat(ticket.jiraId()).isEqualTo(10001L);
      assertThat(ticket.created()).isEqualTo(LocalDateTime.of(2026, 6, 25, 15, 5));
      assertThat(ticket.line()).isEqualTo(2);
    });
  }

  @Test
  void additional_columns_of_the_same_name_are_joined() {
    var file = JiraTicketFile.read("Key;Sprint;Sprint\nA-1;S1;S2\n".getBytes(UTF_8));
    var mapping = List.of(column(JiraImportTarget.KEY),
        new JiraImportColumn(JiraImportTarget.ADDITIONAL, "sprint", false),
        new JiraImportColumn(JiraImportTarget.ADDITIONAL, "sprint", false));

    assertThat(JiraTicketImportReader.read(file, mapping, Map.of())).singleElement()
        .satisfies(ticket -> assertThat(ticket.customFields()).containsEntry("sprint", "S1,S2"));
  }

  private static JiraImportColumn column(JiraImportTarget target) {
    return new JiraImportColumn(target, null, false);
  }
}
