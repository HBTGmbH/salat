package de.hbt.salat.jira.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
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
import org.springframework.data.domain.Sort;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import de.hbt.salat.auth.domain.AuthorizedUser;
import de.hbt.salat.auth.persistence.AuthorizedUserAuditorAware;
import de.hbt.salat.jira.OrderTree;
import de.hbt.salat.jira.domain.JiraImportMappingEntry;
import de.hbt.salat.jira.domain.JiraImportTarget;
import de.hbt.salat.jira.domain.JiraReplicationConfig;
import de.hbt.salat.jira.domain.JiraTicket;
import de.hbt.salat.jira.domain.JiraTicketImport;
import de.hbt.salat.order.domain.Customerorder;
import de.hbt.salat.order.domain.Suborder;

/**
 * The ticket search behind the suggestions of the booking form (#982): scoped to the branch that is
 * being booked on (#1025), matching key and title alike, most recently updated first. Every scope is
 * the pair of order and suborder (#1323, as references since #1368), a {@code null} suborder the
 * whole order.
 */
@DataJpaTest
@Import(AuthorizedUserAuditorAware.class)
@DisplayNameGeneration(ReplaceUnderscores.class)
class JiraTicketRepositoryTest {

  /** Order-wide, one branch, and the sibling branch that shares the suborder sign of the first. */
  private Customerorder alphaOrder;
  private Customerorder betaOrder;
  private Suborder a01;
  private long ALPHA;
  private long BETA;
  private long A;
  private long A_01;
  private long B_01;

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

    var tree = new OrderTree(entityManager);
    alphaOrder = tree.customerorder("ALPHA");
    betaOrder = tree.customerorder("BETA");
    var a = tree.suborder(alphaOrder, null, "A");
    a01 = tree.suborder(alphaOrder, a, "01");
    var b = tree.suborder(alphaOrder, null, "B");
    var b01 = tree.suborder(alphaOrder, b, "01");
    ALPHA = alphaOrder.getId();
    BETA = betaOrder.getId();
    A = a.getId();
    A_01 = a01.getId();
    B_01 = b01.getId();

    save(alphaOrder, null, 1L, "ALPHA-1", "Login schlägt fehl", LocalDateTime.parse("2026-01-01T10:00"));
    save(alphaOrder, a01, 2L, "ALPHA-2", "Bericht exportieren", LocalDateTime.parse("2026-03-01T10:00"));
    save(alphaOrder, b01, 3L, "ALPHA-3", "Login aus dem Nachbarast", LocalDateTime.parse("2026-04-01T10:00"));
    save(betaOrder, null, 4L, "BETA-1", "Login schlägt fehl", LocalDateTime.parse("2026-05-01T10:00"));
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

  /**
   * The suggestions of a branch are one list across its scopes (#1402): the order-wide tickets, those
   * of the parent suborder and those of the suborder booked on, most recently updated first, a tie
   * broken by the key, and the limit applied to the list as a whole. Newer tickets of the sibling
   * branch and of another order stay out.
   */
  @Test
  void the_suggestions_of_a_branch_are_one_list_across_its_scopes_and_the_limit_applies_to_it() {
    var a = entityManager.getReference(Suborder.class, A);
    var b01 = entityManager.getReference(Suborder.class, B_01);
    for (int n = 1; n <= 12; n++) {
      save(alphaOrder, null, null, "ORD-" + n, "auftragsweit", LocalDateTime.of(2026, 6, 2 * n, 10, 0));
      save(alphaOrder, a01, null, "SUB-" + n, "am Unterauftrag", LocalDateTime.of(2026, 6, 2 * n + 1, 10, 0));
    }
    save(alphaOrder, a, null, "PAR-1", "am übergeordneten Unterauftrag", LocalDateTime.parse("2026-06-26T10:00"));
    save(alphaOrder, a01, null, "TIE-B", "gleicher Zeitpunkt", LocalDateTime.parse("2026-06-28T10:00"));
    save(alphaOrder, null, null, "TIE-A", "gleicher Zeitpunkt", LocalDateTime.parse("2026-06-28T10:00"));
    save(alphaOrder, b01, null, "SIB-1", "Nachbarast", LocalDateTime.parse("2026-07-01T10:00"));
    save(betaOrder, null, null, "OTH-1", "anderer Auftrag", LocalDateTime.parse("2026-07-01T10:00"));

    assertThat(searchBranchA("")).extracting(JiraTicket::getKey).containsExactly(
        "TIE-A", "TIE-B", "PAR-1",
        "SUB-12", "ORD-12", "SUB-11", "ORD-11", "SUB-10", "ORD-10", "SUB-9", "ORD-9",
        "SUB-8", "ORD-8", "SUB-7", "ORD-7", "SUB-6", "ORD-6", "SUB-5", "ORD-5", "SUB-4");
  }

