package de.hbt.salat.invoice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static de.hbt.salat.invoice.domain.InvoiceGrouping.SUBORDER;
import static de.hbt.salat.invoice.domain.InvoiceGrouping.SUBORDER_TICKET;

import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
import de.hbt.salat.invoice.domain.InvoiceSuborder;
import de.hbt.salat.invoice.domain.InvoiceTimereport;
import de.hbt.salat.invoice.service.InvoiceService.InvoiceOptions;
import de.hbt.salat.jira.service.JiraTicketService;
import de.hbt.salat.order.domain.Customerorder;
import de.hbt.salat.order.domain.Suborder;
import de.hbt.salat.order.service.CustomerorderService;
import de.hbt.salat.order.service.SuborderService;

/**
 * Subtotals along the tree of suborders (#217). A suborder that only structures the order carries no
 * booking itself; its row shows what was booked below it, and the total still counts every booking once.
 *
 * <pre>
 * 01 Projektleitung      (S1, no bookings)
 *   01/01 Konzeption     (S11, 5:00 + 3:00)
 *   01/02 Abstimmung     (S12, 4:00)
 * 02 Umsetzung           (S2, 6:00)
 * </pre>
 */
@DisplayNameGeneration(ReplaceUnderscores.class)
class InvoiceServiceSuborderTreeTest {

  private static final long ORDER = 1L;
  private static final long S1 = 1L;
  private static final long S11 = 11L;
  private static final long S12 = 12L;
  private static final long S2 = 2L;
  private static final LocalDate JAN = LocalDate.of(2026, 1, 1);
  private static final LocalDate JAN_31 = LocalDate.of(2026, 1, 31);

  private final Map<Long, List<TimereportDTO>> bookings = new HashMap<>();
  private final List<Suborder> suborders = new ArrayList<>();
  private Customerorder customerorder;
  private BudgetQueryService budgetQueryService;
  private InvoiceService service;

  @BeforeEach
  void setUp() {
    var customerorderService = mock(CustomerorderService.class);
    var suborderService = mock(SuborderService.class);
    var timereportService = mock(TimereportService.class);
    budgetQueryService = mock(BudgetQueryService.class);

    customerorder = new Customerorder();
    customerorder.setSign("co");
    customerorder.setShortdescription("order");
    customerorder.setCustomer(new Customer());
    setId(customerorder, ORDER);
    when(customerorderService.getCustomerorderById(ORDER)).thenReturn(customerorder);

    var s1 = suborder("01", S1, null);
    suborder("01", S11, s1);
    suborder("02", S12, s1);
    suborder("02", S2, null);
    when(suborderService.getSubordersByCustomerorderId(ORDER)).thenAnswer(invocation -> List.copyOf(suborders));
    when(timereportService.getTimereportsByDatesAndSuborderId(any(), any(), anyLong()))
        .thenAnswer(invocation -> bookings.getOrDefault((Long) invocation.getArgument(2), List.of()));

    book(110, S11, 5 * 60);
    book(111, S11, 3 * 60);
    book(120, S12, 4 * 60);
    book(200, S2, 6 * 60);

    service = new InvoiceService(customerorderService, suborderService, timereportService, budgetQueryService,
        mock(JiraTicketService.class));
  }

  @Test
  void a_branch_without_bookings_shows_the_sum_of_its_children() {
    var data = generate(SUBORDER);

    assertThat(data.getGroups()).extracting(this::suborderId).containsExactly(S1, S2);
    var branch = group(data, S1);
    assertThat(branch.getLines()).isEmpty();
    assertThat(branch.getChildren()).extracting(this::suborderId).containsExactly(S11, S12);
    assertThat(branch.getTotalDurationVisible()).isEqualTo(hours(12));
    assertThat(group(data, S11).getTotalDurationVisible()).isEqualTo(hours(8));
    assertThat(group(data, S12).getTotalDurationVisible()).isEqualTo(hours(4));
    assertThat(group(data, S2).getTotalDurationVisible()).isEqualTo(hours(6));
  }

  @Test
  void the_total_counts_every_booking_once() {
    var data = generate(SUBORDER);

    assertThat(data.getTotalDuration()).isEqualTo(hours(18));
    assertThat(data.getTotalDurationVisible()).isEqualTo(hours(18));
    assertThat(data.getGroups().stream().map(InvoiceGroup::getTotalDurationVisible).reduce(Duration::plus))
        .contains(hours(18));
  }

