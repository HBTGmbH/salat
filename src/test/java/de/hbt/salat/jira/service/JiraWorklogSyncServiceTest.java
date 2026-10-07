package de.hbt.salat.jira.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static de.hbt.salat.jira.OrderTree.customerorderWithId;
import static de.hbt.salat.jira.domain.JiraApiFlavor.SERVER;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.util.ReflectionTestUtils;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import de.hbt.salat.common.command.CommandPublisher;
import de.hbt.salat.common.test.FixedClock;
import de.hbt.salat.jira.command.GetTicketWorklogSumsCommandEvent;
import de.hbt.salat.jira.command.TicketDaySum;
import de.hbt.salat.jira.domain.JiraReplicationConfig;
import de.hbt.salat.jira.domain.JiraTicket;
import de.hbt.salat.jira.domain.JiraWorklogSync;
import de.hbt.salat.jira.persistence.JiraTicketRepository;
import de.hbt.salat.jira.persistence.JiraWorklogSyncRepository;
import de.hbt.salat.order.domain.Customerorder;

/**
 * Writing the booked hours back to JIRA (#1007): one worklog per day and ticket, its comment naming
 * each person by sign with their share (#1408), overwritten when the sum or the split moves, removed
 * when the bookings are gone, and untouched when nothing changed — or when its ticket is no longer
 * replicated (#1167).
 */
