package de.hbt.salat.jira.domain;

import java.util.List;
import java.util.Locale;

/**
 * What the ticket page filters by (#1386).
 *
 * @param suborderId {@code null} for every scope of the order, otherwise the branch below it
 * @param keys the keys asked for, upper case; empty for all
 * @param withChildren whether the tickets below the keys come along, as in the booking list
 * @param title a part of the title, compared ignoring case; {@code null} for any
 * @param issueTypes the types asked for; empty for all
 * @param maxResults how many rows are listed at most; counted are all hits
 */
public record JiraTicketListFilter(long customerorderId, Long suborderId, List<String> keys, boolean withChildren,
    String title, List<String> issueTypes, int maxResults) {

  public JiraTicketListFilter {
    keys = keys == null ? List.of()
        : keys.stream().map(key -> key.trim().toUpperCase(Locale.ROOT)).filter(key -> !key.isEmpty()).distinct().toList();
    title = title == null || title.isBlank() ? null : title.trim();
    issueTypes = issueTypes == null ? List.of() : issueTypes.stream().filter(type -> !type.isBlank()).distinct().toList();
  }
}