  /** A ticket without an update timestamp comes after every dated one, in each of the scopes (#1402). */
  @Test
  void a_ticket_without_an_update_timestamp_is_suggested_last() {
    save(alphaOrder, a01, null, "UND-2", "Zeitstempel fehlt", null);
    save(alphaOrder, null, null, "UND-1", "Zeitstempel fehlt", null);
    save(alphaOrder, null, null, "DAT-1", "Zeitstempel gesetzt", LocalDateTime.parse("2025-01-01T10:00"));
    save(alphaOrder, a01, null, "DAT-2", "Zeitstempel gesetzt", LocalDateTime.parse("2025-02-01T10:00"));

    assertThat(searchBranchA("zeitstempel")).extracting(JiraTicket::getKey)
        .containsExactly("DAT-2", "DAT-1", "UND-1", "UND-2");
  }

  @Test
  void the_whole_order_is_a_scope_of_its_own_and_not_every_ticket_of_the_order() {
    // a null suborder is compared as such, not left out of the comparison
    assertThat(jiraTicketRepository.findInScope(ALPHA, null)).extracting(JiraTicket::getKey)
        .containsExactly("ALPHA-1");
    assertThat(jiraTicketRepository.findInScope(ALPHA, A_01)).extracting(JiraTicket::getKey)
        .containsExactly("ALPHA-2");
    assertThat(jiraTicketRepository.findUnmaintainedInScopeByJiraId(ALPHA, null, 2L)).isEmpty();
    assertThat(jiraTicketRepository.findInScopeByKey(ALPHA, A_01, "ALPHA-1")).isEmpty();
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

  /**
   * The scope is the reference, not a sign (#1368, #1372): renaming order and suborder leaves every
   * ticket where it was.
   */
  @Test
  void a_renamed_order_and_suborder_keep_their_tickets() {
    alphaOrder.setSign("ALPHA-NEU");
    a01.setSign("02");
    entityManager.flush();
    entityManager.clear();

    assertThat(jiraTicketRepository.findInScope(ALPHA, null)).extracting(JiraTicket::getKey)
        .containsExactly("ALPHA-1");
    assertThat(jiraTicketRepository.findInScope(ALPHA, A_01)).singleElement()
        .satisfies(ticket -> {
          assertThat(ticket.getKey()).isEqualTo("ALPHA-2");
          assertThat(ticket.getCustomerorder().getSign()).isEqualTo("ALPHA-NEU");
          assertThat(ticket.getSuborder().getSign()).isEqualTo("02");
        });
  }

  @Test
  void a_suborder_moved_to_another_order_takes_its_tickets_along() {
    var moved = jiraTicketRepository.moveBranchToCustomerorder(List.of(A, A_01), betaOrder);
    entityManager.clear();

    assertThat(moved).isEqualTo(1);
    assertThat(jiraTicketRepository.findInScope(BETA, A_01)).extracting(JiraTicket::getKey)
        .containsExactly("ALPHA-2");
    assertThat(jiraTicketRepository.findInScope(ALPHA, A_01)).isEmpty();
    assertThat(jiraTicketRepository.findInScope(ALPHA, null)).extracting(JiraTicket::getKey)
        .containsExactly("ALPHA-1");
  }

  @Test
  void a_branch_already_on_its_order_is_not_rewritten() {
    assertThat(jiraTicketRepository.moveBranchToCustomerorder(List.of(A, A_01), alphaOrder)).isZero();
  }

  @Test
  void the_tickets_of_a_deleted_scope_go_with_it() {
    jiraTicketRepository.deleteBySuborderIdIn(List.of(A_01));
    jiraTicketRepository.deleteByCustomerorderId(BETA);
    entityManager.clear();

    assertThat(jiraTicketRepository.findAll()).extracting(JiraTicket::getKey)
        .containsExactlyInAnyOrder("ALPHA-1", "ALPHA-3");
  }

  /**
   * Tickets maintained by hand have no JIRA id (#1386) — several in a scope — and a replication set
   * up later finds them by their key within the scope.
   */
  @Test
  void a_ticket_maintained_by_hand_is_found_by_its_key() {
    save(alphaOrder, null, null, "HAND-1", "von Hand", null);
    save(alphaOrder, null, null, "HAND-2", "auch von Hand", null);
    entityManager.flush();

    assertThat(jiraTicketRepository.findInScopeByKey(ALPHA, null, "HAND-1"))
        .hasValueSatisfying(ticket -> assertThat(ticket.getSummary()).isEqualTo("von Hand"));
    assertThat(jiraTicketRepository.findInScopeByKey(ALPHA, A_01, "HAND-1")).isEmpty();
    assertThat(jiraTicketRepository.findInScopeByKey(ALPHA, null, "ALPHA-1")).isPresent();
  }

  /** The ticket page lists the whole order, or only the branch the filter narrows to (#1386). */
  @Test
  void the_ticket_page_lists_an_order_or_a_branch() {
    assertThat(page(true, List.of(-1L), true, List.of(""), null, true, List.of(""))).extracting(JiraTicket::getKey)
        .containsExactly("ALPHA-1", "ALPHA-2", "ALPHA-3");
    assertThat(page(false, List.of(A, A_01), true, List.of(""), null, true, List.of(""))).extracting(JiraTicket::getKey)
        .containsExactly("ALPHA-2");
  }

  /** Key ignoring case, a part of the title ignoring case, and the type; counted per type (#1386). */
  @Test
  void the_ticket_page_filters_by_key_title_and_type_and_counts_per_type() {
    jiraTicketRepository.findAll().forEach(ticket -> {
      ticket.setIssueType(ticket.getKey().equals("ALPHA-2") ? "Bug" : "Story");
      jiraTicketRepository.save(ticket);
    });
    entityManager.flush();

    assertThat(page(true, List.of(-1L), false, List.of("ALPHA-3"), null, true, List.of("")))
        .extracting(JiraTicket::getKey).containsExactly("ALPHA-3");
    assertThat(page(true, List.of(-1L), true, List.of(""), "LOGIN", true, List.of("")))
        .extracting(JiraTicket::getKey).containsExactly("ALPHA-1", "ALPHA-3");
    assertThat(page(true, List.of(-1L), true, List.of(""), null, false, List.of("Bug")))
        .extracting(JiraTicket::getKey).containsExactly("ALPHA-2");
    assertThat(jiraTicketRepository.countForTicketPage(false, List.of(ALPHA), true, List.of(-1L), true, List.of(""), null, true,
        List.of(""))).extracting(row -> row[0], row -> row[1], row -> row[2])
        .containsExactlyInAnyOrder(tuple("Story", 2L, 0L), tuple("Bug", 1L, 0L));
    assertThat(jiraTicketRepository.findIssueTypes(false, List.of(ALPHA))).containsExactly("Bug", "Story");
  }

  /** Without an order, as the page opens: every order, or the ones the user may see (#1386). */
  @Test
  void the_ticket_page_lists_every_order_or_the_given_ones() {
    assertThat(jiraTicketRepository.findForTicketPage(true, List.of(-1L), true, List.of(-1L), true, List.of(""), null,
        true, List.of(""), PageRequest.of(0, 100, Sort.by("key")))).extracting(JiraTicket::getKey)
        .containsExactly("ALPHA-1", "ALPHA-2", "ALPHA-3", "BETA-1");
    assertThat(jiraTicketRepository.findForTicketPage(false, List.of(BETA), true, List.of(-1L), true, List.of(""), null,
        true, List.of(""), PageRequest.of(0, 100, Sort.by("key")))).extracting(JiraTicket::getKey)
        .containsExactly("BETA-1");
    assertThat(jiraTicketRepository.findParentLinks(true, List.of(-1L))).hasSize(4);
  }

  @Test
  void the_ticket_page_honours_its_limit() {
    assertThat(jiraTicketRepository.findForTicketPage(false, List.of(ALPHA), true, List.of(-1L), true, List.of(""), null, true,
        List.of(""), PageRequest.of(0, 2))).hasSize(2);
  }

  private List<JiraTicket> page(boolean allScopes, List<Long> suborderIds, boolean allKeys, List<String> keys,
                                String title, boolean allTypes, List<String> types) {
    return jiraTicketRepository.findForTicketPage(false, List.of(ALPHA), allScopes, suborderIds, allKeys, keys, title, allTypes, types,
        PageRequest.of(0, 100, Sort.by("key")));
  }

  /** The ticket page offers only orders and suborders that have tickets (#1386). */
  @Test
  void the_orders_and_suborders_with_tickets_are_found() {
    assertThat(jiraTicketRepository.findCustomerorderIdsWithTickets()).containsExactlyInAnyOrder(ALPHA, BETA);
    assertThat(jiraTicketRepository.findSuborderIdsWithTickets(ALPHA)).containsExactlyInAnyOrder(A_01, B_01);
    assertThat(jiraTicketRepository.findSuborderIdsWithTickets(BETA)).isEmpty();
    assertThat(jiraTicketRepository.existsInCustomerorder(BETA)).isTrue();
    jiraTicketRepository.deleteByCustomerorderId(BETA);
    assertThat(jiraTicketRepository.existsInCustomerorder(BETA)).isFalse();
  }

  @Autowired
  private JiraTicketImportRepository importRepository;

  /** The latest import of exactly the scope, with its column reading stored as JSON (#1386). */
  @Test
  void the_latest_import_of_a_scope_is_found_with_its_column_reading() {
    saveImport(alphaOrder, null, "alt.csv");
    saveImport(alphaOrder, null, "neu.csv");
    saveImport(alphaOrder, a01, "ast.csv");
    entityManager.flush();
    entityManager.clear();

    assertThat(importRepository.findLatestInScope(ALPHA, null, PageRequest.of(0, 1))).singleElement()
        .satisfies(latest -> {
          assertThat(latest.getFileName()).isEqualTo("neu.csv");
          assertThat(latest.getColumnMapping()).containsExactly(
              new JiraImportMappingEntry("Team", JiraImportTarget.ADDITIONAL, "team", true));
          assertThat(latest.inheritedFields()).containsExactly("team");
        });
    assertThat(importRepository.findLatestInScope(ALPHA, A_01, PageRequest.of(0, 1)))
        .extracting(JiraTicketImport::getFileName).containsExactly("ast.csv");
    assertThat(importRepository.findLatestInScope(BETA, null, PageRequest.of(0, 1))).isEmpty();
  }

  /**
   * The imports of a scope go with it (#1386): they refer to order and suborder by a foreign key, and
   * left behind they would block the deletion of the order.
   */
  @Test
  void an_order_with_an_import_can_be_deleted_once_its_scope_data_is_gone() {
    saveImport(betaOrder, null, "beta.csv");
    saveImport(alphaOrder, a01, "ast.csv");
    entityManager.flush();

    jiraTicketRepository.deleteByCustomerorderId(BETA);
    assertThat(importRepository.deleteByCustomerorderId(BETA)).isEqualTo(1);
    assertThat(importRepository.deleteBySuborderIdIn(List.of(A_01))).isEqualTo(1);
    entityManager.clear();
    entityManager.remove(entityManager.find(Customerorder.class, BETA));
    entityManager.flush();

    assertThat(importRepository.findAll()).isEmpty();
  }

  @Test
  void a_suborder_moved_to_another_order_takes_its_imports_along() {
    saveImport(alphaOrder, a01, "ast.csv");
    saveImport(alphaOrder, null, "auftrag.csv");
    entityManager.flush();

    assertThat(importRepository.moveBranchToCustomerorder(List.of(A, A_01), betaOrder)).isEqualTo(1);
    entityManager.clear();

    assertThat(importRepository.findLatestInScope(BETA, A_01, PageRequest.of(0, 1)))
        .extracting(JiraTicketImport::getFileName).containsExactly("ast.csv");
    assertThat(importRepository.findLatestInScope(ALPHA, null, PageRequest.of(0, 1)))
        .extracting(JiraTicketImport::getFileName).containsExactly("auftrag.csv");
  }

  /**
   * A JIRA id is unique per replication, not per scope (#1386): two replications of one scope may read
   * two JIRA instances. A ticket nobody maintains is found by its id within the scope only.
   */
  @Test
  void a_jira_id_is_found_per_replication_or_among_the_tickets_nobody_maintains() {
    var alpha = replication("Alpha");
    var beta = replication("Beta");
    jiraTicketRepository.findAll().forEach(ticket -> ticket.setReplication(
        ticket.getKey().equals("ALPHA-1") ? alpha : ticket.getKey().equals("ALPHA-2") ? beta : null));
    entityManager.flush();

    assertThat(jiraTicketRepository.findMaintainedByJiraId(alpha.getId(), 1L)).map(JiraTicket::getKey).hasValue("ALPHA-1");
    assertThat(jiraTicketRepository.findMaintainedByJiraId(beta.getId(), 1L)).isEmpty();
    assertThat(jiraTicketRepository.findUnmaintainedInScopeByJiraId(ALPHA, null, 1L)).isEmpty();
    assertThat(jiraTicketRepository.findUnmaintainedInScopeByJiraId(ALPHA, B_01, 3L)).extracting(JiraTicket::getKey)
        .containsExactly("ALPHA-3");
  }

  /** A replication moved to another scope leaves its tickets to nobody (#1386). */
  @Test
  void the_tickets_of_a_replication_are_released() {
    var replication = replication("Alpha");
    jiraTicketRepository.findAll().forEach(ticket -> ticket.setReplication(replication));
    entityManager.flush();

    assertThat(jiraTicketRepository.releaseFromReplication(replication.getId())).isEqualTo(4);
    entityManager.clear();

    assertThat(jiraTicketRepository.findAll()).allSatisfy(ticket -> assertThat(ticket.isReplicated()).isFalse());
  }

  private JiraReplicationConfig replication(String name) {
    var replication = new JiraReplicationConfig();
    replication.setName(name);
    replication.setCustomerorder(alphaOrder);
    replication.setBaseUrl("https://jira.example.com");
    replication.setUsername("user");
    replication.setPassword("secret");
    replication.setJql("project = ALPHA");
    entityManager.persist(replication);
    return replication;
  }

  private void saveImport(Customerorder customerorder, Suborder suborder, String fileName) {
    var ticketImport = new JiraTicketImport();
    ticketImport.setCustomerorder(customerorder);
    ticketImport.setSuborder(suborder);
    ticketImport.setFileName(fileName);
    ticketImport.setColumnMapping(List.of(new JiraImportMappingEntry("Team", JiraImportTarget.ADDITIONAL, "team", true)));
    importRepository.save(ticketImport);
  }

  /** The scopes of ALPHA/A/01: the order itself, its parent suborder, and the suborder. */
  private List<JiraTicket> searchBranchA(String term) {
    return jiraTicketRepository.search(ALPHA, List.of(A, A_01), term, PageRequest.of(0, 20));
  }

  private void save(Customerorder customerorder, Suborder suborder, Long jiraId, String key, String summary,
                    LocalDateTime updated) {
    var ticket = new JiraTicket();
    ticket.setCustomerorder(customerorder);
    ticket.setSuborder(suborder);
    ticket.setJiraId(jiraId);
    ticket.setKey(key);
    ticket.setSummary(summary);
    ticket.setUpdatedTs(updated);
    jiraTicketRepository.save(ticket);
  }

}