@FixedClock
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class JiraWorklogSyncServiceTest {

  private static final long REPLICATION_ID = 11L;

  private static final LocalDate SYNC_FROM = LocalDate.of(2026, 6, 1);
  private static final LocalDate DAY = LocalDate.of(2026, 6, 10);
  private static final String SCOPE = "ALPHA";
  private static final long CUSTOMERORDER_ID = 7L;
  private static final Customerorder CUSTOMERORDER = customerorderWithId(CUSTOMERORDER_ID);

  @InjectMocks
  private JiraWorklogSyncService classUnderTest;

  @Mock
  private CommandPublisher commandPublisher;

  @Mock
  private JiraScopes scopes;

  @Mock
  private JiraTicketRepository ticketRepository;

  @Mock
  private JiraWorklogSyncRepository syncRepository;

  @Mock
  private JiraWorklogClients worklogClients;

  @Mock
  private JiraWorklogClient worklogClient;

  @BeforeEach
  void setUp() {
    when(worklogClients.forFlavor(SERVER)).thenReturn(worklogClient);
    when(scopes.suborderIdsOf(CUSTOMERORDER_ID, null)).thenReturn(List.of(1L, 2L));
    when(scopes.signOf(CUSTOMERORDER_ID, null)).thenReturn(SCOPE);
    when(syncRepository.findInScopeFrom(anyLong(), any(), any()))
        .thenReturn(List.of());
  }

  @Test
  void a_replication_with_the_sync_switched_off_never_touches_jira() {
    var config = config();
    config.setWorklogSyncEnabled(false);

    classUnderTest.sync(config);

    // Not one call — neither to JIRA nor to the module that owns the bookings.
    verifyNoInteractions(worklogClients, commandPublisher, ticketRepository, syncRepository);
  }

  @Test
  void a_sync_without_a_start_date_is_skipped_rather_than_writing_the_whole_history() {
    var config = config();
    config.setWorklogSyncFrom(null);

    classUnderTest.sync(config);

    verifyNoInteractions(worklogClients, commandPublisher);
  }

  @Test
  void a_start_date_in_the_future_has_nothing_to_do_yet() {
    var config = config();
    config.setWorklogSyncFrom(LocalDate.of(2026, 12, 1));

    classUnderTest.sync(config);

    verifyNoInteractions(worklogClients, commandPublisher);
  }

  @Test
  void a_day_and_ticket_without_a_worklog_yet_gets_one() {
    givenBookings(sum("ALPHA-1", "abc", 90));
    givenReplicatedTickets("ALPHA-1");
    when(worklogClient.create(any(), any())).thenReturn("10101");

    classUnderTest.sync(config());

    var entry = ArgumentCaptor.forClass(JiraWorklogEntry.class);
    verify(worklogClient).create(any(), entry.capture());
    assertThat(entry.getValue().workDate()).isEqualTo(DAY);
    assertThat(entry.getValue().minutes()).isEqualTo(90);

    var saved = savedRow();
    assertThat(saved.getIssueKey()).isEqualTo("ALPHA-1");
    assertThat(saved.getWorkDate()).isEqualTo(DAY);
    assertThat(saved.getWorklogId()).isEqualTo("10101");
    assertThat(saved.getMinutes()).isEqualTo(90);
    // the scope of the config, taken over as references (#1323, #1368)
    assertThat(saved.getCustomerorder()).isSameAs(CUSTOMERORDER);
    assertThat(saved.getSuborder()).isNull();
  }

  /**
   * Only tickets the replication maintains are written back to (#1386): one of the scope maintained
   * by hand or by another replication is not its business, however much was booked on it.
   */
  @Test
  void a_ticket_the_replication_does_not_maintain_gets_no_worklog() {
    givenBookings(sum("ALPHA-1", "abc", 90), sum("HAND-1", "abc", 60));
    givenReplicatedTickets("ALPHA-1");
    when(worklogClient.create(any(), any())).thenReturn("10101");

    classUnderTest.sync(config());

    var target = ArgumentCaptor.forClass(JiraWorklogTarget.class);
    verify(worklogClient).create(target.capture(), any());
    assertThat(target.getValue().issueKey()).isEqualTo("ALPHA-1");
    verify(ticketRepository).findMaintainedByKeyIn(eq(REPLICATION_ID), anyList());
  }

  @Test
  void the_worklog_carries_the_sum_over_everybody_who_booked_that_day() {
    // Two people, one ticket, one day — that is one worklog, not two.
    givenBookings(sum("ALPHA-1", "abc", 90), sum("ALPHA-1", "xyz", 30));
    givenReplicatedTickets("ALPHA-1");
    when(worklogClient.create(any(), any())).thenReturn("10101");

    classUnderTest.sync(config());

    var entry = ArgumentCaptor.forClass(JiraWorklogEntry.class);
    verify(worklogClient).create(any(), entry.capture());
    assertThat(entry.getValue().minutes()).isEqualTo(120);
  }

  @Test
  void a_changed_booking_overwrites_the_worklog_of_that_day_instead_of_adding_one() {
    givenBookings(sum("ALPHA-1", "abc", 120));
    givenReplicatedTickets("ALPHA-1");
    var stored = givenStoredWorklog("ALPHA-1", DAY, "10101", 90);

    classUnderTest.sync(config());

    verify(worklogClient).update(any(), eq("10101"), any());
    verify(worklogClient, never()).create(any(), any());
    assertThat(stored.getMinutes()).isEqualTo(120);
    verify(syncRepository).save(stored);
  }

  @Test
  void a_run_without_changes_causes_no_writing_call_at_all() {
    givenBookings(sum("ALPHA-1", "abc", 90));
    givenReplicatedTickets("ALPHA-1");
    givenStoredWorklog("ALPHA-1", DAY, "10101", 90, "Von HBT protokollierte Stunden übertragen:\nabc 1h 30m");

    classUnderTest.sync(config());

    verifyNoInteractions(worklogClient);
    verify(syncRepository, never()).save(any());
    verify(syncRepository, never()).delete(any());
  }

  @Test
  void a_day_whose_bookings_are_all_gone_loses_its_worklog() {
    // Nothing comes back for that day — the bookings were deleted, and a soft-deleted booking is
    // recognisable by nothing else than its absence from the sums.
    givenBookings();
    givenReplicatedTickets("ALPHA-1");
    var stored = givenStoredWorklog("ALPHA-1", DAY, "10101", 90);

    classUnderTest.sync(config());

    verify(worklogClient).delete(any(), eq("10101"));
    verify(syncRepository).delete(stored);
  }

  @Test
  void a_worklog_on_a_ticket_no_longer_replicated_stays_in_jira_and_remembered() {
    // The replication removed the ticket (#1167) - moved away, or no longer matched by the JQL.
    // That says nothing about the bookings, so what SALAT wrote there is left as it is.
    givenBookings(sum("ALPHA-1", "abc", 90));
    givenReplicatedTickets();
    var stored = givenStoredWorklog("ALPHA-1", DAY, "10101", 90);

    classUnderTest.sync(config());

    verify(worklogClient, never()).delete(any(), any());
    verify(syncRepository, never()).delete(any());
    assertThat(stored.getWorklogId()).isEqualTo("10101");
  }

  @Test
  void a_ticket_that_comes_back_picks_up_at_its_remembered_worklog() {
    // no second worklog next to the one written before the ticket was removed
    givenBookings(sum("ALPHA-1", "abc", 120));
    givenReplicatedTickets("ALPHA-1");
    var stored = givenStoredWorklog("ALPHA-1", DAY, "10101", 90);

    classUnderTest.sync(config());

    verify(worklogClient, never()).create(any(), any());
    verify(worklogClient).update(any(), eq("10101"), any());
    assertThat(stored.getMinutes()).isEqualTo(120);
  }

  @Test
  void a_day_left_with_nothing_but_zero_length_bookings_loses_its_worklog_too() {
    givenBookings(sum("ALPHA-1", "abc", 0));
    givenReplicatedTickets("ALPHA-1");
    var stored = givenStoredWorklog("ALPHA-1", DAY, "10101", 90);

    classUnderTest.sync(config());

    verify(worklogClient).delete(any(), eq("10101"));
    verify(syncRepository).delete(stored);
  }

  @Test
  void a_reference_without_a_replicated_ticket_is_skipped() {
    // The reference is free text, so a typo is normal — and must not be written against an issue
    // that does not exist.
    givenBookings(sum("ALPHA-1", "abc", 90), sum("ALPAH-99", "abc", 60));
    givenReplicatedTickets("ALPHA-1");
    when(worklogClient.create(any(), any())).thenReturn("10101");

    classUnderTest.sync(config());

    var target = ArgumentCaptor.forClass(JiraWorklogTarget.class);
    verify(worklogClient).create(target.capture(), any());
    assertThat(target.getValue().issueKey()).isEqualTo("ALPHA-1");
  }

  @Test
  void a_reference_written_in_another_case_still_names_the_same_issue() {
    givenBookings(sum("alpha-1", "abc", 90), sum("ALPHA-1", "abc", 30));
    givenReplicatedTickets("ALPHA-1");
    when(worklogClient.create(any(), any())).thenReturn("10101");

    classUnderTest.sync(config());

    var target = ArgumentCaptor.forClass(JiraWorklogTarget.class);
    var entry = ArgumentCaptor.forClass(JiraWorklogEntry.class);
    verify(worklogClient).create(target.capture(), entry.capture());
    // One worklog carrying the key of the ticket, not the text somebody typed.
    assertThat(target.getValue().issueKey()).isEqualTo("ALPHA-1");
    assertThat(entry.getValue().minutes()).isEqualTo(120);
  }

  /**
   * #1408: who stands behind the number, by sign and share, sorted by sign — and nothing else of the
   * bookings.
   */
  @Test
  void the_comment_names_each_person_by_sign_with_their_share() {
    givenBookings(sum("ALPHA-1", "xyz", 150), sum("ALPHA-1", "abc", 240));
    givenReplicatedTickets("ALPHA-1");
    when(worklogClient.create(any(), any())).thenReturn("10101");

    classUnderTest.sync(config());

    var entry = ArgumentCaptor.forClass(JiraWorklogEntry.class);
    verify(worklogClient).create(any(), entry.capture());
    assertThat(entry.getValue().minutes()).isEqualTo(390);
    assertThat(entry.getValue().comment()).isEqualTo("Von HBT protokollierte Stunden übertragen:\nabc 4h\nxyz 2h 30m");
    assertThat(savedRow().getComment()).isEqualTo("Von HBT protokollierte Stunden übertragen:\nabc 4h\nxyz 2h 30m");
  }

  @Test
  void a_person_left_with_no_minutes_after_the_split_is_not_named() {
    givenBookings(new TicketDaySum(DAY, "ALPHA-1", Map.of("abc", 45L, "xyz", 0L)));
    givenReplicatedTickets("ALPHA-1");
    when(worklogClient.create(any(), any())).thenReturn("10101");

    classUnderTest.sync(config());

    var entry = ArgumentCaptor.forClass(JiraWorklogEntry.class);
    verify(worklogClient).create(any(), entry.capture());
    assertThat(entry.getValue().minutes()).isEqualTo(45);
    assertThat(entry.getValue().comment()).isEqualTo("Von HBT protokollierte Stunden übertragen:\nabc 45m");
  }

  @Test
  void a_reference_written_in_another_case_adds_up_the_shares_of_each_sign_too() {
    givenBookings(sum("alpha-1", "abc", 90),
        new TicketDaySum(DAY, "ALPHA-1", Map.of("abc", 30L, "xyz", 15L)));
    givenReplicatedTickets("ALPHA-1");
    when(worklogClient.create(any(), any())).thenReturn("10101");

    classUnderTest.sync(config());

    var entry = ArgumentCaptor.forClass(JiraWorklogEntry.class);
    verify(worklogClient).create(any(), entry.capture());
    assertThat(entry.getValue().minutes()).isEqualTo(135);
    assertThat(entry.getValue().comment()).isEqualTo("Von HBT protokollierte Stunden übertragen:\nabc 2h\nxyz 15m");
  }

  @Test
  void a_changed_split_at_the_same_sum_overwrites_the_worklog() {
    // One person's booking moved to another: the sum stays at 90, the comment does not.
    givenBookings(sum("ALPHA-1", "abc", 60), sum("ALPHA-1", "xyz", 30));
    givenReplicatedTickets("ALPHA-1");
    var stored = givenStoredWorklog("ALPHA-1", DAY, "10101", 90, "Von HBT protokollierte Stunden übertragen:\nabc 1h 30m");

    classUnderTest.sync(config());

    var entry = ArgumentCaptor.forClass(JiraWorklogEntry.class);
    verify(worklogClient).update(any(), eq("10101"), entry.capture());
    assertThat(entry.getValue().comment()).isEqualTo("Von HBT protokollierte Stunden übertragen:\nabc 1h\nxyz 30m");
    assertThat(stored.getMinutes()).isEqualTo(90);
    assertThat(stored.getComment()).isEqualTo("Von HBT protokollierte Stunden übertragen:\nabc 1h\nxyz 30m");
    verify(syncRepository).save(stored);
  }

  @Test
  void a_worklog_remembered_without_a_comment_is_written_once_more() {
    // Every row from before #1408: the first run after the release gives its worklog the new comment.
    givenBookings(sum("ALPHA-1", "abc", 90));
    givenReplicatedTickets("ALPHA-1");
    var stored = givenStoredWorklog("ALPHA-1", DAY, "10101", 90);

    classUnderTest.sync(config());

    verify(worklogClient).update(any(), eq("10101"), any());
    verify(worklogClient, never()).create(any(), any());
    assertThat(stored.getComment()).isEqualTo("Von HBT protokollierte Stunden übertragen:\nabc 1h 30m");
    verify(syncRepository).save(stored);
  }

  @Test
  void a_worklog_written_again_after_somebody_deleted_it_remembers_its_comment() {
    givenBookings(sum("ALPHA-1", "abc", 120));
    givenReplicatedTickets("ALPHA-1");
    var stored = givenStoredWorklog("ALPHA-1", DAY, "10101", 90, "Von HBT protokollierte Stunden übertragen:\nabc 1h 30m");
    doThrow(new JiraWorklogNotFoundException("ALPHA-1", "10101", null))
        .when(worklogClient).update(any(), eq("10101"), any());
    when(worklogClient.create(any(), any())).thenReturn("10999");

    classUnderTest.sync(config());

    assertThat(stored.getComment()).isEqualTo("Von HBT protokollierte Stunden übertragen:\nabc 2h");
  }

  @Test
  void a_failure_on_one_ticket_leaves_the_others_and_the_remembered_row_alone() {
    givenBookings(sum("ALPHA-1", "abc", 90), sum("ALPHA-2", "abc", 60));
    givenReplicatedTickets("ALPHA-1", "ALPHA-2");
    var failing = givenStoredWorklog("ALPHA-1", DAY, "10101", 30);
    doThrow(new IllegalStateException("JIRA is unwell"))
        .when(worklogClient).update(any(), eq("10101"), any());
    when(worklogClient.create(any(), any())).thenReturn("10202");

    classUnderTest.sync(config());

    // The second ticket is written all the same …
    var saved = savedRow();
    assertThat(saved.getIssueKey()).isEqualTo("ALPHA-2");
    // … and the failed one keeps what it had, so the next run tries again.
    assertThat(failing.getMinutes()).isEqualTo(30);
    verify(syncRepository, never()).save(failing);
  }

  @Test
  void a_worklog_somebody_deleted_in_jira_is_written_again() {
    givenBookings(sum("ALPHA-1", "abc", 120));
    givenReplicatedTickets("ALPHA-1");
    var stored = givenStoredWorklog("ALPHA-1", DAY, "10101", 90);
    doThrow(new JiraWorklogNotFoundException("ALPHA-1", "10101", null))
        .when(worklogClient).update(any(), eq("10101"), any());
    when(worklogClient.create(any(), any())).thenReturn("10999");

    classUnderTest.sync(config());

    assertThat(stored.getWorklogId()).isEqualTo("10999");
    assertThat(stored.getMinutes()).isEqualTo(120);
    verify(syncRepository).save(stored);
  }

  @Test
  void a_worklog_already_gone_from_jira_only_loses_its_row() {
    givenBookings();
    givenReplicatedTickets("ALPHA-1");
    var stored = givenStoredWorklog("ALPHA-1", DAY, "10101", 90);
    doThrow(new JiraWorklogNotFoundException("ALPHA-1", "10101", null))
        .when(worklogClient).delete(any(), eq("10101"));

    classUnderTest.sync(config());

    verify(syncRepository).delete(stored);
  }

  @Test
  void only_the_remembered_rows_from_the_start_date_on_are_compared() {
    // Rows of an earlier period must not be read as "all bookings of that day are gone" — moving
    // the start date would otherwise delete worklogs nobody asked about.
    givenBookings();

    classUnderTest.sync(config());

    verify(syncRepository).findInScopeFrom(CUSTOMERORDER_ID, null, SYNC_FROM);
  }

  @Test
  void the_bookings_are_asked_for_over_the_whole_period_up_to_today() {
    givenBookings();

    classUnderTest.sync(config());

    var command = ArgumentCaptor.forClass(GetTicketWorklogSumsCommandEvent.class);
    verify(commandPublisher).publish(command.capture());
    assertThat(command.getValue().getSuborderIds()).containsExactly(1L, 2L);
    assertThat(command.getValue().getFrom()).isEqualTo(SYNC_FROM);
    assertThat(command.getValue().getUntil()).isEqualTo(LocalDate.of(2026, 6, 25));
  }

  @Test
  void the_bookings_are_asked_for_without_restriction_unless_it_is_switched_on() {
    givenBookings();

    classUnderTest.sync(config());

    var command = ArgumentCaptor.forClass(GetTicketWorklogSumsCommandEvent.class);
    verify(commandPublisher).publish(command.capture());
    assertThat(command.getValue().isInvoiceableOnly()).isFalse();
  }

  @Test
  void the_restriction_to_invoiceable_bookings_is_passed_on_with_the_question() {
    givenBookings();

    classUnderTest.sync(invoiceableOnly(config()));

    var command = ArgumentCaptor.forClass(GetTicketWorklogSumsCommandEvent.class);
    verify(commandPublisher).publish(command.capture());
    assertThat(command.getValue().isInvoiceableOnly()).isTrue();
  }

  @Test
  void switching_the_restriction_on_lowers_a_mixed_day_to_its_invoiceable_minutes() {
    // #1218: the worklog was written with everything; the next run keeps only what is billed.
    givenBookings(
        List.of(sum("ALPHA-1", "abc", 60)),
        List.of(sum("ALPHA-1", "abc", 30)));
    givenReplicatedTickets("ALPHA-1");
    var stored = givenStoredWorklog("ALPHA-1", DAY, "10101", 90);

    classUnderTest.sync(invoiceableOnly(config()));

    var entry = ArgumentCaptor.forClass(JiraWorklogEntry.class);
    verify(worklogClient).update(any(), eq("10101"), entry.capture());
    assertThat(entry.getValue().minutes()).isEqualTo(60);
    assertThat(stored.getMinutes()).isEqualTo(60);
  }

  @Test
  void switching_the_restriction_on_removes_a_worklog_of_nothing_but_non_invoiceable_bookings() {
    givenBookings(List.of(), List.of(sum("ALPHA-1", "abc", 90)));
    givenReplicatedTickets("ALPHA-1");
    var stored = givenStoredWorklog("ALPHA-1", DAY, "10101", 90);

    classUnderTest.sync(invoiceableOnly(config()));

    verify(worklogClient).delete(any(), eq("10101"));
    verify(syncRepository).delete(stored);
  }

  @Test
  void lifting_the_restriction_writes_the_non_invoiceable_minutes_again() {
    givenBookings(
        List.of(sum("ALPHA-1", "abc", 60)),
        List.of(sum("ALPHA-1", "abc", 30), sum("ALPHA-2", "abc", 45)));
    givenReplicatedTickets("ALPHA-1", "ALPHA-2");
    var stored = givenStoredWorklog("ALPHA-1", DAY, "10101", 60);
    when(worklogClient.create(any(), any())).thenReturn("10202");

    classUnderTest.sync(config());

    // The mixed day goes back up to the full sum …
    assertThat(stored.getMinutes()).isEqualTo(90);
    verify(worklogClient).update(any(), eq("10101"), any());
    // … and the day that only carried non-invoiceable bookings gets its worklog back.
    var target = ArgumentCaptor.forClass(JiraWorklogTarget.class);
    verify(worklogClient).create(target.capture(), any());
    assertThat(target.getValue().issueKey()).isEqualTo("ALPHA-2");
  }

  @Test
  void a_scope_of_nothing_but_non_invoiceable_suborders_is_still_a_scope() {
    // The restriction filters bookings, not suborders: a scope with no invoiceable suborder must
    // lose its worklogs, not be skipped as if it named nothing.
    givenBookings(List.of(), List.of(sum("ALPHA-1", "abc", 90)));
    givenReplicatedTickets("ALPHA-1");
    givenStoredWorklog("ALPHA-1", DAY, "10101", 90);

    classUnderTest.sync(invoiceableOnly(config()));

    var command = ArgumentCaptor.forClass(GetTicketWorklogSumsCommandEvent.class);
    verify(commandPublisher).publish(command.capture());
    assertThat(command.getValue().getSuborderIds()).containsExactly(1L, 2L);
    verify(worklogClient).delete(any(), eq("10101"));
  }

  @Test
  void a_scope_that_matches_no_suborder_writes_nothing() {
    when(scopes.suborderIdsOf(CUSTOMERORDER_ID, null)).thenReturn(List.of());

    classUnderTest.sync(config());

    verifyNoInteractions(worklogClients, commandPublisher);
  }

  private JiraReplicationConfig config() {
    var config = new JiraReplicationConfig();
    ReflectionTestUtils.setField(config, "id", REPLICATION_ID);
    config.setName("Alpha");
    config.setCustomerorder(CUSTOMERORDER);
    config.setBaseUrl("https://jira.example.com");
    config.setApiFlavor(SERVER);
    config.setUsername("jira-user");
    config.setPassword("token");
    config.setEnabled(true);
    config.setWorklogSyncEnabled(true);
    config.setWorklogSyncFrom(SYNC_FROM);
    return config;
  }

  /** Answers the command event the service publishes with the given sums. */
  private void givenBookings(TicketDaySum... sums) {
    doAnswer(invocation -> {
      var event = (GetTicketWorklogSumsCommandEvent) invocation.getArgument(0);
      event.setResult(Arrays.asList(sums));
      return null;
    }).when(commandPublisher).publish(any());
  }

  /**
   * Answers the command event the way the owning module does (#1218): the sums of non-invoiceable
   * suborders only count while the question carries no restriction.
   */
  private void givenBookings(List<TicketDaySum> invoiceable, List<TicketDaySum> notInvoiceable) {
    doAnswer(invocation -> {
      var event = (GetTicketWorklogSumsCommandEvent) invocation.getArgument(0);
      var sums = new ArrayList<>(invoiceable);
      if (!event.isInvoiceableOnly()) {
        sums.addAll(notInvoiceable);
      }
      event.setResult(sums);
      return null;
    }).when(commandPublisher).publish(any());
  }

  private static JiraReplicationConfig invoiceableOnly(JiraReplicationConfig config) {
    config.setWorklogSyncInvoiceableOnly(true);
    return config;
  }

  private void givenReplicatedTickets(String... keys) {
    var tickets = new ArrayList<JiraTicket>();
    for (String key : keys) {
      var ticket = new JiraTicket();
      ticket.setCustomerorder(CUSTOMERORDER);
      ticket.setKey(key);
      tickets.add(ticket);
    }
    when(ticketRepository.findMaintainedByKeyIn(eq(REPLICATION_ID), anyList())).thenReturn(tickets);
  }

  private JiraWorklogSync givenStoredWorklog(String issueKey, LocalDate workDate, String worklogId,
                                             int minutes) {
    return givenStoredWorklog(issueKey, workDate, worklogId, minutes, null);
  }

  private JiraWorklogSync givenStoredWorklog(String issueKey, LocalDate workDate, String worklogId,
                                             int minutes, String comment) {
    var row = new JiraWorklogSync();
    row.setCustomerorder(CUSTOMERORDER);
    row.setIssueKey(issueKey);
    row.setWorkDate(workDate);
    row.setWorklogId(worklogId);
    row.setMinutes(minutes);
    row.setComment(comment);
    when(syncRepository.findInScopeFrom(CUSTOMERORDER_ID, null, SYNC_FROM))
        .thenReturn(List.of(row));
    return row;
  }

  /** What one person booked on a ticket on {@link #DAY}, after the split (#1326, #1408). */
  private static TicketDaySum sum(String ticketReference, String sign, long minutes) {
    return new TicketDaySum(DAY, ticketReference, Map.of(sign, minutes));
  }

  private JiraWorklogSync savedRow() {
    var captor = ArgumentCaptor.forClass(JiraWorklogSync.class);
    verify(syncRepository).save(captor.capture());
    return captor.getValue();
  }
}
