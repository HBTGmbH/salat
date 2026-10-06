package de.hbt.salat.jira.service;

import static de.hbt.salat.common.exception.ErrorCode.JI_TICKET_IMPORT_DATE_INVALID;
import static de.hbt.salat.common.exception.ErrorCode.JI_TICKET_IMPORT_EMPTY;
import static de.hbt.salat.common.exception.ErrorCode.JI_TICKET_IMPORT_FIELD_NAME_MISSING;
import static de.hbt.salat.common.exception.ErrorCode.JI_TICKET_IMPORT_ID_INVALID;
import static de.hbt.salat.common.exception.ErrorCode.JI_TICKET_IMPORT_ID_TWICE;
import static de.hbt.salat.common.exception.ErrorCode.JI_TICKET_IMPORT_KEY_MISSING;
import static de.hbt.salat.common.exception.ErrorCode.JI_TICKET_IMPORT_KEY_TWICE;
import static de.hbt.salat.common.exception.ErrorCode.JI_TICKET_IMPORT_MAPPING_MISMATCH;
import static de.hbt.salat.common.exception.ErrorCode.JI_TICKET_IMPORT_NO_KEY_COLUMN;
import static de.hbt.salat.common.exception.ErrorCode.JI_TICKET_IMPORT_TARGET_TWICE;
import static de.hbt.salat.common.exception.ErrorCode.JI_TICKET_IMPORT_VALUE_TOO_LONG;
import static de.hbt.salat.common.exception.ServiceFeedbackMessage.error;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.IntStream;
import de.hbt.salat.common.exception.InvalidDataException;
import de.hbt.salat.common.exception.ServiceFeedbackMessage;
import de.hbt.salat.jira.domain.JiraImportColumn;
import de.hbt.salat.jira.domain.JiraImportMappingEntry;
import de.hbt.salat.jira.domain.JiraImportTarget;
import de.hbt.salat.jira.domain.JiraTicketImportPreview;

/**
 * Reads the tickets of a file by the columns the user assigned (#1386) — what the headings suggest,
 * confirmed or changed in the preview. Only reads: what to create and what to update is the
 * service's business.
 */
final class JiraTicketImportReader {

  /** How many different values the preview shows of a column. */
  static final int SAMPLE_VALUES = 5;

  /** The column widths of {@code jira_ticket}. */
  static final int KEY_LENGTH = 64;
  static final int SUMMARY_LENGTH = 1024;
  static final int ISSUE_TYPE_LENGTH = 128;
  static final int LABELS_LENGTH = 4000;

  /**
   * The formats a date is read in: ISO as an Excel date cell becomes, the German and the ISO way of
   * writing it, and what a JIRA export writes ({@code 25/Jun/26 3:05 PM}). A date without time is
   * the start of the day.
   */
  private static final List<DateTimeFormatter> DATE_TIMES = List.of(
      DateTimeFormatter.ISO_LOCAL_DATE_TIME,
      DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm[:ss]"),
      DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm[:ss]"),
      new DateTimeFormatterBuilder().parseCaseInsensitive().appendPattern("dd/MMM/yy h:mm a").toFormatter(Locale.ENGLISH));
  private static final List<DateTimeFormatter> DATES = List.of(
      DateTimeFormatter.ISO_LOCAL_DATE,
      DateTimeFormatter.ofPattern("dd.MM.yyyy"),
      new DateTimeFormatterBuilder().parseCaseInsensitive().appendPattern("dd/MMM/yy").toFormatter(Locale.ENGLISH));

  /**
   * A ticket as the file names it.
   *
   * @param jiraId {@code null} where the file has no id column or the row no id
   * @param created {@code null} where the file has no column for it
   * @param customFields the additional fields with a value, by the name the user gave them
   */
  record Ticket(int line, String key, Long jiraId, String summary, String issueType, String labels,
      String parentKey, LocalDateTime created, LocalDateTime updated, Map<String, String> customFields) {
  }

  private JiraTicketImportReader() {
  }

  /** {@link #preview(JiraTicketFile, List)} without an earlier import to go by. */
  static JiraTicketImportPreview preview(JiraTicketFile file) {
    return preview(file, List.of());
  }

