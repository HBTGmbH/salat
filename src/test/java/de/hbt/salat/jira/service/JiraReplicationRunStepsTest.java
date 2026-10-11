package de.hbt.salat.jira.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.util.ReflectionUtils.findField;
import static org.springframework.util.ReflectionUtils.makeAccessible;
import static org.springframework.util.ReflectionUtils.setField;
import static de.hbt.salat.jira.OrderTree.customerorderWithId;
import static de.hbt.salat.jira.domain.JiraApiFlavor.SERVER;
import static de.hbt.salat.jira.domain.JiraReplicationRun.Status.FAILED;
import static de.hbt.salat.jira.domain.JiraReplicationRun.Status.INCOMPLETE;
import static de.hbt.salat.jira.domain.JiraReplicationRun.Status.SKIPPED;
import static de.hbt.salat.jira.domain.JiraReplicationRun.Status.SUCCEEDED;
import static de.hbt.salat.jira.domain.JiraReplicationRunStep.Step.READ_TICKETS;
import static de.hbt.salat.jira.domain.JiraReplicationRunStep.Step.WRITE_WORKLOGS;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.MessageSource;
import org.springframework.web.client.RestClientException;
import de.hbt.salat.common.SalatProperties;
import de.hbt.salat.common.domain.AuditedEntity;
import de.hbt.salat.common.exception.BusinessRuleException;
import de.hbt.salat.common.exception.ErrorCode;
import de.hbt.salat.common.test.FixedClock;
import de.hbt.salat.jira.configuration.JiraSwitch;
import de.hbt.salat.jira.domain.JiraReplicationConfig;
import de.hbt.salat.jira.domain.JiraReplicationRunStep;
import de.hbt.salat.jira.domain.JiraTicket;
import de.hbt.salat.jira.persistence.JiraReplicationConfigRepository;
import de.hbt.salat.jira.persistence.JiraTicketImportRepository;
import de.hbt.salat.jira.persistence.JiraTicketRepository;

/**
 * The steps of a replication run (#1325): reading the tickets and writing the worklogs, each with its
 * counters, written once it has ended — and the status of the run that follows from them.
 */
@FixedClock
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayNameGeneration(ReplaceUnderscores.class)
class JiraReplicationRunStepsTest {

  private static final long RUN_ID = 77L;
  private static final long REPLICATION_ID = 1L;
  private static final JiraCredentials CREDENTIALS = JiraCredentials.basic("mockUser", "mockPassword");

  @Mock
  private JiraSearchClients searchClients;

  @Mock
  private JiraSearchClient searchClient;

  @Mock
  private JiraReplicationConfigRepository configRepo;

  @Mock
  private JiraTicketRepository ticketRepo;

  @Mock
  private JiraWorklogSyncService worklogSyncService;

  @Mock
  private JiraReplicationRunService runService;

  @Mock
  private JiraScopes scopes;

  @Mock
  private JiraTicketImportRepository importRepo;

  @Mock
  private JiraCredentialStore credentialStore;

  @Mock
  private MessageSource messageSource;

  /** JIRA switched on, as by default (#1458). */
  @Spy
  private JiraSwitch jiraSwitch = new JiraSwitch(new SalatProperties());

  @InjectMocks
  private JiraReplicationService replicationService;

  private JiraReplicationConfig config;

  @BeforeEach
  void setUp() {
    config = new JiraReplicationConfig();
    var idField = findField(AuditedEntity.class, "id");
    makeAccessible(idField);
    setField(idField, config, REPLICATION_ID);
    config.setName("Alpha");
    config.setBaseUrl("http://jira.example");
    config.setApiFlavor(SERVER);
    config.setJql("project = ALPHA");
    config.setCustomerorder(customerorderWithId(7L));
    when(configRepo.findById(REPLICATION_ID)).thenReturn(Optional.of(config));
    when(credentialStore.credentialsOf(config)).thenReturn(CREDENTIALS);
    when(searchClients.forFlavor(SERVER)).thenReturn(searchClient);
    when(scopes.signOf(7L, null)).thenReturn("ALPHA");
  }

