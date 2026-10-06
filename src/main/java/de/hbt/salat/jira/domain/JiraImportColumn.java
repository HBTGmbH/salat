package de.hbt.salat.jira.domain;

/**
 * How one column of an imported ticket file is read (#1386), by its position: a JIRA export repeats
 * headings, so the heading cannot name the column.
 *
 * @param fieldName for {@link JiraImportTarget#ADDITIONAL} the key in {@code custom_fields} — a JIRA
 *     response key such as {@code customfield_10123} is read by the reports like a replicated field
 * @param inherited for {@link JiraImportTarget#ADDITIONAL} whether a ticket without a value of its
 *     own takes the one of its nearest ancestor ({@code custom_fields_effective})
 */
public record JiraImportColumn(JiraImportTarget target, String fieldName, boolean inherited) {

  public static JiraImportColumn ignored() {
    return new JiraImportColumn(JiraImportTarget.IGNORE, null, false);
  }
}
