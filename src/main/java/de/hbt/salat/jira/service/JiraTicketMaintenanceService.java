package de.hbt.salat.jira.service;

import static de.hbt.salat.common.exception.ErrorCode.JI_REPLICATION_SCOPE_NOT_FOUND;
import static de.hbt.salat.common.exception.ErrorCode.JI_TICKET_IMPORT_ID_TAKEN;
import static de.hbt.salat.common.exception.ErrorCode.JI_TICKET_KEY_REQUIRED;
import static de.hbt.salat.common.exception.ErrorCode.JI_TICKET_KEY_TAKEN;
import static de.hbt.salat.common.exception.ErrorCode.JI_TICKET_NOT_FOUND;
import static de.hbt.salat.common.exception.ErrorCode.JI_TICKET_REPLICATED;
import static de.hbt.salat.common.exception.ErrorCode.JI_TICKET_SCOPE_COVERED;
import static de.hbt.salat.common.exception.ErrorCode.JI_TICKET_VALUE_TOO_LONG;
import static de.hbt.salat.common.exception.ServiceFeedbackMessage.error;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import de.hbt.salat.auth.domain.Authorized;
import de.hbt.salat.common.exception.InvalidDataException;
import de.hbt.salat.common.exception.ServiceFeedbackMessage;
import de.hbt.salat.common.util.DateTimeUtils;
import de.hbt.salat.jira.domain.JiraImportColumn;
import de.hbt.salat.jira.domain.JiraImportTarget;
import de.hbt.salat.jira.domain.JiraManualTicketData;
import de.hbt.salat.jira.domain.JiraReplicationConfig;
import de.hbt.salat.jira.domain.JiraTicket;
import de.hbt.salat.jira.domain.JiraTicketImportPreview;
import de.hbt.salat.jira.domain.JiraTicketRow;
import de.hbt.salat.jira.domain.ResolvedFieldValue;
import de.hbt.salat.jira.persistence.JiraReplicationConfigRepository;
import de.hbt.salat.jira.persistence.JiraTicketRepository;
import de.hbt.salat.jira.persistence.OrderReferences;

/**
 * Tickets maintained by hand, for scopes no replication covers (#1386) — a customer whose JIRA allows
 * no API access, or who has no JIRA at all.
 *
 * <p>They live in {@code jira_ticket} like the replicated ones, in the same scope of order and
 * suborder, and so reach the suggestions of the booking form, the ticket filter of the booking list
 * and the reports without any of them knowing the difference. What tells them apart is the
 * replication: a ticket without one is maintained here.
 *
 * <p>Where a replication covers the scope — the order itself, or a suborder on the path down to it —
 * nothing is created or imported here: the replication is the source of those tickets and would
 * take a manual one over by its key on its next run. A manual ticket that is already there, left
 * from before the replication, may still be changed and deleted.
 */
@Service
@RequiredArgsConstructor
@Transactional
@Authorized(requireUnrestricted = true)
public class JiraTicketMaintenanceService {

  private final JiraTicketRepository ticketRepository;
  private final JiraReplicationConfigRepository configRepository;
  private final JiraTicketAuthorization authorization;
  private final JiraScopes scopes;
  private final OrderReferences orderReferences;

  /**
   * The tickets of the order, replicated and maintained by hand: of the whole order and every
   * suborder, or only of the suborder {@code suborderId} and the branch below it.
   */
  @Transactional(readOnly = true)
  public List<JiraTicketRow> getTickets(long customerorderId, Long suborderId) {
    authorization.checkMayMaintain(customerorderId);
    var tickets = suborderId == null
        ? ticketRepository.findAllOfCustomerorder(customerorderId)
        : ticketRepository.findAllOfSuborders(scopes.branchOf(suborderId));
    var signs = new HashMap<List<Long>, String>();
    return tickets.stream()
        .map(ticket -> toRow(ticket, signs.computeIfAbsent(scopeOf(ticket),
            scope -> scopes.signOf(ticket.getCustomerorderId(), ticket.getSuborderId()))))
        .toList();
  }

  @Transactional(readOnly = true)
  public JiraTicketRow getTicket(long id) {
    var ticket = load(id);
    authorization.checkMayMaintain(ticket.getCustomerorderId());
    return toRow(ticket, scopes.signOf(ticket.getCustomerorderId(), ticket.getSuborderId()));
  }

