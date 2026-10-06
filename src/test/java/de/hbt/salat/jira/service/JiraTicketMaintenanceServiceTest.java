package de.hbt.salat.jira.service;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.entry;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.anyIterable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Pageable;
import de.hbt.salat.auth.domain.AuthorizedUser;
import de.hbt.salat.common.domain.AuditedEntity;
import de.hbt.salat.common.exception.AuthorizationException;
import de.hbt.salat.common.exception.ErrorCode;
import de.hbt.salat.common.exception.ErrorCodeException;
import de.hbt.salat.common.exception.InvalidDataException;
import de.hbt.salat.jira.auth.JiraTicketAuthorization;
import de.hbt.salat.common.exception.ServiceFeedbackMessage;
import de.hbt.salat.jira.domain.JiraImportColumn;
import de.hbt.salat.jira.domain.JiraImportMappingEntry;
import de.hbt.salat.jira.domain.JiraImportTarget;
import de.hbt.salat.jira.domain.JiraManualTicketData;
import de.hbt.salat.jira.domain.JiraReplicationConfig;
import de.hbt.salat.jira.domain.JiraTicket;
import de.hbt.salat.jira.domain.JiraTicketImport;
import de.hbt.salat.jira.domain.JiraTicketListFilter;
import de.hbt.salat.jira.domain.JiraTicketParentLink;
import de.hbt.salat.jira.domain.JiraTicketRow;
import de.hbt.salat.jira.domain.ResolvedFieldValue;
import de.hbt.salat.jira.persistence.JiraReplicationConfigRepository;
import de.hbt.salat.jira.persistence.JiraTicketImportRepository;
import de.hbt.salat.jira.persistence.JiraTicketRepository;
import de.hbt.salat.jira.persistence.OrderReferences;
import de.hbt.salat.order.domain.Customerorder;
import de.hbt.salat.order.domain.CustomerorderOption;
import de.hbt.salat.order.domain.SuborderLocation;
import de.hbt.salat.order.service.CustomerorderService;
import de.hbt.salat.order.service.SuborderService;

/**
 * Tickets maintained by hand (#1386): one by one and by CSV import, only where no replication covers
 * the scope, and only for managers and the people responsible for the order.
 */
@DisplayNameGeneration(ReplaceUnderscores.class)
class JiraTicketMaintenanceServiceTest {

  private static final long ORDER = 7L;
  private static final long OTHER_ORDER = 8L;
  private static final long SUBORDER = 70L;
  private static final long USER = 42L;

  private JiraTicketRepository ticketRepository;
  private JiraReplicationConfigRepository configRepository;
  private AuthorizedUser authorizedUser;
  private CustomerorderService customerorderService;
  private JiraScopes scopes;
  private OrderReferences orderReferences;
  private JiraTicketImportRepository importRepository;
  private SuborderService suborderService;
  private final List<JiraTicketImport> imports = new ArrayList<>();
  private JiraTicketMaintenanceService service;

  @BeforeEach
  void setUp() {
    ticketRepository = mock(JiraTicketRepository.class);
    configRepository = mock(JiraReplicationConfigRepository.class);
    authorizedUser = mock(AuthorizedUser.class);
    customerorderService = mock(CustomerorderService.class);
    scopes = mock(JiraScopes.class);
    orderReferences = mock(OrderReferences.class);
    importRepository = mock(JiraTicketImportRepository.class);
    suborderService = mock(SuborderService.class);
    // The imports saved, the latest first — as the repository answers for the scope.
    when(importRepository.save(any(JiraTicketImport.class))).thenAnswer(invocation -> {
      imports.addFirst(invocation.getArgument(0));
      return invocation.getArgument(0);
    });
    when(importRepository.findLatestInScope(anyLong(), any(), any())).thenAnswer(invocation -> List.copyOf(imports));
    var authorization = new JiraTicketAuthorization(authorizedUser, customerorderService);
    service = new JiraTicketMaintenanceService(ticketRepository, configRepository, authorization, scopes,
        orderReferences, importRepository, suborderService);

    when(authorizedUser.isManager()).thenReturn(true);
    when(scopes.customerorderExists(ORDER)).thenReturn(true);
    when(scopes.locationOf(SUBORDER))
        .thenReturn(Optional.of(new SuborderLocation(SUBORDER, ORDER, List.of(SUBORDER), "ALPHA/01")));
    var order = new Customerorder();
    setId(order, ORDER);
    when(orderReferences.customerorder(ORDER)).thenReturn(order);
    when(ticketRepository.save(any(JiraTicket.class))).thenAnswer(invocation -> {
      JiraTicket ticket = invocation.getArgument(0);
      if (ticket.getId() == null) setId(ticket, 99L);
      return ticket;
    });
  }

  @Test
  void a_ticket_is_created_in_the_chosen_scope_without_jira_id_and_replication() {
    service.create(ORDER, null, new JiraManualTicketData("  ABC-1 ", "Titel", "Story", null));

    var saved = savedTicket();
    assertThat(saved.getKey()).isEqualTo("ABC-1");
    assertThat(saved.getSummary()).isEqualTo("Titel");
    assertThat(saved.getIssueType()).isEqualTo("Story");
    assertThat(saved.getCustomerorderId()).isEqualTo(ORDER);
    assertThat(saved.getJiraId()).isNull();
    assertThat(saved.isReplicated()).isFalse();
    assertThat(saved.getUpdatedTs()).isNotNull();
  }

