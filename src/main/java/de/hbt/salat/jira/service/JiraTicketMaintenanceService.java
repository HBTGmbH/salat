package de.hbt.salat.jira.service;

import static de.hbt.salat.common.exception.ErrorCode.JI_REPLICATION_SCOPE_NOT_FOUND;
import static de.hbt.salat.common.exception.ErrorCode.JI_TICKET_IMPORT_ID_TAKEN;
import static de.hbt.salat.common.exception.ErrorCode.JI_TICKET_IMPORT_KEY_REPLICATED;
import static de.hbt.salat.common.exception.ErrorCode.JI_TICKET_KEY_REQUIRED;
import static de.hbt.salat.common.exception.ErrorCode.JI_TICKET_KEY_TAKEN;
import static de.hbt.salat.common.exception.ErrorCode.JI_TICKET_NOT_FOUND;
import static de.hbt.salat.common.exception.ErrorCode.JI_TICKET_REPLICATED;
import static de.hbt.salat.common.exception.ErrorCode.JI_TICKET_VALUE_TOO_LONG;
import static de.hbt.salat.common.exception.ServiceFeedbackMessage.error;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import de.hbt.salat.auth.domain.Authorized;
import de.hbt.salat.common.exception.InvalidDataException;
import de.hbt.salat.common.exception.ServiceFeedbackMessage;
import de.hbt.salat.common.util.DateTimeUtils;
import de.hbt.salat.jira.domain.JiraImportColumn;
import de.hbt.salat.jira.domain.JiraImportMappingEntry;
import de.hbt.salat.jira.domain.JiraImportTarget;
import de.hbt.salat.jira.domain.JiraManualTicketData;
import de.hbt.salat.jira.domain.JiraReplicationConfig;
import de.hbt.salat.jira.domain.JiraTicket;
import de.hbt.salat.jira.domain.JiraTicketDetail;
import de.hbt.salat.jira.domain.JiraTicketImport;
import de.hbt.salat.jira.domain.JiraTicketImportOrigin;
import de.hbt.salat.jira.domain.JiraTicketListFilter;
import de.hbt.salat.jira.domain.JiraTicketListResult;
import de.hbt.salat.jira.domain.JiraTicketSort;
import de.hbt.salat.jira.domain.JiraTicketImportPreview;
import de.hbt.salat.jira.domain.JiraTicketRow;
import de.hbt.salat.jira.persistence.JiraReplicationConfigRepository;
import de.hbt.salat.jira.persistence.JiraTicketImportRepository;
import de.hbt.salat.jira.persistence.JiraTicketRepository;
import de.hbt.salat.jira.persistence.OrderReferences;

