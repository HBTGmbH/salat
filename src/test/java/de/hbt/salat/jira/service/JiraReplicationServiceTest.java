package de.hbt.salat.jira.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Timeout.ThreadMode.SEPARATE_THREAD;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyIterable;
import static org.mockito.Mockito.anyList;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.util.ReflectionUtils.findField;
import static org.springframework.util.ReflectionUtils.makeAccessible;
import static org.springframework.util.ReflectionUtils.setField;
import static de.hbt.salat.jira.OrderTree.customerorderWithId;
import static de.hbt.salat.jira.OrderTree.suborderWithId;
import static de.hbt.salat.jira.domain.JiraApiFlavor.CLOUD;
import static de.hbt.salat.jira.domain.JiraApiFlavor.SERVER;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.mockito.ArgumentCaptor;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.client.RestClientException;
import de.hbt.salat.auth.domain.AuthorizedUser;
import de.hbt.salat.common.domain.AuditedEntity;
import de.hbt.salat.common.test.FixedClock;
import de.hbt.salat.common.util.DateTimeUtils;
import de.hbt.salat.jira.domain.JiraFieldConfig;
import de.hbt.salat.jira.domain.JiraReplicationConfig;
import de.hbt.salat.jira.domain.JiraReplicationRun;
import de.hbt.salat.jira.domain.JiraTicket;
import de.hbt.salat.jira.domain.ResolvedFieldValue;
import de.hbt.salat.jira.persistence.JiraReplicationConfigRepository;
import de.hbt.salat.jira.persistence.JiraTicketRepository;
import de.hbt.salat.order.domain.Customerorder;
import de.hbt.salat.order.domain.Suborder;

@FixedClock
@SpringBootTest
class JiraReplicationServiceTest {

  /** The order of the mock replication, and a suborder below it (#1323). */
  private static final long ORDER = 1L;
  private static final long SUBORDER_A_01 = 12L;
  private static final Customerorder ORDER_REFERENCE = customerorderWithId(ORDER);
  private static final Suborder SUBORDER_A_01_REFERENCE = suborderWithId(SUBORDER_A_01, ORDER_REFERENCE);

  @MockitoBean
  private JiraSearchClients searchClients;

  /** Not a bean override: the context holds one client per flavour, the registry hands ours out. */
  private final JiraSearchClient searchClient = mock(JiraSearchClient.class);

  @MockitoBean
  private JiraReplicationConfigRepository configRepo;

  @MockitoBean
  private JiraTicketRepository ticketRepo;

  @MockitoBean
  private JiraReplicationRunService runService;

  @MockitoBean
  private AuthorizedUser authorizedUser;

  @MockitoBean
  private JiraScopes scopes;

  @Autowired
  private JiraReplicationService jiraReplicationService;

  @BeforeEach
  void setUp() {
    when(authorizedUser.isAuthenticated()).thenReturn(true);
    when(authorizedUser.isManager()).thenReturn(true);
    when(searchClients.forFlavor(SERVER)).thenReturn(searchClient);
    when(scopes.signOf(ORDER, null)).thenReturn("MOCK_ORDER");
    when(scopes.signOf(ORDER, SUBORDER_A_01)).thenReturn("MOCK_ORDER/A/01");
  }

  @Test
  void testRunReplicationWithValidConfig() {
    JiraReplicationConfig config = createMockReplicationConfig();
    when(configRepo.findById(config.getId())).thenReturn(Optional.of(config));
    when(searchClient.search(any())).thenReturn(issues(mockIssue()));

    jiraReplicationService.runReplication(config.getId());

    verify(configRepo, times(1)).findById(config.getId());
    verify(ticketRepo, times(1)).save(any(JiraTicket.class));
    verify(ticketRepo, times(1)).saveAll(anyList());
    verify(configRepo, times(1)).save(config);
  }

  @Test
  void aRecordedRunWritesWhatItDidIntoItsRow() {
    // #1282: the run history is where a manual run reports back, it has no page waiting for it
    JiraReplicationConfig config = createMockReplicationConfig();
    when(configRepo.findById(config.getId())).thenReturn(Optional.of(config));
    when(searchClient.search(any())).thenReturn(issues(mockIssue()));

    jiraReplicationService.continueRun(77L, config.getId());

    verify(runService).finishRun(77L, JiraReplicationRun.Status.SUCCEEDED, "1 Tickets geholt, 1 geschrieben.");
  }

  @Test
  void aRunWithAnIssueItCouldNotStoreIsRecordedAsFailed() {
    JiraReplicationConfig config = createMockReplicationConfig();
    config.setLastMaxUpdated(LocalDateTime.of(2026, 6, 1, 8, 0, 0));
    when(configRepo.findById(config.getId())).thenReturn(Optional.of(config));
    when(searchClient.search(any())).thenReturn(issues(
        failingIssue(LocalDateTime.of(2026, 6, 10, 9, 0, 0)),
        mockIssue(LocalDateTime.of(2026, 6, 20, 17, 30, 0))));

    jiraReplicationService.continueRun(77L, config.getId());

    verify(runService).finishRun(77L, JiraReplicationRun.Status.FAILED,
        "2 Tickets geholt, 1 geschrieben. 1 nicht verarbeitet — der Wasserstand rückt nicht über sie hinaus.");
  }

  @Test
  void anAbortedRunIsRecordedWithoutThePasswordAndStillThrows() {
    JiraReplicationConfig config = createMockReplicationConfig();
    when(configRepo.findById(config.getId())).thenReturn(Optional.of(config));
    when(searchClient.search(any())).thenThrow(new RestClientException("401 for mockUser:mockPassword"));

    assertThrows(RestClientException.class, () -> jiraReplicationService.continueRun(77L, config.getId()));

    verify(runService).finishRun(77L, JiraReplicationRun.Status.FAILED, "Abgebrochen: 401 for mockUser:***");
  }

  @Test
  void testRunReplicationWithNoIssues() {
    JiraReplicationConfig config = createMockReplicationConfig();
    when(configRepo.findById(config.getId())).thenReturn(Optional.of(config));
    when(searchClient.search(any())).thenReturn(issues());

    jiraReplicationService.runReplication(config.getId());

    verify(ticketRepo, times(1)).saveAll(anyList());
    verify(configRepo, times(1)).findById(config.getId());
  }

