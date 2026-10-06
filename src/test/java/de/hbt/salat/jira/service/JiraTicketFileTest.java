package de.hbt.salat.jira.service;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;

/**
 * A ticket file as it arrives (#1386): CSV as RFC 4180 writes it, in UTF-8 or as a German
 * spreadsheet saves it, and Excel with formulas.
 */
@DisplayNameGeneration(ReplaceUnderscores.class)
class JiraTicketFileTest {

  /** A backslash is a character like any other: CSV escapes quotes by doubling them. */
  @Test
  void a_backslash_is_kept() {
    var file = JiraTicketFile.read("Key,Summary\nABC-1,C:\\Temp\\x\nABC-2,\"Pfad C:\\\"\n".getBytes(UTF_8));

    assertThat(file.lines()).extracting(line -> line.cell(1)).containsExactly("C:\\Temp\\x", "Pfad C:\\");
  }

  /** What a spreadsheet in a German locale saves as CSV is Windows-1252, not UTF-8. */
  @Test
  void a_file_that_is_no_utf8_is_read_as_windows_1252() {
    var file = JiraTicketFile.read("Key;Summary\nABC-1;Änderung für Größe\n".getBytes(Charset.forName("windows-1252")));

    assertThat(file.lines()).extracting(line -> line.cell(1)).containsExactly("Änderung für Größe");
  }

  @Test
  void utf8_stays_utf8() {
    var file = JiraTicketFile.read("Key;Summary\nABC-1;Änderung für Größe\n".getBytes(UTF_8));

    assertThat(file.lines()).extracting(line -> line.cell(1)).containsExactly("Änderung für Größe");
  }

  /** A finding names the line the user sees in an editor, also below a cell spanning several lines. */
  @Test
  void a_row_keeps_the_number_of_its_first_line() {
    var file = JiraTicketFile.read("Key,Description\nABC-1,\"eins\nzwei\ndrei\"\nABC-2,vier\n".getBytes(UTF_8));

    assertThat(file.lines()).extracting(JiraTicketFile.Line::number).containsExactly(2, 5);
  }

  /** A formula cell is read as the value the sheet shows, not as its formula. */
  @Test
  void a_formula_is_read_as_its_value() throws IOException {
    byte[] content;
    try (var workbook = new XSSFWorkbook(); var out = new ByteArrayOutputStream()) {
      var sheet = workbook.createSheet();
      var heading = sheet.createRow(0);
      heading.createCell(0).setCellValue("Project");
      heading.createCell(1).setCellValue("Number");
      heading.createCell(2).setCellValue("Key");
      var row = sheet.createRow(1);
      row.createCell(0).setCellValue("ABC");
      row.createCell(1).setCellValue(7);
      row.createCell(2).setCellFormula("A2&\"-\"&B2");
      workbook.getCreationHelper().createFormulaEvaluator().evaluateAll();
      workbook.write(out);
      content = out.toByteArray();
    }

    var file = JiraTicketFile.read(content);

    assertThat(file.lines()).singleElement().satisfies(line -> assertThat(line.cell(2)).isEqualTo("ABC-7"));
  }
}
