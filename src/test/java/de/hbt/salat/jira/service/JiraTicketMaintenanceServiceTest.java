package de.hbt.salat.jira.service;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
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
import de.hbt.salat.auth.domain.AuthorizedUser;
import de.hbt.salat.common.domain.AuditedEntity;
import de.hbt.salat.common.exception.AuthorizationException;
import de.hbt.salat.common.exception.ErrorCode;
import de.hbt.salat.common.exception.ErrorCodeException;
import de.hbt.salat.common.exception.InvalidDataException;
import de.hbt.salat.common.exception.ServiceFeedbackMessage;
import de.hbt.salat.jira.domain.JiraImportColumn;
import de.hbt.salat.jira.domain.JiraImportTarget;
import de.hbt.salat.jira.domain.JiraManualTicketData;
import de.hbt.salat.jira.domain.JiraReplicationConfig;
import de.hbt.salat.jira.domain.JiraTicket;
import de.hbt.salat.jira.domain.ResolvedFieldValue;
import de.hbt.salat.jira.persistence.JiraReplicationConfigRepository;
import de.hbt.salat.jira.persistence.JiraTicketRepository;
import de.hbt.salat.jira.persistence.OrderReferences;
import de.hbt.salat.order.domain.Customerorder;
import de.hbt.salat.order.domain.CustomerorderOption;
import de.hbt.salat.order.domain.SuborderLocation;
import de.hbt.salat.order.service.CustomerorderService;

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
  private JiraTicketMaintenanceService service;

  @BeforeEach
  void setUp() {
    ticketRepository = mock(JiraTicketRepository.class);
    configRepository = mock(JiraReplicationConfigRepository.class);
    authorizedUser = mock(AuthorizedUser.class);
    customerorderService = mock(CustomerorderService.class);
    scopes = mock(JiraScopes.class);
    orderReferences = mock(OrderReferences.class);
    var authorization = new JiraTicketAuthorization(authorizedUser, customerorderService);
    service = new JiraTicketMaintenanceService(ticketRepository, configRepository, authorization, scopes,
        orderReferences);

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

  /** The replication would overwrite or take over whatever is entered by hand. */
  @Test
  void nothing_is_created_where_a_replication_covers_the_scope() {
    when(configRepository.findCovering(ORDER, List.of(SUBORDER))).thenReturn(List.of(replication("Alpha-JIRA")));

    assertThatThrownBy(() -> service.create(ORDER, SUBORDER, new JiraManualTicketData("ABC-1", null, null, null)))
        .satisfies(ex -> assertThat(firstCode(ex)).isEqualTo(ErrorCode.JI_TICKET_SCOPE_COVERED));
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
    int count = service.importTickets(ORDER, null, JIRA_EXPORT.getBytes(UTF_8), jiraExportMapping(false));

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

    service.importTickets(ORDER, null, JIRA_EXPORT.getBytes(UTF_8), jiraExportMapping(true));

    var story = saved.stream().filter(ticket -> ticket.getKey().equals("ABC-2")).findFirst().orElseThrow();
    assertThat(story.getCustomFieldsEffective())
        .containsEntry("customfield_10500", new ResolvedFieldValue("Blau", "ABC-1"));
    assertThat(story.getTopLevelKey()).isEqualTo("ABC-1");
  }

  /** A key the scope already has is updated, not created a second time. */
  @Test
  void an_existing_key_is_updated_by_the_import() {
    var stored = manual("ABC-1", 5L);
    when(ticketRepository.findInScope(ORDER, null)).thenReturn(List.of(stored));

    service.importTickets(ORDER, null, "Key;Summary\nABC-1;Neuer Titel\n".getBytes(UTF_8),
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

    assertThatThrownBy(() -> service.importTickets(ORDER, null, file.getBytes(UTF_8), mapping))
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

    assertThatThrownBy(() -> service.importTickets(ORDER, null, "Key;Id\nABC-1;1\n".getBytes(UTF_8),
        List.of(column(JiraImportTarget.KEY), column(JiraImportTarget.ID))))
        .satisfies(ex -> assertThat(firstCode(ex)).isEqualTo(ErrorCode.JI_TICKET_IMPORT_ID_TAKEN));
  }

  @Test
  void the_assignment_needs_a_key_column_and_one_column_per_single_field() {
    var file = "Key;Summary;Title;Extra\nABC-1;a;b;c\n".getBytes(UTF_8);

    assertThatThrownBy(() -> service.importTickets(ORDER, null, file, List.of(column(JiraImportTarget.SUMMARY),
        column(JiraImportTarget.IGNORE), column(JiraImportTarget.IGNORE), column(JiraImportTarget.IGNORE))))
        .satisfies(ex -> assertThat(firstCode(ex)).isEqualTo(ErrorCode.JI_TICKET_IMPORT_NO_KEY_COLUMN));
    assertThatThrownBy(() -> service.importTickets(ORDER, null, file, List.of(column(JiraImportTarget.KEY),
        column(JiraImportTarget.SUMMARY), column(JiraImportTarget.SUMMARY), column(JiraImportTarget.IGNORE))))
        .satisfies(ex -> assertThat(firstCode(ex)).isEqualTo(ErrorCode.JI_TICKET_IMPORT_TARGET_TWICE));
    assertThatThrownBy(() -> service.importTickets(ORDER, null, file, List.of(column(JiraImportTarget.KEY),
        column(JiraImportTarget.SUMMARY), column(JiraImportTarget.IGNORE),
        new JiraImportColumn(JiraImportTarget.ADDITIONAL, " ", false))))
        .satisfies(ex -> assertThat(firstCode(ex)).isEqualTo(ErrorCode.JI_TICKET_IMPORT_FIELD_NAME_MISSING));
    assertThatThrownBy(() -> service.importTickets(ORDER, null, file, List.of(column(JiraImportTarget.KEY))))
        .satisfies(ex -> assertThat(firstCode(ex)).isEqualTo(ErrorCode.JI_TICKET_IMPORT_MAPPING_MISMATCH));
    verify(ticketRepository, never()).saveAll(anyIterable());
  }

  @Test
  void a_file_with_only_headings_contains_no_tickets() {
    assertThatThrownBy(() -> service.importTickets(ORDER, null, "Key;Summary\n".getBytes(UTF_8),
        List.of(column(JiraImportTarget.KEY), column(JiraImportTarget.SUMMARY))))
        .satisfies(ex -> assertThat(firstCode(ex)).isEqualTo(ErrorCode.JI_TICKET_IMPORT_EMPTY));
  }

  @Test
  void nothing_is_imported_where_a_replication_covers_the_scope() {
    when(configRepository.findCovering(ORDER, List.of())).thenReturn(List.of(replication("Alpha-JIRA")));

    assertThatThrownBy(() -> service.importTickets(ORDER, null, "Key\nABC-1\n".getBytes(UTF_8),
        List.of(column(JiraImportTarget.KEY))))
        .satisfies(ex -> assertThat(firstCode(ex)).isEqualTo(ErrorCode.JI_TICKET_SCOPE_COVERED));
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
    assertThatThrownBy(() -> service.getTickets(ORDER, null)).isInstanceOf(AuthorizationException.class);
    verify(ticketRepository, never()).save(any(JiraTicket.class));
  }

  @Test
  void a_restricted_user_may_not_even_as_manager() {
    when(authorizedUser.isRestricted()).thenReturn(true);

    assertThatThrownBy(() -> service.importTickets(ORDER, null, "Key\nABC-1\n".getBytes(UTF_8),
        List.of(column(JiraImportTarget.KEY))))
        .isInstanceOf(AuthorizationException.class);
  }

  /** The suggestion of the preview, with the team column as additional field. */
  private List<JiraImportColumn> jiraExportMapping(boolean inherited) {
    var suggested = new ArrayList<>(service.preview(JIRA_EXPORT.getBytes(UTF_8)).suggested());
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