  @Test
  void reading_the_tickets_is_a_step_with_its_counters() {
    when(searchClient.search(any())).thenReturn(List.of(issue("1001", "ALPHA-1"), issue("1002", "ALPHA-2")).iterator());

    replicationService.continueRun(RUN_ID, REPLICATION_ID);

    var step = theOnlyStep();
    assertThat(step.getStep()).isEqualTo(READ_TICKETS);
    assertThat(step.getStatus()).isEqualTo(SUCCEEDED);
    assertThat(step.getStartedAt()).isNotNull();
    assertThat(step.getFinishedAt()).isNotNull();
    assertThat(step.getTicketsFetched()).isEqualTo(2);
    assertThat(step.getTicketsWritten()).isEqualTo(2);
    assertThat(step.getTicketsFailed()).isZero();
    assertThat(step.getTicketsSkipped()).isZero();
    assertThat(step.getTicketsRemoved()).isZero();
    assertThat(step.getParentChainsResolved()).isZero();
    assertThat(step.getMessage()).isNull();
    assertThat(step.getWorklogsCreated()).isNull();
    verify(runService).finishRun(RUN_ID, SUCCEEDED, "2 Tickets geholt, 2 geschrieben.");
  }

  @Test
  void an_issue_that_could_not_be_stored_makes_the_step_and_the_run_incomplete() {
    var unreadable = issue("not-a-number", "ALPHA-2");
    when(searchClient.search(any())).thenReturn(List.of(issue("1001", "ALPHA-1"), unreadable).iterator());

    replicationService.continueRun(RUN_ID, REPLICATION_ID);

    var step = theOnlyStep();
    assertThat(step.getStatus()).isEqualTo(INCOMPLETE);
    assertThat(step.getTicketsFailed()).isEqualTo(1);
    assertThat(step.getMessage()).isEqualTo("1 nicht verarbeitet — der Wasserstand rückt nicht über sie hinaus.");
    verify(runService).finishRun(eq(RUN_ID), eq(INCOMPLETE), any());
  }

  @Test
  void a_run_without_watermark_counts_the_tickets_it_removes() {
    when(searchClient.search(any())).thenReturn(List.of(issue("1001", "ALPHA-1")).iterator());
    var gone = new JiraTicket();
    gone.setKey("ALPHA-9");
    gone.setJiraId(1009L);
    gone.setReplication(config);
    when(ticketRepo.findInScope(7L, null)).thenReturn(new ArrayList<>(List.of(gone)));

    replicationService.continueRun(RUN_ID, REPLICATION_ID);

    assertThat(theOnlyStep().getTicketsRemoved()).isEqualTo(1);
  }

  @Test
  void a_step_that_breaks_off_is_recorded_as_failed_without_the_password_and_the_worklogs_get_no_entry() {
    config.setWorklogSyncEnabled(true);
    when(searchClient.search(any())).thenThrow(new RestClientException("401 for mockUser:mockPassword"));

    assertThatThrownBy(() -> replicationService.continueRun(RUN_ID, REPLICATION_ID))
        .isInstanceOf(RestClientException.class);

    var step = theOnlyStep();
    assertThat(step.getStep()).isEqualTo(READ_TICKETS);
    assertThat(step.getStatus()).isEqualTo(FAILED);
    assertThat(step.getMessage()).isEqualTo("401 for mockUser:***");
    assertThat(step.getTicketsFetched()).isNull();
    verify(worklogSyncService, never()).sync(any(), any());
    verify(runService).finishRun(RUN_ID, FAILED, "Abgebrochen: 401 for mockUser:***");
  }

  @Test
  void a_run_that_breaks_off_before_the_first_step_records_none() {
    when(credentialStore.credentialsOf(config)).thenThrow(new BusinessRuleException(ErrorCode.SC_SECRET_UNREADABLE));

    assertThatThrownBy(() -> replicationService.continueRun(RUN_ID, REPLICATION_ID))
        .isInstanceOf(BusinessRuleException.class);

    verify(runService, never()).recordStep(anyLong(), any());
    verify(runService).finishRun(eq(RUN_ID), eq(FAILED), any());
  }