  /** The names of the replications that cover the scope; empty where tickets may be maintained here. */
  @Transactional(readOnly = true)
  public List<String> getCoveringReplications(long customerorderId, Long suborderId) {
    return coveringReplications(customerorderId, suborderId).stream().map(JiraReplicationConfig::getName).toList();
  }

  public long create(long customerorderId, Long suborderId, JiraManualTicketData data) {
    authorization.checkMayMaintain(customerorderId);
    checkScopeOpen(customerorderId, suborderId);
    var values = validated(data);
    checkKeyFree(customerorderId, suborderId, values.key(), null);
    var ticket = newTicket(customerorderId, suborderId);
    apply(values, ticket);
    var id = ticketRepository.save(ticket).getId();
    resolveParentChains(customerorderId, suborderId, Set.of());
    return id;
  }

  /** The scope stays: a ticket moved to another one is a different ticket there. */
  public void update(long id, JiraManualTicketData data) {
    var ticket = loadMaintainedByHand(id);
    var values = validated(data);
    checkKeyFree(ticket.getCustomerorderId(), ticket.getSuborderId(), values.key(), ticket.getId());
    apply(values, ticket);
    ticketRepository.save(ticket);
    resolveParentChains(ticket.getCustomerorderId(), ticket.getSuborderId(), Set.of());
  }

  /** Bookings carrying the key keep it: their ticket reference is text, not a foreign key (#982). */
  public void delete(long id) {
    var ticket = loadMaintainedByHand(id);
    ticketRepository.delete(ticket);
    ticketRepository.flush();
    resolveParentChains(ticket.getCustomerorderId(), ticket.getSuborderId(), Set.of());
  }

  /**
   * What a ticket file holds and how its headings would be read — nothing is saved. The file is sent
   * again with the import; keeping it here in between would need a session (ADR-0013).
   */
  @Transactional(readOnly = true)
  public JiraTicketImportPreview preview(byte[] content) {
    return JiraTicketImport.preview(JiraTicketFile.read(content));
  }

  /**
   * Creates the tickets of a file, or updates them where the scope already has the key, reading the
   * columns as {@code mapping} says — one entry per column. Every faulty row is reported with its
   * number, and then nothing is saved.
   *
   * @return how many tickets the file named
   */
  public int importTickets(long customerorderId, Long suborderId, byte[] content, List<JiraImportColumn> mapping) {
    authorization.checkMayMaintain(customerorderId);
    checkScopeOpen(customerorderId, suborderId);
    var file = JiraTicketFile.read(content);

    var inScope = ticketRepository.findInScope(customerorderId, suborderId);
    var byKey = new HashMap<String, JiraTicket>();
    var byJiraId = new HashMap<Long, JiraTicket>();
    inScope.forEach(ticket -> {
      byKey.put(ticket.getKey(), ticket);
      if (ticket.getJiraId() != null) byJiraId.put(ticket.getJiraId(), ticket);
    });
    var keysByJiraId = byJiraId.values().stream().collect(Collectors.toMap(JiraTicket::getJiraId, JiraTicket::getKey));
    var tickets = JiraTicketImport.read(file, mapping, keysByJiraId);

    var findings = new ArrayList<ServiceFeedbackMessage>();
    for (var ticket : tickets) {
      var carrier = ticket.jiraId() == null ? null : byJiraId.get(ticket.jiraId());
      if (carrier != null && !carrier.getKey().equals(ticket.key())) {
        findings.add(error(JI_TICKET_IMPORT_ID_TAKEN, ticket.line(), ticket.jiraId(), carrier.getKey()));
      }
    }
    if (!findings.isEmpty()) {
      throw new InvalidDataException(findings);
    }

    var toSave = new ArrayList<JiraTicket>();
    for (var imported : tickets) {
      var ticket = byKey.computeIfAbsent(imported.key(), key -> newTicket(customerorderId, suborderId));
      apply(imported, mapping, ticket);
      toSave.add(ticket);
    }
    ticketRepository.saveAll(toSave);
    resolveParentChains(customerorderId, suborderId, inheritedFieldsOf(mapping));
    return toSave.size();
  }

  private JiraTicket load(long id) {
    return ticketRepository.findById(id).orElseThrow(() -> new InvalidDataException(JI_TICKET_NOT_FOUND));
  }

  private JiraTicket loadMaintainedByHand(long id) {
    var ticket = load(id);
    authorization.checkMayMaintain(ticket.getCustomerorderId());
    if (ticket.isReplicated()) {
      throw new InvalidDataException(JI_TICKET_REPLICATED);
    }
    return ticket;
  }

