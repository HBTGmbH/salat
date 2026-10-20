package de.hbt.salat.invoice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static de.hbt.salat.invoice.domain.InvoiceGrouping.SUBORDER;
import static de.hbt.salat.invoice.domain.InvoiceGrouping.SUBORDER_TICKET;
import static de.hbt.salat.invoice.domain.InvoiceGrouping.TICKET;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import de.hbt.salat.budget.service.BudgetQueryService;
import de.hbt.salat.common.LocalDateRange;
import de.hbt.salat.common.domain.AuditedEntity;
import de.hbt.salat.customer.domain.Customer;
import de.hbt.salat.dailyreport.domain.TimereportDTO;
import de.hbt.salat.dailyreport.service.TimereportService;
import de.hbt.salat.invoice.domain.InvoiceData;
import de.hbt.salat.invoice.domain.InvoiceGroup;
import de.hbt.salat.invoice.domain.InvoiceGrouping;
import de.hbt.salat.invoice.domain.InvoiceLine;
import de.hbt.salat.invoice.domain.InvoiceProjectKey;
import de.hbt.salat.invoice.domain.InvoiceTicketOption;
import de.hbt.salat.invoice.service.InvoiceService.InvoiceOptions;
import de.hbt.salat.jira.domain.JiraTicketInfo;
import de.hbt.salat.jira.service.JiraTicketService;
import de.hbt.salat.order.domain.Customerorder;
import de.hbt.salat.order.domain.Suborder;
import de.hbt.salat.order.service.CustomerorderService;
import de.hbt.salat.order.service.SuborderService;

/**
 * Grouping an invoice by the tickets its bookings refer to (#1390) and along the ticket hierarchy
 * (#1393). Whatever the grouping and the selection, every minute is billed exactly once: the
 * selection only decides which group a minute falls to.
 */
@DisplayNameGeneration(ReplaceUnderscores.class)
class InvoiceServiceTicketGroupingTest {

  private static final long ORDER = 1L;
  /** Two top-level suborders, and one below the second. */
  private static final long S10 = 10L;
  private static final long S20 = 20L;
  private static final long S21 = 21L;
  private static final LocalDate JAN = LocalDate.of(2026, 1, 1);
  private static final LocalDate JAN_31 = LocalDate.of(2026, 1, 31);
  private static final LocalDateTime EARLIER = LocalDateTime.of(2026, 1, 1, 8, 0);
  private static final LocalDateTime LATER = LocalDateTime.of(2026, 1, 2, 8, 0);

  private final Map<Long, List<TimereportDTO>> bookings = new HashMap<>();
  private final List<JiraTicketInfo> tickets = new ArrayList<>();
  private JiraTicketService jiraTicketService;
  private InvoiceService service;

  @BeforeEach
  void setUp() {
    var customerorderService = mock(CustomerorderService.class);
    var suborderService = mock(SuborderService.class);
    var timereportService = mock(TimereportService.class);
    jiraTicketService = mock(JiraTicketService.class);

    var customerorder = new Customerorder();
    customerorder.setSign("co");
    customerorder.setShortdescription("order");
    customerorder.setCustomer(new Customer());
    setId(customerorder, ORDER);
    when(customerorderService.getCustomerorderById(ORDER)).thenReturn(customerorder);

    var s10 = suborder("01", S10, customerorder, null);
    var s20 = suborder("02", S20, customerorder, null);
    var s21 = suborder("03", S21, customerorder, s20);
    when(suborderService.getSubordersByCustomerorderId(ORDER)).thenReturn(List.of(s10, s20, s21));
    when(timereportService.getTimereportsByDatesAndSuborderId(any(), any(), anyLong()))
        .thenAnswer(invocation -> bookings.getOrDefault((Long) invocation.getArgument(2), List.of()));
    when(jiraTicketService.getTickets(any(), any())).thenAnswer(invocation -> List.copyOf(tickets));

    service = new InvoiceService(customerorderService, suborderService, timereportService,
        mock(BudgetQueryService.class), jiraTicketService);
  }

  // --- grouping by suborder ------------------------------------------------------------------