  @Test
  void testRunReplicationUpdatesLastMaxUpdated() {
    LocalDateTime mockUpdated = LocalDateTime.of(2026, 6, 25, 11, 20, 25);
    JiraReplicationConfig config = createMockReplicationConfig();
    when(configRepo.findById(config.getId())).thenReturn(Optional.of(config));
    when(searchClient.search(any())).thenReturn(issues(mockIssue(mockUpdated)));

    jiraReplicationService.runReplication(config.getId());

    verify(configRepo, times(1)).save(config);
    assertEquals(mockUpdated, config.getLastMaxUpdated());
  }

  @Test
  void testAbortedRunDoesNotAdvanceLastMaxUpdated() {
    LocalDateTime watermark = LocalDateTime.of(2026, 6, 1, 8, 0, 0);
    JiraReplicationConfig config = createMockReplicationConfig();
    config.setLastMaxUpdated(watermark);
    when(configRepo.findById(config.getId())).thenReturn(Optional.of(config));
    // the first issue is stored with a far newer timestamp, fetching the next page then fails
    when(searchClient.search(any()))
        .thenReturn(failingAfter(mockIssue(LocalDateTime.of(2026, 8, 19, 10, 0, 0))));

    assertThrows(RestClientException.class, () -> jiraReplicationService.runReplication(config.getId()));

    verify(ticketRepo, times(1)).save(any(JiraTicket.class));
    verify(configRepo, never()).save(any(JiraReplicationConfig.class));
    assertEquals(watermark, config.getLastMaxUpdated());
  }

  @Test
  void testAFailedIssueCapsTheWatermarkBelowItself() {
    LocalDateTime watermark = LocalDateTime.of(2026, 6, 1, 8, 0, 0);
    JiraReplicationConfig config = createMockReplicationConfig();
    config.setLastMaxUpdated(watermark);
    when(configRepo.findById(config.getId())).thenReturn(Optional.of(config));
    when(searchClient.search(any())).thenReturn(issues(
        failingIssue(LocalDateTime.of(2026, 6, 10, 9, 0, 0)),
        mockIssue(LocalDateTime.of(2026, 6, 20, 17, 30, 0))));

    jiraReplicationService.runReplication(config.getId());

    // the younger issue alone would push the watermark past the failed one, which JIRA would then
    // never offer again - the ticket would be missing for good (#841)
    assertEquals(LocalDateTime.of(2026, 6, 10, 8, 59, 59), config.getLastMaxUpdated());
  }

  @Test
  void testAFailedIssueIsAskedForAgainInTheNextRun() {
    JiraReplicationConfig config = createMockReplicationConfig();
    config.setLastMaxUpdated(LocalDateTime.of(2026, 6, 1, 8, 0, 0));
    when(configRepo.findById(config.getId())).thenReturn(Optional.of(config));
    when(searchClient.search(any()))
        .thenReturn(issues(
            failingIssue(LocalDateTime.of(2026, 6, 10, 9, 0, 0)),
            mockIssue(LocalDateTime.of(2026, 6, 20, 17, 30, 0))))
        .thenReturn(issues());

    jiraReplicationService.runReplication(config.getId());
    jiraReplicationService.runReplication(config.getId());

    var requests = ArgumentCaptor.forClass(JiraSearchRequest.class);
    verify(searchClient, times(2)).search(requests.capture());
    assertEquals("(project = MOCK) AND updated >= '2026-06-10'", requests.getAllValues().get(1).jql());
  }

  @Test
  void testAFailureWithoutAReadableTimestampHoldsTheWatermark() {
    LocalDateTime watermark = LocalDateTime.of(2026, 6, 1, 8, 0, 0);
    JiraReplicationConfig config = createMockReplicationConfig();
    config.setLastMaxUpdated(watermark);
    when(configRepo.findById(config.getId())).thenReturn(Optional.of(config));
    var unreadable = mockIssue(LocalDateTime.of(2026, 6, 10, 9, 0, 0));
    unreadable.setId("1002");
    unreadable.setKey("MOCK-2");
    unreadable.getFields().put("updated", "vorgestern");
    when(searchClient.search(any())).thenReturn(issues(
        unreadable, mockIssue(LocalDateTime.of(2026, 6, 20, 17, 30, 0))));

    jiraReplicationService.runReplication(config.getId());

    // without a timestamp of its own the failed issue gives no position to cap at, so the watermark
    // stays where it was rather than guessing a bar the issue might fall under
    verify(configRepo, never()).save(any(JiraReplicationConfig.class));
    assertEquals(watermark, config.getLastMaxUpdated());
  }

  @Test
  void testFailedIssuesAreSummarisedAtTheEndOfTheRun() {
    JiraReplicationConfig config = createMockReplicationConfig();
    config.setLastMaxUpdated(LocalDateTime.of(2026, 6, 1, 8, 0, 0));
    when(configRepo.findById(config.getId())).thenReturn(Optional.of(config));
    when(searchClient.search(any())).thenReturn(issues(
        failingIssue(LocalDateTime.of(2026, 6, 10, 9, 0, 0)),
        mockIssue(LocalDateTime.of(2026, 6, 20, 17, 30, 0))));
    var logged = captureWarnings();

    jiraReplicationService.runReplication(config.getId());

    // the single ERROR per issue drowns in a long run - the count and its effect on the watermark
    // are what say whether the run needs attention
    assertThat(logged.list).filteredOn(event -> event.getLevel() == Level.WARN)
        .extracting(ILoggingEvent::getFormattedMessage)
        .anyMatch(message -> message.contains("1 issues") && message.contains("watermark"));
  }

  @Test
  void testARunWithoutFailuresLogsNoSummary() {
    JiraReplicationConfig config = createMockReplicationConfig();
    when(configRepo.findById(config.getId())).thenReturn(Optional.of(config));
    when(searchClient.search(any())).thenReturn(issues(mockIssue()));
    var logged = captureWarnings();

    jiraReplicationService.runReplication(config.getId());

    assertThat(logged.list).filteredOn(event -> event.getLevel() == Level.WARN)
        .extracting(ILoggingEvent::getFormattedMessage)
        .noneMatch(message -> message.contains("could not be processed"));
  }

