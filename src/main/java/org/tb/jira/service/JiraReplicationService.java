package org.tb.jira.service;

import static java.util.Objects.requireNonNull;
import static java.util.function.Function.identity;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.tb.jira.domain.JiraReplicationConfig;
import org.tb.jira.domain.JiraTicket;
import org.tb.jira.persistence.JiraReplicationConfigRepository;
import org.tb.jira.persistence.JiraTicketRepository;
import org.tb.jira.service.JiraClient.JiraIssue;
import org.tb.jira.service.JiraClient.JiraSearchResult;

@Slf4j
@Service
@RequiredArgsConstructor
public class JiraReplicationService {

  private final JiraClient jiraClient;
  private final JiraReplicationConfigRepository configRepo;
  private final JiraTicketRepository ticketRepo;

  public List<JiraReplicationConfig> getEnabledReplications() {
    return configRepo.findByEnabledTrue();
  }

  public void runReplication(long replicationId) {
    JiraReplicationConfig cfg = configRepo.findById(replicationId)
        .orElseThrow(() -> new IllegalArgumentException("Unknown replication config id=" + replicationId));
    runReplication(cfg);
  }

  public void runReplication(JiraReplicationConfig cfg) {
    requireNonNull(cfg.getBaseUrl(), "baseUrl");
    requireNonNull(cfg.getUsername(), "username");
    requireNonNull(cfg.getPassword(), "password");
    requireNonNull(cfg.getJql(), "jql");

    int pageSize = cfg.getPageSize() != null && cfg.getPageSize() > 0 ? cfg.getPageSize() : 100;

    log.info("Starting JIRA replication: id={}, name={}, customerorderSign={}, pageSize={}",
        cfg.getId(), cfg.getName(), cfg.getCustomerorderSign(), pageSize);

    // Note: We do not modify JQL per requirement. We filter during upsert by updated timestamp.
    var baseline = ticketRepo.findMaxUpdatedTs(cfg.getCustomerorderSign());
    if (baseline == null) baseline = cfg.getLastMaxUpdated();
    int startAt = 0;
    int processed = 0;
    LocalDateTime newMax = baseline;

    var fields = buildFieldList(cfg);
    var jql = appendMaxUpdated(cfg.getJql(), baseline);

    while (true) {
      JiraSearchResult result = jiraClient.searchIssues(
          cfg.getBaseUrl(), cfg.getUsername(), cfg.getPassword(), jql, startAt, pageSize, fields);

      if (result == null || result.getIssues() == null || result.getIssues().isEmpty()) {
        break;
      }

      for (var issue : result.getIssues()) {
        try {
          var changed = upsertIfChanged(cfg, issue);
          if (changed) {
            processed++;
          }
          var updated = toDateTime(getString(issue.getFields(), "updated"));
          if (updated != null && (newMax == null || updated.isAfter(newMax))) newMax = updated;
        } catch (Exception ex) {
          log.error(
              "Failed to process issue {} in replication {}: {}",
              issue.getKey(), cfg.getName(), ex.getMessage(), ex
          );
        }
      }

      startAt += result.getIssues().size();
      if (startAt >= result.getTotal()) {
        break;
      }
    }

    resolveTopLevelKeys();

    // Update last_max_updated if progressed
    if (newMax != null && (cfg.getLastMaxUpdated() == null || newMax.isAfter(cfg.getLastMaxUpdated()))) {
      cfg.setLastMaxUpdated(newMax);
      configRepo.save(cfg);
    }

    log.info("Finished JIRA replication: name={}, processed={} (updated/inserted)", cfg.getName(), processed);
  }

  private void resolveTopLevelKeys() {
    var ticketsByKey = ticketRepo.findAll().stream().collect(Collectors.toMap(JiraTicket::getKey, identity()));
    var updatedChildren = new LinkedList<JiraTicket>();

    for(var ticket : ticketsByKey.values()) {
      var parent = ticket;
      while (parent.getParentKey() != null && ticketsByKey.containsKey(parent.getParentKey())) {
        parent = ticketsByKey.get(parent.getParentKey());
      }
      if(Objects.equals(parent.getKey(), ticket.getTopLevelKey())) continue;
      ticket.setTopLevelKey(parent.getKey());
      updatedChildren.add(ticket);
    }

    log.info("Resolved top-level keys for {} tickets", updatedChildren.size());
    ticketRepo.saveAll(updatedChildren);
  }

