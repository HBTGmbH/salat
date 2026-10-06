package de.hbt.salat.jira.controller;

import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.springframework.web.multipart.MultipartFile;
import de.hbt.salat.jira.domain.JiraImportColumn;
import de.hbt.salat.jira.domain.JiraImportTarget;

/**
 * The import of a ticket file (#1386): the file, the scope the filter shows, and how each column is
 * read — one entry per column, by position, as the preview offered it.
 */
@Getter
@Setter
public class JiraTicketImportForm {

  private Long customerorderId;
  private Long suborderId;
  private MultipartFile file;
  private List<Column> columns = new ArrayList<>();

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

  List<JiraImportColumn> mapping() {
    return columns.stream().map(Column::toColumn).toList();
  }
}