  @Test
  void testBaselineIsTakenFromConfigWatermark() {
    JiraReplicationConfig config = createMockReplicationConfig();
    config.setLastMaxUpdated(LocalDateTime.of(2026, 6, 1, 8, 0, 0));
    when(configRepo.findById(config.getId())).thenReturn(Optional.of(config));
    when(searchClient.search(any())).thenReturn(issues());

    jiraReplicationService.runReplication(config.getId());

    assertEquals("(project = MOCK) AND updated >= '2026-06-01'", capturedRequest().jql());
  }

  @Test
  void testMissingWatermarkFetchesEverything() {
    JiraReplicationConfig config = createMockReplicationConfig();
    config.setLastMaxUpdated(null);
    when(configRepo.findById(config.getId())).thenReturn(Optional.of(config));
    when(searchClient.search(any())).thenReturn(issues());

    jiraReplicationService.runReplication(config.getId());

    assertEquals("project = MOCK", capturedRequest().jql());
  }

  @Test
  void testCloudConfigUsesTheCloudClient() {
    JiraSearchClient cloudClient = mock(JiraSearchClient.class);
    when(searchClients.forFlavor(CLOUD)).thenReturn(cloudClient);
    when(cloudClient.search(any())).thenReturn(issues(mockIssue()));
    JiraReplicationConfig config = createMockReplicationConfig();
    config.setApiFlavor(CLOUD);
    when(configRepo.findById(config.getId())).thenReturn(Optional.of(config));

    jiraReplicationService.runReplication(config.getId());

    verify(searchClient, never()).search(any());
    verify(ticketRepo, times(1)).save(any(JiraTicket.class));
  }

  @Test
  void testARunReadsAndWritesOnlyTicketsOfItsOwnScope() {
    // Two replications on the same order but with different scopes are independent (#1025). Every
    // ticket access keys on exactly one scope - the pair of order and suborder (#1323) - and the
    // ticket takes both over from the config as references (#1368).
    JiraReplicationConfig config = createMockReplicationConfig();
    config.setSuborder(SUBORDER_A_01_REFERENCE);
    when(configRepo.findById(config.getId())).thenReturn(Optional.of(config));
    when(searchClient.search(any())).thenReturn(issues(mockIssue()));

    jiraReplicationService.runReplication(config.getId());

    assertThat(savedTicket().getCustomerorder()).isSameAs(ORDER_REFERENCE);
    assertThat(savedTicket().getSuborder()).isSameAs(SUBORDER_A_01_REFERENCE);
    assertEquals(ORDER, savedTicket().getCustomerorderId());
    assertEquals(SUBORDER_A_01, savedTicket().getSuborderId());
    verify(ticketRepo).findMaintainedByJiraId(config.getId(), 1001L);
    verify(ticketRepo).findInScopeByKey(ORDER, SUBORDER_A_01, "MOCK-1");
    verify(ticketRepo).findUnmaintainedInScopeByJiraId(ORDER, SUBORDER_A_01, 1001L);
    verify(ticketRepo).findInScope(ORDER, SUBORDER_A_01);
    verify(ticketRepo, never()).findInScope(ORDER, null);
  }

  @Test
  void testParentChainsAreNotResolvedAcrossScopeBoundaries() {
    // The chain is walked over the tickets of this scope alone, so a parent replicated by another
    // replication of the same order is not reached and no field is inherited across the boundary.
    JiraReplicationConfig config = createIncrementalReplicationConfig();
    config.setSuborder(SUBORDER_A_01_REFERENCE);
    config.setAdditionalFieldNames("customfield_10123");
    config.setInheritedFieldNames("customfield_10123");
    when(configRepo.findById(config.getId())).thenReturn(Optional.of(config));
    when(searchClient.search(any())).thenReturn(issues());
    var child = ticket("MOCK-2", "MOCK-1");
    child.setSuborder(SUBORDER_A_01_REFERENCE);
    // the parent lives in the order-wide scope and is therefore invisible to this run
    when(ticketRepo.findInScope(ORDER, SUBORDER_A_01)).thenReturn(List.of(child));

    jiraReplicationService.runReplication(config.getId());

    assertEquals("MOCK-2", child.getTopLevelKey());
    assertNull(child.getCustomFieldsEffective());
  }

  @Test
  void testTopLevelKeysAreResolvedWithinTheCustomerOrderOnly() {
    JiraReplicationConfig config = createIncrementalReplicationConfig();
    when(configRepo.findById(config.getId())).thenReturn(Optional.of(config));
    when(searchClient.search(any())).thenReturn(issues());
    var parent = ticket("MOCK-1", null);
    var child = ticket("MOCK-2", "MOCK-1");
    when(ticketRepo.findInScope(ORDER, null)).thenReturn(List.of(parent, child));

    jiraReplicationService.runReplication(config.getId());

    // an issue key is only unique per customer order, so the parent chain must not be walked
    // across all tickets - identical keys under another order would collide
    verify(ticketRepo, never()).findAll();
    assertEquals("MOCK-1", child.getTopLevelKey());
    assertEquals("MOCK-1", parent.getTopLevelKey());
  }

  @Test
  void testConfiguredFieldsAreRequestedAndStored() {
    JiraReplicationConfig config = createMockReplicationConfig();
    config.setAdditionalFieldNames(" customfield_10123 , status ");
    when(configRepo.findById(config.getId())).thenReturn(Optional.of(config));
    when(searchClient.search(any())).thenReturn(issues(mockIssue(Map.of(
        "customfield_10123", Map.of("value", "Wartung"),
        "status", Map.of("name", "In Arbeit")))));

    jiraReplicationService.runReplication(config.getId());

    // a standard field is configured by its response key exactly like a custom one
    assertThat(capturedRequest().fields()).contains("customfield_10123", "status");
    assertThat(savedTicket().getCustomFields())
        .containsEntry("customfield_10123", "Wartung")
        .containsEntry("status", "In Arbeit");
  }

