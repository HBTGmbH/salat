package de.hbt.salat.jira.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static de.hbt.salat.jira.domain.JiraApiFlavor.SERVER;

import jakarta.persistence.EntityManager;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import de.hbt.salat.auth.domain.AuthorizedUser;
import de.hbt.salat.jira.OrderTree;
import de.hbt.salat.jira.domain.JiraAuthMethod;
import de.hbt.salat.jira.domain.JiraReplicationConfig;
import de.hbt.salat.jira.persistence.JiraReplicationConfigRepository;
import de.hbt.salat.order.domain.Customerorder;
import de.hbt.salat.secret.domain.Token;
import de.hbt.salat.secret.domain.UsernamePassword;
import de.hbt.salat.secret.service.SecretService;

/**
 * Moving the plain text secrets of the replications into the secret store at start (#1432,
 * ADR-0038 §10), against the database and with the key of the test configuration.
 */
@SpringBootTest
@DisplayNameGeneration(ReplaceUnderscores.class)
class JiraPlaintextSecretMigrationTest {

  private static final String SCOPE = "MIGRATION";

  @MockitoBean
  private AuthorizedUser authorizedUser;

  @Autowired
  private JiraPlaintextSecretMigration migration;

  @Autowired
  private JiraCredentialStore credentialStore;

  @Autowired
  private SecretService secretService;

  @Autowired
  private JiraReplicationConfigRepository configRepository;

  @Autowired
  private EntityManager sharedEntityManager;

  @Autowired
  private PlatformTransactionManager transactionManager;

  private OrderTree orderTree;
  private Customerorder customerorder;

  @BeforeEach
  void setUp() {
    when(authorizedUser.isAuthenticated()).thenReturn(true);
    when(authorizedUser.isManager()).thenReturn(true);
    when(authorizedUser.getLoginSign()).thenReturn("SYSTEM");
    orderTree = new OrderTree(sharedEntityManager, SCOPE);
    customerorder = new TransactionTemplate(transactionManager).execute(status -> orderTree.customerorder(SCOPE));
  }

  @AfterEach
  void tearDown() {
    for (var config : configRepository.findAll()) {
      configRepository.delete(config);
      if (config.getSecretId() != null) {
        secretService.delete(config.getSecretId());
      }
    }
    new TransactionTemplate(transactionManager).executeWithoutResult(status -> orderTree.remove(customerorder));
  }

  @Test
  void a_plain_text_password_moves_into_a_secret_and_leaves_the_columns_empty() {
    var id = plainText("Basic", JiraAuthMethod.BASIC, "jira-user", "api-token");

    migration.moveToSecretStore();

    var config = configRepository.findById(id).orElseThrow();
    assertThat(config.getSecretId()).isNotNull();
    assertThat(config.getLegacyUsername()).isNull();
    assertThat(config.getLegacyPassword()).isNull();
    assertThat(secretService.read(config.getSecretId())).isEqualTo(new UsernamePassword("jira-user", "api-token"));
  }

  @Test
  void a_personal_access_token_becomes_a_token() {
    var id = plainText("Token", JiraAuthMethod.PERSONAL_ACCESS_TOKEN, null, "pat");

    migration.moveToSecretStore();

    var config = configRepository.findById(id).orElseThrow();
    assertThat(secretService.read(config.getSecretId())).isEqualTo(new Token("pat"));
  }

  /** The run signs in exactly as it did before the move. */
  @Test
  void a_moved_replication_signs_in_as_before() {
    var before = JiraCredentials.basic("jira-user", "api-token");
    var id = plainText("Basic", JiraAuthMethod.BASIC, "jira-user", "api-token");

    migration.moveToSecretStore();

    var after = credentialStore.credentialsOf(configRepository.findById(id).orElseThrow());
    assertThat(after).isEqualTo(before);
    assertThat(after.authorizationHeader()).isEqualTo(before.authorizationHeader());
  }

  @Test
  void a_second_start_finds_nothing_left_to_move() {
    var id = plainText("Basic", JiraAuthMethod.BASIC, "jira-user", "api-token");
    migration.moveToSecretStore();
    var secretId = configRepository.findById(id).orElseThrow().getSecretId();

    migration.moveToSecretStore();

    assertThat(configRepository.findIdsWithPlaintextSecret()).isEmpty();
    assertThat(configRepository.findById(id).orElseThrow().getSecretId()).isEqualTo(secretId);
  }

  /** Without a key nothing is moved: the secret stays where it is until a start with a key. */
  @Test
  void without_a_key_nothing_is_moved() {
    var repository = mock(JiraReplicationConfigRepository.class);
    var store = mock(JiraCredentialStore.class);
    var transactions = mock(PlatformTransactionManager.class);
    @SuppressWarnings("unchecked")
    ObjectProvider<AuthorizedUser> users = mock(ObjectProvider.class);
    when(users.getObject()).thenReturn(mock(AuthorizedUser.class));
    when(repository.findIdsWithPlaintextSecret()).thenReturn(List.of(1L));
    when(store.canStore()).thenReturn(false);

    new JiraPlaintextSecretMigration(repository, store, users, transactions).moveToSecretStore();

    verify(store, never()).store(any(), any(), any(), any());
    verifyNoInteractions(transactions);
  }

  private long plainText(String name, JiraAuthMethod method, String username, String password) {
    var config = new JiraReplicationConfig();
    config.setName(name);
    config.setCustomerorder(customerorder);
    config.setBaseUrl("http://jira.example");
    config.setApiFlavor(SERVER);
    config.setAuthMethod(method);
    config.setLegacyUsername(username);
    config.setLegacyPassword(password);
    config.setJql("project = MIG");
    config.setEnabled(true);
    return configRepository.save(config).getId();
  }
}