  @Test
  void the_depth_follows_the_tree_and_every_line_counts_into_each_group_above_it() {
    var data = generate(SUBORDER);

    assertThat(group(data, S1).getDepth()).isZero();
    assertThat(group(data, S11).getDepth()).isEqualTo(1);
    var line = group(data, S11).getLines().get(0);
    assertThat(line.getGroupPath()).isEqualTo(group(data, S1).getId() + " " + group(data, S11).getId());
  }

  @Test
  void three_levels_add_up_on_every_level() {
    var s11 = suborders.stream().filter(suborder -> suborder.getId() == S11).findFirst().orElseThrow();
    suborder("01", 111L, s11);
    book(1110, 111L, 2 * 60);

    var data = generate(SUBORDER);

    assertThat(group(data, 111L).getDepth()).isEqualTo(2);
    assertThat(group(data, S11).getTotalDurationVisible()).isEqualTo(hours(10));
    assertThat(group(data, S1).getTotalDurationVisible()).isEqualTo(hours(14));
    assertThat(data.getTotalDurationVisible()).isEqualTo(hours(20));
  }

  @Test
  void a_deselected_child_or_booking_lowers_the_subtotal_of_the_branch() {
    var data = generate(SUBORDER);

    invoiceSuborder(data, S12).setVisible(false);
    booking(data, 111).setVisible(false);

    assertThat(group(data, S1).getTotalDurationVisible()).isEqualTo(hours(5));
    assertThat(data.getTotalDurationVisible()).isEqualTo(hours(11));
  }

  /** The branch loses its row and its own bookings; the suborders below it stay with theirs. */
  @Test
  void a_deselected_branch_keeps_its_children() {
    book(100, S1, 60);
    var data = generate(SUBORDER);

    invoiceSuborder(data, S1).setVisible(false);

    assertThat(group(data, S1).isVisible()).isFalse();
    assertThat(group(data, S11).isVisible()).isTrue();
    assertThat(group(data, S12).isVisible()).isTrue();
    assertThat(group(data, S11).getTotalDurationVisible()).isEqualTo(hours(8));
    assertThat(data.getTotalDurationVisible()).isEqualTo(hours(18));
  }

  /** A structuring branch that is not invoicable drops out; its invoicable children move up. */
  @Test
  void the_children_of_a_branch_that_drops_out_move_up_to_the_nearest_ancestor_left() {
    suborders.get(0).setInvoice('N');

    var data = generate(SUBORDER);

    assertThat(data.getGroups()).extracting(this::suborderId).containsExactly(S11, S12, S2);
    assertThat(group(data, S11).getDepth()).isZero();
    assertThat(data.getTotalDurationVisible()).isEqualTo(hours(18));
  }

  @Test
  void a_grandchild_hangs_below_its_grandparent_when_the_parent_drops_out() {
    var s11 = suborders.stream().filter(suborder -> suborder.getId() == S11).findFirst().orElseThrow();
    suborder("01", 111L, s11);
    book(1110, 111L, 2 * 60);
    s11.setInvoice('N');

    var data = generate(SUBORDER);

    // in the order of the complete sign: co/01/01/01 before co/01/02
    assertThat(group(data, S1).getChildren()).extracting(this::suborderId).containsExactly(111L, S12);
    assertThat(group(data, 111L).getDepth()).isEqualTo(1);
    assertThat(group(data, S1).getTotalDurationVisible()).isEqualTo(hours(6));
  }

  @Test
  void one_suborder_means_its_subtree() {
    var data = service.generateInvoiceData(ORDER, Optional.of(S1), Optional.empty(), january(), options(SUBORDER));

    assertThat(data.getGroups()).extracting(this::suborderId).containsExactly(S1);
    assertThat(group(data, S1).getChildren()).extracting(this::suborderId).containsExactly(S11, S12);
    assertThat(data.getTotalDurationVisible()).isEqualTo(hours(12));
  }

  @Test
  void the_filters_apply_to_every_node_of_the_chosen_subtree() {
    suborders.stream().filter(suborder -> suborder.getId() == S12).findFirst().orElseThrow().setInvoice('N');

    var data = service.generateInvoiceData(ORDER, Optional.of(S1), Optional.empty(), january(), options(SUBORDER));

    assertThat(group(data, S1).getChildren()).extracting(this::suborderId).containsExactly(S11);
    assertThat(data.getTotalDurationVisible()).isEqualTo(hours(8));
  }