  @Test
  void a_ticket_needs_a_key() {
    assertThatThrownBy(() -> service.create(ORDER, null, new JiraManualTicketData("  ", "Titel", null, null)))
        .satisfies(ex -> assertThat(firstCode(ex)).isEqualTo(ErrorCode.JI_TICKET_KEY_REQUIRED));
    verify(ticketRepository, never()).save(any(JiraTicket.class));
  }

  @Test
  void a_key_is_unique_within_the_scope() {
    when(ticketRepository.findInScopeByKey(ORDER, null, "ABC-1")).thenReturn(Optional.of(manual("ABC-1", 5L)));

    assertThatThrownBy(() -> service.create(ORDER, null, new JiraManualTicketData("ABC-1", null, null, null)))
        .satisfies(ex -> assertThat(firstCode(ex)).isEqualTo(ErrorCode.JI_TICKET_KEY_TAKEN));
  }

  @Test
  void a_value_longer_than_its_column_is_refused() {
    var data = new JiraManualTicketData("ABC-1", null, "x".repeat(129), null);

    assertThatThrownBy(() -> service.create(ORDER, null, data))
        .satisfies(ex -> assertThat(firstCode(ex)).isEqualTo(ErrorCode.JI_TICKET_VALUE_TOO_LONG));
  }

  /** Who maintains a ticket is decided per ticket: a replication on the scope blocks nothing. */
  @Test
  void a_ticket_is_created_even_where_a_replication_covers_the_scope() {
    when(configRepository.findCovering(ORDER, List.of(SUBORDER))).thenReturn(List.of(replication("Alpha-JIRA")));

    service.create(ORDER, SUBORDER, new JiraManualTicketData("ABC-1", null, null, null));

    verify(ticketRepository).save(any(JiraTicket.class));
    assertThat(service.getCoveringReplications(ORDER, SUBORDER)).containsExactly("Alpha-JIRA");
  }

  @Test
  void a_suborder_of_another_order_is_no_scope() {
    when(scopes.locationOf(SUBORDER))
        .thenReturn(Optional.of(new SuborderLocation(SUBORDER, OTHER_ORDER, List.of(SUBORDER), "BETA/01")));

    assertThatThrownBy(() -> service.create(ORDER, SUBORDER, new JiraManualTicketData("ABC-1", null, null, null)))
        .satisfies(ex -> assertThat(firstCode(ex)).isEqualTo(ErrorCode.JI_REPLICATION_SCOPE_NOT_FOUND));
  }

  @Test
  void a_ticket_maintained_by_hand_is_changed() {
    var stored = manual("ABC-1", 5L);
    when(ticketRepository.findById(5L)).thenReturn(Optional.of(stored));

    service.update(5L, new JiraManualTicketData("ABC-1", "Neu", "Bug", "ABC-0"));

    assertThat(stored.getSummary()).isEqualTo("Neu");
    assertThat(stored.getIssueType()).isEqualTo("Bug");
    assertThat(stored.getParentKey()).isEqualTo("ABC-0");
  }

  @Test
  void a_replicated_ticket_is_neither_changed_nor_deleted_by_hand() {
    var replicated = manual("ABC-1", 5L);
    replicated.setReplication(replication("Alpha-JIRA"));
    when(ticketRepository.findById(5L)).thenReturn(Optional.of(replicated));

    assertThatThrownBy(() -> service.update(5L, new JiraManualTicketData("ABC-1", null, null, null)))
        .satisfies(ex -> assertThat(firstCode(ex)).isEqualTo(ErrorCode.JI_TICKET_REPLICATED));
    assertThatThrownBy(() -> service.delete(5L))
        .satisfies(ex -> assertThat(firstCode(ex)).isEqualTo(ErrorCode.JI_TICKET_REPLICATED));
    verify(ticketRepository, never()).delete(any(JiraTicket.class));
  }

  @Test
  void a_ticket_maintained_by_hand_is_deleted() {
    var stored = manual("ABC-1", 5L);
    when(ticketRepository.findById(5L)).thenReturn(Optional.of(stored));

    service.delete(5L);

    verify(ticketRepository).delete(stored);
  }

  /** The reports group by the top-level key, which the replication writes for its own tickets (#881). */
  @Test
  void the_top_level_key_follows_the_parent_chain_within_the_scope() {
    var epic = manual("ABC-1", 1L);
    var story = manual("ABC-2", 2L);
    story.setParentKey("ABC-1");
    var task = manual("ABC-3", 3L);
    task.setParentKey("ABC-2");
    when(ticketRepository.findInScope(ORDER, null)).thenReturn(List.of(epic, story, task));

    service.create(ORDER, null, new JiraManualTicketData("ABC-4", null, null, null));

    assertThat(task.getTopLevelKey()).isEqualTo("ABC-1");
    assertThat(story.getTopLevelKey()).isEqualTo("ABC-1");
    assertThat(epic.getTopLevelKey()).isEqualTo("ABC-1");
  }

