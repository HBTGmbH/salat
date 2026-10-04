package de.hbt.salat.jira.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import de.hbt.salat.auth.domain.AuthorizedUser;
import de.hbt.salat.auth.persistence.AuthorizedUserAuditorAware;
import de.hbt.salat.jira.domain.JiraTicket;

/**
 * The ticket search behind the suggestions of the booking form (#982): scoped to the branch that is
 * being booked on (#1025), matching key and title alike, most recently updated first. Every scope is
 * the pair of order and suborder ids (#1323), a {@code null} suborder the whole order.
 */
@DataJpaTest
@Import(AuthorizedUserAuditorAware.class)
@DisplayNameGeneration(ReplaceUnderscores.class)
class JiraTicketRepositoryTest {

  /** Order-wide, one branch, and the sibling branch that shares the suborder sign of the first. */
  private static final long ALPHA = 1L;
  private static final long BETA = 2L;
  private static final long A = 10L;
  private static final long A_01 = 11L;
  private static final long B_01 = 21L;

  @Autowired
  private JiraTicketRepository jiraTicketRepository;

  @Autowired
  private EntityManager entityManager;

  @MockitoBean
  private AuthorizedUser authorizedUser;

  @BeforeEach
  void setUp() {
    when(authorizedUser.isAuthenticated()).thenReturn(true);
    when(authorizedUser.getLoginSign()).thenReturn("test");

    save(ALPHA, null, "ALPHA", 1L, "ALPHA-1", "Login schlägt fehl", LocalDateTime.parse("2026-01-01T10:00"));
    save(ALPHA, A_01, "ALPHA/A/01", 2L, "ALPHA-2", "Bericht exportieren", LocalDateTime.parse("2026-03-01T10:00"));
    save(ALPHA, B_01, "ALPHA/B/01", 3L, "ALPHA-3", "Login aus dem Nachbarast", LocalDateTime.parse("2026-04-01T10:00"));
    save(BETA, null, "BETA", 4L, "BETA-1", "Login schlägt fehl", LocalDateTime.parse("2026-05-01T10:00"));
  }

  @Test
  void the_number_is_matched() {
    assertThat(searchBranchA("ALPHA-2")).extracting(JiraTicket::getKey).containsExactly("ALPHA-2");
  }

  @Test
  void the_title_is_matched_too() {
    // whoever books remembers what the ticket was about rather than its number
    assertThat(searchBranchA("login")).extracting(JiraTicket::getKey).containsExactly("ALPHA-1");
  }

  @Test
  void tickets_of_another_order_are_not_offered() {
    assertThat(searchBranchA("Login")).extracting(JiraTicket::getCustomerorderId)
        .doesNotContain(BETA);
  }

  @Test
  void the_whole_branch_is_offered_at_once() {
    // the order-wide replication and the one on the suborder alike
    assertThat(searchBranchA("")).extracting(JiraTicket::getKey)
        .containsExactly("ALPHA-2", "ALPHA-1");
  }

  @Test
  void a_sibling_branch_stays_out_even_with_the_same_suborder_sign() {
    assertThat(searchBranchA("Login")).extracting(JiraTicket::getKey)
        .doesNotContain("ALPHA-3");
  }

  @Test
  void the_most_recently_updated_ticket_comes_first() {
    assertThat(searchBranchA("")).extracting(JiraTicket::getKey)
        .containsExactly("ALPHA-2", "ALPHA-1");
  }

  @Test
  void the_whole_order_is_a_scope_of_its_own_and_not_every_ticket_of_the_order() {
    // a null suborder is compared as such, not left out of the comparison
    assertThat(jiraTicketRepository.findInScope(ALPHA, null)).extracting(JiraTicket::getKey)
        .containsExactly("ALPHA-1");
    assertThat(jiraTicketRepository.findInScope(ALPHA, A_01)).extracting(JiraTicket::getKey)
        .containsExactly("ALPHA-2");
    assertThat(jiraTicketRepository.findInScopeByJiraId(ALPHA, null, 2L)).isEmpty();
    assertThat(jiraTicketRepository.findInScopeByKeyIn(ALPHA, A_01, List.of("ALPHA-1", "ALPHA-2")))
        .extracting(JiraTicket::getKey).containsExactly("ALPHA-2");
  }