  private void checkScopeOpen(long customerorderId, Long suborderId) {
    var covering = coveringReplications(customerorderId, suborderId);
    if (!covering.isEmpty()) {
      throw new InvalidDataException(JI_TICKET_SCOPE_COVERED,
          String.join(", ", covering.stream().map(JiraReplicationConfig::getName).toList()));
    }
  }

  /** Also checks that the scope exists and the suborder belongs to the order. */
  private List<JiraReplicationConfig> coveringReplications(long customerorderId, Long suborderId) {
    if (suborderId == null) {
      if (!scopes.customerorderExists(customerorderId)) {
        throw new InvalidDataException(JI_REPLICATION_SCOPE_NOT_FOUND);
      }
      return configRepository.findCovering(customerorderId, List.of());
    }
    var location = scopes.locationOf(suborderId)
        .filter(found -> found.customerorderId() == customerorderId)
        .orElseThrow(() -> new InvalidDataException(JI_REPLICATION_SCOPE_NOT_FOUND));
    return configRepository.findCovering(customerorderId, location.path());
  }

  private void checkKeyFree(long customerorderId, Long suborderId, String key, Long ownId) {
    ticketRepository.findInScopeByKey(customerorderId, suborderId, key)
        .filter(other -> !Objects.equals(other.getId(), ownId))
        .ifPresent(other -> {
          throw new InvalidDataException(JI_TICKET_KEY_TAKEN, key);
        });
  }

  private static JiraManualTicketData validated(JiraManualTicketData data) {
    var values = new JiraManualTicketData(trimToNull(data.key()), trimToNull(data.summary()),
        trimToNull(data.issueType()), trimToNull(data.parentKey()));
    if (values.key() == null) {
      throw new InvalidDataException(JI_TICKET_KEY_REQUIRED);
    }
    if (longerThan(values.key(), JiraTicketImport.KEY_LENGTH) || longerThan(values.parentKey(), JiraTicketImport.KEY_LENGTH)
        || longerThan(values.summary(), JiraTicketImport.SUMMARY_LENGTH)
        || longerThan(values.issueType(), JiraTicketImport.ISSUE_TYPE_LENGTH)) {
      throw new InvalidDataException(JI_TICKET_VALUE_TOO_LONG);
    }
    return values;
  }

  private static boolean longerThan(String value, int max) {
    return value != null && value.length() > max;
  }

  private JiraTicket newTicket(long customerorderId, Long suborderId) {
    var ticket = new JiraTicket();
    ticket.setCustomerorder(orderReferences.customerorder(customerorderId));
    ticket.setSuborder(orderReferences.suborder(suborderId));
    ticket.setCreatedTs(DateTimeUtils.now());
    return ticket;
  }

  /**
   * The update timestamp is the moment of the change: the suggestions of the booking form put the
   * most recently updated tickets first, and a ticket just entered by hand is what someone is about
   * to book on.
   */
  private static void apply(JiraManualTicketData values, JiraTicket ticket) {
    ticket.setKey(values.key());
    ticket.setSummary(values.summary());
    ticket.setIssueType(values.issueType());
    ticket.setParentKey(values.parentKey());
    ticket.setUpdatedTs(DateTimeUtils.now());
  }

  /**
   * A column the file does not have leaves the stored value alone, a column it has overwrites it —
   * an empty cell included. The timestamps of the file win where it has them; otherwise a new ticket
   * was created now, and every imported one was updated now.
   */
  private static void apply(JiraTicketImport.Ticket imported, List<JiraImportColumn> mapping, JiraTicket ticket) {
    var assigned = mapping.stream().map(JiraImportColumn::target).collect(Collectors.toSet());
    ticket.setKey(imported.key());
    if (assigned.contains(JiraImportTarget.ID)) ticket.setJiraId(imported.jiraId());
    if (assigned.contains(JiraImportTarget.SUMMARY)) ticket.setSummary(imported.summary());
    if (assigned.contains(JiraImportTarget.ISSUE_TYPE)) ticket.setIssueType(imported.issueType());
    if (assigned.contains(JiraImportTarget.LABELS)) ticket.setLabels(imported.labels());
    if (assigned.contains(JiraImportTarget.PARENT)) ticket.setParentKey(imported.parentKey());
    if (imported.created() != null) ticket.setCreatedTs(imported.created());
    ticket.setUpdatedTs(imported.updated() != null ? imported.updated() : DateTimeUtils.now());
    if (assigned.contains(JiraImportTarget.ADDITIONAL)) {
      var fields = ticket.getCustomFields() == null ? new LinkedHashMap<String, String>()
          : new LinkedHashMap<>(ticket.getCustomFields());
      mapping.stream().filter(column -> column.target() == JiraImportTarget.ADDITIONAL)
          .map(column -> column.fieldName().trim())
          .forEach(name -> {
            if (imported.customFields().containsKey(name)) fields.put(name, imported.customFields().get(name));
            else fields.remove(name);
          });
      // Absent rather than empty: a json column cannot hold an empty string (#881).
      ticket.setCustomFields(fields.isEmpty() ? null : fields);
    }
  }

