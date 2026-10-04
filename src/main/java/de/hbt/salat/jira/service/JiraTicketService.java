package de.hbt.salat.jira.service;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import de.hbt.salat.auth.domain.Authorized;
import de.hbt.salat.jira.domain.JiraTicket;
import de.hbt.salat.jira.domain.JiraTicketInfo;
import de.hbt.salat.jira.persistence.JiraTicketRepository;

/**
 * Reads the replicated tickets for whoever wants to filter by them (#1092).
 *
 * <p>Answers in {@link JiraTicketInfo}: the entity stays in this module, the caller gets a copy of plain values
 * (→ ADR-0021). Authorization is not this module's business — which tickets somebody may see follows from the orders
 * they may see, and that is decided where the bookings are read. What this service is told to look up, it looks up.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Authorized
public class JiraTicketService {

  private final JiraTicketRepository jiraTicketRepository;

  /**
   * Every ticket of these scopes: the order-wide ones of these customer orders and those of these suborders (#1323).
   * The same key may come back more than once — one per scope it was replicated under — and de-duplicating is the
   * caller's job, as it is for the suggestions.
   */
  public List<JiraTicketInfo> getTickets(Collection<Long> customerorderIds, Collection<Long> suborderIds) {
    return ticketsOf(customerorderIds, suborderIds).stream().map(JiraTicketService::toInfo).toList();
  }

  /**
   * The given keys plus everything below them, level by level until nothing new turns up.
   *
   * <p>A key that was never replicated has no children and comes back alone — the reference in a booking is free text
   * and may name a ticket this application has never seen. Comparison ignores case for the same reason.
   */
  public Set<String> expandWithDescendants(Collection<String> keys, Collection<Long> customerorderIds,
                                          Collection<Long> suborderIds) {
    var result = new LinkedHashSet<String>();
    keys.stream().map(JiraTicketService::normalise).filter(key -> !key.isEmpty()).forEach(result::add);
    if (result.isEmpty()) return result;

    // The tree is walked in memory over the tickets of these scopes rather than with one statement per level: the
    // scopes are the orders the filter names, so the set is small — and the comparison of the keys stays ours instead
    // of the database's, which is what makes "ignoring case" independent of the collation.
    var tickets = ticketsOf(customerorderIds, suborderIds);
    boolean grown = true;
    while (grown) {
      grown = false;
      for (var ticket : tickets) {
        if (ticket.getParentKey() == null) continue;
        if (result.contains(normalise(ticket.getParentKey())) && result.add(normalise(ticket.getKey()))) {
          grown = true;
        }
      }
    }
    return result;
  }

  private List<JiraTicket> ticketsOf(Collection<Long> customerorderIds, Collection<Long> suborderIds) {
    if (customerorderIds.isEmpty() && suborderIds.isEmpty()) return List.of();
    return jiraTicketRepository.findInScopes(customerorderIds, suborderIds);
  }

  private static JiraTicketInfo toInfo(JiraTicket ticket) {
    return new JiraTicketInfo(ticket.getKey(), ticket.getSummary(), ticket.getIssueType(), ticket.getParentKey());
  }

  private static String normalise(String key) {
    return key == null ? "" : key.trim().toUpperCase(Locale.ROOT);
  }
}