  /** A JIRA export as it comes: repeated label columns, a parent by id, a parent's title, a custom field. */
  private static final String JIRA_EXPORT = """
      Summary,Issue key,Issue id,Issue Type,Labels,Labels,Parent,Parent summary,Custom field (Team),Created
      Epic eins,ABC-1,10001,Epic,alpha,,,,Blau,25/Jun/26 3:05 PM
      Story zwei,ABC-2,10002,Story,alpha,beta,10001,Epic eins,,2026-06-26 09:00
      """;

  @Test
  void a_file_creates_its_tickets_as_the_columns_are_assigned() {
    int count = service.importTickets(ORDER, null, "tickets.csv", JIRA_EXPORT.getBytes(UTF_8), jiraExportMapping(false));

    assertThat(count).isEqualTo(2);
    var saved = savedAll();
    assertThat(saved).extracting(JiraTicket::getKey, JiraTicket::getJiraId, JiraTicket::getIssueType,
            JiraTicket::getLabels, JiraTicket::getParentKey)
        .containsExactly(
            tuple("ABC-1", 10001L, "Epic", "alpha", null),
            tuple("ABC-2", 10002L, "Story", "alpha,beta", "ABC-1"));
    assertThat(saved.get(0).getCreatedTs()).isEqualTo(LocalDateTime.of(2026, 6, 25, 15, 5));
    assertThat(saved.get(0).getCustomFields()).containsEntry("customfield_10500", "Blau");
    assertThat(saved.get(1).getCustomFields()).isNull();
    assertThat(saved).allSatisfy(ticket -> assertThat(ticket.isReplicated()).isFalse());
  }

  /** As the replication does for its own (#881): a value missing on the ticket comes from its parent. */
  @Test
  void an_inherited_additional_field_is_resolved_along_the_parent_chain() {
    var saved = new ArrayList<JiraTicket>();
    when(ticketRepository.saveAll(anyIterable())).thenAnswer(invocation -> {
      Iterable<JiraTicket> tickets = invocation.getArgument(0);
      tickets.forEach(saved::add);
      return List.of();
    });
    when(ticketRepository.findInScope(ORDER, null)).thenAnswer(invocation -> List.copyOf(saved));

    service.importTickets(ORDER, null, "tickets.csv", JIRA_EXPORT.getBytes(UTF_8), jiraExportMapping(true));

    var story = saved.stream().filter(ticket -> ticket.getKey().equals("ABC-2")).findFirst().orElseThrow();
    assertThat(story.getCustomFieldsEffective())
        .containsEntry("customfield_10500", new ResolvedFieldValue("Blau", "ABC-1"));
    assertThat(story.getTopLevelKey()).isEqualTo("ABC-1");
  }

  /** The import is kept with its file, its column reading and what came of it; its tickets point at it. */
  @Test
  void an_import_is_kept_and_its_tickets_point_at_it() {
    var stored = manual("ABC-1", 5L);
    when(ticketRepository.findInScope(ORDER, null)).thenReturn(List.of(stored));

    service.importTickets(ORDER, null, "tickets.csv", "Key;Team\nABC-1;Blau\nABC-2;Rot\n".getBytes(UTF_8),
        List.of(column(JiraImportTarget.KEY), new JiraImportColumn(JiraImportTarget.ADDITIONAL, " team ", true)));

    assertThat(imports).singleElement().satisfies(ticketImport -> {
      assertThat(ticketImport.getFileName()).isEqualTo("tickets.csv");
      assertThat(ticketImport.getCreatedCount()).isEqualTo(1);
      assertThat(ticketImport.getUpdatedCount()).isEqualTo(1);
      assertThat(ticketImport.getColumnMapping()).containsExactly(
          new JiraImportMappingEntry("Key", JiraImportTarget.KEY, null, false),
          new JiraImportMappingEntry("Team", JiraImportTarget.ADDITIONAL, "team", true));
      assertThat(ticketImport.inheritedFields()).containsExactly("team");
    });
    assertThat(savedAll()).allSatisfy(ticket -> assertThat(ticket.getTicketImport()).isSameAs(imports.getFirst()));
  }

  /** The latest import of the scope decides the inheritance: importing again without the mark switches it off. */
  @Test
  void the_latest_import_switches_an_inheritance_off() {
    var saved = new ArrayList<JiraTicket>();
    when(ticketRepository.saveAll(anyIterable())).thenAnswer(invocation -> {
      Iterable<JiraTicket> tickets = invocation.getArgument(0);
      tickets.forEach(ticket -> { if (!saved.contains(ticket)) saved.add(ticket); });
      return List.of();
    });
    when(ticketRepository.findInScope(ORDER, null)).thenAnswer(invocation -> List.copyOf(saved));
    var file = "Key;Parent;Team\nABC-1;;Blau\nABC-2;ABC-1;\n".getBytes(UTF_8);
    var child = (java.util.function.Supplier<JiraTicket>) () ->
        saved.stream().filter(ticket -> ticket.getKey().equals("ABC-2")).findFirst().orElseThrow();

    service.importTickets(ORDER, null, "a.csv", file, List.of(column(JiraImportTarget.KEY),
        column(JiraImportTarget.PARENT), new JiraImportColumn(JiraImportTarget.ADDITIONAL, "team", true)));
    assertThat(child.get().getCustomFieldsEffective()).containsEntry("team", new ResolvedFieldValue("Blau", "ABC-1"));

    service.importTickets(ORDER, null, "b.csv", file, List.of(column(JiraImportTarget.KEY),
        column(JiraImportTarget.PARENT), new JiraImportColumn(JiraImportTarget.ADDITIONAL, "team", false)));
    assertThat(child.get().getCustomFieldsEffective()).isNull();
  }

