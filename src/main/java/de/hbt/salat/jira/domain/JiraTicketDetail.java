package de.hbt.salat.jira.domain;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

/**
 * Everything the detail page of a ticket shows (#1386).
 *
 * @param row what the list shows of it
 * @param labels the labels one by one
 * @param createdTs created in JIRA, or when created by hand or named by an import
 * @param lastChange when the row was last written, and by whom
 * @param customFields the additional fields as the ticket carries them itself
 * @param effectiveFields the inherited fields as resolved along the parent chain
 * @param children the tickets of the same scope that name it as parent
 * @param importedFrom the file of the import that wrote the ticket last, {@code null} if none did
 */
public record JiraTicketDetail(
    JiraTicketRow row,
    Long jiraId,
    List<String> labels,
    LocalDateTime createdTs,
    String inSystemBy,
    LocalDateTime lastChange,
    String lastChangeBy,
    Map<String, String> customFields,
    Map<String, ResolvedFieldValue> effectiveFields,
    List<JiraTicketRow> children,
    String importedFrom,
    LocalDateTime importedAt,
    String importedBy
) {

  /** The names of the own and the inherited fields together, sorted. */
  public List<String> fieldNames() {
    var names = new TreeSet<>(customFields.keySet());
    names.addAll(effectiveFields.keySet());
    return List.copyOf(names);
  }
}