  @Test
  void the_grouping_by_suborder_is_the_invoice_as_before() {
    book(100, S10, 1, 60, "ABC-1", "ABC-2");
    book(101, S10, 2, 30);
    tickets.add(ticket("ABC-1", "Login", null, null, LATER));

    var data = generate(SUBORDER, null);

    // S21 stands below its parent (#217)
    assertThat(data.getGroups()).extracting(group -> group.getSuborder().getId()).containsExactly(S10, S20);
    assertThat(data.getGroups().get(1).getChildren()).extracting(group -> group.getSuborder().getId())
        .containsExactly(S21);
    var first = data.getGroups().get(0);
    assertThat(first.getChildren()).isEmpty();
    assertThat(first.getLines()).extracting(line -> line.getTimereport().getId()).containsExactly(100L, 101L);
    // whole bookings, naming all their references
    assertThat(first.getLines().get(0).getShare()).isEqualTo(minutes(60));
    assertThat(first.getLines().get(0).getTicketText()).isEqualTo("ABC-1, ABC-2");
  }

  /** Nothing to group by: no question to the ticket module, and no ticket grouping whatever was asked for. */
  @Test
  void without_any_reference_the_invoice_is_grouped_by_suborder() {
    book(100, S10, 1, 60);

    var data = generate(TICKET, null);

    assertThat(data.getGrouping()).isEqualTo(SUBORDER);
    assertThat(data.isTicketReferencesPresent()).isFalse();
    assertThat(data.getGroups()).allMatch(InvoiceGroup::isSuborderGroup);
    verify(jiraTicketService, never()).getTickets(any(), any());
  }

  // --- grouping by ticket ----------------------------------------------------------------------

  @Test
  void every_reference_gets_a_group_with_its_subtotal_and_the_rest_goes_to_without_ticket() {
    book(100, S10, 1, 180, "ABC-12");
    book(101, S10, 2, 60, "abc-99");
    book(102, S10, 3, 30, "Wartung");
    book(103, S10, 4, 90);
    tickets.add(ticket("ABC-12", "Login schlägt fehl", null, null, LATER));

    var suborderGroup = generate(SUBORDER_TICKET, null).getGroups().get(0);

    assertThat(suborderGroup.getLines()).isEmpty();
    // keys first, by number; free text after; „ohne Ticket“ last
    assertThat(suborderGroup.getChildren()).extracting(InvoiceGroup::getHeading)
        .containsExactly("ABC-12 - Login schlägt fehl", "abc-99", "Wartung", null);
    assertThat(suborderGroup.getChildren()).extracting(InvoiceGroup::getTotalDuration)
        .containsExactly(minutes(180), minutes(60), minutes(30), minutes(90));
    assertThat(suborderGroup.getChildren().get(3).isNoTicketGroup()).isTrue();
    assertThat(suborderGroup.getTotalDuration()).isEqualTo(minutes(360));
  }

  @Test
  void keys_are_ordered_by_number_not_as_text() {
    book(100, S10, 1, 10, "ABC-10");
    book(101, S10, 2, 10, "ABC-2");

    var data = generate(TICKET, null);

    assertThat(data.getGroups()).extracting(InvoiceGroup::getHeading).containsExactly("ABC-2", "ABC-10");
  }

  /** The remaining minute goes to the first reference; the shares add up to the booking. */
  @Test
  void a_booking_with_several_references_is_divided_evenly() {
    book(100, S10, 1, 241, "ABC-15", "ABC-12");

    var data = generate(TICKET, null);

    assertThat(data.getGroups()).extracting(InvoiceGroup::getHeading).containsExactly("ABC-12", "ABC-15");
    assertThat(shareOf(data, "ABC-15", 100)).isEqualTo(minutes(121));
    assertThat(shareOf(data, "ABC-12", 100)).isEqualTo(minutes(120));
    assertThat(sumOfTopGroups(data)).isEqualTo(data.getTotalDuration()).isEqualTo(minutes(241));
    var line = lineOf(data, "ABC-15", 100);
    assertThat(line.isShared()).isTrue();
    // within a ticket group the line names its own ticket only
    assertThat(line.getTicketText()).isEqualTo("ABC-15");
  }

  @Test
  void the_grouping_by_ticket_brings_bookings_of_different_suborders_together() {
    book(100, S10, 2, 60, "ABC-12");
    book(200, S20, 1, 30, "ABC-12");

    var data = generate(TICKET, null);

    assertThat(data.getGroups()).hasSize(1);
    var lines = data.getGroups().get(0).getLines();
    // by day across the suborders
    assertThat(lines).extracting(line -> line.getTimereport().getId()).containsExactly(200L, 100L);
    assertThat(lines).extracting(line -> line.getSuborder().getId()).containsExactly(S20, S10);
  }

  // --- the selection -----------------------------------------------------------------------------

  @Test
  void at_first_every_booked_reference_is_selected() {
    book(100, S10, 1, 60, "ABC-12", "Wartung");

    var data = generate(TICKET, null);

    assertThat(data.getTicketOptions()).allMatch(InvoiceTicketOption::selected);
  }

