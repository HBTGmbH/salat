package de.hbt.salat.jira.domain;

import lombok.Getter;

/** The columns the ticket page sorts by (#1386), with the attribute of {@code JiraTicket} behind each. */
@Getter
public enum JiraTicketSort {

  KEY("key"),
  TYPE("issueType"),
  UPDATED("updatedTs"),
  IN_SYSTEM("created");

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
