package de.hbt.salat.jira.persistence;

import static de.hbt.salat.testutils.CustomerTestUtils.uniqueShortname;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import jakarta.persistence.EntityManager;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Set;
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
import de.hbt.salat.customer.domain.Customer;
import de.hbt.salat.jira.domain.JiraApiFlavor;
import de.hbt.salat.jira.domain.JiraAuthMethod;
import de.hbt.salat.jira.domain.JiraOAuthConnection;
import de.hbt.salat.jira.domain.JiraReplicationConfig;
import de.hbt.salat.order.domain.Customerorder;
import de.hbt.salat.order.domain.OrderType;

/**
 * The connected Atlassian account of a replication as JSON in {@code oauth_connection} (#1417): it
 * comes back as it was written, time and scopes included, and a replication without one stores
 * {@code NULL}.
 */
@DataJpaTest
@Import(AuthorizedUserAuditorAware.class)
@DisplayNameGeneration(ReplaceUnderscores.class)
class JiraReplicationConfigOAuthConnectionTest {

  @Autowired
  private JiraReplicationConfigRepository repository;

  @Autowired
  private EntityManager entityManager;

  @MockitoBean
  private AuthorizedUser authorizedUser;

  private Customerorder order;

  @BeforeEach
  void setUp() {
    when(authorizedUser.isAuthenticated()).thenReturn(true);
    when(authorizedUser.getLoginSign()).thenReturn("test");
    var customer = new Customer();
    customer.setName("Testkunde");
    customer.setShortname(uniqueShortname("TK"));
    customer.setAddress("Teststraße 1");
    entityManager.persist(customer);
    order = new Customerorder();
    order.setCustomer(customer);
    order.setSign("OAUTH");
    order.setDescription("OAUTH");
    order.setFromDate(LocalDate.of(2026, 1, 1));
    order.setOrderType(OrderType.STANDARD);
    order.setDebithours(Duration.ZERO);
    entityManager.persist(order);
  }

  @Test
  void the_connection_is_read_back_as_it_was_written() {
    var connection = new JiraOAuthConnection("account-1", "Person A", "cloud-1", "https://example.atlassian.net",
        Set.of("read:jira-work", "offline_access"), "mgr", LocalDateTime.of(2026, 10, 7, 9, 30, 15));
    var id = repository.save(config(connection)).getId();
    entityManager.flush();
    entityManager.clear();

    assertThat(repository.findById(id).orElseThrow().getOauthConnection()).isEqualTo(connection);
  }

  @Test
  void a_replication_without_oauth_stores_no_connection() {
    var id = repository.save(config(null)).getId();
    entityManager.flush();
    entityManager.clear();

    assertThat(repository.findById(id).orElseThrow().getOauthConnection()).isNull();
  }

  private JiraReplicationConfig config(JiraOAuthConnection connection) {
    var config = new JiraReplicationConfig();
    config.setCustomerorder(order);
    config.setName("Alpha");
    config.setBaseUrl("https://example.atlassian.net");
    config.setApiFlavor(JiraApiFlavor.CLOUD);
    config.setAuthMethod(JiraAuthMethod.OAUTH);
    config.setJql("project = ALPHA");
    config.setOauthConnection(connection);
    return config;
  }
}
