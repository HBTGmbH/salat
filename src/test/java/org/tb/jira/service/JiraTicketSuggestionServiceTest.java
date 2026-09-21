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
import org.tb.order.domain.Customerorder;
import org.tb.order.domain.Suborder;
import org.tb.order.service.SuborderService;

/**
 * The ticket suggestions offered while booking (#982), scoped to the branch of the suborder being
 * booked on (#1025).
 */
@ExtendWith(MockitoExtension.class)
class JiraTicketSuggestionServiceTest {

  private static final long SUBORDER_ID = 4711L;

  @InjectMocks
  private JiraTicketSuggestionService classUnderTest;

  @Mock
  private JiraTicketRepository jiraTicketRepository;

  @Mock
  private SuborderService suborderService;

  @Test
  void a_suggestion_carries_the_key_and_the_title() {
    givenBranch("ALPHA", "01");
    when(jiraTicketRepository.search(any(), eq("log"), any()))
        .thenReturn(List.of(ticket("PROJ-123", "Login schlägt fehl")));

    var suggestions = classUnderTest.search(SUBORDER_ID, "log");

    assertThat(suggestions).singleElement().satisfies(suggestion -> {
      assertThat(suggestion.key()).isEqualTo("PROJ-123");
      assertThat(suggestion.summary()).isEqualTo("Login schlägt fehl");
    });
  }

  @Test
  void without_a_suborder_there_is_nothing_to_suggest() {
    assertThat(classUnderTest.search(null, "log")).isEmpty();

    verifyNoInteractions(jiraTicketRepository, suborderService);
  }

  @Test
  void a_suborder_that_is_gone_suggests_nothing_rather_than_failing() {
    when(suborderService.getSuborderById(SUBORDER_ID)).thenReturn(null);

    assertThat(classUnderTest.search(SUBORDER_ID, "log")).isEmpty();

    verifyNoInteractions(jiraTicketRepository);
  }

  @Test
  void the_whole_branch_is_searched_from_the_order_down_to_the_suborder() {
    // a replication may sit on any level of the path — the order-wide one, an ancestor, or the
    // suborder itself — and all of them are the branch that is being booked on
    givenBranch("ALPHA", "A", "01");
    when(jiraTicketRepository.search(any(), any(), any())).thenReturn(List.of());

    classUnderTest.search(SUBORDER_ID, "log");

    assertThat(capturedScopes())
        .containsExactlyInAnyOrder("ALPHA", "ALPHA/A", "ALPHA/A/01");
  }

  @Test
  void a_sibling_branch_with_the_same_suborder_sign_is_not_searched() {
    // ALPHA/A/01 and ALPHA/B/01 may both exist — what identifies a scope is the path, not the sign
    givenBranch("ALPHA", "B", "01");
    when(jiraTicketRepository.search(any(), any(), any())).thenReturn(List.of());

    classUnderTest.search(SUBORDER_ID, "");

    assertThat(capturedScopes()).doesNotContain("ALPHA/A/01").contains("ALPHA/B/01");
  }

  @Test
  void a_ticket_replicated_in_two_scopes_of_the_branch_is_offered_once() {
    givenBranch("ALPHA", "01");
    when(jiraTicketRepository.search(any(), any(), any())).thenReturn(List.of(
        ticket("PROJ-1", "Aus dem Unterauftrag"),
        ticket("PROJ-1", "Aus dem ganzen Auftrag"),
        ticket("PROJ-2", "Ein anderes")));

    assertThat(classUnderTest.search(SUBORDER_ID, ""))
        .extracting(suggestion -> suggestion.key())
        .containsExactly("PROJ-1", "PROJ-2");
  }

  @Test
  void the_first_occurrence_wins_so_the_most_recently_updated_title_is_shown() {
    givenBranch("ALPHA", "01");
    when(jiraTicketRepository.search(any(), any(), any())).thenReturn(List.of(
        ticket("PROJ-1", "Zuletzt aktualisiert"),
        ticket("PROJ-1", "Aelter")));

    assertThat(classUnderTest.search(SUBORDER_ID, "")).singleElement()
        .satisfies(suggestion -> assertThat(suggestion.summary()).isEqualTo("Zuletzt aktualisiert"));
  }

  @Test
  void an_empty_search_term_offers_the_tickets_of_the_branch() {
    // opening the dropdown without typing is a legitimate way to browse the branch's tickets
    givenBranch("ALPHA", "01");
    when(jiraTicketRepository.search(any(), eq(""), any()))
        .thenReturn(List.of(ticket("PROJ-1", "Erstes Ticket")));

    assertThat(classUnderTest.search(SUBORDER_ID, null)).hasSize(1);
  }

  @Test
  void the_list_stays_short_enough_to_be_scanned() {
    // the limit covers the branch as a whole, not each of its scopes
    givenBranch("ALPHA", "01");
    when(jiraTicketRepository.search(any(), any(), any())).thenReturn(List.of());

    classUnderTest.search(SUBORDER_ID, "log");

    var pageable = ArgumentCaptor.forClass(Pageable.class);
    verify(jiraTicketRepository).search(any(), eq("log"), pageable.capture());
    assertThat(pageable.getValue().getPageSize())
        .isEqualTo(JiraTicketSuggestionService.MAX_SUGGESTIONS);
  }

  @SuppressWarnings("unchecked")
  private List<String> capturedScopes() {
    var scopes = ArgumentCaptor.forClass(java.util.Collection.class);
    verify(jiraTicketRepository).search(scopes.capture(), any(), any());
    return List.copyOf(scopes.getValue());
  }

  /**
   * A branch {@code orderSign/sign…}, deepest suborder last — the one the booking is made on.
   */
  private void givenBranch(String orderSign, String... signs) {
    var customerorder = new Customerorder();
    customerorder.setSign(orderSign);
    Suborder deepest = null;
    for (var sign : signs) {
      var suborder = new Suborder();
      suborder.setCustomerorder(customerorder);
      suborder.setSign(sign);
      suborder.setParentorder(deepest);
      deepest = suborder;
    }
    when(suborderService.getSuborderById(SUBORDER_ID)).thenReturn(deepest);
  }

  private static JiraTicket ticket(String key, String summary) {
    var ticket = new JiraTicket();
    ticket.setKey(key);
    ticket.setSummary(summary);
    return ticket;
  }

}