  /** The branch has no assigned booking itself, but one below it — so it stays, with the assigned share only. */
  @Test
  void with_a_budget_plan_a_branch_stays_as_long_as_its_subtree_is_billed() {
    when(budgetQueryService.getAssignedTimereportIds(7L)).thenReturn(List.of(120L));

    var data = service.generateInvoiceData(ORDER, Optional.empty(), Optional.of(7L), january(), options(SUBORDER));

    assertThat(data.getGroups()).extracting(this::suborderId).containsExactly(S1);
    assertThat(group(data, S1).getChildren()).extracting(this::suborderId).containsExactly(S12);
    assertThat(group(data, S1).getTotalDurationVisible()).isEqualTo(hours(4));
    assertThat(data.getTotalDurationVisible()).isEqualTo(hours(4));
  }

  /** Below a branch its own ticket groups come first, then the suborders below it. */
  @Test
  void grouped_by_suborder_and_ticket_the_child_suborders_follow_the_own_tickets() {
    bookWithTicket(100, S1, 60, "ABC-1");
    bookWithTicket(112, S11, 30, "ABC-2");

    var data = generate(SUBORDER_TICKET);

    var branch = group(data, S1);
    assertThat(branch.getChildren()).extracting(InvoiceGroup::getKind)
        .containsExactly(InvoiceGroup.Kind.TICKET, InvoiceGroup.Kind.SUBORDER, InvoiceGroup.Kind.SUBORDER);
    assertThat(branch.getTotalDurationVisible()).isEqualTo(hours(12).plusMinutes(90));
    assertThat(data.getTotalDurationVisible()).isEqualTo(hours(18).plusMinutes(90));
  }

  // --- fixture ---------------------------------------------------------------------------------

  private InvoiceData generate(InvoiceGrouping grouping) {
    return service.generateInvoiceData(ORDER, Optional.empty(), Optional.empty(), january(), options(grouping));
  }

  private static InvoiceOptions options(InvoiceGrouping grouping) {
    return InvoiceOptions.builder().showTimereports(true).grouping(grouping).build();
  }

  private static LocalDateRange january() {
    return new LocalDateRange(JAN, JAN_31);
  }

  private long suborderId(InvoiceGroup group) {
    return group.getSuborder().getId();
  }

  private static InvoiceGroup group(InvoiceData data, long suborderId) {
    return allGroups(data.getGroups())
        .filter(group -> group.isSuborderGroup() && group.getSuborder().getId() == suborderId)
        .findFirst().orElseThrow();
  }

  private static Stream<InvoiceGroup> allGroups(List<InvoiceGroup> groups) {
    return groups.stream().flatMap(group -> Stream.concat(Stream.of(group), allGroups(group.getChildren())));
  }

  private static InvoiceSuborder invoiceSuborder(InvoiceData data, long suborderId) {
    return data.getSuborders().stream().filter(suborder -> suborder.getId() == suborderId).findFirst().orElseThrow();
  }

  private static InvoiceTimereport booking(InvoiceData data, long timereportId) {
    return data.getSuborders().stream().flatMap(suborder -> suborder.getTimereports().stream())
        .filter(booking -> booking.getId() == timereportId).findFirst().orElseThrow();
  }

  private void book(long id, long suborderId, int minutes) {
    bookWithTicket(id, suborderId, minutes);
  }

  private void bookWithTicket(long id, long suborderId, int minutes, String... references) {
    bookings.computeIfAbsent(suborderId, k -> new ArrayList<>()).add(TimereportDTO.builder()
        .id(id)
        .suborderId(suborderId)
        .employeeSign("emp")
        .referenceday(JAN.plusDays(1))
        .duration(Duration.ofMinutes(minutes))
        .taskdescription("work")
        .ticketReferences(List.of(references))
        .build());
  }

  private static Duration hours(int hours) {
    return Duration.ofHours(hours);
  }

  private Suborder suborder(String sign, long id, Suborder parent) {
    var suborder = new Suborder();
    setId(suborder, id);
    suborder.setSign(sign);
    suborder.setShortdescription(sign);
    suborder.setCustomerorder(customerorder);
    suborder.setParentorder(parent);
    suborder.setInvoice('Y');
    suborder.deriveCompleteOrderSign();
    suborders.add(suborder);
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
