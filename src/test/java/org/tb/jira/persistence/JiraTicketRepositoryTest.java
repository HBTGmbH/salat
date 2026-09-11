package org.tb.jira.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.tb.auth.domain.AuthorizedUser;
import org.tb.auth.persistence.AuthorizedUserAuditorAware;
import org.tb.jira.domain.JiraTicket;

/**
 * The ticket search behind the suggestions of the booking form (#982): scoped to one customer order,
 * matching key and title alike, most recently updated first.
 */
@DataJpaTest
@Import(AuthorizedUserAuditorAware.class)
@DisplayNameGeneration(ReplaceUnderscores.class)
class JiraTicketRepositoryTest {

  private static final String ORDER_SIGN = "ALPHA";

  @Autowired
  private JiraTicketRepository jiraTicketRepository;

  @MockitoBean
  private AuthorizedUser authorizedUser;

  @BeforeEach
  void setUp() {
    when(authorizedUser.isAuthenticated()).thenReturn(true);
    when(authorizedUser.getLoginSign()).thenReturn("test");

    save(ORDER_SIGN, 1L, "ALPHA-1", "Login schlägt fehl", LocalDateTime.parse("2026-01-01T10:00"));
    save(ORDER_SIGN, 2L, "ALPHA-2", "Bericht exportieren", LocalDateTime.parse("2026-03-01T10:00"));
    save("BETA", 3L, "BETA-1", "Login schlägt fehl", LocalDateTime.parse("2026-05-01T10:00"));
  }

  @Test
  void the_number_is_matched() {
    assertThat(search("ALPHA-2")).extracting(JiraTicket::getKey).containsExactly("ALPHA-2");
  }

  @Test
  void the_title_is_matched_too() {
    // whoever books remembers what the ticket was about rather than its number
    assertThat(search("login")).extracting(JiraTicket::getKey).containsExactly("ALPHA-1");
  }

  @Test
  void tickets_of_another_order_are_not_offered() {
    assertThat(search("Login")).extracting(JiraTicket::getCustomerorderSign)
        .containsOnly(ORDER_SIGN);
  }

  @Test
  void the_most_recently_updated_ticket_comes_first() {
    assertThat(search("")).extracting(JiraTicket::getKey).containsExactly("ALPHA-2", "ALPHA-1");
  }

  private java.util.List<JiraTicket> search(String term) {
    return jiraTicketRepository.search(ORDER_SIGN, term, PageRequest.of(0, 20));
  }

  private void save(String orderSign, long jiraId, String key, String summary, LocalDateTime updated) {
    var ticket = new JiraTicket();
    ticket.setCustomerorderSign(orderSign);
    ticket.setJiraId(jiraId);
    ticket.setKey(key);
    ticket.setSummary(summary);
    ticket.setUpdatedTs(updated);
    jiraTicketRepository.save(ticket);
  }

}