  /** The preview proposes how the latest import of the scope read the columns of the same headings. */
  @Test
  void the_preview_proposes_the_reading_of_the_latest_import() {
    var earlier = new JiraTicketImport();
    earlier.setColumnMapping(List.of(new JiraImportMappingEntry("Issue key", JiraImportTarget.KEY, null, false),
        new JiraImportMappingEntry("Custom field (Team)", JiraImportTarget.ADDITIONAL, "customfield_10500", true)));
    imports.add(earlier);

    var preview = service.preview(JIRA_EXPORT.getBytes(UTF_8), ORDER, null);

    assertThat(preview.suggested().get(8))
        .isEqualTo(new JiraImportColumn(JiraImportTarget.ADDITIONAL, "customfield_10500", true));
    assertThat(preview.suggested().get(1).target()).isEqualTo(JiraImportTarget.KEY);
  }

  /** Without an import in the scope, the latest of the order proposes the reading — and says so. */
  @Test
  void without_an_import_in_the_scope_the_latest_of_the_order_is_proposed() {
    var ofOrder = new JiraTicketImport();
    ofOrder.setFileName("alt.csv");
    ofOrder.setColumnMapping(List.of(
        new JiraImportMappingEntry("Custom field (Team)", JiraImportTarget.ADDITIONAL, "customfield_10500", true)));
    when(importRepository.findLatestInCustomerorder(eq(ORDER), any())).thenReturn(List.of(ofOrder));
    when(scopes.signOf(anyLong(), any())).thenReturn("ALPHA");

    var preview = service.preview(JIRA_EXPORT.getBytes(UTF_8), ORDER, SUBORDER);

    assertThat(preview.suggested().get(8))
        .isEqualTo(new JiraImportColumn(JiraImportTarget.ADDITIONAL, "customfield_10500", true));
    assertThat(preview.origin().fileName()).isEqualTo("alt.csv");
  }

  /** Without any import in the order, the latest file with exactly these headings is proposed. */
  @Test
  void without_an_import_in_the_order_a_file_with_the_same_headings_is_proposed() {
    var otherShape = new JiraTicketImport();
    otherShape.setFileName("anders.csv");
    otherShape.setColumnMapping(List.of(new JiraImportMappingEntry("Key", JiraImportTarget.KEY, null, false)));
    var sameShape = new JiraTicketImport();
    sameShape.setFileName("gleich.csv");
    var headings = List.of("Summary", "Issue key", "Issue id", "Issue Type", "Labels", "Labels", "Parent",
        "Parent summary", "Custom field (Team)", "Created");
    var entries = new ArrayList<JiraImportMappingEntry>();
    headings.forEach(heading -> entries.add(new JiraImportMappingEntry(heading.toUpperCase(),
        heading.startsWith("Custom") ? JiraImportTarget.ADDITIONAL : JiraImportTarget.IGNORE,
        heading.startsWith("Custom") ? "team" : null, false)));
    sameShape.setColumnMapping(entries);
    when(importRepository.findLatest(eq(true), any(), any())).thenReturn(List.of(otherShape, sameShape));

    var preview = service.preview(JIRA_EXPORT.getBytes(UTF_8), ORDER, null);

    assertThat(preview.origin().fileName()).isEqualTo("gleich.csv");
    assertThat(preview.suggested().get(8)).isEqualTo(new JiraImportColumn(JiraImportTarget.ADDITIONAL, "team", false));
  }

  /** A key the scope already has is updated, not created a second time. */
  @Test
  void an_existing_key_is_updated_by_the_import() {
    var stored = manual("ABC-1", 5L);
    when(ticketRepository.findInScope(ORDER, null)).thenReturn(List.of(stored));

    service.importTickets(ORDER, null, "tickets.csv", "Key;Summary\nABC-1;Neuer Titel\n".getBytes(UTF_8),
        List.of(column(JiraImportTarget.KEY), column(JiraImportTarget.SUMMARY)));

    assertThat(savedAll()).containsExactly(stored);
    assertThat(stored.getSummary()).isEqualTo("Neuer Titel");
  }

  @Test
  void faulty_lines_are_reported_with_their_number_and_nothing_is_saved() {
    var file = """
        Key;Id;Created
        ABC-1;1;2026-01-01
        ;2;
        ABC-1;3;
        ABC-4;keine Zahl;
        ABC-5;5;gestern
        """;
    var mapping = List.of(column(JiraImportTarget.KEY), column(JiraImportTarget.ID), column(JiraImportTarget.CREATED));

    assertThatThrownBy(() -> service.importTickets(ORDER, null, "tickets.csv", file.getBytes(UTF_8), mapping))
        .isInstanceOf(InvalidDataException.class)
        .satisfies(ex -> assertThat(((ErrorCodeException) ex).getMessages())
            .extracting(ServiceFeedbackMessage::getErrorCode, message -> message.getArguments().get(0))
            .containsExactly(
                tuple(ErrorCode.JI_TICKET_IMPORT_KEY_MISSING, 3),
                tuple(ErrorCode.JI_TICKET_IMPORT_KEY_TWICE, 4),
                tuple(ErrorCode.JI_TICKET_IMPORT_ID_INVALID, 5),
                tuple(ErrorCode.JI_TICKET_IMPORT_DATE_INVALID, 6)));
    verify(ticketRepository, never()).saveAll(anyIterable());
  }