  private static Set<String> inheritedFieldsOf(List<JiraImportColumn> mapping) {
    return mapping.stream()
        .filter(column -> column.target() == JiraImportTarget.ADDITIONAL && column.inherited())
        .map(column -> column.fieldName().trim())
        .collect(Collectors.toSet());
  }

  /**
   * Writes the top-level key and the inherited fields of the tickets maintained by hand in the scope,
   * as a replication does for its own (#881): the reports group by the one and read the other.
   *
   * <p>Which fields are inherited is not stored anywhere apart from the result: a field the import
   * marked as inherited appears in {@code custom_fields_effective}. That is what a later change of a
   * single ticket goes by. The chain stays within the scope and stops at a cycle, which nothing in a
   * hand-written parent column rules out.
   */
  private void resolveParentChains(long customerorderId, Long suborderId, Set<String> newlyInherited) {
    var tickets = ticketRepository.findInScope(customerorderId, suborderId);
    var byKey = new HashMap<String, JiraTicket>();
    tickets.forEach(ticket -> byKey.putIfAbsent(ticket.getKey(), ticket));
    var inherited = new HashSet<>(newlyInherited);
    tickets.stream().filter(ticket -> !ticket.isReplicated() && ticket.getCustomFieldsEffective() != null)
        .forEach(ticket -> inherited.addAll(ticket.getCustomFieldsEffective().keySet()));

    var changed = new ArrayList<JiraTicket>();
    for (var ticket : tickets) {
      if (ticket.isReplicated()) continue;
      var effective = new LinkedHashMap<String, ResolvedFieldValue>();
      inherited.forEach(field -> ownValue(ticket, field)
          .ifPresent(value -> effective.put(field, new ResolvedFieldValue(value, null))));
      var visited = new HashSet<String>();
      visited.add(ticket.getKey());
      var top = ticket;
      while (top.getParentKey() != null && visited.add(top.getParentKey()) && byKey.containsKey(top.getParentKey())) {
        top = byKey.get(top.getParentKey());
        var ancestor = top;
        inherited.stream().filter(field -> !effective.containsKey(field))
            .forEach(field -> ownValue(ancestor, field)
                .ifPresent(value -> effective.put(field, new ResolvedFieldValue(value, ancestor.getKey()))));
      }
      var resolved = effective.isEmpty() ? null : effective;
      if (!Objects.equals(top.getKey(), ticket.getTopLevelKey())
          || !Objects.equals(resolved, ticket.getCustomFieldsEffective())) {
        ticket.setTopLevelKey(top.getKey());
        ticket.setCustomFieldsEffective(resolved);
        changed.add(ticket);
      }
    }
    ticketRepository.saveAll(changed);
  }

  private static Optional<String> ownValue(JiraTicket ticket, String field) {
    return Optional.ofNullable(ticket.getCustomFields()).map(fields -> fields.get(field));
  }

  private static String trimToNull(String value) {
    if (value == null) return null;
    var trimmed = value.trim();
    return trimmed.isEmpty() ? null : trimmed;
  }

  private static List<Long> scopeOf(JiraTicket ticket) {
    return Arrays.asList(ticket.getCustomerorderId(), ticket.getSuborderId());
  }

  private static JiraTicketRow toRow(JiraTicket ticket, String scopeSign) {
    var replication = ticket.getReplication();
    return new JiraTicketRow(ticket.getId(), ticket.getCustomerorderId(), ticket.getSuborderId(), ticket.getKey(),
        ticket.getSummary(), ticket.getIssueType(), ticket.getParentKey(), scopeSign,
        replication != null ? replication.getName() : null, replication == null);
  }
}
