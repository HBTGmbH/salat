package org.tb.jira.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.tb.jira.domain.JiraApiFlavor.SERVER;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.tb.common.command.CommandPublisher;
import org.tb.common.test.FixedClock;
import org.tb.jira.command.GetTicketWorklogSumsCommandEvent;
import org.tb.jira.command.TicketDaySum;
import org.tb.jira.domain.JiraReplicationConfig;
import org.tb.jira.domain.JiraTicket;
import org.tb.jira.domain.JiraWorklogSync;
import org.tb.jira.persistence.JiraTicketRepository;
import org.tb.jira.persistence.JiraWorklogSyncRepository;

/**
 * Writing the booked hours back to JIRA (#1007): one worklog per day and ticket, overwritten when
 * the sum moves, removed when the bookings are gone, and untouched when nothing changed.
 */
@FixedClock
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class JiraWorklogSyncServiceTest {

  private static final LocalDate SYNC_FROM = LocalDate.of(2026, 6, 1);
  private static final LocalDate DAY = LocalDate.of(2026, 6, 10);
  private static final String SCOPE = "ALPHA";

  @InjectMocks
  private JiraWorklogSyncService classUnderTest;

  @Mock
  private CommandPublisher commandPublisher;

  @Mock
  private JiraScopeSuborders scopeSuborders;

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
    when(scopeSuborders.idsOf(SCOPE)).thenReturn(List.of(1L, 2L));
    when(syncRepository.findByScopeSignAndWorkDateGreaterThanEqual(anyString(), any()))
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
    givenBookings(new TicketDaySum(DAY, "ALPHA-1", 90));
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
  }

  @Test
  void the_worklog_carries_the_sum_over_everybody_who_booked_that_day() {
    // Two people, one ticket, one day — that is one worklog, not two.
    givenBookings(new TicketDaySum(DAY, "ALPHA-1", 90), new TicketDaySum(DAY, "ALPHA-1", 30));
    givenReplicatedTickets("ALPHA-1");
    when(worklogClient.create(any(), any())).thenReturn("10101");

    classUnderTest.sync(config());

    var entry = ArgumentCaptor.forClass(JiraWorklogEntry.class);
    verify(worklogClient).create(any(), entry.capture());
    assertThat(entry.getValue().minutes()).isEqualTo(120);
  }

  @Test
  void a_changed_booking_overwrites_the_worklog_of_that_day_instead_of_adding_one() {
    givenBookings(new TicketDaySum(DAY, "ALPHA-1", 120));
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
    givenBookings(new TicketDaySum(DAY, "ALPHA-1", 90));
    givenReplicatedTickets("ALPHA-1");
    givenStoredWorklog("ALPHA-1", DAY, "10101", 90);

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
    var stored = givenStoredWorklog("ALPHA-1", DAY, "10101", 90);

    classUnderTest.sync(config());

    verify(worklogClient).delete(any(), eq("10101"));
    verify(syncRepository).delete(stored);
  }

  @Test
  void a_day_left_with_nothing_but_zero_length_bookings_loses_its_worklog_too() {
    givenBookings(new TicketDaySum(DAY, "ALPHA-1", 0));
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
    givenBookings(new TicketDaySum(DAY, "ALPHA-1", 90), new TicketDaySum(DAY, "ALPAH-99", 60));
    givenReplicatedTickets("ALPHA-1");
    when(worklogClient.create(any(), any())).thenReturn("10101");

    classUnderTest.sync(config());

    var target = ArgumentCaptor.forClass(JiraWorklogTarget.class);
    verify(worklogClient).create(target.capture(), any());
    assertThat(target.getValue().issueKey()).isEqualTo("ALPHA-1");
  }

  @Test
  void a_reference_written_in_another_case_still_names_the_same_issue() {
    givenBookings(new TicketDaySum(DAY, "alpha-1", 90), new TicketDaySum(DAY, "ALPHA-1", 30));
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

  @Test
  void a_failure_on_one_ticket_leaves_the_others_and_the_remembered_row_alone() {
    givenBookings(new TicketDaySum(DAY, "ALPHA-1", 90), new TicketDaySum(DAY, "ALPHA-2", 60));
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
    givenBookings(new TicketDaySum(DAY, "ALPHA-1", 120));
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

    verify(syncRepository).findByScopeSignAndWorkDateGreaterThanEqual(SCOPE, SYNC_FROM);
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
  void a_scope_that_matches_no_suborder_writes_nothing() {
    when(scopeSuborders.idsOf(SCOPE)).thenReturn(List.of());

    classUnderTest.sync(config());

    verifyNoInteractions(worklogClients, commandPublisher);
  }

  private JiraReplicationConfig config() {
    var config = new JiraReplicationConfig();
    config.setName("Alpha");
    config.setScopeSign(SCOPE);
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

  private void givenReplicatedTickets(String... keys) {
    var tickets = new ArrayList<JiraTicket>();
    for (String key : keys) {
      var ticket = new JiraTicket();
      ticket.setScopeSign(SCOPE);
      ticket.setKey(key);
      tickets.add(ticket);
    }
    when(ticketRepository.findByScopeSignAndKeyIn(eq(SCOPE), anyList())).thenReturn(tickets);
  }

  private JiraWorklogSync givenStoredWorklog(String issueKey, LocalDate workDate, String worklogId,
                                             int minutes) {
    var row = new JiraWorklogSync();
    row.setScopeSign(SCOPE);
    row.setIssueKey(issueKey);
    row.setWorkDate(workDate);
    row.setWorklogId(worklogId);
    row.setMinutes(minutes);
    when(syncRepository.findByScopeSignAndWorkDateGreaterThanEqual(SCOPE, SYNC_FROM))
        .thenReturn(List.of(row));
    return row;
  }

  private JiraWorklogSync savedRow() {
    var captor = ArgumentCaptor.forClass(JiraWorklogSync.class);
    verify(syncRepository).save(captor.capture());
    return captor.getValue();
  }
}
