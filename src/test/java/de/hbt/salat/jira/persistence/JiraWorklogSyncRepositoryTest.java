package de.hbt.salat.jira.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import de.hbt.salat.auth.domain.AuthorizedUser;
import de.hbt.salat.auth.persistence.AuthorizedUserAuditorAware;
import de.hbt.salat.jira.OrderTree;
import de.hbt.salat.jira.domain.JiraWorklogSync;
import de.hbt.salat.order.domain.Customerorder;
import de.hbt.salat.order.domain.Suborder;

/**
 * What SALAT has written to JIRA, found by the scope that wrote it (#1323) — order and suborder as
 * references (#1368).
 */
@DataJpaTest
@Import(AuthorizedUserAuditorAware.class)
@DisplayNameGeneration(ReplaceUnderscores.class)
class JiraWorklogSyncRepositoryTest {

  private static final LocalDate DAY = LocalDate.of(2026, 6, 1);

  @Autowired
  private JiraWorklogSyncRepository syncRepository;

  @Autowired
  private EntityManager entityManager;

  @MockitoBean
  private AuthorizedUser authorizedUser;

  private Customerorder alpha;
  private Customerorder beta;
  private Suborder a;
  private Suborder a01;

  @BeforeEach
  void setUp() {
    when(authorizedUser.isAuthenticated()).thenReturn(true);
    when(authorizedUser.getLoginSign()).thenReturn("test");

    var tree = new OrderTree(entityManager);
    alpha = tree.customerorder("ALPHA");
    beta = tree.customerorder("BETA");
    a = tree.suborder(alpha, null, "A");
    a01 = tree.suborder(alpha, a, "01");
    save(alpha, null, "ALPHA-1");
    save(alpha, a01, "ALPHA-2");
    entityManager.flush();
    entityManager.clear();
  }

  @Test
  void the_whole_order_is_a_scope_of_its_own() {
    assertThat(syncRepository.findInScopeFrom(alpha.getId(), null, DAY))
        .extracting(JiraWorklogSync::getIssueKey).containsExactly("ALPHA-1");
    assertThat(syncRepository.findInScopeFrom(alpha.getId(), a01.getId(), DAY))
        .extracting(JiraWorklogSync::getIssueKey).containsExactly("ALPHA-2");
  }

  @Test
  void a_suborder_moved_to_another_order_takes_its_rows_along() {
    var moved = syncRepository.moveBranchToCustomerorder(List.of(a.getId(), a01.getId()), beta);
    entityManager.clear();

    assertThat(moved).isEqualTo(1);
    assertThat(syncRepository.findInScopeFrom(beta.getId(), a01.getId(), DAY))
        .extracting(JiraWorklogSync::getIssueKey).containsExactly("ALPHA-2");
    assertThat(syncRepository.findInScopeFrom(alpha.getId(), null, DAY))
        .extracting(JiraWorklogSync::getIssueKey).containsExactly("ALPHA-1");
  }

  @Test
  void keeps_the_comment_last_written_with_its_umlaut() {
    // #1408: the comment is compared as text on every run, so it has to come back exactly as written.
    var row = syncRepository.findInScopeFrom(alpha.getId(), null, DAY).getFirst();
    row.setComment("Von HBT protokollierte Stunden übertragen:\nabc 4h\nxyz 2h 30m");
    syncRepository.save(row);
    entityManager.flush();
    entityManager.clear();

    assertThat(syncRepository.findInScopeFrom(alpha.getId(), null, DAY))
        .extracting(JiraWorklogSync::getComment)
        .containsExactly("Von HBT protokollierte Stunden übertragen:\nabc 4h\nxyz 2h 30m");
  }

  private void save(Customerorder customerorder, Suborder suborder, String issueKey) {
    var row = new JiraWorklogSync();
    row.setCustomerorder(customerorder);
    row.setSuborder(suborder);
    row.setIssueKey(issueKey);
    row.setWorkDate(DAY);
    row.setWorklogId("1");
    row.setMinutes(60);
    syncRepository.save(row);
  }

}