  @Test
  void a_deselected_reference_leaves_the_whole_booking_to_the_selected_ones() {
    book(100, S10, 1, 90, "ABC-12", "XYZ-3", "ABC-15");

    var data = generate(TICKET, Set.of("ABC-12", "abc-15"));

    assertThat(data.getGroups()).extracting(InvoiceGroup::getHeading).containsExactly("ABC-12", "ABC-15");
    assertThat(shareOf(data, "ABC-12", 100)).isEqualTo(minutes(45));
    assertThat(shareOf(data, "ABC-15", 100)).isEqualTo(minutes(45));
    assertThat(sumOfTopGroups(data)).isEqualTo(minutes(90));
  }

  @Test
  void a_booking_whose_tickets_are_all_deselected_stands_in_full_under_without_ticket() {
    book(100, S10, 1, 90, "XYZ-3", "XYZ-4");
    book(101, S10, 2, 30, "ABC-1");

    var data = generate(TICKET, Set.of("ABC-1"));

    var withoutTicket = data.getGroups().get(data.getGroups().size() - 1);
    assertThat(withoutTicket.isNoTicketGroup()).isTrue();
    assertThat(withoutTicket.getLines()).singleElement().satisfies(line -> {
      assertThat(line.getShare()).isEqualTo(minutes(90));
      assertThat(line.getTicketText()).isEqualTo("XYZ-3, XYZ-4");
    });
    assertThat(sumOfTopGroups(data)).isEqualTo(minutes(120));
  }

  /** Deliberately empty is not the same as never sent. */
  @Test
  void an_empty_selection_puts_everything_under_without_ticket() {
    book(100, S10, 1, 60, "ABC-12");

    var data = generate(TICKET, Set.of());

    assertThat(data.getGroups()).singleElement().matches(InvoiceGroup::isNoTicketGroup);
    assertThat(data.getTicketOptions()).noneMatch(InvoiceTicketOption::selected);
  }

  @Test
  void project_keys_count_their_tickets_and_free_text_has_none() {
    book(100, S10, 1, 60, "ABC-12", "ABC-15", "XYZ-3", "Wartung");

    var data = generate(TICKET, null);

    assertThat(data.getProjectKeys()).containsExactly(new InvoiceProjectKey("ABC", 2), new InvoiceProjectKey("XYZ", 1));
  }

  // --- scopes -------------------------------------------------------------------------------------

  /** A key is unique per scope only; within the branch, the most recently updated entry counts. */
  @Test
  void the_heading_comes_from_the_most_recently_updated_ticket_of_the_branch() {
    book(210, S21, 1, 60, "ABC-12");
    tickets.add(ticket("ABC-12", "older", null, S20, EARLIER));
    tickets.add(ticket("ABC-12", "newer", null, S21, LATER));
    // a sibling branch does not count, however recent
    tickets.add(ticket("ABC-12", "elsewhere", null, S10, LATER.plusDays(1)));

    var data = generate(TICKET, null);

    assertThat(data.getGroups()).extracting(InvoiceGroup::getHeading).containsExactly("ABC-12 - newer");
  }

  @Test
  void a_ticket_kept_for_the_whole_order_counts_in_every_branch() {
    book(100, S10, 1, 60, "ABC-12");
    tickets.add(ticket("ABC-12", "order-wide", null, null, EARLIER));

    var data = generate(TICKET, null);

    assertThat(data.getGroups()).extracting(InvoiceGroup::getHeading).containsExactly("ABC-12 - order-wide");
  }

  // --- hierarchy (#1393) -----------------------------------------------------------------------

  @Test
  void the_selection_offers_the_tickets_above_the_booked_ones_as_a_tree_without_selecting_them() {
    book(100, S10, 1, 300, "ABC-12");
    book(101, S10, 2, 120, "ABC-15");
    givenHierarchy();

    var options = generate(TICKET, null).getTicketOptions();

    assertThat(options).extracting(InvoiceTicketOption::key, InvoiceTicketOption::level, InvoiceTicketOption::selected)
        .containsExactly(
            tuple("ABC-1", 0, false),
            tuple("ABC-10", 1, false),
            tuple("ABC-12", 2, true),
            tuple("ABC-15", 2, true));
    assertThat(options.get(0).booked()).isNull();
    assertThat(options.get(2).booked()).isEqualTo(minutes(300));
  }