  /**
   * How each column would be read: as the latest import of the scope read the column of the same
   * heading (#1386) — a repeated heading by its occurrence — otherwise as the heading suggests. A
   * field that takes one column goes to the first column naming it; a later one is left out, so that
   * the suggestion never fails the import on its own.
   *
   * @param previous the column reading of the latest import of the scope, empty if there was none
   */
  static JiraTicketImportPreview preview(JiraTicketFile file, List<JiraImportMappingEntry> previous) {
    var taken = EnumSet.noneOf(JiraImportTarget.class);
    var suggested = new ArrayList<JiraImportColumn>();
    var seen = new HashMap<String, Integer>();
    for (var heading : file.headings()) {
      var normalised = normalised(heading);
      int occurrence = seen.merge(normalised, 1, Integer::sum) - 1;
      var earlier = previous.stream().filter(entry -> normalised(entry.heading()).equals(normalised))
          .skip(occurrence).findFirst();
      var column = earlier
          .map(entry -> new JiraImportColumn(entry.target(), entry.fieldName(), entry.inherited()))
          .orElseGet(() -> new JiraImportColumn(JiraImportTarget.suggestedFor(heading), null, false));
      var target = column.target();
      if (!target.isMultiple() && target != JiraImportTarget.IGNORE && !taken.add(target)) {
        column = JiraImportColumn.ignored();
      }
      suggested.add(column);
    }
    var samples = IntStream.range(0, file.headings().size()).mapToObj(column -> sampleValues(file, column)).toList();
    return new JiraTicketImportPreview(file.headings(), samples, suggested, file.lines().size(), null);
  }

  /**
   * The tickets of the file. Every faulty row is reported with its line number, and then nothing is
   * read at all.
   *
   * @param keysByJiraIdInScope the tickets already in the scope that carry a JIRA id — a parent named
   *     by id is looked up there when the file does not have it
   */
  static List<Ticket> read(JiraTicketFile file, List<JiraImportColumn> mapping, Map<Long, String> keysByJiraIdInScope) {
    checkMapping(file, mapping);
    var findings = new ArrayList<ServiceFeedbackMessage>();
    var keysById = new HashMap<Long, String>(keysByJiraIdInScope);
    var firstLineOfKey = new HashMap<String, Integer>();
    var firstLineOfId = new HashMap<Long, Integer>();
    var tickets = new ArrayList<Ticket>();

    for (var line : file.lines()) {
      int findingsBefore = findings.size();
      var key = single(line, mapping, JiraImportTarget.KEY);
      var id = id(line, mapping, findings);
      var created = date(line, mapping, JiraImportTarget.CREATED, findings);
      var updated = date(line, mapping, JiraImportTarget.UPDATED, findings);
      var summary = single(line, mapping, JiraImportTarget.SUMMARY);
      var issueType = single(line, mapping, JiraImportTarget.ISSUE_TYPE);
      var labels = joined(line, mapping, JiraImportTarget.LABELS);

      if (key == null) {
        findings.add(error(JI_TICKET_IMPORT_KEY_MISSING, line.number()));
      } else if (longerThan(key, KEY_LENGTH) || longerThan(summary, SUMMARY_LENGTH)
          || longerThan(issueType, ISSUE_TYPE_LENGTH) || longerThan(labels, LABELS_LENGTH)) {
        findings.add(error(JI_TICKET_IMPORT_VALUE_TOO_LONG, line.number()));
      } else if (firstLineOfKey.containsKey(key)) {
        findings.add(error(JI_TICKET_IMPORT_KEY_TWICE, line.number(), key, firstLineOfKey.get(key)));
      } else if (id != null && firstLineOfId.containsKey(id)) {
        findings.add(error(JI_TICKET_IMPORT_ID_TWICE, line.number(), id, firstLineOfId.get(id)));
      }
      if (findings.size() > findingsBefore) continue;

      firstLineOfKey.put(key, line.number());
      if (id != null) {
        firstLineOfId.put(id, line.number());
        keysById.put(id, key);
      }
      tickets.add(new Ticket(line.number(), key, id, summary, issueType, labels, null, created, updated,
          customFields(line, mapping)));
    }
    if (!findings.isEmpty()) {
      throw new InvalidDataException(findings);
    }
    if (tickets.isEmpty()) {
      throw new InvalidDataException(JI_TICKET_IMPORT_EMPTY);
    }

    // Second pass: a parent named by id may stand further down in the file.
    var lines = new HashMap<Integer, JiraTicketFile.Line>();
    file.lines().forEach(line -> lines.put(line.number(), line));
    return tickets.stream()
        .map(ticket -> withParent(ticket, parentOf(lines.get(ticket.line()), mapping, keysById)))
        .toList();
  }

  private static void checkMapping(JiraTicketFile file, List<JiraImportColumn> mapping) {
    if (mapping == null || mapping.size() != file.headings().size()) {
      throw new InvalidDataException(JI_TICKET_IMPORT_MAPPING_MISMATCH);
    }
    var taken = EnumSet.noneOf(JiraImportTarget.class);
    for (int column = 0; column < mapping.size(); column++) {
      var target = mapping.get(column).target();
      var heading = file.headings().get(column);
      if (target != JiraImportTarget.IGNORE && !target.isMultiple() && !taken.add(target)) {
        throw new InvalidDataException(JI_TICKET_IMPORT_TARGET_TWICE, heading);
      }
      if (target == JiraImportTarget.ADDITIONAL && isBlank(mapping.get(column).fieldName())) {
        throw new InvalidDataException(JI_TICKET_IMPORT_FIELD_NAME_MISSING, heading);
      }
    }
    if (!taken.contains(JiraImportTarget.KEY)) {
      throw new InvalidDataException(JI_TICKET_IMPORT_NO_KEY_COLUMN);
    }
  }

