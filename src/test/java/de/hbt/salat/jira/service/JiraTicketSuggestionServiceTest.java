package de.hbt.salat.jira.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import de.hbt.salat.jira.domain.JiraTicket;
import de.hbt.salat.jira.persistence.JiraTicketRepository;
import de.hbt.salat.order.domain.SuborderLocation;
import de.hbt.salat.order.service.SuborderService;

/**
 * The ticket suggestions offered while booking (#982), scoped to the branch of the suborder being
 * booked on (#1025) — by the ids of order and suborders (#1323).
 */
@ExtendWith(MockitoExtension.class)
class JiraTicketSuggestionServiceTest {

  private static final long ORDER_ID = 1L;
  private static final long SUBORDER_ID = 4711L;
  private static final long A = 10L;
  private static final long B = 20L;

  @InjectMocks
  private JiraTicketSuggestionService classUnderTest;

  @Mock
  private JiraTicketRepository jiraTicketRepository;

  @Mock
  private SuborderService suborderService;

  @Test
  void a_suggestion_carries_the_key_and_the_title() {
    givenBranch(SUBORDER_ID);
    when(jiraTicketRepository.search(anyLong(), any(), eq("log"), anyInt()))
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
    when(suborderService.getSuborderLocationsByIds(List.of(SUBORDER_ID))).thenReturn(Map.of());

    assertThat(classUnderTest.search(SUBORDER_ID, "log")).isEmpty();

    verifyNoInteractions(jiraTicketRepository);
  }

  @Test
  void the_whole_branch_is_searched_from_the_order_down_to_the_suborder() {
    // a replication may sit on any level of the path — the order-wide one, an ancestor, or the
    // suborder itself — and all of them are the branch that is being booked on
    givenBranch(A, SUBORDER_ID);
    when(jiraTicketRepository.search(anyLong(), any(), any(), anyInt())).thenReturn(List.of());

    classUnderTest.search(SUBORDER_ID, "log");

    assertThat(capturedSuborders()).containsExactlyInAnyOrder(A, SUBORDER_ID);
  }

  @Test
  void a_sibling_branch_with_the_same_suborder_sign_is_not_searched() {
    // ALPHA/A/01 and ALPHA/B/01 may both exist — what identifies a scope is the id, not the sign
    givenBranch(B, SUBORDER_ID);
    when(jiraTicketRepository.search(anyLong(), any(), any(), anyInt())).thenReturn(List.of());

    classUnderTest.search(SUBORDER_ID, "");

    assertThat(capturedSuborders()).doesNotContain(A).contains(B, SUBORDER_ID);
  }

  @Test
  void a_ticket_replicated_in_two_scopes_of_the_branch_is_offered_once() {
    givenBranch(SUBORDER_ID);
    when(jiraTicketRepository.search(anyLong(), any(), any(), anyInt())).thenReturn(List.of(
        ticket("PROJ-1", "Aus dem Unterauftrag"),
        ticket("PROJ-1", "Aus dem ganzen Auftrag"),
        ticket("PROJ-2", "Ein anderes")));

    assertThat(classUnderTest.search(SUBORDER_ID, ""))
        .extracting(suggestion -> suggestion.key())
        .containsExactly("PROJ-1", "PROJ-2");
  }

  @Test
  void the_first_occurrence_wins_so_the_most_recently_updated_title_is_shown() {
    givenBranch(SUBORDER_ID);
    when(jiraTicketRepository.search(anyLong(), any(), any(), anyInt())).thenReturn(List.of(
        ticket("PROJ-1", "Zuletzt aktualisiert"),
        ticket("PROJ-1", "Aelter")));

    assertThat(classUnderTest.search(SUBORDER_ID, "")).singleElement()
        .satisfies(suggestion -> assertThat(suggestion.summary()).isEqualTo("Zuletzt aktualisiert"));
  }

  @Test
  void an_empty_search_term_offers_the_tickets_of_the_branch() {
    // opening the dropdown without typing is a legitimate way to browse the branch's tickets
    givenBranch(SUBORDER_ID);
    when(jiraTicketRepository.search(anyLong(), any(), eq(""), anyInt()))
        .thenReturn(List.of(ticket("PROJ-1", "Erstes Ticket")));

    assertThat(classUnderTest.search(SUBORDER_ID, null)).hasSize(1);
  }

  @Test
  void the_list_stays_short_enough_to_be_scanned() {
    // the limit covers the branch as a whole, not each of its scopes
    givenBranch(SUBORDER_ID);
    when(jiraTicketRepository.search(anyLong(), any(), any(), anyInt())).thenReturn(List.of());

    classUnderTest.search(SUBORDER_ID, "log");

    verify(jiraTicketRepository).search(anyLong(), any(), eq("log"), eq(JiraTicketSuggestionService.MAX_SUGGESTIONS));
  }

  /** The suborders searched; the order-wide scope is always the order of the branch. */
  @SuppressWarnings("unchecked")
  private List<Long> capturedSuborders() {
    var suborders = ArgumentCaptor.forClass(java.util.Collection.class);
    verify(jiraTicketRepository).search(eq(ORDER_ID), suborders.capture(), any(), anyInt());
    return List.copyOf(suborders.getValue());
  }

  /** A branch of the order, as the ids from the top level down to the suborder booked on. */
  private void givenBranch(Long... path) {
    var location = new SuborderLocation(SUBORDER_ID, ORDER_ID, List.of(path), "ALPHA/01");
    when(suborderService.getSuborderLocationsByIds(List.of(SUBORDER_ID)))
        .thenReturn(Map.of(SUBORDER_ID, location));
  }

  private static JiraTicket ticket(String key, String summary) {
    var ticket = new JiraTicket();
    ticket.setKey(key);
    ticket.setSummary(summary);
    return ticket;
  }

}
