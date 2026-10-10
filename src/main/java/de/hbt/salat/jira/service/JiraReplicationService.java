package de.hbt.salat.jira.service;

import static java.lang.Boolean.TRUE;
import static java.util.Objects.requireNonNull;
import static de.hbt.salat.jira.domain.JiraReplicationRun.Status.FAILED;
import static de.hbt.salat.jira.domain.JiraReplicationRun.Status.SUCCEEDED;
import static de.hbt.salat.jira.service.JiraCredentialRedaction.redacted;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSource;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import de.hbt.salat.auth.domain.Authorized;
import de.hbt.salat.common.exception.ErrorCodeException;
import de.hbt.salat.jira.domain.JiraAuthMethod;
import de.hbt.salat.jira.domain.JiraFieldConfig;
import de.hbt.salat.jira.domain.JiraReplicationConfig;
import de.hbt.salat.jira.domain.JiraReplicationRun.Trigger;
import de.hbt.salat.jira.domain.JiraTicket;
import de.hbt.salat.jira.persistence.JiraReplicationConfigRepository;
import de.hbt.salat.jira.persistence.JiraTicketImportRepository;
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
  private final JiraTicketImportRepository importRepo;
  private final JiraCredentialStore credentialStore;
  private final MessageSource messageSource;

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
    JiraCredentials credentials = null;
    JiraReplicationResult result;
    try {
      var cfg = configRepo.findById(replicationId)
          .orElseThrow(() -> new IllegalArgumentException("Unknown replication config id=" + replicationId));
      credentials = credentialStore.credentialsOf(cfg);
      result = runReplication(cfg, credentials);
    } catch (ErrorCodeException ex) {
      // A rule of the application, credentials that cannot be read above all (#1432): the history
      // says it in the words the form uses, not as a code.
      runService.finishRun(runId, FAILED, "Abgebrochen: " + germanTextOf(ex));
      throw ex;
    } catch (RuntimeException ex) {
      runService.finishRun(runId, FAILED, "Abgebrochen: " + redacted(ex, credentials));
      throw ex;
    }
    runService.finishRun(runId, result.succeeded() ? SUCCEEDED : FAILED, result.summary());
    return result;
  }

  public JiraReplicationResult runReplication(long replicationId) {
    JiraReplicationConfig cfg = configRepo.findById(replicationId)
        .orElseThrow(() -> new IllegalArgumentException("Unknown replication config id=" + replicationId));
    return runReplication(cfg, credentialStore.credentialsOf(cfg));
  }

  /** The first message of the exception in German, like every message of the run history. */
  private String germanTextOf(ErrorCodeException ex) {
    return ex.getMessages().stream().findFirst()
        .map(message -> messageSource.getMessage(message.getErrorCode().messageKey(),
            message.getArguments().toArray(), ex.getMessage(), Locale.GERMAN))
        .orElse(ex.getMessage());
  }

  private JiraReplicationResult runReplication(JiraReplicationConfig cfg, JiraCredentials credentials) {
    requireNonNull(cfg.getBaseUrl(), "baseUrl");
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
    // Tickets another replication maintains (#1386): left alone, but counted, because two replications
    // delivering the same tickets into one scope is a JQL to correct.
    int skipped = 0;
    var skippedTickets = new ArrayList<String>();
    // The parents the tickets of this run name by id (#1392), by ticket key: translated once the run
    // has stored every ticket an id may name.
    var parentIdsToTranslate = new HashMap<String, List<JiraParentReference>>();
    LocalDateTime oldestFailure = null;
    boolean failureWithoutTimestamp = false;

    var fields = buildFieldList(cfg, fieldConfig);
    var jql = appendMaxUpdated(cfg.getJql(), baseline);
    var request = new JiraSearchRequest(
        credentials.baseUrl(cfg.getBaseUrl()), credentials, jql, fields, pageSize);

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
        var upsert = upsertIfChanged(cfg, fieldConfig, jiraId, issue, skippedTickets, parentIdsToTranslate);
        if (upsert == Upsert.WRITTEN) {
          processed++;
        } else if (upsert == Upsert.SKIPPED) {
          skipped++;
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

    var reparented = translateParentIds(cfg, tickets, parentIdsToTranslate);
    resolveParentChains(cfg, scopeSign, tickets, reparented);

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
    var syncCredentials = credentials;
    try {
      // An OAuth access token lives an hour (#1417); after a long search it is fetched again, and
      // renewed if need be, rather than failing halfway through the worklogs.
      if (credentials.method() == JiraAuthMethod.OAUTH && TRUE.equals(cfg.getWorklogSyncEnabled())) {
        syncCredentials = credentialStore.credentialsOf(cfg);
      }
      worklogSyncService.sync(cfg, syncCredentials);
    } catch (ErrorCodeException ex) {
      log.error("Worklog sync failed after the replication of {}: {}", cfg.getName(), ex.getMessage());
      worklogSyncError = germanTextOf(ex);
    } catch (Exception ex) {
      log.error("Worklog sync failed after the replication of {}: {}", cfg.getName(), ex.getMessage(), ex);
      worklogSyncError = redacted(ex, syncCredentials);
    }
    return new JiraReplicationResult(fetched, processed, failed, skipped, skippedTickets, worklogSyncError);
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
    // Only its own tickets (#1386): who maintains a ticket is its foreign key. One maintained by hand,
    // by another replication, or left behind by a deleted one is not this run's to remove.
    var unseen = tickets.stream()
        .filter(ticket -> isMaintainedBy(ticket, cfg) && !seenJiraIds.contains(ticket.getJiraId()))
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
  private void resolveParentChains(JiraReplicationConfig cfg, String scopeSign, List<JiraTicket> tickets,
                                   List<JiraTicket> reparented) {
    // Every ticket of the scope, whoever maintains it (#1386): these values are derived, never entered,
    // and the page writing a ticket by hand derives them the same way.
    var configs = new ArrayList<>(configRepo.findInScope(cfg.getCustomerorderId(), cfg.getSuborderId()));
    configs.removeIf(other -> Objects.equals(other.getId(), cfg.getId()));
    configs.add(cfg);
    var latestImport = importRepo.findLatestInScope(cfg.getCustomerorderId(), cfg.getSuborderId(), PageRequest.of(0, 1))
        .stream().findFirst();
    var inherited = JiraTicketChains.inheritedFields(configs, latestImport);
    var changed = new LinkedHashSet<>(reparented);
    changed.addAll(JiraTicketChains.resolve(tickets, inherited));
    log.info("Resolved parent chains for {} changed tickets of scope {} with inherited fields {}",
        changed.size(), scopeSign, inherited);
    ticketRepo.saveAll(new ArrayList<>(changed));
  }

  /**
   * Translates the parents the tickets of this run name by id into keys (#1392). Only now: the parent
   * may come later in the same run than the child naming it. An id that names no ticket is skipped,
   * and the next reference counts.
   *
   * @return the tickets whose parent changed, to be saved
   */
  private static List<JiraTicket> translateParentIds(JiraReplicationConfig cfg, List<JiraTicket> tickets,
                                                     Map<String, List<JiraParentReference>> parentIdsToTranslate) {
    if (parentIdsToTranslate.isEmpty()) return List.of();
    var keysByJiraId = keysByJiraId(cfg, tickets);
    var reparented = new ArrayList<JiraTicket>();
    for (var ticket : tickets) {
      var references = parentIdsToTranslate.get(ticket.getKey());
      if (references == null) continue;
      var parentKey = JiraParentReference.firstParentKey(references, ticket.getKey(), keysByJiraId);
      if (Objects.equals(parentKey, ticket.getParentKey())) continue;
      ticket.setParentKey(parentKey);
      reparented.add(ticket);
    }
    return reparented;
  }

  /**
   * The keys an id of this replication translates into. Not every JIRA id of the scope: an id is unique
   * per replication only, and another replication of the scope may read another JIRA instance with
   * the same numeric ids. So its own tickets count, and after them those nobody maintains — the same
   * order in which {@link #upsertIfChanged} finds the ticket of an issue by its id, which takes a
   * ticket nobody maintains over as the same issue. The tickets of other replications do not count.
   */
  private static Map<Long, String> keysByJiraId(JiraReplicationConfig cfg, List<JiraTicket> tickets) {
    var keys = new HashMap<Long, String>();
    for (var ticket : tickets) {
      if (ticket.getJiraId() != null && !ticket.isReplicated()) keys.putIfAbsent(ticket.getJiraId(), ticket.getKey());
    }
    for (var ticket : tickets) {
      if (ticket.getJiraId() != null && isMaintainedBy(ticket, cfg)) keys.put(ticket.getJiraId(), ticket.getKey());
    }
    return keys;
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

  /** What became of one issue. */
  private enum Upsert { WRITTEN, UNCHANGED, SKIPPED }

  /**
   * @param skippedTickets collects the first skipped tickets with the replication that maintains them,
   *     for the message of the run
   * @param parentIdsToTranslate collects the parent references of a written ticket that names a parent
   *     by id, by the ticket's key
   */
  private Upsert upsertIfChanged(JiraReplicationConfig cfg, JiraFieldConfig fieldConfig, long jiraId,
                                 JiraIssue issue, List<String> skippedTickets,
                                 Map<String, List<JiraParentReference>> parentIdsToTranslate) {
    // Its own ticket by the JIRA id, otherwise the ticket of the scope with the key, otherwise one
    // nobody maintains with the JIRA id (#1386). A JIRA id is unique per replication, not per scope:
    // another replication of the scope may read another JIRA instance with the same numeric ids. A
    // ticket nobody maintains — entered by hand, or left behind by a deleted replication — is taken
    // over: this replication fills in its JIRA id and keeps it from here on. One another replication
    // maintains is skipped and stays with it.
    var existing = ticketRepo.findMaintainedByJiraId(cfg.getId(), jiraId)
        .or(() -> ticketRepo.findInScopeByKey(cfg.getCustomerorderId(), cfg.getSuborderId(), issue.getKey()))
        .or(() -> ticketRepo.findUnmaintainedInScopeByJiraId(cfg.getCustomerorderId(), cfg.getSuborderId(), jiraId)
            .stream().findFirst())
        .orElse(null);
    if (maintainedByAnotherReplication(existing, cfg)) {
      return skip(cfg, issue.getKey(), existing, skippedTickets);
    }
    // Renamed in JIRA — moved to another project — to a key another ticket of the scope carries.
    var keyHolder = keyTakenByAnotherTicket(cfg, existing, issue.getKey());
    if (keyHolder != null) {
      if (maintainedByAnotherReplication(keyHolder, cfg)) {
        return skip(cfg, issue.getKey(), keyHolder, skippedTickets);
      }
      // By hand, left behind, or a stale one of its own: the same issue under the key JIRA gives it
      // now. It gives way before the rename, which would otherwise hit the unique key on every run.
      log.info("Replication {} replaces ticket {} (id {}) by its issue {}, renamed to that key in JIRA",
          cfg.getName(), keyHolder.getKey(), keyHolder.getId(), existing.getKey());
      ticketRepo.delete(keyHolder);
      ticketRepo.flush();
    }
    var fields = issue.getFields();
    var updatedTs = toDateTime(getString(fields, "updated"));

    // Global baseline filter per requirement. A ticket this replication does not maintain yet — one
    // taken over, or left behind by a deleted replication — is written even when JIRA reports it as
    // unchanged, or it would never point at the replication that keeps it now.
    // Only while the ticket was written with the field list that is configured now. After a change
    // to that list JIRA still reports the ticket as unchanged - its `updated` has not moved - so
    // this is the point where the new fields would never reach an already replicated ticket.
    boolean ownWithCurrentFields = existing != null && isMaintainedBy(existing, cfg)
        && Objects.equals(existing.getFieldConfigHash(), fieldConfig.hash());
    if (ownWithCurrentFields && notUpdatedSince(existing, updatedTs) && parentIsKeyOrNone(existing)) {
      return Upsert.UNCHANGED;
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

    // A key is final. An id waits for the end of the run, and until then the column holds the id: a
    // run aborted in between leaves a parent that is no key, and the next run writes the ticket again.
    var parentReferences = extractParentReferences(cfg, fields);
    t.setParentKey(untranslatedParent(parentReferences, issue.getKey()));
    if (parentReferences.stream().anyMatch(reference -> !reference.isByKey())) {
      parentIdsToTranslate.put(issue.getKey(), parentReferences);
    }

    t.setCustomFields(extractCustomFields(fieldConfig, fields));
    t.setFieldConfigHash(fieldConfig.hash());

    ticketRepo.save(t);
    return Upsert.WRITTEN;
  }

  /**
   * Whether the stored parent is a key, or there is none. Not so for one stored before #1392 — an id,
   * the text form of an object — nor for an id an aborted run left untranslated: such a ticket is
   * written again even though JIRA reports it unchanged, so a run without watermark corrects them all.
   */
  private static boolean parentIsKeyOrNone(JiraTicket ticket) {
    return ticket.getParentKey() == null
        || JiraParentReference.of(ticket.getParentKey()).filter(JiraParentReference::isByKey).isPresent();
  }

  /** Whether JIRA reports nothing newer than what the ticket was last written with. */
  private static boolean notUpdatedSince(JiraTicket ticket, LocalDateTime updatedTs) {
    return ticket.getUpdatedTs() != null && (updatedTs == null || !updatedTs.isAfter(ticket.getUpdatedTs()));
  }

  private static boolean maintainedByAnotherReplication(JiraTicket ticket, JiraReplicationConfig cfg) {
    return ticket != null && ticket.isReplicated() && !isMaintainedBy(ticket, cfg);
  }

  /** The other ticket of the scope carrying {@code key}, where {@code existing} is about to take it. */
  private JiraTicket keyTakenByAnotherTicket(JiraReplicationConfig cfg, JiraTicket existing, String key) {
    if (existing == null || Objects.equals(existing.getKey(), key)) return null;
    return ticketRepo.findInScopeByKey(cfg.getCustomerorderId(), cfg.getSuborderId(), key)
        .filter(holder -> !Objects.equals(holder.getId(), existing.getId()))
        .orElse(null);
  }

  private Upsert skip(JiraReplicationConfig cfg, String key, JiraTicket maintained, List<String> skippedTickets) {
    log.warn("Replication {} skips ticket {}: replication {} maintains it in the same scope - "
            + "the two deliver the same key",
        cfg.getName(), key, maintained.getReplication().getName());
    if (skippedTickets.size() < JiraReplicationResult.SKIPPED_NAMED) {
      skippedTickets.add(key + " (" + maintained.getReplication().getName() + ")");
    }
    return Upsert.SKIPPED;
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

  /**
   * How the issue names its parent, in the order that counts (#1392): the standard {@code parent}
   * first, then the configured parent fields in their order. Each is read as
   * {@link JiraParentReference#of} reads it — a key, an id, or an object with either.
   */
  private static List<JiraParentReference> extractParentReferences(JiraReplicationConfig cfg, Map<String, Object> fields) {
    var references = new ArrayList<JiraParentReference>();
    JiraParentReference.of(fields.get("parent")).ifPresent(references::add);
    if (cfg.getParentFieldNames() != null && !cfg.getParentFieldNames().isBlank()) {
      for (String f : cfg.getParentFieldNames().split(",")) {
        String trimmed = f.trim();
        if (trimmed.isEmpty()) continue;
        JiraParentReference.of(fields.get(trimmed)).ifPresent(references::add);
      }
    }
    return references;
  }

  /** The first parent the issue names, an id as it is until the end of the run translates it. */
  private static String untranslatedParent(List<JiraParentReference> references, String ownKey) {
    return references.stream()
        .map(JiraParentReference::asStored)
        .filter(parent -> !parent.equals(ownKey))
        .findFirst()
        .orElse(null);
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