  @Test
  void several_scopes_are_read_at_once_even_without_a_suborder() {
    // the booking list names orders and suborders; either list may be empty
    assertThat(jiraTicketRepository.findInScopes(List.of(ALPHA), List.of())).extracting(JiraTicket::getKey)
        .containsExactly("ALPHA-1");
    assertThat(jiraTicketRepository.findInScopes(List.of(), List.of(B_01))).extracting(JiraTicket::getKey)
        .containsExactly("ALPHA-3");
    assertThat(jiraTicketRepository.findInScopes(List.of(ALPHA, BETA), List.of(A_01)))
        .extracting(JiraTicket::getKey).containsExactlyInAnyOrder("ALPHA-1", "ALPHA-2", "BETA-1");
  }

  @Test
  void a_renamed_order_renames_the_sign_of_its_order_wide_tickets() {
    jiraTicketRepository.mirrorOrderWide(ALPHA, "ALPHA-NEU");
    entityManager.clear();

    assertThat(jiraTicketRepository.findInScope(ALPHA, null)).extracting(JiraTicket::getScopeSign)
        .containsExactly("ALPHA-NEU");
    assertThat(jiraTicketRepository.findInScope(ALPHA, A_01)).extracting(JiraTicket::getScopeSign)
        .containsExactly("ALPHA/A/01");
  }

  @Test
  void a_suborder_moved_to_another_order_takes_its_tickets_along() {
    jiraTicketRepository.mirrorSuborder(A_01, BETA, "BETA/A/01");
    entityManager.clear();

    assertThat(jiraTicketRepository.findInScope(BETA, A_01)).singleElement()
        .satisfies(ticket -> assertThat(ticket.getScopeSign()).isEqualTo("BETA/A/01"));
    assertThat(jiraTicketRepository.findInScope(ALPHA, A_01)).isEmpty();
  }

  @Test
  void the_suborders_that_carry_tickets_are_found_per_order_and_per_branch() {
    assertThat(jiraTicketRepository.findSuborderIdsOfCustomerorder(ALPHA)).containsExactlyInAnyOrder(A_01, B_01);
    assertThat(jiraTicketRepository.findSuborderIdsIn(List.of(A, A_01))).containsExactly(A_01);
  }

  @Test
  void the_tickets_of_a_deleted_scope_go_with_it() {
    jiraTicketRepository.deleteBySuborderIdIn(List.of(A_01));
    jiraTicketRepository.deleteByCustomerorderId(BETA);
    entityManager.clear();

    assertThat(jiraTicketRepository.findAll()).extracting(JiraTicket::getKey)
        .containsExactlyInAnyOrder("ALPHA-1", "ALPHA-3");
  }

  /** The scopes of ALPHA/A/01: the order itself, its parent suborder, and the suborder. */
  private List<JiraTicket> searchBranchA(String term) {
    return jiraTicketRepository.search(ALPHA, List.of(A, A_01), term, PageRequest.of(0, 20));
  }

  private void save(long customerorderId, Long suborderId, String scopeSign, long jiraId, String key,
                    String summary, LocalDateTime updated) {
    var ticket = new JiraTicket();
    ticket.setCustomerorderId(customerorderId);
    ticket.setSuborderId(suborderId);
    ticket.setScopeSign(scopeSign);
    ticket.setJiraId(jiraId);
    ticket.setKey(key);
    ticket.setSummary(summary);
    ticket.setUpdatedTs(updated);
    jiraTicketRepository.save(ticket);
  }

}
