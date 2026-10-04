package de.hbt.salat.jira.service;

import java.util.LinkedHashMap;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import de.hbt.salat.auth.domain.Authorized;
import de.hbt.salat.jira.domain.JiraTicket;
import de.hbt.salat.jira.domain.JiraTicketSuggestion;
import de.hbt.salat.jira.persistence.JiraTicketRepository;
import de.hbt.salat.order.service.SuborderService;

/**
 * Offers the replicated tickets as suggestions while booking (#982).
 *
 * <p>The scope is derived from the suborder that is being booked on, not taken from the request
 * (#1025): the caller names an id, this looks the branch up. Everything on the path from that
 * suborder up to the customer order contributes — the suborder itself, every parent up to the root,
 * and the order-wide replication. A sibling branch does not, which is the whole point: on a large
 * order the twenty entries a dropdown can show used to fill up with whatever was last touched
 * anywhere in it.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Authorized
public class JiraTicketSuggestionService {

  /** A dropdown is scanned, not paged through — more entries would only make it harder to read. */
  static final int MAX_SUGGESTIONS = 20;

  private final JiraTicketRepository jiraTicketRepository;
  private final SuborderService suborderService;

  public List<JiraTicketSuggestion> search(Long suborderId, String searchTerm) {
    if (suborderId == null) {
      return List.of();
    }
    // SuborderService carries the plain class-level @Authorized: being logged in is enough, which is
    // the same line this endpoint draws. Externals and interns book their time like everyone else
    // and need the suggestions while doing so, so there is deliberately no requireUnrestricted here
    // and none needed there. Reading a suborder by id discloses nothing beyond its sign, and the
    // suborder the booking form offered is one the person may book on anyway.
    var location = suborderService.getSuborderLocationsByIds(List.of(suborderId)).get(suborderId);
    if (location == null) {
      return List.of();
    }
    // Every scope the branch can carry tickets under (#1323): the customer order for an order-wide
    // replication, and every suborder on the path from the top level down to the one being booked
    // on. The order within the path carries no meaning — it becomes an in clause, and what the
    // suggestions are sorted by is the update timestamp.
    String term = searchTerm == null ? "" : searchTerm.trim();
    var tickets = jiraTicketRepository.search(location.customerorderId(), location.path(), term,
        PageRequest.of(0, MAX_SUGGESTIONS));
    return deduplicated(tickets);
  }

  /**
   * A ticket key replicated in two scopes of the same branch is one ticket and is offered once. The
   * rows arrive most recently updated first, so the first occurrence is the one to keep — and a
   * duplicate costs a slot rather than being made up for by a wider query, which would turn the
   * limit into "twenty per scope".
   */
  private static List<JiraTicketSuggestion> deduplicated(List<JiraTicket> tickets) {
    var byKey = new LinkedHashMap<String, JiraTicketSuggestion>();
    tickets.forEach(ticket -> byKey.putIfAbsent(ticket.getKey(), toSuggestion(ticket)));
    return List.copyOf(byKey.values());
  }

  private static JiraTicketSuggestion toSuggestion(JiraTicket ticket) {
    return new JiraTicketSuggestion(ticket.getKey(), ticket.getSummary());
  }

}
