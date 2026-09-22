package org.tb.jira.service;

import static java.lang.Boolean.TRUE;
import static java.util.Comparator.comparing;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.tb.common.command.CommandPublisher;
import org.tb.common.util.DateTimeUtils;
import org.tb.common.util.DateUtils;
import org.tb.jira.command.GetTicketWorklogSumsCommandEvent;
import org.tb.jira.command.TicketDaySum;
import org.tb.jira.domain.JiraReplicationConfig;
import org.tb.jira.domain.JiraTicket;
import org.tb.jira.domain.JiraWorklogSync;
import org.tb.jira.persistence.JiraTicketRepository;
import org.tb.jira.persistence.JiraWorklogSyncRepository;

/**
 * Writes the hours booked in SALAT back into JIRA as worklogs (#1007): one worklog per day and
 * ticket, carrying the sum over everybody who booked on that ticket that day.
 *
 * <p>Deliberately not {@code @Transactional}. Every step talks to JIRA over HTTP, and a transaction
 * around it would hold a database connection for the whole of a foreign system's response time —
 * the same reason {@code JiraReplicationConfigService.runNow} suspends one. It also means a worklog
 * that was written keeps its row in {@code jira_worklog_sync} even if a later one fails; a rollback
 * there would make SALAT forget a worklog it had just created, and the next run would write a second
 * one next to it.
 *
 * <p>Every run compares the full sums of the period against what was last written, rather than
 * tracking which days changed. A booking is soft-deleted, and the delete statement moves no
 * timestamp (see {@code TimereportRepository.getTicketDaySums}) — a deleted booking is recognisable
 * by nothing at all, and exactly its disappearance has to lower the sum. Comparing sums makes that
 * a non-issue, and a period without changes still produces no writing call whatsoever.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class JiraWorklogSyncService {

  /** How many unknown references a log line names before it only counts them. */
  private static final int LOGGED_UNKNOWN_REFERENCES = 10;

  private final CommandPublisher commandPublisher;
  private final JiraScopeSuborders scopeSuborders;
  private final JiraTicketRepository ticketRepository;
  private final JiraWorklogSyncRepository syncRepository;
  private final JiraWorklogClients worklogClients;

  /**
   * Brings JIRA in line with what was booked in SALAT. Does nothing at all — not one HTTP call —
   * while the sync is switched off on this config.
   */
  public void sync(JiraReplicationConfig cfg) {
    if (!TRUE.equals(cfg.getWorklogSyncEnabled())) {
      return;
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

    var suborderIds = scopeSuborders.idsOf(cfg.getScopeSign());
    if (suborderIds.isEmpty()) {
      log.warn("Worklog sync of JIRA replication {} found no suborder under scope {} - skipped",
          cfg.getName(), cfg.getScopeSign());
      return;
    }

    var wanted = wantedWorklogs(cfg, bookedMinutes(suborderIds, from, until));
    var stored = storedWorklogs(cfg, from);

    log.info("Starting JIRA worklog sync: name={}, scopeSign={}, from={}, until={}, "
            + "wanted={}, stored={}",
        cfg.getName(), cfg.getScopeSign(), from, until, wanted.size(), stored.size());

    var outcome = new Outcome();
    wanted.forEach((key, minutes) -> writeOne(cfg, key, minutes, stored.get(key), outcome));
    stored.forEach((key, row) -> {
      if (!wanted.containsKey(key)) {
        removeOne(cfg, key, row, outcome);
      }
    });

    log.info("Finished JIRA worklog sync: name={}, created={}, updated={}, deleted={}, "
            + "unchanged={}, failed={}",
        cfg.getName(), outcome.created, outcome.updated, outcome.deleted, outcome.unchanged,
        outcome.failed);
  }

  /**
   * The booked minutes per day and ticket reference, asked of the module that owns the bookings.
   * jira must not import dailyreport, so the answer comes back through a command event.
   */
  private List<TicketDaySum> bookedMinutes(List<Long> suborderIds, LocalDate from, LocalDate until) {
    var command = GetTicketWorklogSumsCommandEvent.builder()
        .suborderIds(suborderIds)
        .from(from)
        .until(until)
        .build();
    commandPublisher.publish(command);
    var result = command.getResult();
    return result == null ? List.of() : result;
  }

  /**
   * What JIRA should hold after this run: the sums, reduced to the references that actually name a
   * replicated ticket of this scope.
   *
   * <p>The reference is free text (#982), so a typo is normal and must not turn into a write
   * against an issue that does not exist. Skipped references are counted and logged once, not per
   * booking.
   *
   * <p>The key of the map is the key of the <em>ticket</em>, not the text that was typed: a
   * reference differing only in case names the same issue, and the two would otherwise compete for
   * the same worklog. Their minutes are added up instead.
   *
   * <p>A sum of zero is left out, so a day that only carries zero-length bookings is treated like
   * one without bookings — JIRA rejects a worklog of no time at all.
   */
  private Map<WorklogKey, Long> wantedWorklogs(JiraReplicationConfig cfg, List<TicketDaySum> sums) {
    var ticketKeys = ticketKeysByReference(cfg, sums);
    var wanted = new TreeMap<WorklogKey, Long>(
        comparing(WorklogKey::issueKey).thenComparing(WorklogKey::workDate));
    var unknown = new ArrayList<String>();
    for (var sum : sums) {
      var ticketKey = ticketKeys.get(normalized(sum.ticketReference()));
      if (ticketKey == null) {
        if (!unknown.contains(sum.ticketReference())) unknown.add(sum.ticketReference());
        continue;
      }
      wanted.merge(new WorklogKey(ticketKey, sum.workDate()), sum.minutes(), Long::sum);
    }
    wanted.values().removeIf(minutes -> minutes <= 0);
    if (!unknown.isEmpty()) {
      log.info("Worklog sync of JIRA replication {} skipped {} ticket reference(s) without a "
              + "replicated ticket in scope {}: {}",
          cfg.getName(), unknown.size(), cfg.getScopeSign(),
          unknown.stream().limit(LOGGED_UNKNOWN_REFERENCES).toList());
    }
    return wanted;
  }

  /** Every referenced ticket that exists in this scope, found by its normalised key. */
  private Map<String, String> ticketKeysByReference(JiraReplicationConfig cfg, List<TicketDaySum> sums) {
    var references = sums.stream().map(TicketDaySum::ticketReference).distinct().toList();
    if (references.isEmpty()) {
      return Map.of();
    }
    var byNormalizedKey = new LinkedHashMap<String, String>();
    for (JiraTicket ticket : ticketRepository.findByScopeSignAndKeyIn(cfg.getScopeSign(), references)) {
      byNormalizedKey.putIfAbsent(normalized(ticket.getKey()), ticket.getKey());
    }
    return byNormalizedKey;
  }

  private Map<WorklogKey, JiraWorklogSync> storedWorklogs(JiraReplicationConfig cfg, LocalDate from) {
    var stored = new LinkedHashMap<WorklogKey, JiraWorklogSync>();
    syncRepository.findByScopeSignAndWorkDateGreaterThanEqual(cfg.getScopeSign(), from)
        .forEach(row -> stored.put(new WorklogKey(row.getIssueKey(), row.getWorkDate()), row));
    return stored;
  }

  /**
   * One day and ticket: created, overwritten, or left alone because the sum has not moved. A
   * failure is logged and the run carries on with the next one — the remembered row stays as it
   * was, so the next run tries again.
   */
  private void writeOne(JiraReplicationConfig cfg, WorklogKey key, long minutes,
                        JiraWorklogSync stored, Outcome outcome) {
    if (stored != null && stored.getMinutes() == minutes) {
      outcome.unchanged++;
      return;
    }
    var client = worklogClients.forFlavor(cfg.getApiFlavor());
    var target = targetFor(cfg, key.issueKey());
    var entry = new JiraWorklogEntry(key.workDate(), Math.toIntExact(minutes));
    try {
      if (stored == null) {
        remember(cfg, key, client.create(target, entry), minutes);
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
      stored.setMinutes(Math.toIntExact(minutes));
      stored.setLastSynced(DateTimeUtils.now());
      syncRepository.save(stored);
      outcome.updated++;
    } catch (Exception ex) {
      outcome.failed++;
      log.error("Worklog sync of JIRA replication {} failed for issue {} on {}: {}",
          cfg.getName(), key.issueKey(), key.workDate(), ex.getMessage(), ex);
    }
  }

  /** A day and ticket that has no bookings left — in JIRA it must not stay behind. */
  private void removeOne(JiraReplicationConfig cfg, WorklogKey key, JiraWorklogSync stored,
                         Outcome outcome) {
    var client = worklogClients.forFlavor(cfg.getApiFlavor());
    try {
      try {
        client.delete(targetFor(cfg, key.issueKey()), stored.getWorklogId());
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

  private void remember(JiraReplicationConfig cfg, WorklogKey key, String worklogId, long minutes) {
    var row = new JiraWorklogSync();
    row.setScopeSign(cfg.getScopeSign());
    row.setIssueKey(key.issueKey());
    row.setWorkDate(key.workDate());
    row.setWorklogId(worklogId);
    row.setMinutes(Math.toIntExact(minutes));
    row.setLastSynced(DateTimeUtils.now());
    syncRepository.save(row);
  }

  private static JiraWorklogTarget targetFor(JiraReplicationConfig cfg, String issueKey) {
    return new JiraWorklogTarget(cfg.getBaseUrl(), cfg.getUsername(), cfg.getPassword(), issueKey);
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
    private int failed;
  }
}