  @Test
  void a_booking_falls_to_the_nearest_selected_ticket_of_its_chain() {
    book(100, S10, 1, 300, "ABC-12");
    book(101, S10, 2, 120, "ABC-15");
    givenHierarchy();

    var data = generate(TICKET, Set.of("ABC-10"));

    assertThat(data.getGroups()).singleElement().satisfies(epic -> {
      assertThat(epic.getHeading()).isEqualTo("ABC-10 - Anmeldung");
      assertThat(epic.isAncestor()).isTrue();
      assertThat(epic.getLines()).hasSize(2);
      assertThat(epic.getTotalDuration()).isEqualTo(minutes(420));
    });
    // the column still names the booked ticket
    assertThat(data.getGroups().get(0).getLines()).extracting(InvoiceLine::getTicketText).containsExactly("ABC-12", "ABC-15");
  }

  @Test
  void selected_tickets_of_several_levels_nest_and_each_subtotal_includes_the_levels_below() {
    book(100, S10, 1, 300, "ABC-12");
    book(101, S10, 2, 120, "ABC-15");
    book(102, S10, 3, 60, "ABC-20");
    givenHierarchy();
    tickets.add(ticket("ABC-20", "Teilaufgabe", "ABC-12", null, EARLIER));

    var data = generate(TICKET, Set.of("ABC-1", "ABC-10", "ABC-20"));

    var initiative = data.getGroups().get(0);
    assertThat(initiative.getHeading()).isEqualTo("ABC-1 - Kundenportal");
    assertThat(initiative.getLines()).isEmpty();
    var epic = initiative.getChildren().get(0);
    // ABC-12 and ABC-15 are not selected, so their bookings stand directly at the epic
    assertThat(epic.getLines()).extracting(line -> line.getTimereport().getId()).containsExactly(100L, 101L);
    var subtask = epic.getChildren().get(0);
    assertThat(subtask.getHeading()).isEqualTo("ABC-20 - Teilaufgabe");
    assertThat(subtask.getTotalDuration()).isEqualTo(minutes(60));
    assertThat(epic.getTotalDuration()).isEqualTo(minutes(480));
    assertThat(initiative.getTotalDuration()).isEqualTo(minutes(480));
    assertThat(subtask.getDepth()).isEqualTo(2);
    assertThat(subtask.getLines().get(0).getGroupPath())
        .isEqualTo(initiative.getId() + " " + epic.getId() + " " + subtask.getId());
  }

  /** Two shares of one booking in the same group make one line with their sum. */
  @Test
  void shares_with_a_common_selected_ancestor_merge_into_one_line() {
    book(100, S10, 1, 241, "ABC-12", "ABC-15");
    givenHierarchy();

    var data = generate(TICKET, Set.of("ABC-10"));

    var epic = data.getGroups().get(0);
    assertThat(epic.getLines()).singleElement().satisfies(line -> {
      assertThat(line.getShare()).isEqualTo(minutes(241));
      assertThat(line.isShared()).isFalse();
      assertThat(line.getTicketText()).isEqualTo("ABC-12, ABC-15");
    });
  }

  @Test
  void references_without_a_ticket_have_no_ancestors() {
    book(100, S10, 1, 60, "ABC-99", "Wartung");
    givenHierarchy();

    var data = generate(TICKET, Set.of("ABC-1", "ABC-10"));

    assertThat(data.getGroups()).singleElement().matches(InvoiceGroup::isNoTicketGroup);
    assertThat(data.getTicketOptions()).extracting(InvoiceTicketOption::key).containsExactly("ABC-99", "Wartung");
  }

  @Test
  void a_parent_outside_the_branch_ends_the_chain() {
    book(210, S21, 1, 60, "ABC-12");
    tickets.add(ticket("ABC-12", "Login", "ABC-10", S21, EARLIER));
    tickets.add(ticket("ABC-10", "Anmeldung", null, S10, EARLIER));

    var data = generate(TICKET, Set.of("ABC-10"));

    assertThat(data.getGroups()).singleElement().matches(InvoiceGroup::isNoTicketGroup);
    assertThat(data.getTicketOptions()).extracting(InvoiceTicketOption::key).containsExactly("ABC-12");
  }

  @Test
  void a_cycle_in_the_parents_does_not_hang_the_invoice() {
    book(100, S10, 1, 60, "ABC-1");
    tickets.add(ticket("ABC-1", "a", "ABC-2", null, EARLIER));
    tickets.add(ticket("ABC-2", "b", "ABC-1", null, EARLIER));

    var data = generate(TICKET, Set.of("ABC-1", "ABC-2"));

    assertThat(sumOfTopGroups(data)).isEqualTo(minutes(60));
    assertThat(data.getTicketOptions()).hasSize(2);
  }

