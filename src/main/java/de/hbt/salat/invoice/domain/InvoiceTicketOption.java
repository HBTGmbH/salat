package de.hbt.salat.invoice.domain;

import java.time.Duration;

/**
 * One entry of the ticket selection (#1390, #1393): a ticket that was booked on in the invoice, or
 * one above such a ticket in the hierarchy. The entries come as a tree in document order, the
 * {@code level} being the indentation.
 *
 * @param key        the reference as it is stored, or the key as JIRA spells it for a ticket only
 *                   reached as a parent
 * @param summary    the title, {@code null} when no ticket in {@code jira_ticket} matches
 * @param issueType  Epic, Story …, {@code null} when unknown
 * @param projectKey the part of the key before the hyphen, {@code null} for free text
 * @param level      how deep in the tree, {@code 0} at the top
 * @param booked     the duration booked on it — a booking with several references counts with its
 *                   share — or {@code null} when nobody booked on it directly
 * @param selected   whether it gets a group of its own
 */
public record InvoiceTicketOption(String key, String summary, String issueType, String projectKey, int level,
                                  Duration booked, boolean selected) {

  /** Free text such as {@code Wartung}: a reference without the shape of a ticket key. */
  public boolean isFreeText() {
    return projectKey == null;
  }

  /** A key that no ticket in {@code jira_ticket} matches. */
  public boolean isUnknown() {
    return projectKey != null && summary == null;
  }
}
