package org.tb.jira.service;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.tb.auth.domain.Authorized;
import org.tb.jira.domain.JiraTicket;
import org.tb.jira.domain.JiraTicketSuggestion;
import org.tb.jira.persistence.JiraTicketRepository;

/**
 * Offers the replicated tickets of one customer order as suggestions while booking (#982).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Authorized
public class JiraTicketSuggestionService {

  /** A dropdown is scanned, not paged through — more entries would only make it harder to read. */
  static final int MAX_SUGGESTIONS = 20;

  private final JiraTicketRepository jiraTicketRepository;

  public List<JiraTicketSuggestion> search(String customerorderSign, String searchTerm) {
    if (customerorderSign == null || customerorderSign.isBlank()) {
      return List.of();
    }
    String term = searchTerm == null ? "" : searchTerm.trim();
    return jiraTicketRepository
        .search(customerorderSign, term, PageRequest.of(0, MAX_SUGGESTIONS))
        .stream()
        .map(JiraTicketSuggestionService::toSuggestion)
        .toList();
  }

  private static JiraTicketSuggestion toSuggestion(JiraTicket ticket) {
    return new JiraTicketSuggestion(ticket.getKey(), ticket.getSummary());
  }

}