  @Test
  void testAPathIsRequestedByItsHeadAndStoredUnderThePath() {
    JiraReplicationConfig config = createMockReplicationConfig();
    config.setAdditionalFieldNames("customfield_10200,customfield_10200.child.value");
    when(configRepo.findById(config.getId())).thenReturn(Optional.of(config));
    when(searchClient.search(any())).thenReturn(issues(mockIssue(Map.of(
        "customfield_10200", Map.of("value", "Wartung", "child", Map.of("value", "Hotfix"))))));

    jiraReplicationService.runReplication(config.getId());

    // JIRA only accepts top-level ids in its fields parameter, so both paths are one request key
    assertThat(capturedRequest().fields()).containsOnlyOnce("customfield_10200");
    assertThat(capturedRequest().fields()).doesNotContain("customfield_10200.child.value");
    assertThat(savedTicket().getCustomFields())
        .containsEntry("customfield_10200", "Wartung")
        .containsEntry("customfield_10200.child.value", "Hotfix");
  }

  @Test
  void testWithoutConfiguredFieldsNothingIsStored() {
    JiraReplicationConfig config = createMockReplicationConfig();
    when(configRepo.findById(config.getId())).thenReturn(Optional.of(config));
    when(searchClient.search(any())).thenReturn(issues(mockIssue()));

    jiraReplicationService.runReplication(config.getId());

    // no empty document either - JSON_EXTRACT on one aborts the statement around it
    assertThat(savedTicket().getCustomFields()).isNull();
    assertThat(savedTicket().getFieldConfigHash()).isNull();
  }

  @Test
  void testInheritedValueComesFromTheNearestAncestorThatHasOne() {
    JiraReplicationConfig config = createIncrementalReplicationConfig();
    config.setInheritedFieldNames("customfield_10123");
    when(configRepo.findById(config.getId())).thenReturn(Optional.of(config));
    when(searchClient.search(any())).thenReturn(issues());
    var epic = ticket("MOCK-1", null, Map.of("customfield_10123", "Wartung"));
    var story = ticket("MOCK-2", "MOCK-1", Map.of());
    var task = ticket("MOCK-3", "MOCK-2", Map.of());
    when(ticketRepo.findInScope(ORDER, null)).thenReturn(List.of(epic, story, task));

    jiraReplicationService.runReplication(config.getId());

    assertThat(task.getCustomFieldsEffective())
        .containsEntry("customfield_10123", new ResolvedFieldValue("Wartung", "MOCK-1"));
    // from = null says "set on the ticket itself"
    assertThat(epic.getCustomFieldsEffective())
        .containsEntry("customfield_10123", new ResolvedFieldValue("Wartung", null));
  }

  @Test
  void testOwnValueBeatsTheInheritedOne() {
    JiraReplicationConfig config = createIncrementalReplicationConfig();
    config.setInheritedFieldNames("customfield_10123");
    when(configRepo.findById(config.getId())).thenReturn(Optional.of(config));
    when(searchClient.search(any())).thenReturn(issues());
    var epic = ticket("MOCK-1", null, Map.of("customfield_10123", "Wartung"));
    var task = ticket("MOCK-2", "MOCK-1", Map.of("customfield_10123", "Migration"));
    when(ticketRepo.findInScope(ORDER, null)).thenReturn(List.of(epic, task));

    jiraReplicationService.runReplication(config.getId());

    assertThat(task.getCustomFieldsEffective())
        .containsEntry("customfield_10123", new ResolvedFieldValue("Migration", null));
  }

  @Test
  void testFieldWithoutAValueAnywhereInTheChainStaysAbsent() {
    JiraReplicationConfig config = createIncrementalReplicationConfig();
    config.setInheritedFieldNames("customfield_10123");
    when(configRepo.findById(config.getId())).thenReturn(Optional.of(config));
    when(searchClient.search(any())).thenReturn(issues());
    var epic = ticket("MOCK-1", null, Map.of());
    var task = ticket("MOCK-2", "MOCK-1", Map.of());
    when(ticketRepo.findInScope(ORDER, null)).thenReturn(List.of(epic, task));

    jiraReplicationService.runReplication(config.getId());

    assertNull(task.getCustomFieldsEffective());
  }

  @Test
  @Timeout(value = 10, threadMode = SEPARATE_THREAD)
  void testCycleInTheParentChainDoesNotHang() {
    JiraReplicationConfig config = createIncrementalReplicationConfig();
    config.setInheritedFieldNames("customfield_10123");
    when(configRepo.findById(config.getId())).thenReturn(Optional.of(config));
    when(searchClient.search(any())).thenReturn(issues());
    // parent_field_names allows any field as the parent source, so a chain pointing back at itself
    // is a shape the foreign system can hand over
    var one = ticket("MOCK-1", "MOCK-2", Map.of("customfield_10123", "Wartung"));
    var two = ticket("MOCK-2", "MOCK-1", Map.of());
    when(ticketRepo.findInScope(ORDER, null)).thenReturn(List.of(one, two));

    jiraReplicationService.runReplication(config.getId());

    assertThat(two.getCustomFieldsEffective())
        .containsEntry("customfield_10123", new ResolvedFieldValue("Wartung", "MOCK-1"));
  }

  @Test
  void testChangedFieldListRewritesAnUnchangedTicket() {
    JiraReplicationConfig config = createMockReplicationConfig();
    config.setAdditionalFieldNames("customfield_10123");
    when(configRepo.findById(config.getId())).thenReturn(Optional.of(config));
    var stored = ticket("MOCK-1", null, Map.of());
    stored.setUpdatedTs(LocalDateTime.of(2026, 6, 25, 15, 5, 0));
    stored.setFieldConfigHash("the hash of an earlier field list");
    when(ticketRepo.findMaintainedByJiraId(1L, 1001L))
        .thenReturn(Optional.of(stored));
    when(searchClient.search(any()))
        .thenReturn(issues(mockIssue(Map.of("customfield_10123", "Wartung"))));

    jiraReplicationService.runReplication(config.getId());

    // JIRA reports the ticket as unchanged - its `updated` has not moved - so this is the only
    // point at which a newly configured field can reach an already replicated ticket
    verify(ticketRepo, times(1)).save(stored);
    assertThat(stored.getCustomFields()).containsEntry("customfield_10123", "Wartung");
  }

