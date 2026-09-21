package org.tb.jira.service;

import static java.util.Objects.requireNonNull;
import static java.util.function.Function.identity;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.tb.jira.domain.JiraFieldConfig;
import org.tb.jira.domain.JiraReplicationConfig;
import org.tb.jira.domain.JiraTicket;
import org.tb.jira.domain.ResolvedFieldValue;
import org.tb.jira.persistence.JiraReplicationConfigRepository;
import org.tb.jira.persistence.JiraTicketRepository;

@Slf4j
@Service
@RequiredArgsConstructor
public class JiraReplicationService {

  private final JiraSearchClients searchClients;
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
    var fieldConfig = JiraFieldConfig.from(cfg);

    log.info("Starting JIRA replication: id={}, name={}, scopeSign={}, apiFlavor={}, "
            + "pageSize={}, additionalFields={}, inheritedFields={}",
        cfg.getId(), cfg.getName(), cfg.getScopeSign(), cfg.getApiFlavor(), pageSize,
        fieldConfig.fieldPaths(), fieldConfig.inheritedFieldPaths());

    // Note: We do not modify JQL per requirement. We filter during upsert by updated timestamp.
    // The baseline comes solely from the config watermark, which is only written after a run has
    // completed (see below). Deriving it from the stored tickets would let a single ticket saved by
    // an aborted run raise the bar for all other tickets of that customer order, permanently
    // skipping the ones that were never fetched. Re-fetching is harmless — upsertIfChanged is
    // idempotent — so the failure mode has to be "fetch again", never "skip".
    var baseline = cfg.getLastMaxUpdated();
    int processed = 0;
    LocalDateTime newMax = baseline;

    var fields = buildFieldList(cfg, fieldConfig);
    var jql = appendMaxUpdated(cfg.getJql(), baseline);
    var request = new JiraSearchRequest(
        cfg.getBaseUrl(), cfg.getUsername(), cfg.getPassword(), jql, fields, pageSize);

    // Which of the configured fields any answer actually carried. JIRA either rejects an unknown
    // field id with HTTP 400 — the run then fails visibly — or drops it silently, and that second
    // case is what this catches: a typo would otherwise show up as a field that simply stays empty.
    var answeredFields = new HashSet<String>();
    int fetched = 0;