  @Test
  void an_id_another_ticket_of_the_scope_carries_is_refused() {
    var other = manual("ABC-9", 5L);
    other.setJiraId(1L);
    when(ticketRepository.findInScope(ORDER, null)).thenReturn(List.of(other));

    assertThatThrownBy(() -> service.importTickets(ORDER, null, "tickets.csv", "Key;Id\nABC-1;1\n".getBytes(UTF_8),
        List.of(column(JiraImportTarget.KEY), column(JiraImportTarget.ID))))
        .satisfies(ex -> assertThat(firstCode(ex)).isEqualTo(ErrorCode.JI_TICKET_IMPORT_ID_TAKEN));
  }

  @Test
  void the_assignment_needs_a_key_column_and_one_column_per_single_field() {
    var file = "Key;Summary;Title;Extra\nABC-1;a;b;c\n".getBytes(UTF_8);

    assertThatThrownBy(() -> service.importTickets(ORDER, null, "tickets.csv", file, List.of(column(JiraImportTarget.SUMMARY),
        column(JiraImportTarget.IGNORE), column(JiraImportTarget.IGNORE), column(JiraImportTarget.IGNORE))))
        .satisfies(ex -> assertThat(firstCode(ex)).isEqualTo(ErrorCode.JI_TICKET_IMPORT_NO_KEY_COLUMN));
    assertThatThrownBy(() -> service.importTickets(ORDER, null, "tickets.csv", file, List.of(column(JiraImportTarget.KEY),
        column(JiraImportTarget.SUMMARY), column(JiraImportTarget.SUMMARY), column(JiraImportTarget.IGNORE))))
        .satisfies(ex -> assertThat(firstCode(ex)).isEqualTo(ErrorCode.JI_TICKET_IMPORT_TARGET_TWICE));
    assertThatThrownBy(() -> service.importTickets(ORDER, null, "tickets.csv", file, List.of(column(JiraImportTarget.KEY),
        column(JiraImportTarget.SUMMARY), column(JiraImportTarget.IGNORE),
        new JiraImportColumn(JiraImportTarget.ADDITIONAL, " ", false))))
        .satisfies(ex -> assertThat(firstCode(ex)).isEqualTo(ErrorCode.JI_TICKET_IMPORT_FIELD_NAME_MISSING));
    assertThatThrownBy(() -> service.importTickets(ORDER, null, "tickets.csv", file, List.of(column(JiraImportTarget.KEY))))
        .satisfies(ex -> assertThat(firstCode(ex)).isEqualTo(ErrorCode.JI_TICKET_IMPORT_MAPPING_MISMATCH));
    verify(ticketRepository, never()).saveAll(anyIterable());
  }

  @Test
  void a_file_with_only_headings_contains_no_tickets() {
    assertThatThrownBy(() -> service.importTickets(ORDER, null, "tickets.csv", "Key;Summary\n".getBytes(UTF_8),
        List.of(column(JiraImportTarget.KEY), column(JiraImportTarget.SUMMARY))))
        .satisfies(ex -> assertThat(firstCode(ex)).isEqualTo(ErrorCode.JI_TICKET_IMPORT_EMPTY));
  }

  /** A ticket a replication maintains is not overwritten by an import; the others of the file wait. */
  @Test
  void an_import_does_not_overwrite_a_replicated_ticket() {
    var replicated = manual("ABC-1", 5L);
    replicated.setReplication(replication("Alpha-JIRA"));
    when(ticketRepository.findInScope(ORDER, null)).thenReturn(List.of(replicated));

    assertThatThrownBy(() -> service.importTickets(ORDER, null, "tickets.csv", "Key\nABC-2\nABC-1\n".getBytes(UTF_8),
        List.of(column(JiraImportTarget.KEY))))
        .satisfies(ex -> assertThat(((ErrorCodeException) ex).getMessages())
            .extracting(ServiceFeedbackMessage::getErrorCode, message -> message.getArguments().get(0))
            .containsExactly(tuple(ErrorCode.JI_TICKET_IMPORT_KEY_REPLICATED, 3)));
    verify(ticketRepository, never()).saveAll(anyIterable());
  }