  @Test
  void the_worklog_sync_switched_off_leaves_no_step() {
    config.setWorklogSyncEnabled(false);
    when(searchClient.search(any())).thenReturn(List.<JiraIssue>of().iterator());
    when(worklogSyncService.sync(config, CREDENTIALS)).thenReturn(Optional.empty());

    replicationService.continueRun(RUN_ID, REPLICATION_ID);

    assertThat(theOnlyStep().getStep()).isEqualTo(READ_TICKETS);
  }

  @Test
  void writing_the_worklogs_is_a_step_with_the_counters_and_the_period_of_the_sync() {
    config.setWorklogSyncEnabled(true);
    when(searchClient.search(any())).thenReturn(List.<JiraIssue>of().iterator());
    when(worklogSyncService.sync(config, CREDENTIALS)).thenReturn(Optional.of(new JiraWorklogSyncResult(
        LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 25), null, 3, 2, 1, 7, 4, 0, List.of())));

    replicationService.continueRun(RUN_ID, REPLICATION_ID);

    var steps = steps();
    assertThat(steps).extracting(JiraReplicationRunStep::getStep).containsExactly(READ_TICKETS, WRITE_WORKLOGS);
    var worklogs = steps.get(1);
    assertThat(worklogs.getStatus()).isEqualTo(SUCCEEDED);
    assertThat(worklogs.getWorklogsFrom()).isEqualTo(LocalDate.of(2026, 6, 1));
    assertThat(worklogs.getWorklogsUntil()).isEqualTo(LocalDate.of(2026, 6, 25));
    assertThat(worklogs.getWorklogsCreated()).isEqualTo(3);
    assertThat(worklogs.getWorklogsUpdated()).isEqualTo(2);
    assertThat(worklogs.getWorklogsDeleted()).isEqualTo(1);
    assertThat(worklogs.getWorklogsUnchanged()).isEqualTo(7);
    assertThat(worklogs.getWorklogsKept()).isEqualTo(4);
    assertThat(worklogs.getWorklogsFailed()).isZero();
    assertThat(worklogs.getTicketsFetched()).isNull();
    assertThat(worklogs.getMessage()).isNull();
    verify(runService).finishRun(RUN_ID, SUCCEEDED, "0 Tickets geholt, 0 geschrieben.");
  }

  @Test
  void single_worklogs_that_failed_make_the_step_and_the_run_incomplete() {
    config.setWorklogSyncEnabled(true);
    when(searchClient.search(any())).thenReturn(List.<JiraIssue>of().iterator());
    when(worklogSyncService.sync(config, CREDENTIALS)).thenReturn(Optional.of(new JiraWorklogSyncResult(
        LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 25), null, 3, 0, 0, 0, 0, 2,
        List.of("ALPHA-1 am 10.06.2026", "ALPHA-2 am 11.06.2026"))));

    replicationService.continueRun(RUN_ID, REPLICATION_ID);

    var worklogs = steps().get(1);
    assertThat(worklogs.getStatus()).isEqualTo(INCOMPLETE);
    assertThat(worklogs.getWorklogsFailed()).isEqualTo(2);
    assertThat(worklogs.getMessage()).isEqualTo("2 Worklogs konnten nicht abgeglichen werden, der nächste Lauf "
        + "versucht es erneut: ALPHA-1 am 10.06.2026, ALPHA-2 am 11.06.2026.");
    verify(runService).finishRun(RUN_ID, INCOMPLETE, "0 Tickets geholt, 0 geschrieben. 2 Worklogs nicht abgeglichen.");
  }

  @Test
  void a_sync_that_breaks_off_is_a_failed_step_and_fails_the_run() {
    config.setWorklogSyncEnabled(true);
    when(searchClient.search(any())).thenReturn(List.<JiraIssue>of().iterator());
    when(worklogSyncService.sync(config, CREDENTIALS)).thenThrow(new RestClientException("500 for mockPassword"));

    replicationService.continueRun(RUN_ID, REPLICATION_ID);

    var worklogs = steps().get(1);
    assertThat(worklogs.getStep()).isEqualTo(WRITE_WORKLOGS);
    assertThat(worklogs.getStatus()).isEqualTo(FAILED);
    assertThat(worklogs.getMessage()).isEqualTo("500 for ***");
    assertThat(worklogs.getWorklogsCreated()).isNull();
    verify(runService).finishRun(RUN_ID, FAILED,
        "0 Tickets geholt, 0 geschrieben. Worklog-Abgleich fehlgeschlagen: 500 for ***");
  }

  @Test
  void a_sync_without_a_write_grant_is_a_failed_step_with_the_reason_in_words() {
    config.setWorklogSyncEnabled(true);
    when(searchClient.search(any())).thenReturn(List.<JiraIssue>of().iterator());
    when(worklogSyncService.sync(config, CREDENTIALS))
        .thenThrow(new BusinessRuleException(ErrorCode.JI_REPLICATION_OAUTH_WRITE_NOT_GRANTED));
    when(messageSource.getMessage(eq(ErrorCode.JI_REPLICATION_OAUTH_WRITE_NOT_GRANTED.messageKey()), any(), any(), any()))
        .thenReturn("Der Verbindung fehlt das Recht, Worklogs zu schreiben.");

    replicationService.continueRun(RUN_ID, REPLICATION_ID);

    var worklogs = steps().get(1);
    assertThat(worklogs.getStatus()).isEqualTo(FAILED);
    assertThat(worklogs.getMessage()).isEqualTo("Der Verbindung fehlt das Recht, Worklogs zu schreiben.");
  }

  @Test
  void a_sync_with_nothing_to_work_on_is_a_skipped_step_with_its_reason() {
    config.setWorklogSyncEnabled(true);
    when(searchClient.search(any())).thenReturn(List.<JiraIssue>of().iterator());
    when(worklogSyncService.sync(config, CREDENTIALS)).thenReturn(Optional.of(JiraWorklogSyncResult.skipped(
        "Der Abgleich beginnt am 01.07.2026 und hat bis dahin nichts zu tun.")));

    replicationService.continueRun(RUN_ID, REPLICATION_ID);

    var worklogs = steps().get(1);
    assertThat(worklogs.getStatus()).isEqualTo(SKIPPED);
    assertThat(worklogs.getMessage()).isEqualTo("Der Abgleich beginnt am 01.07.2026 und hat bis dahin nichts zu tun.");
    assertThat(worklogs.getWorklogsCreated()).isNull();
    assertThat(worklogs.getWorklogsFrom()).isNull();
    verify(runService).finishRun(RUN_ID, SUCCEEDED, "0 Tickets geholt, 0 geschrieben.");
  }

  @Test
  void a_run_without_a_row_in_the_history_records_no_steps() {
    when(searchClient.search(any())).thenReturn(List.of(issue("1001", "ALPHA-1")).iterator());

    replicationService.runReplication(REPLICATION_ID);

    verify(runService, never()).recordStep(anyLong(), any());
  }

  private JiraReplicationRunStep theOnlyStep() {
    var steps = steps();
    assertThat(steps).hasSize(1);
    return steps.getFirst();
  }

  private List<JiraReplicationRunStep> steps() {
    var captor = ArgumentCaptor.forClass(JiraReplicationRunStep.class);
    verify(runService, atLeast(0)).recordStep(eq(RUN_ID), captor.capture());
    return captor.getAllValues();
  }

  private static JiraIssue issue(String id, String key) {
    var issue = new JiraIssue();
    issue.setId(id);
    issue.setKey(key);
    issue.setFields(new HashMap<>(Map.of(
        "summary", "Titel",
        "updated", LocalDateTime.of(2026, 6, 20, 10, 0).toString(),
        "created", LocalDateTime.of(2026, 6, 1, 10, 0).toString(),
        "issuetype", Map.of("name", "Task"))));
    return issue;
  }

}
