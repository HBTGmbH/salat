package de.hbt.salat.jira.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static de.hbt.salat.jira.domain.JiraApiFlavor.SERVER;
import static de.hbt.salat.jira.domain.JiraReplicationRun.Status.RUNNING;
import static de.hbt.salat.jira.domain.JiraReplicationRun.Status.SUCCEEDED;
import static de.hbt.salat.jira.domain.JiraReplicationRun.Trigger.MANUAL;

import jakarta.persistence.EntityManagerFactory;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
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
import de.hbt.salat.common.exception.BusinessRuleException;
import de.hbt.salat.jira.domain.JiraReplicationConfig;
import de.hbt.salat.jira.domain.JiraReplicationRun;
import de.hbt.salat.jira.domain.JiraTicket;
import de.hbt.salat.jira.persistence.JiraReplicationConfigRepository;
import de.hbt.salat.jira.persistence.JiraReplicationRunRepository;
import de.hbt.salat.jira.persistence.JiraTicketRepository;

/**
 * A replication started by hand (#1282) is started from inside the HTTP request, and with Open
 * Session in View the request holds an EntityManager for its whole duration. The scheduled run knows
 * no such EntityManager. These tests bind one the way the view interceptor does and start the
 * replication against the real database — see {@link JiraReplicationLauncher} for what went wrong
 * while the run still used it.
 */
@SpringBootTest
class JiraReplicationManualRunTest {

  private static final String SCOPE = "MANUAL_RUN";
  private static final Duration TIMEOUT = Duration.ofSeconds(20);

  @MockitoBean
  private JiraSearchClients searchClients;

  @MockitoBean
  private AuthorizedUser authorizedUser;

  @MockitoSpyBean
  private JiraWorklogSyncService worklogSyncService;

  /** The order 1L of the config is not in the test database; the sign is all the run reads. */
  @MockitoBean
  private JiraScopes scopes;

  private final JiraSearchClient searchClient = mock(JiraSearchClient.class);

  @Autowired
  private JiraReplicationLauncher launcher;

  @Autowired
  private JiraReplicationConfigRepository configRepo;

  @Autowired
  private JiraTicketRepository ticketRepo;

  @Autowired
  private JiraReplicationRunRepository runRepo;

  @Autowired
  private EntityManagerFactory entityManagerFactory;

  private long configId;

  @BeforeEach
  void setUp() {
    when(authorizedUser.isAuthenticated()).thenReturn(true);
    when(authorizedUser.getLoginSign()).thenReturn("mgr");
    when(authorizedUser.isManager()).thenReturn(true);
    when(searchClients.forFlavor(SERVER)).thenReturn(searchClient);
    when(scopes.signOf(1L, null)).thenReturn(SCOPE);

    var config = new JiraReplicationConfig();
    config.setName("Manueller Lauf");
    config.setCustomerorderId(1L);
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
    runRepo.deleteByReplicationId(configId);
    ticketRepo.deleteAll(ticketRepo.findInScope(1L, null));
    configRepo.deleteById(configId);
  }

  @Test
  void a_ticket_written_and_re_parented_in_the_same_run_is_stored() throws InterruptedException {
    when(searchClient.search(any())).thenReturn(List.of(
        issue("1", "MAN-1", null, "2026-09-01T10:00"),
        issue("3", "MAN-3", "MAN-1", "2026-09-01T10:00")).iterator());
    assertThat(finished(startInRequest()).getStatus()).isEqualTo(SUCCEEDED);

    // MAN-3 moves from MAN-1 to the new MAN-2: written by the upsert, re-parented right after
    when(searchClient.search(any())).thenReturn(List.of(
        issue("2", "MAN-2", null, "2026-09-02T10:00"),
        issue("3", "MAN-3", "MAN-2", "2026-09-02T11:00")).iterator());
    var run = finished(startInRequest());

    assertThat(run.getStatus()).as(run.getMessage()).isEqualTo(SUCCEEDED);
    assertThat(run.getMessage()).isEqualTo("2 Tickets geholt, 2 geschrieben.");
    assertThat(ticket("MAN-3").getTopLevelKey()).isEqualTo("MAN-2");
    assertThat(configRepo.findById(configId).orElseThrow().getLastMaxUpdated())
        .isEqualTo(LocalDateTime.parse("2026-09-02T11:00"));
    verify(worklogSyncService).sync(argThat(config -> config.getId().equals(configId)
        && LocalDateTime.parse("2026-09-02T11:00").equals(config.getLastMaxUpdated())));
  }

  @Test
  void the_request_returns_while_the_run_is_still_going() throws InterruptedException {
    // JIRA answers only once the test lets it — until then the run has to be in the list already
    var release = new CountDownLatch(1);
    when(searchClient.search(any())).thenAnswer(invocation -> {
      release.await();
      return List.of(issue("1", "MAN-1", null, "2026-09-01T10:00")).iterator();
    });

    var run = startInRequest();
    try {
      assertThat(runRepo.findById(run.getId()).orElseThrow().getStatus()).isEqualTo(RUNNING);
      // and the same replication does not start a second time meanwhile
      assertThatThrownBy(this::startInRequest).isInstanceOf(BusinessRuleException.class);
    } finally {
      release.countDown();
    }
    assertThat(finished(run).getStatus()).isEqualTo(SUCCEEDED);
    assertThat(runRepo.findAll()).filteredOn(r -> r.getReplicationId() == configId)
        .singleElement().extracting(JiraReplicationRun::getTriggeredBy).isEqualTo(MANUAL);
  }

  /** What the view interceptor does around every request: one EntityManager, bound to the thread. */
  private JiraReplicationRun startInRequest() {
    var entityManager = entityManagerFactory.createEntityManager();
    TransactionSynchronizationManager.bindResource(entityManagerFactory, new EntityManagerHolder(entityManager));
    try {
      return launcher.startManualRun(configId);
    } finally {
      TransactionSynchronizationManager.unbindResource(entityManagerFactory);
      entityManager.close();
    }
  }

  /** The run's row once it no longer stands on RUNNING — the run goes on in the background. */
  private JiraReplicationRun finished(JiraReplicationRun run) throws InterruptedException {
    var deadline = System.nanoTime() + TIMEOUT.toNanos();
    while (System.nanoTime() < deadline) {
      var current = runRepo.findById(run.getId()).orElseThrow();
      if (current.getStatus() != RUNNING) return current;
      Thread.sleep(20);
    }
    throw new AssertionError("run " + run.getId() + " did not finish within " + TIMEOUT);
  }

  private JiraTicket ticket(String key) {
    return ticketRepo.findInScope(1L, null).stream()
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