  @Test
  void within_a_suborder_the_tickets_nest_as_well() {
    book(100, S10, 1, 300, "ABC-12");
    book(101, S10, 2, 120, "ABC-15");
    givenHierarchy();

    var data = generate(SUBORDER_TICKET, Set.of("ABC-10", "ABC-12"));

    var suborderGroup = data.getGroups().get(0);
    var epic = suborderGroup.getChildren().get(0);
    assertThat(epic.getHeading()).isEqualTo("ABC-10 - Anmeldung");
    assertThat(epic.getLines()).extracting(line -> line.getTimereport().getId()).containsExactly(101L);
    assertThat(epic.getChildren()).extracting(InvoiceGroup::getHeading).containsExactly("ABC-12 - Login schlägt fehl");
    assertThat(suborderGroup.getTotalDuration()).isEqualTo(minutes(420));
  }

  // --- the comment -------------------------------------------------------------------------------

  @Test
  void the_comment_gets_the_tickets_it_does_not_name_yet() {
    bookWithComment(100, S10, 1, 60, "comment ABC-15", "ABC-12", "ABC-15");

    var line = lineOf(generate(SUBORDER, null), null, 100);

    assertThat(line.getTaskDescriptionWithTickets()).isEqualTo("ABC-12 comment ABC-15");
  }

  // --- fixture ---------------------------------------------------------------------------------

  /** ABC-1 Initiative → ABC-10 Epic → ABC-12, ABC-15 Stories, all kept for the whole order. */
  private void givenHierarchy() {
    tickets.add(ticket("ABC-1", "Kundenportal", null, null, EARLIER));
    tickets.add(ticket("ABC-10", "Anmeldung", "ABC-1", null, EARLIER));
    tickets.add(ticket("ABC-12", "Login schlägt fehl", "ABC-10", null, EARLIER));
    tickets.add(ticket("ABC-15", "Passwort zurücksetzen", "ABC-10", null, EARLIER));
  }

  private InvoiceData generate(InvoiceGrouping grouping, Set<String> selection) {
    var options = InvoiceOptions.builder().showTimereports(true).grouping(grouping).ticketSelection(selection).build();
    return service.generateInvoiceData(ORDER, Optional.empty(), Optional.empty(), new LocalDateRange(JAN, JAN_31), options);
  }

  private void book(long id, long suborderId, int day, int minutes, String... references) {
    bookWithComment(id, suborderId, day, minutes, "work", references);
  }

  private void bookWithComment(long id, long suborderId, int day, int minutes, String comment, String... references) {
    bookings.computeIfAbsent(suborderId, k -> new ArrayList<>()).add(TimereportDTO.builder()
        .id(id)
        .suborderId(suborderId)
        .employeeSign("emp")
        .referenceday(JAN.plusDays(day))
        .duration(minutes(minutes))
        .taskdescription(comment)
        .ticketReferences(List.of(references))
        .build());
  }

  private static JiraTicketInfo ticket(String key, String summary, String parentKey, Long suborderId, LocalDateTime updated) {
    return new JiraTicketInfo(key, summary, "Story", parentKey, ORDER, suborderId, updated);
  }

  private static Duration minutes(int minutes) {
    return Duration.ofMinutes(minutes);
  }

  private static Duration sumOfTopGroups(InvoiceData data) {
    return data.getGroups().stream().map(InvoiceGroup::getTotalDuration).reduce(Duration.ZERO, Duration::plus);
  }

  private static Duration shareOf(InvoiceData data, String heading, long timereportId) {
    return lineOf(data, heading, timereportId).getShare();
  }

  /** The line of the booking in the group with this heading, anywhere in the tree; any group for {@code null}. */
  private static InvoiceLine lineOf(InvoiceData data, String heading, long timereportId) {
    return allGroups(data.getGroups())
        .filter(group -> heading == null || heading.equals(group.getHeading()))
        .flatMap(group -> group.getLines().stream())
        .filter(line -> line.getTimereport().getId() == timereportId)
        .findFirst().orElseThrow();
  }

  private static Stream<InvoiceGroup> allGroups(List<InvoiceGroup> groups) {
    return groups.stream().flatMap(group -> Stream.concat(Stream.of(group), allGroups(group.getChildren())));
  }

  private static Suborder suborder(String sign, long id, Customerorder customerorder, Suborder parent) {
    var suborder = new Suborder();
    setId(suborder, id);
    suborder.setSign(sign);
    suborder.setShortdescription(sign);
    suborder.setCustomerorder(customerorder);
    suborder.setParentorder(parent);
    suborder.setInvoice('Y');
    return suborder;
  }

  /** The id is generated, so there is no setter; a stored record always has one. */
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
