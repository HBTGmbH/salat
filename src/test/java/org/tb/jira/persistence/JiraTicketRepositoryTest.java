package org.tb.jira.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

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
import org.tb.auth.domain.AuthorizedUser;
import org.tb.auth.persistence.AuthorizedUserAuditorAware;
import org.tb.jira.domain.JiraTicket;

/**
 * The ticket search behind the suggestions of the booking form (#982): scoped to the branch that is
 * being booked on (#1025), matching key and title alike, most recently updated first. And the
 * removal of the tickets a complete replication run no longer saw (#1167), bound to one scope.
 */
@DataJpaTest
@Import(AuthorizedUserAuditorAware.class)
@DisplayNameGeneration(ReplaceUnderscores.class)
class JiraTicketRepositoryTest {

  /** Order-wide, one branch, and the sibling branch that shares the suborder sign of the first. */
  private static final String WHOLE_ORDER = "ALPHA";
  private static final String BRANCH_A = "ALPHA/A/01";
  private static final String BRANCH_B = "ALPHA/B/01";

  @Autowired
  private JiraTicketRepository jiraTicketRepository;

  @MockitoBean
  private AuthorizedUser authorizedUser;

  @BeforeEach
  void setUp() {
    when(authorizedUser.isAuthenticated()).thenReturn(true);
    when(authorizedUser.getLoginSign()).thenReturn("test");

    save(WHOLE_ORDER, 1L, "ALPHA-1", "Login schlägt fehl", LocalDateTime.parse("2026-01-01T10:00"));
    save(BRANCH_A, 2L, "ALPHA-2", "Bericht exportieren", LocalDateTime.parse("2026-03-01T10:00"));
    save(BRANCH_B, 3L, "ALPHA-3", "Login aus dem Nachbarast", LocalDateTime.parse("2026-04-01T10:00"));
    save("BETA", 4L, "BETA-1", "Login schlägt fehl", LocalDateTime.parse("2026-05-01T10:00"));
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
    assertThat(searchBranchA("Login")).extracting(JiraTicket::getScopeSign)
        .doesNotContain("BETA");
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
  void a_complete_run_removes_the_tickets_of_its_scope_it_did_not_see() {
    save(BRANCH_A, 5L, "ALPHA-5", "Aus der JQL gefallen", LocalDateTime.parse("2026-02-01T10:00"));

    int removed = jiraTicketRepository.deleteByScopeSignAndJiraIdNotIn(BRANCH_A, List.of(2L));

    assertThat(removed).isEqualTo(1);
    assertThat(jiraTicketRepository.findByScopeSign(BRANCH_A)).extracting(JiraTicket::getKey)
        .containsExactly("ALPHA-2");
  }

  @Test
  void the_removal_leaves_other_scopes_alone_even_with_the_same_key_and_id() {
    // two JIRA instances can hand out the same key and the same id, a sign tells them apart
    save(BRANCH_B, 2L, "ALPHA-2", "Gleiche Nummer im Nachbarast", LocalDateTime.parse("2026-02-01T10:00"));

    jiraTicketRepository.deleteByScopeSignAndJiraIdNotIn(BRANCH_A, List.of(99L));

    assertThat(jiraTicketRepository.findByScopeSign(BRANCH_A)).isEmpty();
    assertThat(jiraTicketRepository.findByScopeSign(BRANCH_B)).extracting(JiraTicket::getKey)
        .containsExactlyInAnyOrder("ALPHA-3", "ALPHA-2");
    assertThat(jiraTicketRepository.findByScopeSign(WHOLE_ORDER)).hasSize(1);
  }

  @Test
  void a_complete_run_that_saw_nothing_removes_the_whole_scope_and_only_that() {
    int removed = jiraTicketRepository.deleteByScopeSignAndJiraIdNotIn(BRANCH_A, List.of());

    assertThat(removed).isEqualTo(1);
    assertThat(jiraTicketRepository.findByScopeSign(BRANCH_A)).isEmpty();
    assertThat(jiraTicketRepository.findAll()).hasSize(3);
  }

  /** The scopes of ALPHA/A/01: the order itself, its parent suborder, and the suborder. */
  private List<JiraTicket> searchBranchA(String term) {
    return jiraTicketRepository.search(List.of(WHOLE_ORDER, "ALPHA/A", BRANCH_A), term,
        PageRequest.of(0, 20));
  }

  private void save(String scopeSign, long jiraId, String key, String summary, LocalDateTime updated) {
    var ticket = new JiraTicket();
    ticket.setScopeSign(scopeSign);
    ticket.setJiraId(jiraId);
    ticket.setKey(key);
    ticket.setSummary(summary);
    ticket.setUpdatedTs(updated);
    jiraTicketRepository.save(ticket);
  }

}