  @Test
  void testUnchangedFieldListLeavesAnUnchangedTicketAlone() {
    JiraReplicationConfig config = createMockReplicationConfig();
    config.setAdditionalFieldNames("customfield_10123");
    when(configRepo.findById(config.getId())).thenReturn(Optional.of(config));
    var stored = ticket("MOCK-1", null, Map.of("customfield_10123", "Wartung"));
    stored.setUpdatedTs(LocalDateTime.of(2026, 6, 25, 15, 5, 0));
    stored.setFieldConfigHash(JiraFieldConfig.from(config).hash());
    stored.setReplication(config);
    when(ticketRepo.findMaintainedByJiraId(1L, 1001L))
        .thenReturn(Optional.of(stored));
    when(searchClient.search(any()))
        .thenReturn(issues(mockIssue(Map.of("customfield_10123", "Wartung"))));

    jiraReplicationService.runReplication(config.getId());

    verify(ticketRepo, never()).save(any(JiraTicket.class));
  }

  @Test
  void testFieldNoAnswerCarriedIsLogged() {
    JiraReplicationConfig config = createMockReplicationConfig();
    config.setAdditionalFieldNames("customfield_10123,customfield_99999");
    when(configRepo.findById(config.getId())).thenReturn(Optional.of(config));
    when(searchClient.search(any()))
        .thenReturn(issues(mockIssue(Map.of("customfield_10123", "Wartung"))));
    var logged = captureWarnings();

    jiraReplicationService.runReplication(config.getId());

    // JIRA Server drops an unknown field id without a word, so never arriving is the only sign
    assertThat(logged.list).filteredOn(event -> event.getLevel() == Level.WARN)
        .extracting(ILoggingEvent::getFormattedMessage)
        .anyMatch(message -> message.contains("customfield_99999"))
        // and the field that did arrive is not put up for suspicion
        .noneMatch(message -> message.contains("customfield_10123"));
  }

  @Test
  void aRunWithoutWatermarkRemovesTheTicketsItDidNotSee() {
    // Moved to a project the JQL does not match, or left out by a narrowed JQL: JIRA reports
    // neither, the ticket is simply missing from a complete answer (#1167).
    JiraReplicationConfig config = createMockReplicationConfig();
    when(configRepo.findById(config.getId())).thenReturn(Optional.of(config));
    when(searchClient.search(any())).thenReturn(issues(mockIssue(), otherIssue()));
    var seen = stored(1001L, "MOCK-1");
    var gone = stored(1002L, "MOCK-2");
    var seenToo = stored(1003L, "MOCK-3");
    when(ticketRepo.findInScope(ORDER, null)).thenReturn(List.of(seen, gone, seenToo));

    jiraReplicationService.runReplication(config.getId());

    assertThat(removedTickets()).containsExactly(gone);
  }

  /** A ticket maintained by hand has no JIRA id (#1386): JIRA never sends it, so it stays. */
  @Test
  void aRunWithoutWatermarkLeavesTheTicketsMaintainedByHand() {
    JiraReplicationConfig config = createMockReplicationConfig();
    when(configRepo.findById(config.getId())).thenReturn(Optional.of(config));
    when(searchClient.search(any())).thenReturn(issues(mockIssue()));
    var seen = stored(1001L, "MOCK-1");
    var byHand = byHand("HAND-1");
    when(ticketRepo.findInScope(ORDER, null)).thenReturn(List.of(seen, byHand));

    jiraReplicationService.runReplication(config.getId());

    verifyNothingRemoved();
  }

  /**
   * Who maintains a ticket is its foreign key (#1386): a run removes only its own tickets — not one of
   * another replication of the same scope, and not one a deleted replication left behind.
   */
  @Test
  void aRunWithoutWatermarkRemovesOnlyItsOwnTickets() {
    JiraReplicationConfig config = createMockReplicationConfig();
    when(configRepo.findById(config.getId())).thenReturn(Optional.of(config));
    when(searchClient.search(any())).thenReturn(issues());
    var own = stored(1001L, "MOCK-1");
    var ofAnother = stored(1002L, "MOCK-2");
    ofAnother.setReplication(replicationWithId(2L));
    var orphan = stored(1003L, "MOCK-3");
    orphan.setReplication(null);
    when(ticketRepo.findInScope(ORDER, null)).thenReturn(List.of(own, ofAnother, orphan));

    jiraReplicationService.runReplication(config.getId());

    assertThat(removedTickets()).containsExactly(own);
  }

  /**
   * A ticket another replication maintains in the same scope is skipped and stays with it (#1386) —
   * found by its JIRA id or by its key. The run says which, so that the JQL can be corrected.
   */
  @Test
  void aTicketOfAnotherReplicationIsSkippedAndNamedInTheRun() {
    JiraReplicationConfig config = createMockReplicationConfig();
    when(configRepo.findById(config.getId())).thenReturn(Optional.of(config));
    when(searchClient.search(any())).thenReturn(issues(mockIssue(), otherIssue()));
    var other = replicationWithId(2L);
    other.setName("Andere");
    var byId = stored(1001L, "MOCK-1");
    byId.setReplication(other);
    when(ticketRepo.findInScopeByKey(ORDER, null, "MOCK-1")).thenReturn(Optional.of(byId));
    var byKey = stored(9003L, "MOCK-3");
    byKey.setReplication(other);
    when(ticketRepo.findInScopeByKey(ORDER, null, "MOCK-3")).thenReturn(Optional.of(byKey));

    var result = jiraReplicationService.runReplication(config.getId());

    verify(ticketRepo, never()).save(any(JiraTicket.class));
    assertThat(byId.getReplication()).isSameAs(other);
    assertThat(byKey.getJiraId()).isEqualTo(9003L);
    assertThat(result.skipped()).isEqualTo(2);
    assertThat(result.written()).isZero();
    assertThat(result.succeeded()).isTrue();
    assertThat(result.summary()).contains("2 übersprungen").contains("MOCK-1 (Andere)").contains("MOCK-3 (Andere)");
  }

