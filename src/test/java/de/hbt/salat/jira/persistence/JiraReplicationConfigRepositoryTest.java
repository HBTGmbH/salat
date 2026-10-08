package de.hbt.salat.jira.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static de.hbt.salat.jira.domain.JiraApiFlavor.SERVER;

import jakarta.persistence.EntityManager;
import java.util.List;
import org.hibernate.Hibernate;
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
import de.hbt.salat.jira.domain.JiraReplicationConfig;
import de.hbt.salat.order.domain.Customerorder;
import de.hbt.salat.order.domain.Suborder;

/**
 * The replications found by their scope (#1322): the derived queries name the id of a reference
 * (#1368), and the path {@code customerorder.id} has to resolve to the foreign key column.
 */
@DataJpaTest
@Import(AuthorizedUserAuditorAware.class)
@DisplayNameGeneration(ReplaceUnderscores.class)
class JiraReplicationConfigRepositoryTest {

  @Autowired
  private JiraReplicationConfigRepository configRepository;

  @Autowired
  private EntityManager entityManager;

  @MockitoBean
  private AuthorizedUser authorizedUser;

  private Customerorder alpha;
  private Suborder a;
  private Suborder a01;

  @BeforeEach
  void setUp() {
    when(authorizedUser.isAuthenticated()).thenReturn(true);
    when(authorizedUser.getLoginSign()).thenReturn("test");

    var tree = new OrderTree(entityManager);
    alpha = tree.customerorder("ALPHA");
    var beta = tree.customerorder("BETA");
    a = tree.suborder(alpha, null, "A");
    a01 = tree.suborder(alpha, a, "01");
    save("Ganzer Auftrag", alpha, null);
    save("Unterauftrag", alpha, a01);
    save("Anderer Auftrag", beta, null);
    entityManager.flush();
    entityManager.clear();
  }

  @Test
  void the_replications_of_an_order_are_counted_order_wide_and_on_its_suborders() {
    assertThat(configRepository.countByCustomerorderId(alpha.getId())).isEqualTo(2);
  }

  @Test
  void the_replications_of_a_branch_are_found_and_counted_by_suborder() {
    assertThat(configRepository.findBySuborderIdIn(List.of(a.getId(), a01.getId())))
        .extracting(JiraReplicationConfig::getName).containsExactly("Unterauftrag");
    assertThat(configRepository.countBySuborderIdIn(List.of(a.getId()))).isZero();
  }

  /**
   * The replications that cover a scope (#1386): the order-wide one of the order and those of the
   * suborders on the path down to it — not one of a suborder below, and not one of another order.
   */
  @Test
  void the_replications_covering_a_scope_are_those_on_its_path() {
    assertThat(configRepository.findCovering(alpha.getId(), List.of()))
        .extracting(JiraReplicationConfig::getName).containsExactly("Ganzer Auftrag");
    assertThat(configRepository.findCovering(alpha.getId(), List.of(a.getId(), a01.getId())))
        .extracting(JiraReplicationConfig::getName).containsExactly("Ganzer Auftrag", "Unterauftrag");
    assertThat(configRepository.findCovering(alpha.getId(), List.of(a.getId())))
        .extracting(JiraReplicationConfig::getName).containsExactly("Ganzer Auftrag");
  }

  /** The ids come off the references without a query per row (#1368, ADR-0036 rule 5). */
  @Test
  void reading_the_ids_of_the_scope_loads_neither_order_nor_suborder() {
    assertThat(configRepository.findBySuborderIdIn(List.of(a01.getId()))).singleElement()
        .satisfies(config -> {
          assertThat(config.getCustomerorderId()).isEqualTo(alpha.getId());
          assertThat(config.getSuborderId()).isEqualTo(a01.getId());
          assertThat(Hibernate.isInitialized(config.getCustomerorder())).isFalse();
          assertThat(Hibernate.isInitialized(config.getSuborder())).isFalse();
        });
  }

  /** The scope is the reference: a rename of order and suborder leaves it where it was (#1372). */
  @Test
  void a_renamed_order_and_suborder_keep_their_replications() {
    entityManager.find(Customerorder.class, alpha.getId()).setSign("ALPHA-NEU");
    entityManager.find(Suborder.class, a01.getId()).setSign("02");
    entityManager.flush();
    entityManager.clear();

    assertThat(configRepository.findBySuborderIdIn(List.of(a01.getId()))).singleElement()
        .satisfies(config -> {
          assertThat(config.getCustomerorderId()).isEqualTo(alpha.getId());
          assertThat(config.getCustomerorder().getSign()).isEqualTo("ALPHA-NEU");
          assertThat(config.getSuborder().getSign()).isEqualTo("02");
        });
  }

  private void save(String name, Customerorder customerorder, Suborder suborder) {
    var config = new JiraReplicationConfig();
    config.setName(name);
    config.setCustomerorder(customerorder);
    config.setSuborder(suborder);
    config.setBaseUrl("http://jira.example");
    config.setApiFlavor(SERVER);
    config.setJql("project = RUN");
    config.setEnabled(true);
    configRepository.save(config);
  }

}