/**
 * Tickets maintained by hand (#1386) — for a customer whose JIRA allows no API access or who has no
 * JIRA at all, and next to a replication for what it does not fetch (yet).
 *
 * <p>They live in {@code jira_ticket} like the replicated ones, in the same scope of order and
 * suborder, and so reach the suggestions of the booking form, the ticket filter of the booking list
 * and the reports without any of them knowing the difference. What tells them apart is the
 * replication: a ticket without one is maintained here.
 *
 * <p>Who maintains a ticket is decided per ticket, not per scope: one a replication maintains is
 * not changed here, not even by an import, and one maintained by hand stays editable until a
 * replication of the same scope delivers its key and takes it over on its next run.
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
  private final JiraTicketImportRepository importRepository;

  /** How many recent imports are searched for a file of the same headings. */
  static final int RECENT_IMPORTS = 200;

  /**
   * The ticket page (#1386): the tickets of the order, replicated and maintained by hand, as the
   * filter narrows them, up to its limit — counted are all hits. Keys with their children are
   * expanded within the order, like the ticket filter of the booking list.
   */
  @Transactional(readOnly = true)
  public JiraTicketListResult search(JiraTicketListFilter filter) {
    // Without an order, as the page opens, every order the user may see — like the booking list.
    boolean allOrders;
    List<Long> customerorderIds;
    if (filter.customerorderId() != null) {
      authorization.checkMayMaintain(filter.customerorderId());
      allOrders = false;
      customerorderIds = List.of(filter.customerorderId());
    } else {
      var maintainable = authorization.maintainableCustomerorderIds();
      allOrders = maintainable.isEmpty();
      customerorderIds = maintainable.orElse(List.of(-1L));
      if (!allOrders && customerorderIds.isEmpty()) return JiraTicketListResult.empty(List.of());
    }
    var issueTypes = ticketRepository.findIssueTypes(allOrders, customerorderIds);

    boolean allScopes = filter.suborderId() == null || filter.customerorderId() == null;
    List<Long> suborderIds = allScopes ? List.of(-1L) : scopes.branchOf(filter.suborderId());
    if (suborderIds.isEmpty()) return JiraTicketListResult.empty(issueTypes);
    var keys = filter.withChildren() ? withChildren(allOrders, customerorderIds, filter.keys()) : filter.keys();
    boolean allKeys = keys.isEmpty();
    boolean allTypes = filter.issueTypes().isEmpty();
    Collection<String> keyList = allKeys ? List.of("") : keys;
    Collection<String> typeList = allTypes ? List.of("") : filter.issueTypes();

    var countByType = new LinkedHashMap<String, Long>();
    long total = 0;
    long replicated = 0;
    var counts = new ArrayList<>(ticketRepository.countForTicketPage(allOrders, customerorderIds, allScopes,
        suborderIds, allKeys, keyList, filter.title(), allTypes, typeList));
    counts.sort(Comparator.comparing((Object[] row) -> (Long) row[1]).reversed()
        .thenComparing(row -> row[0] == null ? "" : (String) row[0]));
    for (var row : counts) {
      countByType.put(row[0] == null ? "" : (String) row[0], (Long) row[1]);
      total += (Long) row[1];
      replicated += (Long) row[2];
    }
    if (total == 0) return JiraTicketListResult.empty(issueTypes);

    var tickets = ticketRepository.findForTicketPage(allOrders, customerorderIds, allScopes, suborderIds, allKeys,
        keyList, filter.title(), allTypes, typeList, PageRequest.of(0, filter.maxResults(), sortOf(filter)));
    return new JiraTicketListResult(toRows(tickets), total, replicated, countByType, issueTypes);
  }


  /** Everything about one ticket, with the tickets that name it as parent. */
  @Transactional(readOnly = true)
  public JiraTicketDetail getDetail(long id) {
    var ticket = load(id);
    authorization.checkMayMaintain(ticket.getCustomerorderId());
    var row = toRow(ticket, scopes.signOf(ticket.getCustomerorderId(), ticket.getSuborderId()));
    var labels = ticket.getLabels() == null ? List.<String>of()
        : Arrays.stream(ticket.getLabels().split(",")).map(String::trim).filter(label -> !label.isEmpty()).toList();
    var children = toRows(ticketRepository.findChildrenInScope(ticket.getCustomerorderId(), ticket.getSuborderId(),
        ticket.getKey()));
    return new JiraTicketDetail(row, ticket.getJiraId(), labels, ticket.getCreatedTs(), ticket.getCreatedby(),
        ticket.getLastupdate(), ticket.getLastupdatedby(),
        ticket.getCustomFields() == null ? Map.of() : new TreeMap<>(ticket.getCustomFields()),
        ticket.getCustomFieldsEffective() == null ? Map.of() : new TreeMap<>(ticket.getCustomFieldsEffective()),
        children, importOf(ticket).map(JiraTicketImport::getFileName).orElse(null),
        importOf(ticket).map(JiraTicketImport::getCreated).orElse(null),
        importOf(ticket).map(JiraTicketImport::getCreatedby).orElse(null));
  }

  /**
   * The ticket a parent or top-level key of ticket {@code fromId} names: in its own scope first —
   * a parent chain stays within it — otherwise anywhere in the order. Empty where the order has no
   * ticket with that key; a reference in JIRA may well name one that is not maintained here.
   */
  @Transactional(readOnly = true)
  public Optional<Long> findRelated(long fromId, String key) {
    var from = load(fromId);
    authorization.checkMayMaintain(from.getCustomerorderId());
    return ticketRepository.findInScopeByKey(from.getCustomerorderId(), from.getSuborderId(), key)
        .or(() -> ticketRepository.findInCustomerorderByKey(from.getCustomerorderId(), key).stream().findFirst())
        .map(JiraTicket::getId);
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
    checkScopeExists(customerorderId, suborderId);
    var values = validated(data);
    checkKeyFree(customerorderId, suborderId, values.key(), null);
    var ticket = newTicket(customerorderId, suborderId);
    apply(values, ticket);
    var id = ticketRepository.save(ticket).getId();
    resolveParentChains(customerorderId, suborderId);
    return id;
  }

  /** The scope stays: a ticket moved to another one is a different ticket there. */
  public void update(long id, JiraManualTicketData data) {
    var ticket = loadMaintainedByHand(id);
    var values = validated(data);
    checkKeyFree(ticket.getCustomerorderId(), ticket.getSuborderId(), values.key(), ticket.getId());
    apply(values, ticket);
    ticketRepository.save(ticket);
    resolveParentChains(ticket.getCustomerorderId(), ticket.getSuborderId());
  }

  /** Bookings carrying the key keep it: their ticket reference is text, not a foreign key (#982). */
  public void delete(long id) {
    var ticket = loadMaintainedByHand(id);
    ticketRepository.delete(ticket);
    ticketRepository.flush();
    resolveParentChains(ticket.getCustomerorderId(), ticket.getSuborderId());
  }

  /**
   * What a ticket file holds and how its headings would be read — nothing is saved. The file is sent
   * again with the import; keeping it here in between would need a session (ADR-0013).
   */
  @Transactional(readOnly = true)
  public JiraTicketImportPreview preview(byte[] content, Long customerorderId, Long suborderId) {
    var file = JiraTicketFile.read(content);
    if (customerorderId == null || !authorization.mayMaintain(customerorderId)) {
      return JiraTicketImportReader.preview(file);
    }
    var previous = previousImportFor(file.headings(), customerorderId, suborderId);
    var preview = JiraTicketImportReader.preview(file, previous.map(JiraTicketImport::getColumnMapping).orElse(List.of()));
    return previous.map(earlier -> preview.withOrigin(new JiraTicketImportOrigin(earlier.getFileName(),
            earlier.getCreated(), earlier.getCustomerorderId() == null ? null
                : scopes.signOf(earlier.getCustomerorderId(), earlier.getSuborderId()))))
        .orElse(preview);
  }

  /**
   * The earlier import whose column reading the preview proposes: the latest of the scope, otherwise
   * the latest of the order in any scope, otherwise the latest of a file with exactly these headings
   * among the orders the user may see.
   */
  private Optional<JiraTicketImport> previousImportFor(List<String> headings, long customerorderId, Long suborderId) {
    return latestImport(customerorderId, suborderId)
        .or(() -> importRepository.findLatestInCustomerorder(customerorderId, PageRequest.of(0, 1)).stream().findFirst())
        .or(() -> {
          var maintainable = authorization.maintainableCustomerorderIds();
          var recent = importRepository.findLatest(maintainable.isEmpty(), maintainable.orElse(List.of(-1L)),
              PageRequest.of(0, RECENT_IMPORTS));
          var wanted = normalised(headings);
          return recent.stream()
              .filter(earlier -> normalised(earlier.getColumnMapping().stream()
                  .map(JiraImportMappingEntry::heading).toList()).equals(wanted))
              .findFirst();
        });
  }

  private static List<String> normalised(List<String> headings) {
    return headings.stream().map(heading -> heading == null ? "" : heading.trim().toLowerCase(Locale.ROOT)).toList();
  }

  /**
   * Creates the tickets of a file, or updates them where the scope already has the key, reading the
   * columns as {@code mapping} says — one entry per column. Every faulty row is reported with its
   * number, and then nothing is saved.
   *
   * @return how many tickets the file named
   */
  public int importTickets(long customerorderId, Long suborderId, String fileName, byte[] content,
                           List<JiraImportColumn> mapping) {
    authorization.checkMayMaintain(customerorderId);
    checkScopeExists(customerorderId, suborderId);
    var file = JiraTicketFile.read(content);

    var inScope = ticketRepository.findInScope(customerorderId, suborderId);
    var byKey = new HashMap<String, JiraTicket>();
    var byJiraId = new HashMap<Long, JiraTicket>();
    inScope.forEach(ticket -> {
      byKey.put(ticket.getKey(), ticket);
      if (ticket.getJiraId() != null) byJiraId.put(ticket.getJiraId(), ticket);
    });
    var keysByJiraId = byJiraId.values().stream().collect(Collectors.toMap(JiraTicket::getJiraId, JiraTicket::getKey));
    var tickets = JiraTicketImportReader.read(file, mapping, keysByJiraId);

    var findings = new ArrayList<ServiceFeedbackMessage>();
    for (var ticket : tickets) {
      var stored = byKey.get(ticket.key());
      if (stored != null && stored.isReplicated()) {
        findings.add(error(JI_TICKET_IMPORT_KEY_REPLICATED, ticket.line(), ticket.key(),
            stored.getReplication().getName()));
      }
      var carrier = ticket.jiraId() == null ? null : byJiraId.get(ticket.jiraId());
      if (carrier != null && !carrier.getKey().equals(ticket.key())) {
        findings.add(error(JI_TICKET_IMPORT_ID_TAKEN, ticket.line(), ticket.jiraId(), carrier.getKey()));
      }
    }
    if (!findings.isEmpty()) {
      throw new InvalidDataException(findings);
    }

    var ticketImport = new JiraTicketImport();
    ticketImport.setCustomerorder(orderReferences.customerorder(customerorderId));
    ticketImport.setSuborder(orderReferences.suborder(suborderId));
    ticketImport.setFileName(fileName);
    ticketImport.setColumnMapping(mappingEntries(file.headings(), mapping));
    var toSave = new ArrayList<JiraTicket>();
    for (var imported : tickets) {
      var ticket = byKey.get(imported.key());
      if (ticket == null) {
        ticket = newTicket(customerorderId, suborderId);
        ticketImport.setCreatedCount(ticketImport.getCreatedCount() + 1);
      } else {
        ticketImport.setUpdatedCount(ticketImport.getUpdatedCount() + 1);
      }
      apply(imported, mapping, ticket);
      ticket.setTicketImport(ticketImport);
      toSave.add(ticket);
    }
    importRepository.save(ticketImport);
    ticketRepository.saveAll(toSave);
    resolveParentChains(customerorderId, suborderId);
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

  private void checkScopeExists(long customerorderId, Long suborderId) {
    coveringReplications(customerorderId, suborderId);
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
    if (longerThan(values.key(), JiraTicketImportReader.KEY_LENGTH) || longerThan(values.parentKey(), JiraTicketImportReader.KEY_LENGTH)
        || longerThan(values.summary(), JiraTicketImportReader.SUMMARY_LENGTH)
        || longerThan(values.issueType(), JiraTicketImportReader.ISSUE_TYPE_LENGTH)) {
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
  private static void apply(JiraTicketImportReader.Ticket imported, List<JiraImportColumn> mapping, JiraTicket ticket) {
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

  /** The reading of every column, by its heading, as the import keeps it. */
  private static List<JiraImportMappingEntry> mappingEntries(List<String> headings, List<JiraImportColumn> mapping) {
    var entries = new ArrayList<JiraImportMappingEntry>();
    for (int column = 0; column < headings.size(); column++) {
      var assigned = mapping.get(column);
      var fieldName = assigned.target() == JiraImportTarget.ADDITIONAL ? assigned.fieldName().trim() : null;
      entries.add(new JiraImportMappingEntry(headings.get(column), assigned.target(), fieldName,
          assigned.target() == JiraImportTarget.ADDITIONAL && assigned.inherited()));
    }
    return entries;
  }

  private static Optional<JiraTicketImport> importOf(JiraTicket ticket) {
    return Optional.ofNullable(ticket.getTicketImport());
  }

  private Optional<JiraTicketImport> latestImport(long customerorderId, Long suborderId) {
    return importRepository.findLatestInScope(customerorderId, suborderId, PageRequest.of(0, 1)).stream().findFirst();
  }


  /**
   * Derives top-level key and inherited fields of every ticket of the scope (#881), as a replication
   * does after its run: they are never entered, and the reports group by the one and read the other.
   * Inherited are the fields of the replications of the scope and of its latest import.
   */
  private void resolveParentChains(long customerorderId, Long suborderId) {
    var tickets = ticketRepository.findInScope(customerorderId, suborderId);
    var inherited = JiraTicketChains.inheritedFields(configRepository.findInScope(customerorderId, suborderId),
        latestImport(customerorderId, suborderId));
    ticketRepository.saveAll(JiraTicketChains.resolve(tickets, inherited));
  }



  private static String trimToNull(String value) {
    if (value == null) return null;
    var trimmed = value.trim();
    return trimmed.isEmpty() ? null : trimmed;
  }

  private static List<Long> scopeOf(JiraTicket ticket) {
    return Arrays.asList(ticket.getCustomerorderId(), ticket.getSuborderId());
  }

  /** The column asked for, nulls last, ties by key — so that equal values keep a stable order. */
  private static Sort sortOf(JiraTicketListFilter filter) {
    var direction = filter.descending() ? Sort.Direction.DESC : Sort.Direction.ASC;
    var order = new Sort.Order(direction, filter.sort().getAttribute()).nullsLast();
    return filter.sort() == JiraTicketSort.KEY ? Sort.by(order) : Sort.by(order, Sort.Order.asc("key"));
  }

  /** The scope signs are looked up once per scope, not per row. */
  private List<JiraTicketRow> toRows(List<JiraTicket> tickets) {
    var signs = new HashMap<List<Long>, String>();
    return tickets.stream()
        .map(ticket -> toRow(ticket, signs.computeIfAbsent(scopeOf(ticket),
            scope -> scopes.signOf(ticket.getCustomerorderId(), ticket.getSuborderId()))))
        .toList();
  }

  /** The keys and every ticket below them in these orders, level by level; comparison ignores case. */
  private List<String> withChildren(boolean allOrders, List<Long> customerorderIds, List<String> keys) {
    if (keys.isEmpty()) return keys;
    var result = new LinkedHashSet<>(keys);
    var links = ticketRepository.findParentLinks(allOrders, customerorderIds);
    boolean grown = true;
    while (grown) {
      grown = false;
      for (var link : links) {
        if (link.parentKey() != null && result.contains(link.parentKey().toUpperCase(Locale.ROOT))
            && result.add(link.key().toUpperCase(Locale.ROOT))) {
          grown = true;
        }
      }
    }
    return List.copyOf(result);
  }

  private static JiraTicketRow toRow(JiraTicket ticket, String scopeSign) {
    var replication = ticket.getReplication();
    return new JiraTicketRow(ticket.getId(), ticket.getCustomerorderId(), ticket.getSuborderId(), ticket.getKey(),
        ticket.getSummary(), ticket.getIssueType(), ticket.getParentKey(), ticket.getTopLevelKey(), scopeSign,
        replication != null ? replication.getId() : null, replication != null ? replication.getName() : null,
        replication == null, ticket.getUpdatedTs(), ticket.getCreated());
  }
}
