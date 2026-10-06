package de.hbt.salat.jira.service;

import static de.hbt.salat.common.exception.ErrorCode.JI_TICKET_IMPORT_EMPTY;
import static de.hbt.salat.common.exception.ErrorCode.JI_TICKET_IMPORT_UNREADABLE;
import static java.nio.charset.StandardCharsets.UTF_8;

import com.opencsv.CSVReaderBuilder;
import com.opencsv.RFC4180ParserBuilder;
import com.opencsv.exceptions.CsvException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.StringReader;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import de.hbt.salat.common.exception.InvalidDataException;

/**
 * A ticket file as headings and rows (#1386): CSV — semicolon or comma, as the heading shows — or an
 * Excel workbook (.xlsx), whose first sheet is read. Blank rows are left out; every row keeps the line
 * number the user sees, so that a finding names the place to look.
 */
record JiraTicketFile(List<String> headings, List<JiraTicketFile.Line> lines) {

  record Line(int number, List<String> cells) {

    /**
     * The trimmed cell, {@code null} where the row is shorter or the cell blank. Characters beyond
     * what the text columns of {@code jira_ticket} hold — three bytes each, so no emoji — are left
     * out, as the replication leaves out what its columns cannot hold.
     */
    String cell(int column) {
      if (column >= cells.size() || cells.get(column) == null) return null;
      var value = storable(cells.get(column)).trim();
      return value.isEmpty() ? null : value;
    }
  }

  /** An .xlsx is a zip archive; anything else is read as CSV. */
  static JiraTicketFile read(byte[] content) {
    if (content == null || content.length == 0) {
      throw new InvalidDataException(JI_TICKET_IMPORT_EMPTY);
    }
    var file = isZip(content) ? readWorkbook(content) : readCsv(content);
    if (file.headings().isEmpty()) {
      throw new InvalidDataException(JI_TICKET_IMPORT_EMPTY);
    }
    return file;
  }

  private static boolean isZip(byte[] content) {
    return content.length > 3 && content[0] == 'P' && content[1] == 'K' && content[2] == 3 && content[3] == 4;
  }

  /** What a spreadsheet in a German locale saves as CSV, where the file is no valid UTF-8. */
  private static final Charset WINDOWS_1252 = Charset.forName("windows-1252");

  /**
   * The separator is taken from the heading: a semicolon where it has one — what a spreadsheet saves
   * in a German locale — a comma otherwise, as JIRA exports.
   *
   * <p>Read as RFC 4180 writes it: a quote is escaped by doubling it, and a backslash is a character
   * like any other — a path in a summary keeps it, and one at the end of a cell does not swallow the
   * closing quote. A row keeps the number of its first line, also below a cell spanning several.
   */
  private static JiraTicketFile readCsv(byte[] content) {
    var text = decode(content);
    if (text.startsWith("\uFEFF")) text = text.substring(1);
    var heading = text.lines().findFirst().orElse("");
    char separator = heading.contains(";") ? ';' : ',';
    var headings = new ArrayList<String>();
    var lines = new ArrayList<Line>();
    try (var reader = new CSVReaderBuilder(new StringReader(text))
        .withCSVParser(new RFC4180ParserBuilder().withSeparator(separator).build())
        .build()) {
      String[] columns;
      int firstLine = (int) reader.getLinesRead() + 1;
      while ((columns = reader.readNext()) != null) {
        if (headings.isEmpty()) {
          headings.addAll(Arrays.asList(columns));
        } else if (!isBlank(Arrays.asList(columns))) {
          lines.add(new Line(firstLine, Arrays.asList(columns)));
        }
        firstLine = (int) reader.getLinesRead() + 1;
      }
    } catch (IOException | CsvException ex) {
      throw new InvalidDataException(JI_TICKET_IMPORT_UNREADABLE, ex);
    }
    return new JiraTicketFile(headings, lines);
  }

  /** UTF-8 where the file is valid UTF-8 — JIRA exports it — and Windows-1252 otherwise. */
  private static String decode(byte[] content) {
    try {
      return UTF_8.newDecoder()
          .onMalformedInput(CodingErrorAction.REPORT)
          .onUnmappableCharacter(CodingErrorAction.REPORT)
          .decode(ByteBuffer.wrap(content))
          .toString();
    } catch (CharacterCodingException ex) {
      return new String(content, WINDOWS_1252);
    }
  }

  private static JiraTicketFile readWorkbook(byte[] content) {
    try (var workbook = new XSSFWorkbook(new ByteArrayInputStream(content))) {
      var sheet = workbook.getSheetAt(0);
      var formatter = new DataFormatter();
      // A formula is read as the value the sheet shows, as last calculated by the program that saved it.
      formatter.setUseCachedValuesForFormulaCells(true);
      var headings = new ArrayList<String>();
      var lines = new ArrayList<Line>();
      boolean headingRead = false;
      for (Row row : sheet) {
        var cells = cellsOf(row, formatter);
        if (!headingRead) {
          if (isBlank(cells)) continue;
          headings.addAll(cells);
          headingRead = true;
        } else if (!isBlank(cells)) {
          lines.add(new Line(row.getRowNum() + 1, cells));
        }
      }
      return new JiraTicketFile(headings, lines);
    } catch (IOException | RuntimeException ex) {
      if (ex instanceof InvalidDataException invalid) throw invalid;
      throw new InvalidDataException(JI_TICKET_IMPORT_UNREADABLE, ex);
    }
  }

  /**
   * A date cell becomes an ISO timestamp rather than whatever display format the sheet uses, which
   * would be ambiguous between day and month.
   */
  private static List<String> cellsOf(Row row, DataFormatter formatter) {
    var cells = new ArrayList<String>();
    for (int column = 0; column < Math.max(row.getLastCellNum(), 0); column++) {
      Cell cell = row.getCell(column);
      if (cell == null) {
        cells.add(null);
      } else if (cell.getCellType() == CellType.NUMERIC && DateUtil.isCellDateFormatted(cell)) {
        cells.add(cell.getLocalDateTimeCellValue().toString());
      } else {
        cells.add(formatter.formatCellValue(cell));
      }
    }
    return cells;
  }

  /** The text without the characters outside the Basic Multilingual Plane, which take four bytes. */
  static String storable(String value) {
    if (value == null || value.codePoints().allMatch(Character::isBmpCodePoint)) return value;
    var result = new StringBuilder(value.length());
    value.codePoints().filter(Character::isBmpCodePoint).forEach(result::appendCodePoint);
    return result.toString();
  }

  private static boolean isBlank(List<String> cells) {
    return cells.stream().allMatch(cell -> cell == null || cell.isBlank());
  }
}
