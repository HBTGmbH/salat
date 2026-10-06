package de.hbt.salat.jira.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.springframework.web.multipart.MultipartFile;
import de.hbt.salat.jira.domain.JiraImportColumn;
import de.hbt.salat.jira.domain.JiraImportTarget;

/**
 * The import of a ticket file (#1386): the file, the scope, and how each column is read — one entry
 * per column, by position, as the preview offered it.
 *
 * <p>The reading travels as one JSON field, {@link #mapping}, not as fields per column: Tomcat takes
 * at most 100 parts per multipart request ({@code server.tomcat.max-part-count}), and a JIRA export
 * with all its fields has far more columns than that. {@link #columns} only fills the preview.
 */
@Getter
@Setter
public class JiraTicketImportForm {

  private Long customerorderId;
  private Long suborderId;
  private MultipartFile file;
  private List<Column> columns = new ArrayList<>();

  /** The reading of every column as a JSON array of {@code {target, fieldName, inherited}}. */
  private String mapping;

  private static final ObjectMapper JSON = new ObjectMapper();

  @Getter
  @Setter
  public static class Column {
    private JiraImportTarget target = JiraImportTarget.IGNORE;
    private String fieldName;
    private boolean inherited;

    static Column of(JiraImportColumn column, String heading) {
      var form = new Column();
      form.setTarget(column.target());
      form.setFieldName(column.fieldName() != null ? column.fieldName() : heading);
      form.setInherited(column.inherited());
      return form;
    }

    JiraImportColumn toColumn() {
      return new JiraImportColumn(target != null ? target : JiraImportTarget.IGNORE, fieldName, inherited);
    }
  }

  /**
   * The reading of the columns as {@link #mapping} carries it; empty where it is missing or unreadable,
   * which the import reports as a reading that does not fit the file.
   */
  List<JiraImportColumn> readMapping() {
    if (mapping == null || mapping.isBlank()) return List.of();
    try {
      return JSON.readValue(mapping, new TypeReference<List<Column>>() {}).stream().map(Column::toColumn).toList();
    } catch (JsonProcessingException ex) {
      return List.of();
    }
  }
}
