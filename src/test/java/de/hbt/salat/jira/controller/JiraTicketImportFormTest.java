package de.hbt.salat.jira.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import de.hbt.salat.jira.domain.JiraImportColumn;
import de.hbt.salat.jira.domain.JiraImportTarget;

/**
 * The reading of the columns arrives as one JSON field (#1386): one field per column exceeded the 50
 * parts Tomcat takes per multipart request, and a JIRA export has far more columns.
 */
@DisplayNameGeneration(ReplaceUnderscores.class)
class JiraTicketImportFormTest {

  @Test
  void the_reading_of_every_column_is_read_from_one_field() {
    var form = new JiraTicketImportForm();
    form.setMapping("""
        [{"target":"KEY","fieldName":"Issue key","inherited":false},
         {"target":"ADDITIONAL","fieldName":"customfield_10500","inherited":true}]""");

    assertThat(form.readMapping()).containsExactly(
        new JiraImportColumn(JiraImportTarget.KEY, "Issue key", false),
        new JiraImportColumn(JiraImportTarget.ADDITIONAL, "customfield_10500", true));
  }

  @Test
  void hundreds_of_columns_fit_into_the_one_field() {
    var form = new JiraTicketImportForm();
    form.setMapping(IntStream.range(0, 300)
        .mapToObj(i -> "{\"target\":\"IGNORE\",\"fieldName\":\"Feld " + i + "\",\"inherited\":false}")
        .collect(Collectors.joining(",", "[", "]")));

    assertThat(form.readMapping()).hasSize(300);
  }

  /** The import then reports a reading that does not fit the file, rather than failing on its own. */
  @Test
  void a_missing_or_unreadable_field_is_no_reading() {
    var form = new JiraTicketImportForm();
    assertThat(form.readMapping()).isEmpty();
    form.setMapping("kein JSON");
    assertThat(form.readMapping()).isEmpty();
  }
}