  /**
   * Top-level key and inherited fields are derived, never entered (#1386): a run resolves them for
   * every ticket of the scope — its own, those of another replication and those by hand alike.
   */
  @Test
  void theParentChainsAreResolvedForEveryTicketOfTheScope() {
    JiraReplicationConfig config = createMockReplicationConfig();
    config.setInheritedFieldNames("team");
    when(configRepo.findById(config.getId())).thenReturn(Optional.of(config));
    when(searchClient.search(any())).thenReturn(issues());
    var parentByHand = byHand("HAND-1");
    parentByHand.setCustomFields(Map.of("team", "Blau"));
    var own = stored(1001L, "MOCK-1");
    own.setParentKey("HAND-1");
    var otherChild = stored(1002L, "MOCK-2");
    otherChild.setParentKey("HAND-1");
    otherChild.setReplication(replicationWithId(2L));
    when(ticketRepo.findInScope(ORDER, null)).thenReturn(new ArrayList<>(List.of(parentByHand, own, otherChild)));
    config.setLastMaxUpdated(LocalDateTime.of(2026, 1, 1, 0, 0));

    jiraReplicationService.runReplication(config.getId());

    assertThat(own.getTopLevelKey()).isEqualTo("HAND-1");
    assertThat(otherChild.getTopLevelKey()).isEqualTo("HAND-1");
    assertThat(parentByHand.getTopLevelKey()).isEqualTo("HAND-1");
    assertThat(otherChild.getCustomFieldsEffective()).containsEntry("team", new ResolvedFieldValue("Blau", "HAND-1"));
  }


  /**
   * JIRA gave a replicated issue a new key — moved to another project — that a ticket by hand in the
   * scope already carries (#1386). It is the same issue: the ticket by hand gives way, rather than
   * the key failing the run on every attempt.
   */
  @Test
  void aRenamedIssueReplacesTheTicketByHandWithItsNewKey() {
    JiraReplicationConfig config = createMockReplicationConfig();
    when(configRepo.findById(config.getId())).thenReturn(Optional.of(config));
    when(searchClient.search(any())).thenReturn(issues(mockIssue()));
    var own = stored(1001L, "OLD-7");
    when(ticketRepo.findMaintainedByJiraId(1L, 1001L)).thenReturn(Optional.of(own));
    var byHand = byHand("MOCK-1");
    setField(findField(AuditedEntity.class, "id"), byHand, 55L);
    setField(findField(AuditedEntity.class, "id"), own, 54L);
    when(ticketRepo.findInScopeByKey(ORDER, null, "MOCK-1")).thenReturn(Optional.of(byHand));

    jiraReplicationService.runReplication(config.getId());

    var order = inOrder(ticketRepo);
    order.verify(ticketRepo).delete(byHand);
    order.verify(ticketRepo).flush();
    order.verify(ticketRepo).save(own);
    assertThat(own.getKey()).isEqualTo("MOCK-1");
  }

  /** The new key belongs to another replication of the scope: the issue is skipped, nothing is lost. */
  @Test
  void aRenamedIssueWhoseNewKeyAnotherReplicationMaintainsIsSkipped() {
    JiraReplicationConfig config = createMockReplicationConfig();
    when(configRepo.findById(config.getId())).thenReturn(Optional.of(config));
    when(searchClient.search(any())).thenReturn(issues(mockIssue()));
    var own = stored(1001L, "OLD-7");
    setField(findField(AuditedEntity.class, "id"), own, 54L);
    when(ticketRepo.findMaintainedByJiraId(1L, 1001L)).thenReturn(Optional.of(own));
    var others = stored(2002L, "MOCK-1");
    setField(findField(AuditedEntity.class, "id"), others, 55L);
    var other = replicationWithId(2L);
    other.setName("Andere");
    others.setReplication(other);
    when(ticketRepo.findInScopeByKey(ORDER, null, "MOCK-1")).thenReturn(Optional.of(others));

    var result = jiraReplicationService.runReplication(config.getId());

    verify(ticketRepo, never()).delete(any(JiraTicket.class));
    verify(ticketRepo, never()).save(any(JiraTicket.class));
    assertThat(own.getKey()).isEqualTo("OLD-7");
    assertThat(result.skipped()).isEqualTo(1);
    assertThat(result.summary()).contains("MOCK-1 (Andere)");
  }

  /**
   * A JIRA id is unique per replication, not per scope (#1386): another replication of the scope may
   * read another JIRA instance, which hands out the same numeric ids for other issues. Its ticket
   * stays, and this one is stored next to it.
   */
  @Test
  void theSameJiraIdOfAnotherReplicationIsAnotherIssue() {
    JiraReplicationConfig config = createMockReplicationConfig();
    when(configRepo.findById(config.getId())).thenReturn(Optional.of(config));
    when(searchClient.search(any())).thenReturn(issues(mockIssue()));
    var others = stored(1001L, "OTHER-4");
    others.setReplication(replicationWithId(2L));

    var result = jiraReplicationService.runReplication(config.getId());

    assertThat(result.skipped()).isZero();
    assertThat(savedTicket()).isNotSameAs(others);
    assertThat(savedTicket().getKey()).isEqualTo("MOCK-1");
    assertThat(savedTicket().getReplication()).isSameAs(config);
    assertThat(others.getKey()).isEqualTo("OTHER-4");
  }

  /** A replication set up later takes a ticket maintained by hand over by its key (#1386). */
  @Test
  void aTicketMaintainedByHandIsTakenOverByItsKey() {
    JiraReplicationConfig config = createMockReplicationConfig();
    when(configRepo.findById(config.getId())).thenReturn(Optional.of(config));
    when(searchClient.search(any())).thenReturn(issues(mockIssue()));
    var byHand = byHand("MOCK-1");
    when(ticketRepo.findInScopeByKey(ORDER, null, "MOCK-1")).thenReturn(Optional.of(byHand));

    jiraReplicationService.runReplication(config.getId());

    assertThat(savedTicket()).isSameAs(byHand);
    assertThat(byHand.getJiraId()).isEqualTo(1001L);
    assertThat(byHand.getReplication()).isSameAs(config);
  }

  /**
   * A ticket left behind by a deleted replication is written even though JIRA reports it unchanged,
   * so that it points at the replication that keeps it now (#1386).
   */
  @Test
  void anUnchangedTicketOfADeletedReplicationIsTakenOver() {
    JiraReplicationConfig config = createMockReplicationConfig();
    when(configRepo.findById(config.getId())).thenReturn(Optional.of(config));
    var orphan = stored(1001L, "MOCK-1");
    orphan.setReplication(null);
    orphan.setUpdatedTs(LocalDateTime.of(2026, 6, 25, 15, 5, 0));
    orphan.setFieldConfigHash(JiraFieldConfig.from(config).hash());
    when(ticketRepo.findUnmaintainedInScopeByJiraId(ORDER, null, 1001L)).thenReturn(List.of(orphan));
    when(searchClient.search(any())).thenReturn(issues(mockIssue()));

    jiraReplicationService.runReplication(config.getId());

    assertThat(savedTicket()).isSameAs(orphan);
    assertThat(orphan.getReplication()).isSameAs(config);
  }

