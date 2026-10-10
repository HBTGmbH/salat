package de.hbt.salat.jira.service;

import static java.lang.Boolean.TRUE;
import static de.hbt.salat.common.exception.ErrorCode.JI_REPLICATION_OAUTH_WRITE_NOT_GRANTED;
import static java.util.Comparator.comparing;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import de.hbt.salat.common.exception.BusinessRuleException;
import de.hbt.salat.common.command.CommandPublisher;
import de.hbt.salat.common.util.DateTimeUtils;
import de.hbt.salat.common.util.DateUtils;
import de.hbt.salat.jira.command.GetTicketWorklogSumsCommandEvent;
import de.hbt.salat.jira.command.TicketDaySum;
import de.hbt.salat.jira.domain.JiraReplicationConfig;
import de.hbt.salat.jira.domain.JiraTicket;
import de.hbt.salat.jira.domain.JiraWorklogSync;
import de.hbt.salat.jira.persistence.JiraTicketRepository;
import de.hbt.salat.jira.persistence.JiraWorklogSyncRepository;

/**
 * Writes the hours booked in SALAT back into JIRA as worklogs (#1007): one worklog per day and
 * ticket, carrying the sum over everybody who booked on that ticket that day, and a comment naming
 * each of them by sign with their share (#1408).
 *
 * <p>Deliberately not {@code @Transactional}. Every step talks to JIRA over HTTP, and a transaction
 * around it would hold a database connection for the whole of a foreign system's response time —
 * the same reason {@code JiraReplicationService} runs without one. It also means a worklog
 * that was written keeps its row in {@code jira_worklog_sync} even if a later one fails; a rollback
 * there would make SALAT forget a worklog it had just created, and the next run would write a second
 * one next to it.
 *
 * <p>Every run compares the full sums of the period and their comments against what was last
 * written, rather than tracking which days changed. A booking is soft-deleted, and the delete
 * statement moves no timestamp (see {@code TimereportRepository.getBookedTicketReferences}) — a
 * deleted booking is recognisable by nothing at all, and exactly its disappearance has to lower the
 * sum. Comparing sums makes that a non-issue, and a period without changes still produces no
 * writing call whatsoever. The comment is compared as well (#1408): a split that changes at the same
 * sum — one person's booking moved to another — rewrites the worklog just like a changed sum.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class JiraWorklogSyncService {

  /** How many unknown references a log line names before it only counts them. */
  private static final int LOGGED_UNKNOWN_REFERENCES = 10;

  private final CommandPublisher commandPublisher;
  private final JiraScopes scopes;
  private final JiraTicketRepository ticketRepository;
  private final JiraWorklogSyncRepository syncRepository;
  private final JiraWorklogClients worklogClients;

  /**
   * Brings JIRA in line with what was booked in SALAT. Does nothing at all — not one HTTP call —
   * while the sync is switched off on this config.
   *
   * @param credentials what the replication signed in with — the account the worklogs are authored by
   * @throws BusinessRuleException {@code JI-0048} when an OAuth connection was made without the scope
   *     to write (#1417) — the run says so instead of failing on every single worklog
   */
  public void sync(JiraReplicationConfig cfg, JiraCredentials credentials) {
    if (!TRUE.equals(cfg.getWorklogSyncEnabled())) {
      return;
    }
    if (!credentials.writeGranted()) {
      throw new BusinessRuleException(JI_REPLICATION_OAUTH_WRITE_NOT_GRANTED);
    }
    var from = cfg.getWorklogSyncFrom();
    if (from == null) {
      // The form insists on a date whenever the switch is on, so this is a row edited around it.
      log.warn("Worklog sync of JIRA replication {} is enabled without a start date - skipped, "
          + "because writing the whole history of the order is never what was meant", cfg.getName());
      return;
    }
    var until = DateUtils.today();
    if (until.isBefore(from)) {
      log.info("Worklog sync of JIRA replication {} starts on {} and has nothing to do yet",
          cfg.getName(), from);
      return;
    }

    // The scope as the order tree names it now (#1323), for the log.
    var scopeSign = scopes.signOf(cfg.getCustomerorderId(), cfg.getSuborderId());
    var suborderIds = scopes.suborderIdsOf(cfg.getCustomerorderId(), cfg.getSuborderId());
    if (suborderIds.isEmpty()) {
      log.warn("Worklog sync of JIRA replication {} found no suborder under scope {} - skipped",
          cfg.getName(), scopeSign);
      return;
    }

    var invoiceableOnly = TRUE.equals(cfg.getWorklogSyncInvoiceableOnly());
    var wanted = wantedWorklogs(cfg, scopeSign, bookedMinutes(suborderIds, from, until, invoiceableOnly));
    var stored = storedWorklogs(cfg, from);

    log.info("Starting JIRA worklog sync: name={}, scopeSign={}, from={}, until={}, "
            + "wanted={}, stored={}",
        cfg.getName(), scopeSign, from, until, wanted.size(), stored.size());

    var outcome = new Outcome();
    wanted.forEach((key, entry) -> writeOne(cfg, credentials, key, entry, stored.get(key), outcome));
    var unwanted = new LinkedHashMap<>(stored);
    unwanted.keySet().removeAll(wanted.keySet());
    var replicated = replicatedKeys(cfg, unwanted.keySet());
    unwanted.forEach((key, row) -> {
      if (replicated.contains(normalized(key.issueKey()))) {
        removeOne(cfg, credentials, key, row, outcome);
      } else {
        outcome.kept++;
      }
    });

    log.info("Finished JIRA worklog sync: name={}, created={}, updated={}, deleted={}, "
            + "unchanged={}, kept={}, failed={}",
        cfg.getName(), outcome.created, outcome.updated, outcome.deleted, outcome.unchanged,
        outcome.kept, outcome.failed);
  }

  /**
   * Which of these worklogs are on a ticket this replication still maintains (#1167, #1386). Only on
   * those does a missing sum mean the bookings are gone. A ticket the replication has removed —
   * moved away, or no longer matched by the JQL — says nothing about the bookings; its worklogs stay
   * in JIRA, and so does their row here. Should the ticket come back, the sync picks up at that row
   * instead of writing a second worklog next to the first.
   */
  private Set<String> replicatedKeys(JiraReplicationConfig cfg, Set<WorklogKey> worklogs) {
    if (worklogs.isEmpty()) {
      return Set.of();
    }
    var issueKeys = worklogs.stream().map(WorklogKey::issueKey).distinct().toList();
    return ticketRepository.findMaintainedByKeyIn(cfg.getId(), issueKeys).stream()
        .map(ticket -> normalized(ticket.getKey()))
        .collect(Collectors.toSet());
  }

  /**
   * The booked minutes per day and ticket reference, asked of the module that owns the bookings.
   * jira must not import dailyreport, so the answer comes back through a command event.
   */
  private List<TicketDaySum> bookedMinutes(List<Long> suborderIds, LocalDate from, LocalDate until,
                                           boolean invoiceableOnly) {
    var command = GetTicketWorklogSumsCommandEvent.builder()
        .suborderIds(suborderIds)
        .from(from)
        .until(until)
        .invoiceableOnly(invoiceableOnly)
        .build();
    commandPublisher.publish(command);
    var result = command.getResult();
    return result == null ? List.of() : result;
  }

  /**
   * What JIRA should hold after this run: the sums with their shares per sign, reduced to the
   * references that actually name a replicated ticket of this scope.
   *
   * <p>The reference is free text (#982), so a typo is normal and must not turn into a write
   * against an issue that does not exist. Skipped references are counted and logged once, not per
   * booking.
   *
   * <p>The key of the map is the key of the <em>ticket</em>, not the text that was typed: a
   * reference differing only in case names the same issue, and the two would otherwise compete for
   * the same worklog. Their minutes are added up instead, and so are the shares of each sign.
   *
   * <p>A sum of zero is left out, so a day that only carries zero-length bookings is treated like
   * one without bookings — JIRA rejects a worklog of no time at all.
   */
  private Map<WorklogKey, JiraWorklogEntry> wantedWorklogs(JiraReplicationConfig cfg, String scopeSign,
                                                           List<TicketDaySum> sums) {
    var ticketKeys = ticketKeysByReference(cfg, sums);
    var sharesByWorklog = new TreeMap<WorklogKey, Map<String, Long>>(
        comparing(WorklogKey::issueKey).thenComparing(WorklogKey::workDate));
    var unknown = new ArrayList<String>();
    for (var sum : sums) {
      var ticketKey = ticketKeys.get(normalized(sum.ticketReference()));
      if (ticketKey == null) {
        if (!unknown.contains(sum.ticketReference())) unknown.add(sum.ticketReference());
        continue;
      }
      var shares = sharesByWorklog.computeIfAbsent(new WorklogKey(ticketKey, sum.workDate()), key -> new HashMap<>());
      sum.minutesBySign().forEach((sign, minutes) -> shares.merge(sign, minutes, Long::sum));
    }
    var wanted = new LinkedHashMap<WorklogKey, JiraWorklogEntry>();
    sharesByWorklog.forEach((key, shares) -> {
      var entry = JiraWorklogEntry.of(key.workDate(), shares);
      if (entry.minutes() > 0) {
        wanted.put(key, entry);
      }
    });
    if (!unknown.isEmpty()) {
      log.info("Worklog sync of JIRA replication {} skipped {} ticket reference(s) without a "
              + "replicated ticket in scope {}: {}",
          cfg.getName(), unknown.size(), scopeSign,
          unknown.stream().limit(LOGGED_UNKNOWN_REFERENCES).toList());
    }
    return wanted;
  }

  /**
   * Every referenced ticket this replication maintains, found by its normalised key (#1386): only its
   * own tickets are written back to, not one of the scope maintained by hand or by another
   * replication.
   */
  private Map<String, String> ticketKeysByReference(JiraReplicationConfig cfg, List<TicketDaySum> sums) {
    var references = sums.stream().map(TicketDaySum::ticketReference).distinct().toList();
    if (references.isEmpty()) {
      return Map.of();
    }
    var byNormalizedKey = new LinkedHashMap<String, String>();
    for (JiraTicket ticket : ticketRepository.findMaintainedByKeyIn(cfg.getId(), references)) {
      byNormalizedKey.putIfAbsent(normalized(ticket.getKey()), ticket.getKey());
    }
    return byNormalizedKey;
  }

  private Map<WorklogKey, JiraWorklogSync> storedWorklogs(JiraReplicationConfig cfg, LocalDate from) {
    var stored = new LinkedHashMap<WorklogKey, JiraWorklogSync>();
    syncRepository.findInScopeFrom(cfg.getCustomerorderId(), cfg.getSuborderId(), from)
        .forEach(row -> stored.put(new WorklogKey(row.getIssueKey(), row.getWorkDate()), row));
    return stored;
  }

  /**
   * One day and ticket: created, overwritten, or left alone because neither the sum nor its comment
   * has moved. A failure is logged and the run carries on with the next one — the remembered row
   * stays as it was, so the next run tries again.
   */
  private void writeOne(JiraReplicationConfig cfg, JiraCredentials credentials, WorklogKey key,
                        JiraWorklogEntry entry, JiraWorklogSync stored, Outcome outcome) {
    if (isWrittenAlready(entry, stored)) {
      outcome.unchanged++;
      return;
    }
    var client = worklogClients.forFlavor(cfg.getApiFlavor());
    var target = targetFor(cfg, credentials, key.issueKey());
    try {
      if (stored == null) {
        remember(cfg, key, client.create(target, entry), entry);
        outcome.created++;
        return;
      }
      try {
        client.update(target, stored.getWorklogId(), entry);
      } catch (JiraWorklogNotFoundException ex) {
        // Somebody removed it in JIRA by hand. Writing a new one is right: the bookings are there,
        // and the row would otherwise point at a worklog that no longer exists for good.
        log.info("Worklog {} of issue {} is gone from JIRA, creating a new one",
            stored.getWorklogId(), key.issueKey());
        stored.setWorklogId(client.create(target, entry));
      }
      stored.setMinutes(entry.minutes());
      stored.setComment(entry.comment());
      stored.setLastSynced(DateTimeUtils.now());
      syncRepository.save(stored);
      outcome.updated++;
    } catch (Exception ex) {
      outcome.failed++;
      log.error("Worklog sync of JIRA replication {} failed for issue {} on {}: {}",
          cfg.getName(), key.issueKey(), key.workDate(), ex.getMessage(), ex);
    }
  }

  /**
   * Whether JIRA holds this worklog as it is wanted now: same minutes, same comment (#1408). A row
   * from before the comment was remembered has none, so its worklog is written once more.
   */
  private static boolean isWrittenAlready(JiraWorklogEntry entry, JiraWorklogSync stored) {
    return stored != null
        && stored.getMinutes() == entry.minutes()
        && entry.comment().equals(stored.getComment());
  }

  /**
   * A day and ticket that has no bookings left — in JIRA it must not stay behind. Only called for a
   * ticket that is still replicated, see {@link #replicatedKeys}.
   */
  private void removeOne(JiraReplicationConfig cfg, JiraCredentials credentials, WorklogKey key,
                         JiraWorklogSync stored, Outcome outcome) {
    var client = worklogClients.forFlavor(cfg.getApiFlavor());
    try {
      try {
        client.delete(targetFor(cfg, credentials, key.issueKey()), stored.getWorklogId());
      } catch (JiraWorklogNotFoundException ex) {
        // Already gone — the goal is reached, only the memory of it is stale.
        log.info("Worklog {} of issue {} was already gone from JIRA",
            stored.getWorklogId(), key.issueKey());
      }
      syncRepository.delete(stored);
      outcome.deleted++;
    } catch (Exception ex) {
      outcome.failed++;
      log.error("Worklog sync of JIRA replication {} could not delete the worklog of issue {} "
              + "on {}: {}",
          cfg.getName(), key.issueKey(), key.workDate(), ex.getMessage(), ex);
    }
  }

  private void remember(JiraReplicationConfig cfg, WorklogKey key, String worklogId, JiraWorklogEntry entry) {
    var row = new JiraWorklogSync();
    row.setCustomerorder(cfg.getCustomerorder());
    row.setSuborder(cfg.getSuborder());
    row.setIssueKey(key.issueKey());
    row.setWorkDate(key.workDate());
    row.setWorklogId(worklogId);
    row.setMinutes(entry.minutes());
    row.setComment(entry.comment());
    row.setLastSynced(DateTimeUtils.now());
    syncRepository.save(row);
  }

  private static JiraWorklogTarget targetFor(JiraReplicationConfig cfg, JiraCredentials credentials,
                                             String issueKey) {
    return new JiraWorklogTarget(credentials.baseUrl(cfg.getBaseUrl()), credentials, issueKey);
  }

  /** A typed reference and a ticket key mean the same issue whatever the case was written in. */
  private static String normalized(String key) {
    return key == null ? "" : key.trim().toUpperCase(Locale.ROOT);
  }

  /** One worklog: a ticket and a day. What the sync is keyed by, in SALAT and in JIRA alike. */
  private record WorklogKey(String issueKey, LocalDate workDate) {

  }

  private static final class Outcome {

    private int created;
    private int updated;
    private int deleted;
    private int unchanged;
    private int kept;
    private int failed;
  }
}