  /** Figures over every hit; the rows up to the limit (#1386). */
  @Test
  void the_list_counts_every_hit_per_type_and_lists_up_to_the_limit() {
    when(ticketRepository.findIssueTypes(false, List.of(ORDER))).thenReturn(List.of("Bug", "Story"));
    when(ticketRepository.countForTicketPage(false, List.of(ORDER), true, List.of(-1L), true, List.of(""), null, true, List.of("")))
        .thenReturn(new ArrayList<>(List.of(new Object[] {"Bug", 1L, 0L}, new Object[] {"Story", 3L, 2L})));
    var listed = List.of(manual("ABC-1", 1L), manual("ABC-2", 2L));
    when(ticketRepository.findForTicketPage(anyBoolean(), any(), anyBoolean(), any(), anyBoolean(), any(), any(),
        anyBoolean(), any(), any(Pageable.class))).thenReturn(listed);

    var result = service.search(new JiraTicketListFilter(ORDER, null, List.of(), true, null, List.of(), 2));

    assertThat(result.totalCount()).isEqualTo(4);
    assertThat(result.replicatedCount()).isEqualTo(2);
    assertThat(result.manualCount()).isEqualTo(2);
    assertThat(result.countByType()).containsExactly(entry("Story", 3L), entry("Bug", 1L));
    assertThat(result.rows()).extracting(JiraTicketRow::key).containsExactly("ABC-1", "ABC-2");
    assertThat(result.truncated()).isTrue();
    assertThat(result.issueTypes()).containsExactly("Bug", "Story");
  }

  /** Without an order, as the page opens: a manager sees every order, a responsible their own (#1386). */
  @Test
  void without_an_order_the_list_covers_every_order_the_user_may_see() {
    service.search(new JiraTicketListFilter(null, null, List.of(), true, null, List.of(), 50));
    verify(ticketRepository).findIssueTypes(true, List.of(-1L));

    givenResponsibleFor(ORDER);
    service.search(new JiraTicketListFilter(null, null, List.of(), true, null, List.of(), 50));
    verify(ticketRepository).findIssueTypes(false, List.of(ORDER));
  }

  /** As the ticket filter of the booking list: a key brings the tickets below it along, ignoring case. */
  @Test
  void a_key_filter_takes_the_tickets_below_along() {
    when(ticketRepository.findParentLinks(false, List.of(ORDER))).thenReturn(List.of(
        new JiraTicketParentLink("ABC-2", "abc-1"), new JiraTicketParentLink("ABC-3", "ABC-2"),
        new JiraTicketParentLink("XYZ-1", null)));

    service.search(new JiraTicketListFilter(ORDER, null, List.of("abc-1"), true, null, List.of(), 50));
    service.search(new JiraTicketListFilter(ORDER, null, List.of("abc-1"), false, null, List.of(), 50));

    verify(ticketRepository).countForTicketPage(false, List.of(ORDER), true, List.of(-1L), false, List.of("ABC-1", "ABC-2", "ABC-3"),
        null, true, List.of(""));
    verify(ticketRepository).countForTicketPage(false, List.of(ORDER), true, List.of(-1L), false, List.of("ABC-1"),
        null, true, List.of(""));
  }

  /** A parent is looked up in the ticket's own scope first, then anywhere in the order. */
  @Test
  void a_related_ticket_is_found_in_the_scope_first_then_in_the_order() {
    var from = manual("ABC-2", 5L);
    when(ticketRepository.findById(5L)).thenReturn(Optional.of(from));
    when(ticketRepository.findInScopeByKey(ORDER, null, "ABC-1")).thenReturn(Optional.of(manual("ABC-1", 6L)));
    when(ticketRepository.findInCustomerorderByKey(ORDER, "ABC-0")).thenReturn(List.of(manual("ABC-0", 7L)));

    assertThat(service.findRelated(5L, "ABC-1")).contains(6L);
    assertThat(service.findRelated(5L, "ABC-0")).contains(7L);
    assertThat(service.findRelated(5L, "NIRGENDS-1")).isEmpty();
  }

  @Test
  void the_detail_carries_labels_fields_and_children() {
    var ticket = manual("ABC-1", 5L);
    ticket.setLabels("alpha, beta");
    ticket.setCustomFields(java.util.Map.of("team", "Blau"));
    when(ticketRepository.findById(5L)).thenReturn(Optional.of(ticket));
    when(ticketRepository.findChildrenInScope(ORDER, null, "ABC-1")).thenReturn(List.of(manual("ABC-2", 6L)));

    var detail = service.getDetail(5L);

    assertThat(detail.labels()).containsExactly("alpha", "beta");
    assertThat(detail.fieldNames()).containsExactly("team");
    assertThat(detail.children()).extracting(JiraTicketRow::key).containsExactly("ABC-2");
    assertThat(detail.row().maintainedByHand()).isTrue();
  }

  private static JiraTicketListFilter filter(List<String> keys, List<String> types) {
    return new JiraTicketListFilter(ORDER, null, keys, true, null, types, 50);
  }

  @Test
  void the_person_responsible_for_the_order_may_maintain_its_tickets() {
    givenResponsibleFor(ORDER);

    service.create(ORDER, null, new JiraManualTicketData("ABC-1", null, null, null));

    verify(ticketRepository).save(any(JiraTicket.class));
  }

  @Test
  void anybody_else_may_not() {
    givenResponsibleFor(OTHER_ORDER);

    assertThatThrownBy(() -> service.create(ORDER, null, new JiraManualTicketData("ABC-1", null, null, null)))
        .isInstanceOf(AuthorizationException.class);
    assertThatThrownBy(() -> service.search(filter(List.of(), List.of()))).isInstanceOf(AuthorizationException.class);
    verify(ticketRepository, never()).save(any(JiraTicket.class));
  }