  @Test
  void aRunWithoutWatermarkThatFindsNothingRemovesEveryTicketOfTheScope() {
    JiraReplicationConfig config = createMockReplicationConfig();
    when(configRepo.findById(config.getId())).thenReturn(Optional.of(config));
    when(searchClient.search(any())).thenReturn(issues());
    var one = stored(1001L, "MOCK-1");
    var two = stored(1002L, "MOCK-2");
    when(ticketRepo.findInScope(ORDER, null)).thenReturn(List.of(one, two));

    jiraReplicationService.runReplication(config.getId());

    assertThat(removedTickets()).containsExactlyInAnyOrder(one, two);
  }

  @Test
  void aRunWithoutWatermarkThatSawEveryTicketRemovesNothing() {
    JiraReplicationConfig config = createMockReplicationConfig();
    when(configRepo.findById(config.getId())).thenReturn(Optional.of(config));
    when(searchClient.search(any())).thenReturn(issues(mockIssue()));
    when(ticketRepo.findInScope(ORDER, null)).thenReturn(List.of(stored(1001L, "MOCK-1")));

    jiraReplicationService.runReplication(config.getId());

    verifyNothingRemoved();
  }

  @Test
  void aRunFromTheWatermarkRemovesNothing() {
    // it only sees what changed since, so a missing ticket says nothing about it
    JiraReplicationConfig config = createMockReplicationConfig();
    config.setLastMaxUpdated(LocalDateTime.of(2026, 6, 1, 8, 0, 0));
    when(configRepo.findById(config.getId())).thenReturn(Optional.of(config));
    when(searchClient.search(any())).thenReturn(issues(mockIssue()));
    when(ticketRepo.findInScope(ORDER, null))
        .thenReturn(List.of(stored(1001L, "MOCK-1"), stored(1002L, "MOCK-2")));

    jiraReplicationService.runReplication(config.getId());

    verifyNothingRemoved();
  }

  @Test
  void aRunWithAnIssueItCouldNotProcessRemovesNothing() {
    // the ids seen are not reliable then - parsing the id may itself have been the failure
    JiraReplicationConfig config = createMockReplicationConfig();
    when(configRepo.findById(config.getId())).thenReturn(Optional.of(config));
    when(searchClient.search(any())).thenReturn(issues(
        failingIssue(LocalDateTime.of(2026, 6, 10, 9, 0, 0)), mockIssue()));
    when(ticketRepo.findInScope(ORDER, null))
        .thenReturn(List.of(stored(1001L, "MOCK-1"), stored(1002L, "MOCK-2")));
    var logged = captureWarnings();

    jiraReplicationService.runReplication(config.getId());

    verifyNothingRemoved();
    assertThat(logged.list).filteredOn(event -> event.getLevel() == Level.WARN)
        .extracting(ILoggingEvent::getFormattedMessage)
        .anyMatch(message -> message.contains("not removed"));
  }

  @Test
  void anAbortedRunRemovesNothing() {
    JiraReplicationConfig config = createMockReplicationConfig();
    when(configRepo.findById(config.getId())).thenReturn(Optional.of(config));
    when(searchClient.search(any())).thenReturn(failingAfter(mockIssue()));
    when(ticketRepo.findInScope(ORDER, null)).thenReturn(List.of(stored(1002L, "MOCK-2")));

    assertThrows(RestClientException.class, () -> jiraReplicationService.runReplication(config.getId()));

    verifyNothingRemoved();
  }

  @Test
  void theRemovalLeavesOtherScopesAloneEvenWithTheSameKeyAndId() {
    // two JIRA instances can hand out the same key and the same id, the scope tells them apart
    JiraReplicationConfig config = createMockReplicationConfig();
    config.setSuborder(SUBORDER_A_01_REFERENCE);
    when(configRepo.findById(config.getId())).thenReturn(Optional.of(config));
    when(searchClient.search(any())).thenReturn(issues());
    var own = stored(1002L, "MOCK-2");
    own.setSuborder(SUBORDER_A_01_REFERENCE);
    when(ticketRepo.findInScope(ORDER, SUBORDER_A_01)).thenReturn(List.of(own));
    when(ticketRepo.findInScope(ORDER, null)).thenReturn(List.of(stored(1002L, "MOCK-2")));

    jiraReplicationService.runReplication(config.getId());

    assertThat(removedTickets()).containsExactly(own);
    verify(ticketRepo, never()).findInScope(ORDER, null);
  }

  @Test
  void aRemovedParentNoLongerPassesItsValuesOn() {
    // The removal comes before the chains are walked, so the parent is gone in this very run.
    JiraReplicationConfig config = createMockReplicationConfig();
    config.setInheritedFieldNames("customfield_10123");
    when(configRepo.findById(config.getId())).thenReturn(Optional.of(config));
    when(searchClient.search(any())).thenReturn(issues(mockIssue()));
    var parent = ticket("MOCK-9", null, Map.of("customfield_10123", "Wartung"));
    parent.setJiraId(1009L);
    parent.setReplication(config);
    var child = ticket("MOCK-1", "MOCK-9", Map.of());
    child.setJiraId(1001L);
    child.setReplication(config);
    when(ticketRepo.findInScope(ORDER, null)).thenReturn(List.of(parent, child));

    jiraReplicationService.runReplication(config.getId());

    assertThat(removedTickets()).containsExactly(parent);
    assertNull(child.getCustomFieldsEffective());
    assertEquals("MOCK-1", child.getTopLevelKey());
  }

  @Test
  void theNumberOfRemovedTicketsIsLoggedWithReplicationAndScope() {
    JiraReplicationConfig config = createMockReplicationConfig();
    config.setName("Mock replication");
    when(configRepo.findById(config.getId())).thenReturn(Optional.of(config));
    when(searchClient.search(any())).thenReturn(issues(mockIssue()));
    when(ticketRepo.findInScope(ORDER, null)).thenReturn(List.of(
        stored(1001L, "MOCK-1"), stored(1002L, "MOCK-2"), stored(1003L, "MOCK-3")));
    var logged = captureWarnings();

    jiraReplicationService.runReplication(config.getId());

    // a JQL narrowed by mistake shows up here first
    assertThat(logged.list).filteredOn(event -> event.getLevel() == Level.INFO)
        .extracting(ILoggingEvent::getFormattedMessage)
        .anyMatch(message -> message.contains("Removed 2 tickets")
            && message.contains("MOCK_ORDER") && message.contains("Mock replication"));
  }

