package org.tb.jira.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.tb.auth.domain.Authorized;
import org.tb.jira.domain.JiraTicket;
import org.tb.jira.domain.JiraTicketSuggestion;
import org.tb.jira.persistence.JiraTicketRepository;
import org.tb.order.domain.Suborder;
import org.tb.order.service.SuborderService;

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
    var suborder = suborderService.getSuborderById(suborderId);
    if (suborder == null) {
      return List.of();
    }
    String term = searchTerm == null ? "" : searchTerm.trim();
    var tickets = jiraTicketRepository
        .search(scopesOf(suborder), term, PageRequest.of(0, MAX_SUGGESTIONS));
    return deduplicated(tickets);
  }

  /**
   * Every scope the branch of this suborder can carry tickets under: the customer order itself for
   * an order-wide replication, and the fully qualified sign of every suborder on the path up to it,
   * the one being booked on included. The order within the list carries no meaning — it becomes an
   * {@code in} clause, and what the suggestions are sorted by is the update timestamp.
   */
  private static List<String> scopesOf(Suborder suborder) {
    var scopes = new ArrayList<String>();
    scopes.add(suborder.getCustomerorder().getSign());
    suborder.withParents().forEach(level -> scopes.add(level.getCompleteOrderSign()));
    return scopes;
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