  /**
   * Bookings store a ticket key in capitals, and JIRA writes it so; {@code abc-1} next to a replicated
   * {@code ABC-1} would be two tickets for one key.
   */
  @Test
  void a_key_is_stored_in_capitals() {
    service.create(ORDER, null, new JiraManualTicketData("abc-1", null, null, "abc-0"));

    assertThat(savedTicket().getKey()).isEqualTo("ABC-1");
    assertThat(savedTicket().getParentKey()).isEqualTo("ABC-0");
  }

  @Test
  void an_imported_key_is_stored_in_capitals() {
    service.importTickets(ORDER, null, "tickets.csv", "Key,Parent\nabc-1,abc-0\n".getBytes(UTF_8),
        List.of(column(JiraImportTarget.KEY), column(JiraImportTarget.PARENT)));

    assertThat(savedAll()).singleElement().satisfies(ticket -> {
      assertThat(ticket.getKey()).isEqualTo("ABC-1");
      assertThat(ticket.getParentKey()).isEqualTo("ABC-0");
    });
  }

  /** The text columns hold three bytes per character; an emoji would end in a database error. */
  @Test
  void characters_beyond_what_the_columns_hold_are_left_out() {
    service.create(ORDER, null, new JiraManualTicketData("ABC-1", "Fertig \uD83D\uDE80 Größe", null, null));

    assertThat(savedTicket().getSummary()).isEqualTo("Fertig  Größe");
  }

  @Test
  void a_long_file_name_is_cut_to_its_column() {
    service.importTickets(ORDER, null, "x".repeat(300) + ".csv", "Key\nABC-1\n".getBytes(UTF_8),
        List.of(column(JiraImportTarget.KEY)));

    assertThat(imports.getFirst().getFileName()).hasSize(255).endsWith(".csv");
  }

  /** The preview reads a file only for someone who may import — the page is not open to everyone. */
  @Test
  void the_preview_is_refused_to_anyone_without_the_ticket_page() {
    givenResponsibleFor(OTHER_ORDER);
    when(customerorderService.getResponsibleCustomerorderOptions(USER)).thenReturn(List.of());

    assertThatThrownBy(() -> service.preview("Key\nABC-1\n".getBytes(UTF_8), null, null))
        .isInstanceOf(AuthorizationException.class);
  }

  @Test
  void the_preview_is_refused_for_another_order() {
    givenResponsibleFor(OTHER_ORDER);

    assertThatThrownBy(() -> service.preview("Key\nABC-1\n".getBytes(UTF_8), ORDER, null))
        .isInstanceOf(AuthorizationException.class);
  }

  /** A ticket of another order is neither shown nor changed, whatever the way to it. */
  @Test
  void a_ticket_of_another_order_is_out_of_reach() {
    givenResponsibleFor(OTHER_ORDER);
    when(ticketRepository.findById(5L)).thenReturn(Optional.of(manual("ABC-1", 5L)));
    var data = new JiraManualTicketData("ABC-1", null, null, null);

    assertThatThrownBy(() -> service.getDetail(5L)).isInstanceOf(AuthorizationException.class);
    assertThatThrownBy(() -> service.getTicket(5L)).isInstanceOf(AuthorizationException.class);
    assertThatThrownBy(() -> service.findRelated(5L, "ABC-0")).isInstanceOf(AuthorizationException.class);
    assertThatThrownBy(() -> service.update(5L, data)).isInstanceOf(AuthorizationException.class);
    assertThatThrownBy(() -> service.delete(5L)).isInstanceOf(AuthorizationException.class);
    verify(ticketRepository, never()).save(any(JiraTicket.class));
    verify(ticketRepository, never()).delete(any(JiraTicket.class));
  }

  @Test
  void a_restricted_user_may_not_even_as_manager() {
    when(authorizedUser.isRestricted()).thenReturn(true);

    assertThatThrownBy(() -> service.importTickets(ORDER, null, "tickets.csv", "Key\nABC-1\n".getBytes(UTF_8),
        List.of(column(JiraImportTarget.KEY))))
        .isInstanceOf(AuthorizationException.class);
  }

  /** A ticket needs an order; without one it is refused before anything is looked up (#1386). */
  @Test
  void a_ticket_and_an_import_need_an_order() {
    assertThatThrownBy(() -> service.create(null, null, new JiraManualTicketData("ABC-1", null, null, null)))
        .satisfies(ex -> assertThat(firstCode(ex)).isEqualTo(ErrorCode.JI_TICKET_SCOPE_REQUIRED));
    assertThatThrownBy(() -> service.importTickets(null, null, "tickets.csv", "Key\nABC-1\n".getBytes(UTF_8),
        List.of(column(JiraImportTarget.KEY))))
        .satisfies(ex -> assertThat(firstCode(ex)).isEqualTo(ErrorCode.JI_TICKET_SCOPE_REQUIRED));
  }