  private String appendMaxUpdated(String jql, LocalDateTime lastMaxUpdated) {
    if (lastMaxUpdated == null) return jql;
    if (jql == null || jql.isBlank()) return "updated >= '" + lastMaxUpdated.toLocalDate() + "'";
    return "(" + jql + ") AND updated >= '" + lastMaxUpdated.toLocalDate() + "'";
  }

  private List<String> buildFieldList(JiraReplicationConfig cfg) {
    List<String> fields = new ArrayList<>();
    fields.add("summary");
    fields.add("issuetype");
    fields.add("labels");
    fields.add("created");
    fields.add("updated");
    fields.add("parent");
    if (cfg.getParentFieldNames() != null && !cfg.getParentFieldNames().isBlank()) {
      for (String f : cfg.getParentFieldNames().split(",")) {
        String trimmed = f.trim();
        if (!trimmed.isEmpty()) fields.add(trimmed);
      }
    }
    return fields;
  }

  private boolean upsertIfChanged(JiraReplicationConfig cfg, JiraIssue issue) {
    long jiraId = Long.parseLong(issue.getId());

    var existing = ticketRepo.findByCustomerorderSignAndJiraId(cfg.getCustomerorderSign(), jiraId).orElse(null);
    var fields = issue.getFields();
    var updatedTs = toDateTime(getString(fields, "updated"));

    // Global baseline filter per requirement
    if (existing != null) {
      if (existing.getUpdatedTs() != null && (updatedTs == null || !updatedTs.isAfter(existing.getUpdatedTs()))) {
        // no change
        return false;
      }
    }

    var t = existing != null ? existing : new JiraTicket();
    t.setCustomerorderSign(cfg.getCustomerorderSign());
    t.setJiraId(jiraId);
    t.setKey(issue.getKey());
    t.setSummary(safe(getString(fields, "summary"), 1024));
    t.setIssueType(getIssueTypeName(fields));
    t.setLabels(getString(fields, "labels", ","));
    t.setCreatedTs(toDateTime(getString(fields, "created")));
    t.setUpdatedTs(updatedTs);

    String parentKey = extractParentKey(cfg, fields);
    t.setParentKey(parentKey);

    ticketRepo.save(t);
    return true;
  }

  private String extractParentKey(JiraReplicationConfig cfg, Map<String, Object> fields) {
    String result = null;

    // Standard parent
    Object parentObj = fields.get("parent");
    if (parentObj instanceof Map<?, ?> pm) {
      result = getString(pm, "key");
    }
    if(result != null && !result.isBlank()) return result;

    // Custom fields by names that contain a key string
    if (cfg.getParentFieldNames() != null && !cfg.getParentFieldNames().isBlank()) {
      for (String f : cfg.getParentFieldNames().split(",")) {
        String trimmed = f.trim();
        if (trimmed.isEmpty()) continue;
        result = getString(fields, trimmed);
        if(result != null && !result.isBlank()) return result;
      }
    }
    return null;
  }

  private static String safe(String s, int max) {
    if (s == null) return null;

    // Nicht-ISO-8859-1-Zeichen entfernen (Latin-1: U+0000 .. U+00FF)
    var sb = new StringBuilder(s.length());
    s.trim().codePoints()
        .filter(cp -> cp <= 0x00FF)
        .forEach(sb::appendCodePoint);
    s = sb.toString();

    if (s.length() <= max) return s;
    return s.substring(0, max);
  }

  private LocalDateTime toDateTime(String dateTimeValue) {
    if (dateTimeValue.isBlank()) {
      return null;
    }
    // e.g. 2024-05-31T13:43:33
    return LocalDateTime.parse(dateTimeValue.substring(0, 19));
  }

  private static String getString(Map<?, ?> map, String key) {
    if (map == null) return null;
    Object v = map.get(key);
    if (v == null) return null;
    if (v instanceof String s) return s;
    return String.valueOf(v);
  }

  private static String getString(Map<?, ?> map, String key, String delimiter) {
    if (map == null) return null;
    Object v = map.get(key);
    if (v == null) return null;
    if (v instanceof String s) return s;
    if (v instanceof List<?> l) return l.stream().map(Object::toString).map(String::trim).collect(Collectors.joining(delimiter));
    return String.valueOf(v);
  }

  private static String getIssueTypeName(Map<String, Object> fields) {
    Object it = fields.get("issuetype");
    if (it instanceof Map<?,?> m) {
      Object name = m.get("name");
      if (name instanceof String s) return s;
    }
    return null;
  }
}