    // The client pages lazily, so a failure on a later page surfaces from here and aborts the run
    // before the watermark below is written.
    var issues = searchClients.forFlavor(cfg.getApiFlavor()).search(request);
    while (issues.hasNext()) {
      var issue = issues.next();
      try {
        fetched++;
        if (issue.getFields() != null) answeredFields.addAll(issue.getFields().keySet());
        var changed = upsertIfChanged(cfg, fieldConfig, issue);
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
    warnAboutUnansweredFields(cfg, fieldConfig, answeredFields, fetched);

    resolveParentChains(cfg, fieldConfig);

    // Update last_max_updated if progressed
    if (newMax != null && (cfg.getLastMaxUpdated() == null || newMax.isAfter(cfg.getLastMaxUpdated()))) {
      cfg.setLastMaxUpdated(newMax);
      configRepo.save(cfg);
    }

    log.info("Finished JIRA replication: name={}, processed={} (updated/inserted)", cfg.getName(), processed);
  }

  /**
   * Walks the parent chains within the scope of this replication, once per ticket: it writes the
   * top-level key and resolves the inherited fields (#881) in the same pass. Scoped, not global: an
   * issue key is only unique per scope — two JIRA instances can hand out the same key — and a parent
   * chain never crosses that boundary anyway. Since #1025 a scope can be one suborder rather than a
   * whole order, and the chain stops at that boundary just as it used to stop at the order's.
   *
   * <p>Every ticket is resolved again on every run, not just the ones the run touched. That is what
   * makes the inheritance heal itself when a value is set at a higher level later on: the ancestor
   * changes, the children do not, and JIRA reports only the ancestor as updated.
   */
  private void resolveParentChains(JiraReplicationConfig cfg, JiraFieldConfig fieldConfig) {
    var scopeSign = cfg.getScopeSign();
    var ticketsByKey = ticketRepo.findByScopeSign(scopeSign).stream()
        .collect(Collectors.toMap(JiraTicket::getKey, identity()));
    var updatedChildren = new LinkedList<JiraTicket>();

    for(var ticket : ticketsByKey.values()) {
      var effective = ownValuesOf(ticket, fieldConfig);

      // A chain is data from a foreign system and nothing there rules out a cycle - parent_field_names
      // even allows any field to act as the parent source. Without the visited set an issue pointing
      // back at one of its own ancestors would spin here forever.
      var visited = new HashSet<String>();
      visited.add(ticket.getKey());
      var parent = ticket;
      while (parent.getParentKey() != null && visited.add(parent.getParentKey())
          && ticketsByKey.containsKey(parent.getParentKey())) {
        parent = ticketsByKey.get(parent.getParentKey());
        inheritMissingValues(effective, parent, fieldConfig);
      }

      // Absent rather than empty: a json column cannot hold an empty string, and JSON_EXTRACT on
      // NULL answers NULL instead of aborting the statement around it.
      var resolved = effective.isEmpty() ? null : effective;
      if (Objects.equals(parent.getKey(), ticket.getTopLevelKey())
          && Objects.equals(resolved, ticket.getCustomFieldsEffective())) continue;
      ticket.setTopLevelKey(parent.getKey());
      ticket.setCustomFieldsEffective(resolved);
      updatedChildren.add(ticket);
    }

    log.info("Resolved parent chains for {} changed tickets of scope {}",
        updatedChildren.size(), scopeSign);
    ticketRepo.saveAll(updatedChildren);
  }

  /** What the ticket carries itself — an own value beats an inherited one, so it is filled first. */
  private static Map<String, ResolvedFieldValue> ownValuesOf(JiraTicket ticket, JiraFieldConfig fieldConfig) {
    var effective = new LinkedHashMap<String, ResolvedFieldValue>();
    for (var field : fieldConfig.inheritedFieldPaths()) {
      var own = storedValue(ticket, field);
      if (own != null) effective.put(field, new ResolvedFieldValue(own, null));
    }
    return effective;
  }

  /** Fills the fields still open from this ancestor, so the nearest one that has a value wins. */
  private static void inheritMissingValues(Map<String, ResolvedFieldValue> effective,
                                           JiraTicket ancestor, JiraFieldConfig fieldConfig) {
    for (var field : fieldConfig.inheritedFieldPaths()) {
      if (effective.containsKey(field)) continue;
      var value = storedValue(ancestor, field);
      if (value != null) effective.put(field, new ResolvedFieldValue(value, ancestor.getKey()));
    }
  }

  private static String storedValue(JiraTicket ticket, String field) {
    var customFields = ticket.getCustomFields();
    return customFields != null ? customFields.get(field) : null;
  }

  /**
   * A configured field that no answer of this run carried at all. JIRA Server drops an unknown field
   * id without a word, so the only sign of a typo is that the field never arrives.
   */
  private void warnAboutUnansweredFields(JiraReplicationConfig cfg, JiraFieldConfig fieldConfig,
                                         Set<String> answeredFields, int fetchedIssues) {
    // Without an answer to look at, every field is trivially missing.
    if (fieldConfig.isEmpty() || fetchedIssues == 0) return;
    var unanswered = fieldConfig.requestKeys().stream()
        .filter(field -> !answeredFields.contains(field))
        .toList();
    if (unanswered.isEmpty()) return;
    log.warn("Configured JIRA fields {} were not part of any of the {} issues answered in "
            + "replication {} - check the field ids of that config",
        unanswered, fetchedIssues, cfg.getName());
  }

  private String appendMaxUpdated(String jql, LocalDateTime lastMaxUpdated) {
    if (lastMaxUpdated == null) return jql;
    if (jql == null || jql.isBlank()) return "updated >= '" + lastMaxUpdated.toLocalDate() + "'";
    return "(" + jql + ") AND updated >= '" + lastMaxUpdated.toLocalDate() + "'";
  }

  private List<String> buildFieldList(JiraReplicationConfig cfg, JiraFieldConfig fieldConfig) {
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
    // JIRA only accepts top-level ids here; a path is requested by its head.
    for (String f : fieldConfig.requestKeys()) {
      if (!fields.contains(f)) fields.add(f);
    }
    return fields;
  }

  private boolean upsertIfChanged(JiraReplicationConfig cfg, JiraFieldConfig fieldConfig, JiraIssue issue) {
    long jiraId = Long.parseLong(issue.getId());

    var existing = ticketRepo.findByScopeSignAndJiraId(cfg.getScopeSign(), jiraId).orElse(null);
    var fields = issue.getFields();
    var updatedTs = toDateTime(getString(fields, "updated"));

    // Global baseline filter per requirement
    if (existing != null && Objects.equals(existing.getFieldConfigHash(), fieldConfig.hash())) {
      // Only while the ticket was written with the field list that is configured now. After a change
      // to that list JIRA still reports the ticket as unchanged - its `updated` has not moved - so
      // this is the point where the new fields would never reach an already replicated ticket.
      if (existing.getUpdatedTs() != null && (updatedTs == null || !updatedTs.isAfter(existing.getUpdatedTs()))) {
        // no change
        return false;
      }
    }

    var t = existing != null ? existing : new JiraTicket();
    t.setScopeSign(cfg.getScopeSign());
    t.setJiraId(jiraId);
    t.setKey(issue.getKey());
    t.setSummary(safe(getString(fields, "summary"), 1024));
    t.setIssueType(getIssueTypeName(fields));
    t.setLabels(getString(fields, "labels", ","));
    t.setCreatedTs(toDateTime(getString(fields, "created")));
    t.setUpdatedTs(updatedTs);

    String parentKey = extractParentKey(cfg, fields);
    t.setParentKey(parentKey);

    t.setCustomFields(extractCustomFields(fieldConfig, fields));
    t.setFieldConfigHash(fieldConfig.hash());

    ticketRepo.save(t);
    return true;
  }

  /**
   * The configured fields as this ticket carries them (#881). {@code null} rather than an empty map
   * when nothing is configured or nothing is set — see {@link JiraTicket#getCustomFields()}.
   */
  private static Map<String, String> extractCustomFields(JiraFieldConfig fieldConfig, Map<String, Object> fields) {
    if (fieldConfig.isEmpty() || fields == null) return null;
    var values = new TreeMap<String, String>();
    for (var path : fieldConfig.fieldPaths()) {
      var value = JiraFieldValues.toValue(fields, path);
      if (value != null) values.put(path, value);
    }
    return values.isEmpty() ? null : values;
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
    if (dateTimeValue == null || dateTimeValue.isBlank()) {
      return null;
    }
    // e.g. 2024-05-31T13:43:33.000+0200; strip fraction/offset, but tolerate a
    // value without a seconds field (LocalDateTime.parse accepts yyyy-MM-ddTHH:mm).
    String s = dateTimeValue.length() >= 19 ? dateTimeValue.substring(0, 19) : dateTimeValue;
    return LocalDateTime.parse(s);
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
