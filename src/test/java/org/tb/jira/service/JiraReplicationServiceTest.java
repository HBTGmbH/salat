package org.tb.jira.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
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

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.client.RestClientException;
import org.tb.common.domain.AuditedEntity;
import org.tb.common.test.FixedClock;
import org.tb.common.util.DateTimeUtils;
import org.tb.jira.domain.JiraReplicationConfig;
import org.tb.jira.domain.JiraTicket;
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
  void testTopLevelKeysAreResolvedWithinTheCustomerOrderOnly() {
    JiraReplicationConfig config = createMockReplicationConfig();
    when(configRepo.findById(config.getId())).thenReturn(Optional.of(config));
    when(searchClient.search(any())).thenReturn(issues());
    var parent = ticket("MOCK-1", null);
    var child = ticket("MOCK-2", "MOCK-1");
    when(ticketRepo.findByCustomerorderSign("MOCK_ORDER")).thenReturn(List.of(parent, child));

    jiraReplicationService.runReplication(config.getId());

    // an issue key is only unique per customer order, so the parent chain must not be walked
    // across all tickets - identical keys under another order would collide
    verify(ticketRepo, never()).findAll();
    assertEquals("MOCK-1", child.getTopLevelKey());
    assertEquals("MOCK-1", parent.getTopLevelKey());
  }

  private static JiraTicket ticket(String key, String parentKey) {
    var ticket = new JiraTicket();
    ticket.setCustomerorderSign("MOCK_ORDER");
    ticket.setKey(key);
    ticket.setParentKey(parentKey);
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
    config.setCustomerorderSign("MOCK_ORDER");
    return config;
  }

  private static JiraIssue mockIssue() {
    return mockIssue(LocalDateTime.of(2026, 6, 25, 15, 5, 0));
  }

  private static JiraIssue mockIssue(LocalDateTime updated) {
    JiraIssue issue = new JiraIssue();
    issue.setId("1001");
    issue.setKey("MOCK-1");
    issue.setFields(Map.of(
        "summary", "Mock Summary",
        "updated", updated.toString(),
        "created", DateTimeUtils.now().toString(),
        "issuetype", Map.of("name", "Task")
    ));
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