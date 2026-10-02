package de.hbt.salat.jira.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static de.hbt.salat.jira.domain.JiraApiFlavor.SERVER;

import jakarta.persistence.EntityManagerFactory;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.orm.jpa.EntityManagerHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import de.hbt.salat.auth.domain.AuthorizedUser;
import de.hbt.salat.jira.domain.JiraReplicationConfig;
import de.hbt.salat.jira.domain.JiraReplicationRunOutcome;
import de.hbt.salat.jira.domain.JiraTicket;
import de.hbt.salat.jira.persistence.JiraReplicationConfigRepository;
import de.hbt.salat.jira.persistence.JiraTicketRepository;

/**
 * A replication started by hand (#1282) is started from inside the HTTP request, and with Open
 * Session in View the request holds an EntityManager for its whole duration. The scheduled run knows
 * no such EntityManager. These tests bind one the way the view interceptor does and run the
 * replication against the real database — see {@link JiraReplicationLauncher} for what went wrong
 * while the run still used it.
 */
@SpringBootTest
class JiraReplicationManualRunTest {

  private static final String SCOPE = "MANUAL_RUN";

  @MockitoBean
  private JiraSearchClients searchClients;

  @MockitoBean
  private AuthorizedUser authorizedUser;

  @MockitoSpyBean
  private JiraWorklogSyncService worklogSyncService;

  private final JiraSearchClient searchClient = mock(JiraSearchClient.class);

  @Autowired
  private JiraReplicationConfigService configService;

  @Autowired
  private JiraReplicationConfigRepository configRepo;

  @Autowired
  private JiraTicketRepository ticketRepo;

  @Autowired
  private EntityManagerFactory entityManagerFactory;

  private long configId;

  @BeforeEach
  void setUp() {
    when(authorizedUser.isAuthenticated()).thenReturn(true);
    when(authorizedUser.getLoginSign()).thenReturn("mgr");
    when(authorizedUser.isManager()).thenReturn(true);
    when(searchClients.forFlavor(SERVER)).thenReturn(searchClient);

    var config = new JiraReplicationConfig();
    config.setName("Manueller Lauf");
    config.setScopeSign(SCOPE);
    config.setBaseUrl("http://jira.example");
    config.setApiFlavor(SERVER);
    config.setUsername("user");
    config.setPassword("secret");
    config.setJql("project = MAN");
    config.setEnabled(true);
    configId = configRepo.save(config).getId();
  }

  @AfterEach
  void tearDown() {
    ticketRepo.deleteAll(ticketRepo.findByScopeSign(SCOPE));
    configRepo.deleteById(configId);
  }

  @Test
  void a_ticket_written_and_re_parented_in_the_same_run_is_stored() {
    when(searchClient.search(any())).thenReturn(List.of(
        issue("1", "MAN-1", null, "2026-09-01T10:00"),
        issue("3", "MAN-3", "MAN-1", "2026-09-01T10:00")).iterator());
    assertThat(runInRequest().success()).isTrue();

    // MAN-3 moves from MAN-1 to the new MAN-2: written by the upsert, re-parented right after
    when(searchClient.search(any())).thenReturn(List.of(
        issue("2", "MAN-2", null, "2026-09-02T10:00"),
        issue("3", "MAN-3", "MAN-2", "2026-09-02T11:00")).iterator());
    var outcome = runInRequest();

    assertThat(outcome.success()).as(outcome.message()).isTrue();
    assertThat(ticket("MAN-3").getTopLevelKey()).isEqualTo("MAN-2");
    assertThat(configRepo.findById(configId).orElseThrow().getLastMaxUpdated())
        .isEqualTo(LocalDateTime.parse("2026-09-02T11:00"));
    verify(worklogSyncService).sync(argThat(config -> config.getId().equals(configId)
        && LocalDateTime.parse("2026-09-02T11:00").equals(config.getLastMaxUpdated())));
  }

  /** What the view interceptor does around every request: one EntityManager, bound to the thread. */
  private JiraReplicationRunOutcome runInRequest() {
    var entityManager = entityManagerFactory.createEntityManager();
    TransactionSynchronizationManager.bindResource(entityManagerFactory, new EntityManagerHolder(entityManager));
    try {
      return configService.runNow(configId);
    } finally {
      TransactionSynchronizationManager.unbindResource(entityManagerFactory);
      entityManager.close();
    }
  }

  private JiraTicket ticket(String key) {
    return ticketRepo.findByScopeSign(SCOPE).stream()
        .filter(ticket -> ticket.getKey().equals(key))
        .findFirst().orElseThrow();
  }

  private static JiraIssue issue(String id, String key, String parentKey, String updated) {
    var fields = new HashMap<String, Object>(Map.of(
        "summary", "Ticket " + key,
        "updated", updated,
        "created", "2026-08-01T09:00",
        "issuetype", Map.of("name", "Task")));
    if (parentKey != null) fields.put("parent", Map.of("key", parentKey));
    var issue = new JiraIssue();
    issue.setId(id);
    issue.setKey(key);
    issue.setFields(fields);
    return issue;
  }
}