  /** The cell of the one column read as {@code target}; {@code null} where there is none or it is blank. */
  private static String single(JiraTicketFile.Line line, List<JiraImportColumn> mapping, JiraImportTarget target) {
    var columns = columnsOf(mapping, target);
    return columns.isEmpty() ? null : line.cell(columns.getFirst());
  }

  /** The values of every column read as {@code target}, joined with a comma; {@code null} if none has one. */
  private static String joined(JiraTicketFile.Line line, List<JiraImportColumn> mapping, JiraImportTarget target) {
    var values = columnsOf(mapping, target).stream().map(line::cell).filter(value -> value != null).toList();
    return values.isEmpty() ? null : String.join(",", values);
  }

  private static Long id(JiraTicketFile.Line line, List<JiraImportColumn> mapping,
                         List<ServiceFeedbackMessage> findings) {
    var value = single(line, mapping, JiraImportTarget.ID);
    if (value == null) return null;
    try {
      return Long.parseLong(value);
    } catch (NumberFormatException ex) {
      findings.add(error(JI_TICKET_IMPORT_ID_INVALID, line.number(), value));
      return null;
    }
  }

  private static LocalDateTime date(JiraTicketFile.Line line, List<JiraImportColumn> mapping, JiraImportTarget target,
                                    List<ServiceFeedbackMessage> findings) {
    var value = single(line, mapping, target);
    if (value == null) return null;
    var parsed = parseDate(value);
    if (parsed.isEmpty()) {
      findings.add(error(JI_TICKET_IMPORT_DATE_INVALID, line.number(), value));
    }
    return parsed.orElse(null);
  }

  static Optional<LocalDateTime> parseDate(String value) {
    for (var format : DATE_TIMES) {
      try {
        return Optional.of(LocalDateTime.parse(value, format));
      } catch (DateTimeParseException ignored) {
        // the next format
      }
    }
    for (var format : DATES) {
      try {
        return Optional.of(LocalDate.parse(value, format).atStartOfDay());
      } catch (DateTimeParseException ignored) {
        // the next format
      }
    }
    return Optional.empty();
  }

  /** Several columns of the same name are joined with a comma, like the labels. */
  private static Map<String, String> customFields(JiraTicketFile.Line line, List<JiraImportColumn> mapping) {
    var fields = new LinkedHashMap<String, String>();
    for (int column = 0; column < mapping.size(); column++) {
      var assigned = mapping.get(column);
      var value = line.cell(column);
      if (assigned.target() != JiraImportTarget.ADDITIONAL || value == null) continue;
      fields.merge(assigned.fieldName().trim(), value, (one, other) -> one + "," + other);
    }
    return fields;
  }

  /**
   * The first parent column that answers. A number is an id and is looked up; anything without a
   * blank in it is taken as a key. A value with blanks is a title rather than a reference, and a
   * parent that is the ticket itself is none.
   */
  private static String parentOf(JiraTicketFile.Line line, List<JiraImportColumn> mapping, Map<Long, String> keysById) {
    var key = single(line, mapping, JiraImportTarget.KEY);
    for (var column : columnsOf(mapping, JiraImportTarget.PARENT)) {
      var value = line.cell(column);
      if (value == null) continue;
      String parent;
      if (value.chars().allMatch(Character::isDigit)) {
        parent = value.length() <= 18 ? keysById.get(Long.parseLong(value)) : null;
      } else {
        parent = value.chars().anyMatch(Character::isWhitespace) || value.length() > KEY_LENGTH ? null : value;
      }
      if (parent != null && !parent.equals(key)) return parent;
    }
    return null;
  }

  private static Ticket withParent(Ticket ticket, String parentKey) {
    return new Ticket(ticket.line(), ticket.key(), ticket.jiraId(), ticket.summary(), ticket.issueType(),
        ticket.labels(), parentKey, ticket.created(), ticket.updated(), ticket.customFields());
  }

  private static List<Integer> columnsOf(List<JiraImportColumn> mapping, JiraImportTarget target) {
    return IntStream.range(0, mapping.size()).filter(column -> mapping.get(column).target() == target).boxed().toList();
  }

  private static boolean longerThan(String value, int max) {
    return value != null && value.length() > max;
  }

  /**
   * The first different values of a column, as far down the file as it takes: a repeated value says
   * nothing more about what the column holds, and a column that is empty in its first rows may well
   * be filled further down.
   */
  private static List<String> sampleValues(JiraTicketFile file, int column) {
    var values = new LinkedHashSet<String>();
    for (var line : file.lines()) {
      var value = line.cell(column);
      if (value != null) values.add(value);
      if (values.size() == SAMPLE_VALUES) break;
    }
    return List.copyOf(values);
  }

  private static String normalised(String heading) {
    return heading == null ? "" : heading.trim().toLowerCase(Locale.ROOT);
  }

  private static boolean isBlank(String value) {
    return value == null || value.isBlank();
  }
}
