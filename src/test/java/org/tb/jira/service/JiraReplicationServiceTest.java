package org.tb.jira.service;

import static java.time.temporal.ChronoUnit.SECONDS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.anyList;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.util.ReflectionUtils.findField;
import static org.springframework.util.ReflectionUtils.makeAccessible;
import static org.springframework.util.ReflectionUtils.setField;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.boot.test.context.SpringBootTest;
import org.tb.common.domain.AuditedEntity;
import org.tb.jira.domain.JiraReplicationConfig;
import org.tb.jira.domain.JiraTicket;
import org.tb.jira.persistence.JiraReplicationConfigRepository;
import org.tb.jira.persistence.JiraTicketRepository;
import org.tb.jira.service.JiraClient.JiraIssue;
import org.tb.jira.service.JiraClient.JiraSearchResult;

@SpringBootTest
class JiraReplicationServiceTest {

  @Mock
  private JiraClient jiraClient;

  @Mock
  private JiraReplicationConfigRepository configRepo;

  @Mock
  private JiraTicketRepository ticketRepo;

  @InjectMocks
  private JiraReplicationService jiraReplicationService;

  public JiraReplicationServiceTest() {
    MockitoAnnotations.openMocks(this);
  }

  @Test
  void testRunReplicationWithValidConfig() {
    JiraReplicationConfig config = createMockReplicationConfig();
    when(configRepo.findById(config.getId())).thenReturn(Optional.of(config));
    when(ticketRepo.findMaxUpdatedTs(config.getCustomerorderSign())).thenReturn(null);
    when(jiraClient.searchIssues(any(), any(), any(), any(), anyInt(), anyInt(), any()))
        .thenReturn(createMockJiraSearchResult());

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
    when(ticketRepo.findMaxUpdatedTs(config.getCustomerorderSign())).thenReturn(null);
    when(jiraClient.searchIssues(any(), any(), any(), any(), anyInt(), anyInt(), any()))
        .thenReturn(emptyResult());

    jiraReplicationService.runReplication(config.getId());

    verify(ticketRepo, times(1)).saveAll(anyList());
    verify(configRepo, times(1)).findById(config.getId());
  }

  @Test
  void testRunReplicationUpdatesLastMaxUpdated() {
    LocalDateTime mockUpdated = LocalDateTime.now().truncatedTo(SECONDS);
    JiraReplicationConfig config = createMockReplicationConfig();
    when(configRepo.findById(config.getId())).thenReturn(Optional.of(config));
    when(ticketRepo.findMaxUpdatedTs(config.getCustomerorderSign())).thenReturn(null);
    when(jiraClient.searchIssues(any(), any(), any(), any(), anyInt(), anyInt(), any()))
        .thenReturn(createMockJiraSearchResult(mockUpdated));

    jiraReplicationService.runReplication(config.getId());

    verify(configRepo, times(1)).save(config);
    assertEquals(mockUpdated, config.getLastMaxUpdated());
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

  private JiraSearchResult createMockJiraSearchResult() {
    return createMockJiraSearchResult(LocalDateTime.now());
  }

  private JiraSearchResult createMockJiraSearchResult(LocalDateTime updated) {
    JiraIssue issue = new JiraIssue();
    issue.setId("1001");
    issue.setKey("MOCK-1");
    issue.setFields(Map.of(
        "summary", "Mock Summary",
        "updated", updated.toString(),
        "created", LocalDateTime.now().toString(),
        "issuetype", Map.of("name", "Task")
    ));
    return result(issue);
  }

  private static JiraSearchResult result(JiraIssue issue) {
    var result = new JiraSearchResult();
    result.setIssues(List.of(issue));
    result.setMaxResults(1);
    result.setTotal(1);
    return result;
  }

  private static JiraSearchResult emptyResult() {
    var result = new JiraSearchResult();
    result.setIssues(List.of());
    return result;
  }

}