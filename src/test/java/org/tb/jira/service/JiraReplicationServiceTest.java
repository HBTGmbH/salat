package org.tb.jira.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Timeout.ThreadMode.SEPARATE_THREAD;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.util.ReflectionUtils.findField;
import static org.springframework.util.ReflectionUtils.makeAccessible;
import static org.springframework.util.ReflectionUtils.setField;
import static org.tb.jira.domain.JiraApiFlavor.CLOUD;
import static org.tb.jira.domain.JiraApiFlavor.SERVER;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.time.LocalDateTime;
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
import org.tb.common.domain.AuditedEntity;
import org.tb.common.test.FixedClock;
import org.tb.common.util.DateTimeUtils;
import org.tb.jira.domain.JiraFieldConfig;
import org.tb.jira.domain.JiraReplicationConfig;
import org.tb.jira.domain.JiraTicket;
import org.tb.jira.domain.ResolvedFieldValue;
import org.tb.jira.persistence.JiraReplicationConfigRepository;
import org.tb.jira.persistence.JiraTicketRepository;

@FixedClock
@SpringBootTest
class JiraReplicationServiceTest {

  @MockitoBean
  private JiraSearchClients searchClients;

  /** Not a bean override: the context holds one client per flavour, the registry hands ours out. */
  private final JiraSearchClient searchClient = mock(JiraSearchClient.class);

  @MockitoBean
  private JiraReplicationConfigRepository configRepo;

  @MockitoBean
  private JiraTicketRepository ticketRepo;

  @Autowired
  private JiraReplicationService jiraReplicationService;

  @BeforeEach
  void setUp() {
    when(searchClients.forFlavor(SERVER)).thenReturn(searchClient);
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
    // Two replications on the same order but with different scopes are independent (#1025). The
    // separation costs the run nothing: every ticket access already keys on exactly one sign, and
    // that sign is now the scope rather than the order.
    JiraReplicationConfig config = createMockReplicationConfig();
    config.setScopeSign("MOCK_ORDER/A/01");
    when(configRepo.findById(config.getId())).thenReturn(Optional.of(config));
    when(searchClient.search(any())).thenReturn(issues(mockIssue()));

    jiraReplicationService.runReplication(config.getId());

    assertEquals("MOCK_ORDER/A/01", savedTicket().getScopeSign());
    verify(ticketRepo).findByScopeSignAndJiraId("MOCK_ORDER/A/01", 1001L);
    verify(ticketRepo).findByScopeSign("MOCK_ORDER/A/01");
    verify(ticketRepo, never()).findByScopeSign("MOCK_ORDER");
  }

  @Test
  void testParentChainsAreNotResolvedAcrossScopeBoundaries() {
    // The chain is walked over the tickets of this scope alone, so a parent replicated by another
    // replication of the same order is not reached and no field is inherited across the boundary.
    JiraReplicationConfig config = createMockReplicationConfig();
    config.setScopeSign("MOCK_ORDER/A/01");
    config.setAdditionalFieldNames("customfield_10123");
    config.setInheritedFieldNames("customfield_10123");
    when(configRepo.findById(config.getId())).thenReturn(Optional.of(config));
    when(searchClient.search(any())).thenReturn(issues());
    var child = ticket("MOCK-2", "MOCK-1");
    child.setScopeSign("MOCK_ORDER/A/01");
    // the parent lives in the order-wide scope and is therefore invisible to this run
    when(ticketRepo.findByScopeSign("MOCK_ORDER/A/01")).thenReturn(List.of(child));

    jiraReplicationService.runReplication(config.getId());

    assertEquals("MOCK-2", child.getTopLevelKey());
    assertNull(child.getCustomFieldsEffective());
  }

