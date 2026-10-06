package de.hbt.salat.jira.service;

import static java.util.Objects.requireNonNull;
import static java.util.function.Function.identity;
import static de.hbt.salat.jira.domain.JiraReplicationRun.Status.FAILED;
import static de.hbt.salat.jira.domain.JiraReplicationRun.Status.SUCCEEDED;
import static de.hbt.salat.jira.service.JiraCredentialRedaction.redacted;

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
import de.hbt.salat.auth.domain.Authorized;
import de.hbt.salat.jira.domain.JiraAuthMethod;
import de.hbt.salat.jira.domain.JiraFieldConfig;
import de.hbt.salat.jira.domain.JiraReplicationConfig;
import de.hbt.salat.jira.domain.JiraReplicationRun.Trigger;
import de.hbt.salat.jira.domain.JiraTicket;
import de.hbt.salat.jira.domain.ResolvedFieldValue;
import de.hbt.salat.jira.persistence.JiraReplicationConfigRepository;
import de.hbt.salat.jira.persistence.JiraTicketRepository;

/**
 * Replicates the tickets of one scope from JIRA.
 *
 * <p>Management only, like everything around the replications. The hourly run passes as the job
 * user, which {@code AuthorizedUser#initForJob} makes a manager (→ ADR-0006).
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Authorized(requiresManager = true)
public class JiraReplicationService {

  private final JiraSearchClients searchClients;
  private final JiraReplicationConfigRepository configRepo;
  private final JiraTicketRepository ticketRepo;
  private final JiraWorklogSyncService worklogSyncService;
  private final JiraReplicationRunService runService;
  private final JiraScopes scopes;

  public List<JiraReplicationConfig> getEnabledReplications() {
    return configRepo.findByEnabledTrue();
  }

  /**
   * Runs a replication and records it in the run history (#1282): opens the run, which is also the
   * lock against a second run of the same replication, and writes its outcome at the end. The way
   * of the scheduled run and of the REST interface.
   *
   * @throws de.hbt.salat.common.exception.BusinessRuleException when the replication is still
   *     running — no row is written then, the caller decides what that means
   */
  public JiraReplicationResult runRecorded(long replicationId, Trigger trigger) {
    var run = runService.startRun(replicationId, trigger);
    return continueRun(run.getId(), replicationId);
  }

  /**
   * Carries out a run that is already open (#1282) — the part of a manual run that goes on in the
   * background, after the row was written on the request thread. Ids rather than entities: the row
   * is read afresh at the end anyway, and between start and end lies the whole run.
   */
  public JiraReplicationResult continueRun(long runId, long replicationId) {
    JiraReplicationConfig cfg = null;
    JiraReplicationResult result;
    try {
      cfg = configRepo.findById(replicationId)
          .orElseThrow(() -> new IllegalArgumentException("Unknown replication config id=" + replicationId));
      result = runReplication(cfg);
    } catch (RuntimeException ex) {
      var password = cfg != null ? cfg.getPassword() : null;
      runService.finishRun(runId, FAILED, "Abgebrochen: " + redacted(ex, password));
      throw ex;
    }
    runService.finishRun(runId, result.succeeded() ? SUCCEEDED : FAILED, result.summary());
    return result;
  }

  public JiraReplicationResult runReplication(long replicationId) {
    JiraReplicationConfig cfg = configRepo.findById(replicationId)
        .orElseThrow(() -> new IllegalArgumentException("Unknown replication config id=" + replicationId));
    return runReplication(cfg);
  }

  public JiraReplicationResult runReplication(JiraReplicationConfig cfg) {
    requireNonNull(cfg.getBaseUrl(), "baseUrl");
    if (cfg.getAuthMethod() == JiraAuthMethod.BASIC) {
      requireNonNull(cfg.getUsername(), "username");
    }
    requireNonNull(cfg.getPassword(), "password");
    requireNonNull(cfg.getJql(), "jql");

    int pageSize = cfg.getPageSize() != null && cfg.getPageSize() > 0 ? cfg.getPageSize() : 100;
    var fieldConfig = JiraFieldConfig.from(cfg);
    // The scope as the order tree names it now (#1323), for the log.
    var scopeSign = scopes.signOf(cfg.getCustomerorderId(), cfg.getSuborderId());

    log.info("Starting JIRA replication: id={}, name={}, scopeSign={}, apiFlavor={}, "
            + "pageSize={}, additionalFields={}, inheritedFields={}",
        cfg.getId(), cfg.getName(), scopeSign, cfg.getApiFlavor(), pageSize,
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

    // What the run could not store (#841). The oldest of those timestamps caps the watermark below,
    // and an issue that did not even carry a readable one holds it where it was.
    int failed = 0;
    LocalDateTime oldestFailure = null;
    boolean failureWithoutTimestamp = false;

    var fields = buildFieldList(cfg, fieldConfig);
    var jql = appendMaxUpdated(cfg.getJql(), baseline);
    var request = new JiraSearchRequest(
        cfg.getBaseUrl(), JiraCredentials.of(cfg), jql, fields, pageSize);

    // Which of the configured fields any answer actually carried. JIRA either rejects an unknown
    // field id with HTTP 400 — the run then fails visibly — or drops it silently, and that second
    // case is what this catches: a typo would otherwise show up as a field that simply stays empty.
    var answeredFields = new HashSet<String>();
    int fetched = 0;

    // Only a run without a watermark sees every ticket the JQL matches, so only such a run can tell
    // which of the stored ones it no longer matches (#1167). A run from the watermark sees nothing
    // but the changed ones and collects nothing.
    Set<Long> seenJiraIds = baseline == null ? new HashSet<>() : null;

    // The client pages lazily, so a failure on a later page surfaces from here and aborts the run
    // before the watermark below is written.
    var issues = searchClients.forFlavor(cfg.getApiFlavor()).search(request);
    while (issues.hasNext()) {
      var issue = issues.next();
      try {
        fetched++;
        if (issue.getFields() != null) answeredFields.addAll(issue.getFields().keySet());
        long jiraId = Long.parseLong(issue.getId());
        if (seenJiraIds != null) seenJiraIds.add(jiraId);
        var changed = upsertIfChanged(cfg, fieldConfig, jiraId, issue);
        if (changed) {
          processed++;
        }
        var updated = toDateTime(getString(issue.getFields(), "updated"));
        if (updated != null && (newMax == null || updated.isAfter(newMax))) newMax = updated;
      } catch (Exception ex) {
        failed++;
        log.error(
            "Failed to process issue {} in replication {}: {}",
            issue.getKey(), cfg.getName(), ex.getMessage(), ex
        );
        var failedUpdated = readUpdated(issue);
        if (failedUpdated == null) failureWithoutTimestamp = true;
        else if (oldestFailure == null || failedUpdated.isBefore(oldestFailure)) {
          oldestFailure = failedUpdated;
        }
      }
    }
    warnAboutUnansweredFields(cfg, fieldConfig, answeredFields, fetched);

    // Loaded once for both steps. The removal comes first, so that a removed parent no longer
    // passes values on in this very run.
    var tickets = new ArrayList<>(ticketRepo.findInScope(cfg.getCustomerorderId(), cfg.getSuborderId()));
    if (seenJiraIds != null) removeUnseenTickets(cfg, scopeSign, tickets, seenJiraIds, failed);

    resolveParentChains(scopeSign, fieldConfig, tickets);

    // Update last_max_updated if progressed - but never past an issue this run failed to store
    newMax = capBelowFailures(newMax, baseline, oldestFailure, failureWithoutTimestamp);
    boolean advanced = newMax != null && (baseline == null || newMax.isAfter(baseline));
    if (advanced) {
      cfg.setLastMaxUpdated(newMax);
      configRepo.save(cfg);
    }
    warnAboutFailedIssues(cfg, failed, advanced, newMax);

    log.info("Finished JIRA replication: name={}, processed={} (updated/inserted)", cfg.getName(), processed);

    // Last, and with its own safety net (#1007): the worklogs are written against the tickets this
    // run has just replicated, and a failure while writing them must not take the watermark above
    // with it. Re-fetching the same tickets next time is harmless; losing the watermark is not.
    String worklogSyncError = null;
    try {
      worklogSyncService.sync(cfg);
    } catch (Exception ex) {
      log.error("Worklog sync failed after the replication of {}: {}", cfg.getName(), ex.getMessage(), ex);
      worklogSyncError = redacted(ex, cfg.getPassword());
    }
    return new JiraReplicationResult(fetched, processed, failed, worklogSyncError);
  }

  /**
   * Removes the tickets of this scope that a complete run did not see (#1167): moved to a project the
   * JQL does not match, or left out by a JQL that was narrowed. Neither case is reported by JIRA —
   * the ticket is simply missing from the answer.
   *
   * <p>Only after a clean run. An aborted one never gets here, its exception is already on the way
   * out. One issue that could not be processed is enough to skip it: the ids seen are then not
   * reliable, not least because parsing the id may itself have been the failure.
   *
   * <p>The stale tickets are picked from the scope's tickets in memory and deleted one by one by
   * their primary key, rather than with a {@code not in} over every id the run saw: that list grows
   * with the scope, the stale ones are usually a handful. They are also dropped from
   * {@code tickets}, which the parent chains are resolved on next.
   *
   * <p>The count goes to the log with the replication and its scope. A JQL narrowed by mistake shows
   * up here first. Bookings are not affected: their ticket reference is free text, not a foreign key
   * (#982). Nor are the worklogs written on these tickets — see {@link JiraWorklogSyncService}.
   */
  private void removeUnseenTickets(JiraReplicationConfig cfg, String scopeSign, List<JiraTicket> tickets,
                                   Set<Long> seenJiraIds, int failed) {
    if (failed > 0) {
      log.warn("Replication {} fetched everything the JQL matches, but {} issues could not be "
          + "processed - tickets of scope {} no longer matched are not removed in this run",
          cfg.getName(), failed, scopeSign);
      return;
    }
    // A ticket without a JIRA id was maintained by hand (#1386): JIRA never sends it, so not seeing
    // it says nothing. It stays until somebody deletes it.
    var unseen = tickets.stream()
        .filter(ticket -> ticket.getJiraId() != null && !seenJiraIds.contains(ticket.getJiraId()))
        .toList();
    if (!unseen.isEmpty()) {
      ticketRepo.deleteAll(unseen);
      tickets.removeAll(unseen);
    }
    log.info("Removed {} tickets of scope {} no longer matched by the JQL of replication {}",
        unseen.size(), scopeSign, cfg.getName());
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
  private void resolveParentChains(String scopeSign, JiraFieldConfig fieldConfig, List<JiraTicket> tickets) {
    var ticketsByKey = tickets.stream()
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
   * The watermark must not move past an issue the run could not store (#841). The next run asks JIRA
   * for {@code updated >= watermark}, so an issue below that bar is never offered again: the run
   * counts as successful and the ticket is missing from the database for good. A failure therefore
   * pulls the new maximum back under the oldest failed issue — the next run fetches that window once
   * more, which costs nothing because {@link #upsertIfChanged} is idempotent.
   *
   * <p>An issue whose own {@code updated} could not be read gives no position to cap at, so the
   * watermark stays where it was. Both cases mean the same for a <em>permanently</em> failing issue:
   * the watermark stops advancing and every run fetches the same window again. That is the right
   * direction — fetch again rather than skip — and it makes the failure something that has to be
   * dealt with instead of one that disappears quietly. {@link #warnAboutFailedIssues} says so in the
   * log.
   */
  private static LocalDateTime capBelowFailures(LocalDateTime newMax, LocalDateTime baseline,
                                                LocalDateTime oldestFailure, boolean failureWithoutTimestamp) {
    if (failureWithoutTimestamp) return baseline;
    if (oldestFailure == null || newMax == null || newMax.isBefore(oldestFailure)) return newMax;
    // A second is enough of a step back: the JQL filter is written with day granularity anyway, so
    // the next run asks from the failed issue's own day on.
    return oldestFailure.minusSeconds(1);
  }

  /**
   * The failures of a run as one line, next to the single {@code ERROR} per issue: the count alone
   * says whether a run needs attention, and what it did to the watermark says how urgently.
   */
  private void warnAboutFailedIssues(JiraReplicationConfig cfg, int failed, boolean advanced,
                                     LocalDateTime watermark) {
    if (failed == 0) return;
    if (advanced) {
      log.warn("{} issues of replication {} could not be processed - the watermark was held back to "
              + "{} so that the next run asks for them again",
          failed, cfg.getName(), watermark);
    } else {
      log.warn("{} issues of replication {} could not be processed - the watermark does not advance "
              + "and every run fetches the same window again until the cause is fixed",
          failed, cfg.getName());
    }
  }

  /** The issue's {@code updated}, or {@code null} when it is missing or not readable as a date. */
  private LocalDateTime readUpdated(JiraIssue issue) {
    try {
      return toDateTime(getString(issue.getFields(), "updated"));
    } catch (Exception ex) {
      return null;
    }
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

  private boolean upsertIfChanged(JiraReplicationConfig cfg, JiraFieldConfig fieldConfig, long jiraId,
                                  JiraIssue issue) {
    // A ticket maintained by hand under the same key is taken over (#1386): the replication that now
    // covers the scope fills in its JIRA id and keeps it from here on.
    var existing = ticketRepo.findInScopeByJiraId(cfg.getCustomerorderId(), cfg.getSuborderId(), jiraId)
        .or(() -> ticketRepo.findManualInScopeByKey(cfg.getCustomerorderId(), cfg.getSuborderId(), issue.getKey()))
        .orElse(null);
    var fields = issue.getFields();
    var updatedTs = toDateTime(getString(fields, "updated"));

    // Global baseline filter per requirement. A ticket this replication does not maintain yet — one
    // taken over, or left behind by a deleted replication — is written even when JIRA reports it as
    // unchanged, or it would never point at the replication that keeps it now.
    if (existing != null && isMaintainedBy(existing, cfg)
        && Objects.equals(existing.getFieldConfigHash(), fieldConfig.hash())) {
      // Only while the ticket was written with the field list that is configured now. After a change
      // to that list JIRA still reports the ticket as unchanged - its `updated` has not moved - so
      // this is the point where the new fields would never reach an already replicated ticket.
      if (existing.getUpdatedTs() != null && (updatedTs == null || !updatedTs.isAfter(existing.getUpdatedTs()))) {
        // no change
        return false;
      }
    }

    var t = existing != null ? existing : new JiraTicket();
    t.setCustomerorder(cfg.getCustomerorder());
    t.setSuborder(cfg.getSuborder());
    t.setReplication(cfg);
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

  private static boolean isMaintainedBy(JiraTicket ticket, JiraReplicationConfig cfg) {
    return ticket.getReplication() != null && Objects.equals(ticket.getReplication().getId(), cfg.getId());
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
