package de.hbt.salat.jira.domain;

import java.util.Locale;
import java.util.Set;
import lombok.Getter;

/**
 * What a column of an imported ticket file is read as (#1386) — the columns of {@code jira_ticket}
 * that a file can fill. {@code top_level_key} and {@code custom_fields_effective} are not among them:
 * they follow from the parent chain and are resolved after the import, as the replication does.
 */
@Getter
public enum JiraImportTarget {

  IGNORE("main.jira.ticket.import.target.ignore", false),
  /** {@code issue_key}, required. */
  KEY("main.jira.ticket.import.target.key", false, "key", "issue key", "schlüssel", "schluessel",
      "vorgangsschlüssel", "ticket"),
  /** {@code jira_id}, the numeric id JIRA gave the issue. */
  ID("main.jira.ticket.import.target.id", false, "id", "issue id", "jira id", "vorgangs-id", "vorgangs id"),
  SUMMARY("main.jira.ticket.import.target.summary", false, "summary", "titel", "title", "zusammenfassung"),
  ISSUE_TYPE("main.jira.ticket.import.target.issuetype", false, "issue type", "type", "typ", "vorgangstyp"),
  /** Several columns are joined with a comma: a JIRA export repeats the column for every label. */
  LABELS("main.jira.ticket.import.target.labels", true, "labels", "label", "stichwörter", "stichworte"),
  /**
   * {@code parent_key}. Several columns are allowed — JIRA names the parent in different ways, by
   * key or by id — and the first one that answers wins. An id is looked up in the id column of the
   * file and among the tickets already in the scope.
   */
  PARENT("main.jira.ticket.import.target.parent", true),
  CREATED("main.jira.ticket.import.target.created", false, "created", "erstellt"),
  UPDATED("main.jira.ticket.import.target.updated", false, "updated", "aktualisiert"),
  /** {@code custom_fields}, under a name the user chooses, by default the heading. */
  ADDITIONAL("main.jira.ticket.import.target.additional", true);

  private final String label;

  /** Whether more than one column may be read as this. */
  private final boolean multiple;

  private final Set<String> headings;

  JiraImportTarget(String label, boolean multiple, String... headings) {
    this.label = label;
    this.multiple = multiple;
    this.headings = Set.of(headings);
  }

  /**
   * What a heading suggests. Every heading naming a parent counts as one, unless it names the
   * parent's title — a JIRA export carries "Parent summary" next to "Parent".
   */
  public static JiraImportTarget suggestedFor(String heading) {
    var normalised = heading == null ? "" : heading.trim().toLowerCase(Locale.ROOT);
    if (namesParent(normalised) && !namesTitle(normalised)) {
      return PARENT;
    }
    for (var target : values()) {
      if (target.headings.contains(normalised)) return target;
    }
    return IGNORE;
  }

  private static boolean namesParent(String heading) {
    return heading.contains("parent") || heading.contains("eltern") || heading.contains("übergeordnet");
  }

  private static boolean namesTitle(String heading) {
    return heading.contains("summary") || heading.contains("titel") || heading.contains("zusammenfassung");
  }
}