  @Test
  void testTopLevelKeysAreResolvedWithinTheCustomerOrderOnly() {
    JiraReplicationConfig config = createMockReplicationConfig();
    when(configRepo.findById(config.getId())).thenReturn(Optional.of(config));
    when(searchClient.search(any())).thenReturn(issues());
    var parent = ticket("MOCK-1", null);
    var child = ticket("MOCK-2", "MOCK-1");
    when(ticketRepo.findByScopeSign("MOCK_ORDER")).thenReturn(List.of(parent, child));

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
    JiraReplicationConfig config = createMockReplicationConfig();
    config.setInheritedFieldNames("customfield_10123");
    when(configRepo.findById(config.getId())).thenReturn(Optional.of(config));
    when(searchClient.search(any())).thenReturn(issues());
    var epic = ticket("MOCK-1", null, Map.of("customfield_10123", "Wartung"));
    var story = ticket("MOCK-2", "MOCK-1", Map.of());
    var task = ticket("MOCK-3", "MOCK-2", Map.of());
    when(ticketRepo.findByScopeSign("MOCK_ORDER")).thenReturn(List.of(epic, story, task));

    jiraReplicationService.runReplication(config.getId());

    assertThat(task.getCustomFieldsEffective())
        .containsEntry("customfield_10123", new ResolvedFieldValue("Wartung", "MOCK-1"));
    // from = null says "set on the ticket itself"
    assertThat(epic.getCustomFieldsEffective())
        .containsEntry("customfield_10123", new ResolvedFieldValue("Wartung", null));
  }

  @Test
  void testOwnValueBeatsTheInheritedOne() {
    JiraReplicationConfig config = createMockReplicationConfig();
    config.setInheritedFieldNames("customfield_10123");
    when(configRepo.findById(config.getId())).thenReturn(Optional.of(config));
    when(searchClient.search(any())).thenReturn(issues());
    var epic = ticket("MOCK-1", null, Map.of("customfield_10123", "Wartung"));
    var task = ticket("MOCK-2", "MOCK-1", Map.of("customfield_10123", "Migration"));
    when(ticketRepo.findByScopeSign("MOCK_ORDER")).thenReturn(List.of(epic, task));

    jiraReplicationService.runReplication(config.getId());

    assertThat(task.getCustomFieldsEffective())
        .containsEntry("customfield_10123", new ResolvedFieldValue("Migration", null));
  }

  @Test
  void testFieldWithoutAValueAnywhereInTheChainStaysAbsent() {
    JiraReplicationConfig config = createMockReplicationConfig();
    config.setInheritedFieldNames("customfield_10123");
    when(configRepo.findById(config.getId())).thenReturn(Optional.of(config));
    when(searchClient.search(any())).thenReturn(issues());
    var epic = ticket("MOCK-1", null, Map.of());
    var task = ticket("MOCK-2", "MOCK-1", Map.of());
    when(ticketRepo.findByScopeSign("MOCK_ORDER")).thenReturn(List.of(epic, task));

    jiraReplicationService.runReplication(config.getId());

    assertNull(task.getCustomFieldsEffective());
  }

  @Test
  @Timeout(value = 10, threadMode = SEPARATE_THREAD)
  void testCycleInTheParentChainDoesNotHang() {
    JiraReplicationConfig config = createMockReplicationConfig();
    config.setInheritedFieldNames("customfield_10123");
    when(configRepo.findById(config.getId())).thenReturn(Optional.of(config));
    when(searchClient.search(any())).thenReturn(issues());
    // parent_field_names allows any field as the parent source, so a chain pointing back at itself
    // is a shape the foreign system can hand over
    var one = ticket("MOCK-1", "MOCK-2", Map.of("customfield_10123", "Wartung"));
    var two = ticket("MOCK-2", "MOCK-1", Map.of());
    when(ticketRepo.findByScopeSign("MOCK_ORDER")).thenReturn(List.of(one, two));

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
    when(ticketRepo.findByScopeSignAndJiraId("MOCK_ORDER", 1001L))
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
    when(ticketRepo.findByScopeSignAndJiraId("MOCK_ORDER", 1001L))
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

  private static JiraTicket ticket(String key, String parentKey) {
    var ticket = new JiraTicket();
    ticket.setScopeSign("MOCK_ORDER");
    ticket.setKey(key);
    ticket.setParentKey(parentKey);
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
    config.setScopeSign("MOCK_ORDER");
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

  /**
   * An issue whose processing blows up the way an unexpected id does: {@code Long.parseLong} in
   * {@code upsertIfChanged} throws before the ticket is ever written.
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