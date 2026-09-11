package org.tb.jira.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.tb.jira.domain.JiraTicket;
import org.tb.jira.persistence.JiraTicketRepository;

/**
 * The ticket suggestions offered while booking (#982).
 */
@ExtendWith(MockitoExtension.class)
class JiraTicketSuggestionServiceTest {

  private static final String ORDER_SIGN = "ALPHA";

  @InjectMocks
  private JiraTicketSuggestionService classUnderTest;

  @Mock
  private JiraTicketRepository jiraTicketRepository;

  @Test
  void a_suggestion_carries_the_key_and_the_title() {
    when(jiraTicketRepository.search(eq(ORDER_SIGN), eq("log"), any()))
        .thenReturn(List.of(ticket("PROJ-123", "Login schlägt fehl")));

    var suggestions = classUnderTest.search(ORDER_SIGN, "log");

    assertThat(suggestions).singleElement().satisfies(suggestion -> {
      assertThat(suggestion.key()).isEqualTo("PROJ-123");
      assertThat(suggestion.summary()).isEqualTo("Login schlägt fehl");
    });
  }

  @Test
  void without_an_order_there_is_nothing_to_suggest() {
    assertThat(classUnderTest.search(null, "log")).isEmpty();
    assertThat(classUnderTest.search("  ", "log")).isEmpty();

    verifyNoInteractions(jiraTicketRepository);
  }

  @Test
  void an_empty_search_term_offers_the_tickets_of_the_order() {
    // opening the dropdown without typing is a legitimate way to browse the order's tickets
    when(jiraTicketRepository.search(eq(ORDER_SIGN), eq(""), any()))
        .thenReturn(List.of(ticket("PROJ-1", "Erstes Ticket")));

    assertThat(classUnderTest.search(ORDER_SIGN, null)).hasSize(1);
  }

  @Test
  void the_list_stays_short_enough_to_be_scanned() {
    when(jiraTicketRepository.search(any(), any(), any())).thenReturn(List.of());

    classUnderTest.search(ORDER_SIGN, "log");

    var pageable = ArgumentCaptor.forClass(Pageable.class);
    verify(jiraTicketRepository).search(eq(ORDER_SIGN), eq("log"), pageable.capture());
    assertThat(pageable.getValue().getPageSize())
        .isEqualTo(JiraTicketSuggestionService.MAX_SUGGESTIONS);
  }

  private static JiraTicket ticket(String key, String summary) {
    var ticket = new JiraTicket();
    ticket.setKey(key);
    ticket.setSummary(summary);
    return ticket;
  }

}