  /** The tickets the run deleted, in one call. */
  @SuppressWarnings("unchecked")
  private List<JiraTicket> removedTickets() {
    var removed = ArgumentCaptor.forClass(Iterable.class);
    verify(ticketRepo).deleteAll(removed.capture());
    var tickets = new ArrayList<JiraTicket>();
    ((Iterable<JiraTicket>) removed.getValue()).forEach(tickets::add);
    return tickets;
  }

  private void verifyNothingRemoved() {
    verify(ticketRepo, never()).deleteAll(anyIterable());
    verify(ticketRepo, never()).delete(any(JiraTicket.class));
  }

  /**
   * A ticket already stored in the default scope, as the replication finds it before the chains —
   * maintained by the replication of {@link #createMockReplicationConfig()} (#1386).
   */
  private static JiraTicket stored(long jiraId, String key) {
    var ticket = ticket(key, null);
    ticket.setJiraId(jiraId);
    return ticket;
  }

  private static JiraReplicationConfig replicationWithId(long id) {
    var config = new JiraReplicationConfig();
    var idField = findField(AuditedEntity.class, "id");
    makeAccessible(idField);
    setField(idField, config, id);
    return config;
  }

  /** Collects what the service under test logs for the rest of the test method. */
  private ListAppender<ILoggingEvent> captureWarnings() {
    var appender = new ListAppender<ILoggingEvent>();
    appender.start();
    var logger = (Logger) LoggerFactory.getLogger(JiraReplicationService.class);
    logger.addAppender(appender);
    return appender;
  }

  private JiraTicket savedTicket() {
    var ticket = ArgumentCaptor.forClass(JiraTicket.class);
    verify(ticketRepo).save(ticket.capture());
    return ticket.getValue();
  }

  /** A ticket of the default scope, maintained by the replication of {@link #createMockReplicationConfig()}. */
  private static JiraTicket ticket(String key, String parentKey) {
    var ticket = new JiraTicket();
    ticket.setCustomerorder(ORDER_REFERENCE);
    ticket.setKey(key);
    ticket.setParentKey(parentKey);
    ticket.setReplication(replicationWithId(1L));
    return ticket;
  }

  /** A ticket of the default scope maintained by hand (#1386). */
  private static JiraTicket byHand(String key) {
    var ticket = ticket(key, null);
    ticket.setReplication(null);
    return ticket;
  }

  private static JiraTicket ticket(String key, String parentKey, Map<String, String> customFields) {
    var ticket = ticket(key, parentKey);
    ticket.setCustomFields(customFields);
    return ticket;
  }

  private JiraSearchRequest capturedRequest() {
    var request = ArgumentCaptor.forClass(JiraSearchRequest.class);
    verify(searchClient).search(request.capture());
    return request.getValue();
  }

  /**
   * A config that has run before. The chains are resolved over the stored tickets on every run, but
   * only a run from the watermark leaves the ones it did not see in place (#1167).
   */
  private JiraReplicationConfig createIncrementalReplicationConfig() {
    var config = createMockReplicationConfig();
    config.setLastMaxUpdated(LocalDateTime.of(2026, 6, 1, 8, 0, 0));
    return config;
  }

  private JiraReplicationConfig createMockReplicationConfig() {
    JiraReplicationConfig config = new JiraReplicationConfig();
    var idField = findField(AuditedEntity.class, "id");
    makeAccessible(idField);
    setField(idField, config, 1L);
    config.setBaseUrl("http://mock-jira.com");
    config.setUsername("mockUser");
    config.setPassword("mockPassword");
    config.setJql("project = MOCK");
    config.setPageSize(50);
    config.setCustomerorder(ORDER_REFERENCE);
    return config;
  }

  private static JiraIssue mockIssue() {
    return mockIssue(LocalDateTime.of(2026, 6, 25, 15, 5, 0));
  }

  private static JiraIssue mockIssue(LocalDateTime updated) {
    return mockIssue(updated, Map.of());
  }

  /** An issue carrying the given fields on top of the ones every answer has. */
  private static JiraIssue mockIssue(Map<String, Object> additionalFields) {
    return mockIssue(LocalDateTime.of(2026, 6, 25, 15, 5, 0), additionalFields);
  }

  private static JiraIssue mockIssue(LocalDateTime updated, Map<String, Object> additionalFields) {
    JiraIssue issue = new JiraIssue();
    issue.setId("1001");
    issue.setKey("MOCK-1");
    var fields = new HashMap<String, Object>(Map.of(
        "summary", "Mock Summary",
        "updated", updated.toString(),
        "created", DateTimeUtils.now().toString(),
        "issuetype", Map.of("name", "Task")
    ));
    fields.putAll(additionalFields);
    issue.setFields(fields);
    return issue;
  }

  private static JiraIssue otherIssue() {
    var issue = mockIssue();
    issue.setId("1003");
    issue.setKey("MOCK-3");
    return issue;
  }

  /**
   * An issue whose processing blows up the way an unexpected id does: {@code Long.parseLong} in
   * {@code runReplication} throws before the ticket is ever written.
   */
  private static JiraIssue failingIssue(LocalDateTime updated) {
    var issue = mockIssue(updated);
    issue.setId("not-a-number");
    issue.setKey("MOCK-2");
    return issue;
  }

  private static Iterator<JiraIssue> issues(JiraIssue... issues) {
    return List.of(issues).iterator();
  }

  /**
   * Serves one issue and then fails, the way the lazy client does when fetching a further page
   * breaks down mid-run.
   */
  private static Iterator<JiraIssue> failingAfter(JiraIssue issue) {
    return new Iterator<>() {

      private boolean served;

      @Override
      public boolean hasNext() {
        if (!served) return true;
        throw new RestClientException("connection reset");
      }

      @Override
      public JiraIssue next() {
        if (!hasNext()) throw new NoSuchElementException();
        served = true;
        return issue;
      }
    };
  }

}