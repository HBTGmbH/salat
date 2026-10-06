package de.hbt.salat.jira.domain;

import lombok.Getter;
import de.hbt.salat.common.domain.AuditedEntity_;

/**
 * The columns the ticket page sorts by (#1386), with the attribute of {@code JiraTicket} behind each —
 * named by the metamodel, so that a renamed attribute fails the build rather than the query.
 */
@Getter
public enum JiraTicketSort {

  KEY(JiraTicket_.KEY),
  TYPE(JiraTicket_.ISSUE_TYPE),
  UPDATED(JiraTicket_.UPDATED_TS),
  IN_SYSTEM(AuditedEntity_.CREATED);

  private final String attribute;

  JiraTicketSort(String attribute) {
    this.attribute = attribute;
  }

  /** {@code KEY} or {@code -KEY} for descending, as the column headers send it; anything else is the key. */
  public static JiraTicketSort parse(String value) {
    var name = value == null ? "" : value.startsWith("-") ? value.substring(1) : value;
    for (var sort : values()) {
      if (sort.name().equals(name)) return sort;
    }
    return KEY;
  }

  public static boolean descending(String value) {
    return value != null && value.startsWith("-") && parse(value).name().equals(value.substring(1));
  }
}