  /**
   * The filter offers the orders the user may see that have tickets, and the one chosen; a new ticket
   * may go to every order the user may maintain that is neither hidden nor inactive (#1386, ADR-0029).
   */
  @Test
  void the_filter_offers_orders_with_tickets_and_a_new_ticket_the_creatable_ones() {
    givenResponsibleFor(ORDER);
    when(customerorderService.getResponsibleCustomerorderOptions(USER)).thenReturn(List.of(
        option(ORDER), option(OTHER_ORDER)));
    when(ticketRepository.findCustomerorderIdsWithTickets()).thenReturn(List.of(ORDER, 99L));
    when(customerorderService.getCreatableCustomerorderOptions(null)).thenReturn(List.of(option(OTHER_ORDER), option(99L)));

    assertThat(service.getFilterCustomerorders(null)).extracting(CustomerorderOption::id).containsExactly(ORDER);
    assertThat(service.getScopeCustomerorders(null)).extracting(CustomerorderOption::id).containsExactly(OTHER_ORDER);
  }

  /** Whether a remembered order has tickets at all; for another order the answer is no (#1386). */
  @Test
  void an_order_has_tickets_only_for_whoever_may_see_it() {
    when(ticketRepository.existsInCustomerorder(ORDER)).thenReturn(true);
    assertThat(service.hasTickets(ORDER)).isTrue();

    givenResponsibleFor(OTHER_ORDER);
    assertThat(new JiraTicketMaintenanceService(ticketRepository, configRepository,
        new JiraTicketAuthorization(authorizedUser, customerorderService), scopes, orderReferences, importRepository,
        suborderService).hasTickets(ORDER)).isFalse();
  }

  /** The suborders of another order are not offered — not even their signs (#1386). */
  @Test
  void the_suborders_of_another_order_are_out_of_reach() {
    givenResponsibleFor(OTHER_ORDER);

    assertThat(service.getFilterSuborders(ORDER, null)).isEmpty();
    assertThatThrownBy(() -> service.getScopeSuborders(ORDER, null)).isInstanceOf(AuthorizationException.class);
    assertThatThrownBy(() -> service.getScopeSign(ORDER, null)).isInstanceOf(AuthorizationException.class);
  }

  @Test
  void the_ticket_page_is_open_to_managers_and_to_the_responsible_only() {
    var authorization = new JiraTicketAuthorization(authorizedUser, customerorderService);
    assertThat(authorization.isTicketPageAvailable()).isTrue();

    givenResponsibleFor(ORDER);
    assertThat(new JiraTicketAuthorization(authorizedUser, customerorderService).isTicketPageAvailable()).isTrue();

    when(customerorderService.getResponsibleCustomerorderOptions(USER)).thenReturn(List.of());
    assertThat(new JiraTicketAuthorization(authorizedUser, customerorderService).isTicketPageAvailable()).isFalse();
  }

  private static CustomerorderOption option(long id) {
    return new CustomerorderOption(id, "ORDER" + id, null, null, null, null, false);
  }

  /** The suggestion of the preview, with the team column as additional field. */
  private List<JiraImportColumn> jiraExportMapping(boolean inherited) {
    var suggested = new ArrayList<>(service.preview(JIRA_EXPORT.getBytes(UTF_8), null, null).suggested());
    suggested.set(8, new JiraImportColumn(JiraImportTarget.ADDITIONAL, "customfield_10500", inherited));
    return suggested;
  }

  private static JiraImportColumn column(JiraImportTarget target) {
    return new JiraImportColumn(target, null, false);
  }

  private void givenResponsibleFor(long customerorderId) {
    when(authorizedUser.isManager()).thenReturn(false);
    when(authorizedUser.getEffectiveUserId()).thenReturn(USER);
    when(customerorderService.getResponsibleCustomerorderOptions(USER)).thenReturn(
        List.of(new CustomerorderOption(customerorderId, "ORDER", null, null, null, null, false)));
  }

  private JiraTicket manual(String key, long id) {
    var ticket = new JiraTicket();
    setId(ticket, id);
    var order = new Customerorder();
    setId(order, ORDER);
    ticket.setCustomerorder(order);
    ticket.setKey(key);
    return ticket;
  }

  private static JiraReplicationConfig replication(String name) {
    var config = new JiraReplicationConfig();
    setId(config, 11L);
    config.setName(name);
    return config;
  }

  private JiraTicket savedTicket() {
    var captor = ArgumentCaptor.forClass(JiraTicket.class);
    verify(ticketRepository).save(captor.capture());
    return captor.getValue();
  }

  @SuppressWarnings("unchecked")
  private List<JiraTicket> savedAll() {
    var captor = ArgumentCaptor.forClass(Iterable.class);
    verify(ticketRepository, org.mockito.Mockito.atLeastOnce()).saveAll(captor.capture());
    var tickets = new ArrayList<JiraTicket>();
    ((Iterable<JiraTicket>) captor.getAllValues().get(0)).forEach(tickets::add);
    return tickets;
  }

  private static ErrorCode firstCode(Throwable ex) {
    return ((ErrorCodeException) ex).getMessages().get(0).getErrorCode();
  }

  private static void setId(AuditedEntity entity, long id) {
    try {
      var field = AuditedEntity.class.getDeclaredField("id");
      field.setAccessible(true);
      field.set(entity, id);
    } catch (ReflectiveOperationException e) {
      throw new IllegalStateException("cannot assign an id to the test record", e);
    }
  }
}
