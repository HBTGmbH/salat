package de.hbt.salat.dailyreport.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static de.hbt.salat.testutils.ReferencedayTestUtils.referenceday;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import de.hbt.salat.auth.domain.AuthorizedUser;
import de.hbt.salat.auth.persistence.AuthorizedUserAuditorAware;
import de.hbt.salat.common.GlobalConstants;
import de.hbt.salat.common.LocalDateRange;
import de.hbt.salat.customer.domain.Customer;
import de.hbt.salat.dailyreport.auth.TimereportVisibility;
import de.hbt.salat.dailyreport.auth.TimereportVisibility.Clause;
import de.hbt.salat.dailyreport.domain.TagSelection;
import de.hbt.salat.dailyreport.domain.Timereport;
import de.hbt.salat.dailyreport.domain.TimereportListFilter;
import de.hbt.salat.dailyreport.domain.TimereportListFilter.Billable;
import de.hbt.salat.dailyreport.domain.TimereportListFilter.Sort;
import de.hbt.salat.dailyreport.persistence.TimereportListDAO.BookedDay;
import de.hbt.salat.employee.domain.Employee;
import de.hbt.salat.employee.domain.Employeecontract;
import de.hbt.salat.order.domain.Customerorder;
import de.hbt.salat.order.domain.Employeeorder;
import de.hbt.salat.order.domain.Suborder;
import de.hbt.salat.order.domain.SurchargeKind;

/**
 * The values the booking list offers in its filters, asked of the employee orders instead of the bookings (#1127).
 *
 * <p>The answer must stay the one the bookings give: an employee order is only a cheaper way to the same question,
 * so one that nobody booked on offers nothing, and the visibility restricts it exactly as it restricts a booking —
 * clause by clause, never as a cross product. Further tests hold the rows, the sums and the booked days of the order
 * history (#1534) to the same condition, because all of these queries share it.
 */
@DataJpaTest
@Import({AuthorizedUserAuditorAware.class, TimereportListDAO.class})
@DisplayNameGeneration(ReplaceUnderscores.class)
class TimereportListFilterValuesTest {

  private static final LocalDate DAY = LocalDate.of(2026, 6, 15);

  @Autowired
  private TimereportListDAO timereportListDAO;

  @Autowired
  private TimereportRepository timereportRepository;

  @Autowired
  private TestEntityManager entityManager;

  @MockitoBean
  private AuthorizedUser authorizedUser;

  private Customer customer;
  private Customer otherCustomer;

  @BeforeEach
  void setUp() {
    when(authorizedUser.isAuthenticated()).thenReturn(true);
    when(authorizedUser.getLoginSign()).thenReturn("test");
    customer = customer("TK");
    otherCustomer = customer("OK");
  }

  @Test
  void a_visible_booking_offers_its_employee_customer_order_and_suborder() {
    var reader = employeecontract("aaa");
    var order = customerorder(customer, "ALPHA");
    var suborder = suborder(order, "ALPHA/01", true);
    book(reader, suborder);

    var values = timereportListDAO.findFilterValues(ownBookingsOf(reader));

    assertThat(values.employeeIds()).containsExactly(reader.getEmployee().getId());
    assertThat(values.customerIds()).containsExactly(customer.getId());
    assertThat(values.customerOrderIds()).containsExactly(order.getId());
    assertThat(values.suborderIds()).containsExactly(suborder.getId());
  }

  @Test
  void an_employee_order_nobody_booked_on_offers_nothing() {
    var reader = employeecontract("aaa");
    var booked = suborder(customerorder(customer, "ALPHA"), "ALPHA/01", true);
    var unbooked = suborder(customerorder(otherCustomer, "BETA"), "BETA/01", true);
    book(reader, booked);
    employeeorder(reader, unbooked);

    var values = timereportListDAO.findFilterValues(ownBookingsOf(reader));

    assertThat(values.customerIds()).containsExactly(customer.getId());
    assertThat(values.suborderIds()).containsExactly(booked.getId());
  }

  @Test
  void an_employee_order_whose_only_booking_is_deleted_offers_nothing() {
    var reader = employeecontract("aaa");
    var suborder = suborder(customerorder(customer, "ALPHA"), "ALPHA/01", true);
    timereportRepository.delete(book(reader, suborder));
    entityManager.flush();
    entityManager.clear();

    var values = timereportListDAO.findFilterValues(ownBookingsOf(reader));

    assertThat(values.employeeIds()).isEmpty();
    assertThat(values.suborderIds()).isEmpty();
  }

  /**
   * The position of everybody responsible for an order: their own bookings, or those on their orders. A colleague's
   * booking on some other order is covered by neither clause — collecting the employees of one clause and the orders
   * of the other would offer it all the same.
   */
  @Test
  void the_clauses_are_alternatives_not_a_cross_product() {
    var reader = employeecontract("aaa");
    var colleague = employeecontract("bbb");
    var ownOrder = customerorder(customer, "ALPHA");
    var responsibleFor = customerorder(customer, "GAMMA");
    var elsewhere = customerorder(otherCustomer, "BETA");
    var ownSuborder = suborder(ownOrder, "ALPHA/01", true);
    var responsibleSuborder = suborder(responsibleFor, "GAMMA/01", true);
    var elsewhereSuborder = suborder(elsewhere, "BETA/01", true);
    book(reader, ownSuborder);
    book(colleague, responsibleSuborder);
    book(colleague, elsewhereSuborder);

    var visibility = TimereportVisibility.of(List.of(
        Clause.forEmployees(Set.of(reader.getEmployee().getId())),
        Clause.forOrders(Set.of(responsibleFor.getId()))));
    var values = timereportListDAO.findFilterValues(visibility);

    assertThat(values.employeeIds())
        .containsExactlyInAnyOrder(reader.getEmployee().getId(), colleague.getEmployee().getId());
    assertThat(values.customerIds()).containsExactly(customer.getId());
    assertThat(values.customerOrderIds()).containsExactlyInAnyOrder(ownOrder.getId(), responsibleFor.getId());
    assertThat(values.suborderIds()).containsExactlyInAnyOrder(ownSuborder.getId(), responsibleSuborder.getId());
  }

  @Test
  void the_billable_clause_offers_only_what_was_booked_on_a_billable_suborder() {
    var colleague = employeecontract("bbb");
    var billable = suborder(customerorder(customer, "ALPHA"), "ALPHA/01", true);
    var internal = suborder(customerorder(otherCustomer, "BETA"), "BETA/01", false);
    book(colleague, billable);
    book(colleague, internal);

    var values = timereportListDAO.findFilterValues(TimereportVisibility.of(List.of(Clause.billable())));

    assertThat(values.customerIds()).containsExactly(customer.getId());
    assertThat(values.suborderIds()).containsExactly(billable.getId());
  }

  @Test
  void the_rows_follow_the_same_clauses() {
    var reader = employeecontract("aaa");
    var colleague = employeecontract("bbb");
    var responsibleFor = customerorder(customer, "GAMMA");
    var own = book(reader, suborder(customerorder(customer, "ALPHA"), "ALPHA/01", true));
    var onMyOrder = book(colleague, suborder(responsibleFor, "GAMMA/01", true));
    book(colleague, suborder(customerorder(otherCustomer, "BETA"), "BETA/01", true));

    var visibility = TimereportVisibility.of(List.of(
        Clause.forEmployees(Set.of(reader.getEmployee().getId())),
        Clause.forOrders(Set.of(responsibleFor.getId()))));

    assertThat(timereportListDAO.findRows(wholeMonth(), visibility))
        .extracting(Timereport::getId)
        .containsExactlyInAnyOrder(own.getId(), onMyOrder.getId());
    assertThat(timereportListDAO.findTotals(wholeMonth(), visibility).count()).isEqualTo(2);
  }

  /** The sum row of the list adds the time surcharge credit of all hits (#1469). */
  @Test
  void the_totals_add_the_surcharge_credit_of_the_hits() {
    var reader = employeecontract("aaa");
    var suborder = suborder(customerorder(customer, "ALPHA"), "ALPHA/01", true);
    var flagged = book(reader, suborder);
    flagged.setSurcharge(SurchargeKind.NIGHT, 50);
    entityManager.flush();
    book(reader, suborder);

    var visibility = TimereportVisibility.of(List.of(Clause.forEmployees(Set.of(reader.getEmployee().getId()))));

    assertThat(timereportListDAO.findTotals(wholeMonth(), visibility).surchargeCredit()).isEqualTo(Duration.ofMinutes(30));
  }

  @Test
  void the_rows_still_take_the_filter_on_top_of_the_visibility() {
    var reader = employeecontract("aaa");
    var billable = book(reader, suborder(customerorder(customer, "ALPHA"), "ALPHA/01", true));
    book(reader, suborder(customerorder(otherCustomer, "BETA"), "BETA/01", false));

    var onlyBillable = new TimereportListFilter(List.of(), List.of(), List.of(), List.of(), List.of(), true, TagSelection.NONE,
        DAY.withDayOfMonth(1), DAY.withDayOfMonth(30), Billable.BILLABLE, Sort.DATE, false,
        TimereportListFilter.UNLIMITED);

    assertThat(timereportListDAO.findRows(onlyBillable, ownBookingsOf(reader)))
        .extracting(Timereport::getId)
        .containsExactly(billable.getId());
  }

  /**
   * Tags narrow the other filters (#847). The service has resolved them into order trees: a booking matches when its
   * order or its suborder lies in one of them.
   */
  @Test
  void the_tags_find_the_bookings_in_their_trees_together_with_the_other_filters() {
    var reader = employeecontract("aaa");
    var colleague = employeecontract("bbb");
    var taggedOrder = customerorder(customer, "ALPHA");
    var onTaggedOrder = book(reader, suborder(taggedOrder, "ALPHA/01", true));
    book(colleague, suborder(taggedOrder, "ALPHA/02", true));
    var otherOrder = customerorder(otherCustomer, "BETA");
    var taggedSuborder = suborder(otherOrder, "BETA/01", true);
    var onTaggedSuborder = book(reader, taggedSuborder);
    book(reader, suborder(otherOrder, "BETA/02", true));
    var both = TimereportVisibility.of(List.of(Clause.forEmployees(
        Set.of(reader.getEmployee().getId(), colleague.getEmployee().getId()))));

    var tags = new TagSelection(List.of(1L), List.of(taggedOrder.getId()), List.of(taggedSuborder.getId()));

    assertThat(timereportListDAO.findRows(withTags(tags, List.of(reader.getEmployee().getId())), both))
        .extracting(Timereport::getId)
        .containsExactlyInAnyOrder(onTaggedOrder.getId(), onTaggedSuborder.getId());
  }

  /** Chosen tags no order and no suborder carries leave nothing — not everything. */
  @Test
  void tags_nothing_carries_find_no_booking() {
    var reader = employeecontract("aaa");
    book(reader, suborder(customerorder(customer, "ALPHA"), "ALPHA/01", true));

    var tags = new TagSelection(List.of(1L), List.of(), List.of());

    assertThat(timereportListDAO.findRows(withTags(tags, List.of()), ownBookingsOf(reader))).isEmpty();
    assertThat(timereportListDAO.findTotals(withTags(tags, List.of()), ownBookingsOf(reader)).count()).isZero();
  }

  /**
   * A rule valid on part of the period covers the bookings on its own days only (#1534), in the rows as in the sums —
   * what {@code isAuthorized} says of each of them.
   */
  @Test
  void a_clause_with_days_covers_the_bookings_on_those_days_only() {
    var colleague = employeecontract("bbb");
    var order = customerorder(customer, "ALPHA");
    var suborder = suborder(order, "ALPHA/01", true);
    var onTheDay = book(colleague, suborder, DAY, 1);
    book(colleague, suborder, DAY.plusDays(5), 1);

    var visibility = TimereportVisibility.of(List.of(
        new Clause(Set.of(), Set.of(order.getId()), Set.of(), false, new LocalDateRange(DAY, DAY))));

    assertThat(timereportListDAO.findRows(wholeMonth(), visibility))
        .extracting(Timereport::getId)
        .containsExactly(onTheDay.getId());
    assertThat(timereportListDAO.findTotals(wholeMonth(), visibility).count()).isEqualTo(1);
    assertThat(timereportListDAO.findBookedDays(colleague.getEmployee().getId(), visibility, true))
        .extracting(BookedDay::day)
        .containsExactly(DAY);
  }

  /**
   * The order history reads one row per order and day (#1534): the bookings of the person on any suborder of the
   * order that day, summed; deleted bookings and those of other people stay out.
   */
  @Test
  void the_booked_days_sum_the_bookings_of_the_person_per_order_and_day() {
    var person = employeecontract("aaa");
    var colleague = employeecontract("bbb");
    var alpha = customerorder(customer, "ALPHA");
    var beta = customerorder(otherCustomer, "BETA");
    var alpha1 = suborder(alpha, "ALPHA/01", true);
    var alpha2 = suborder(alpha, "ALPHA/02", false);
    book(person, alpha1, DAY, 1);
    book(person, alpha2, DAY, 2);
    book(person, alpha1, DAY.plusDays(1), 3);
    book(person, suborder(beta, "BETA/01", true), DAY, 4);
    book(colleague, alpha1, DAY, 5);
    timereportRepository.delete(book(person, alpha1, DAY.plusDays(2), 6));
    entityManager.flush();
    entityManager.clear();

    var days = timereportListDAO.findBookedDays(person.getEmployee().getId(), TimereportVisibility.all(), true);

    assertThat(days).containsExactly(
        new BookedDay(alpha.getId(), "ALPHA", DAY, 180L),
        new BookedDay(alpha.getId(), "ALPHA", DAY.plusDays(1), 180L),
        new BookedDay(beta.getId(), "BETA", DAY, 240L));
  }

  /**
   * Only customer orders count (#1534): an order with a billable suborder, with all of its bookings; an internal order
   * without any billable suborder stays out.
   */
  @Test
  void the_booked_days_leave_out_orders_without_a_billable_suborder() {
    var person = employeecontract("aaa");
    var customerOrder = customerorder(customer, "ALPHA");
    suborder(customerOrder, "ALPHA/01", true);
    book(person, suborder(customerOrder, "ALPHA/02", false), DAY, 1);
    book(person, suborder(customerorder(otherCustomer, "INTERN"), "INTERN/01", false), DAY, 1);

    var days = timereportListDAO.findBookedDays(person.getEmployee().getId(), TimereportVisibility.all(), true);

    assertThat(days).extracting(BookedDay::customerorderId).containsExactly(customerOrder.getId());
  }

  /** Asked for every order (#1539), the internal one counts as well, an order like any other. */
  @Test
  void the_booked_days_of_every_order_include_those_without_a_billable_suborder() {
    var person = employeecontract("aaa");
    var customerOrder = customerorder(customer, "ALPHA");
    book(person, suborder(customerOrder, "ALPHA/01", true), DAY, 1);
    var internal = customerorder(otherCustomer, "INTERN");
    book(person, suborder(internal, "INTERN/01", false), DAY, 2);

    var days = timereportListDAO.findBookedDays(person.getEmployee().getId(), TimereportVisibility.all(), false);

    assertThat(days).containsExactly(
        new BookedDay(customerOrder.getId(), "ALPHA", DAY, 60L),
        new BookedDay(internal.getId(), "INTERN", DAY, 120L));
  }

  /** Without the billable ones, a clause for them leaves the internal order out all the same (#1539). */
  @Test
  void the_booked_days_of_every_order_still_follow_the_billable_clause() {
    var person = employeecontract("aaa");
    var customerOrder = customerorder(customer, "ALPHA");
    book(person, suborder(customerOrder, "ALPHA/01", true), DAY, 1);
    book(person, suborder(customerorder(otherCustomer, "INTERN"), "INTERN/01", false), DAY, 1);

    var days = timereportListDAO.findBookedDays(person.getEmployee().getId(),
        TimereportVisibility.of(List.of(Clause.billable())), false);

    assertThat(days).extracting(BookedDay::customerorderId).containsExactly(customerOrder.getId());
  }

  /**
   * Pages cut by an offset (#1539) neither repeat nor skip a row, even where day, person and sequence number are alike:
   * the id decides last.
   */
  @Test
  void consecutive_pages_hold_every_row_once() {
    var reader = employeecontract("aaa");
    var suborder = suborder(customerorder(customer, "ALPHA"), "ALPHA/01", true);
    var first = book(reader, suborder);
    var second = book(reader, suborder);
    var third = book(reader, suborder);
    var visibility = ownBookingsOf(reader);

    var pageOne = timereportListDAO.findRows(page(0, 2), visibility);
    var pageTwo = timereportListDAO.findRows(page(2, 2), visibility);

    assertThat(pageOne).extracting(Timereport::getId).containsExactly(first.getId(), second.getId());
    assertThat(pageTwo).extracting(Timereport::getId).containsExactly(third.getId());
    assertThat(timereportListDAO.findTotals(page(2, 2), visibility).count()).isEqualTo(3);
  }

  @Test
  void the_booked_days_follow_the_visibility() {
    var person = employeecontract("aaa");
    var visibleOrder = customerorder(customer, "ALPHA");
    book(person, suborder(visibleOrder, "ALPHA/01", true), DAY, 1);
    book(person, suborder(customerorder(otherCustomer, "BETA"), "BETA/01", true), DAY, 1);

    var days = timereportListDAO.findBookedDays(person.getEmployee().getId(),
        TimereportVisibility.of(List.of(Clause.forOrders(Set.of(visibleOrder.getId())))), true);

    assertThat(days).extracting(BookedDay::customerorderId).containsExactly(visibleOrder.getId());
  }

  /**
   * A customer stands for the orders it placed as intermediary as well (#1536): the filter finds their bookings, and the
   * values offer the intermediary as a customer.
   */
  @Test
  void the_customer_filter_finds_the_bookings_on_orders_the_customer_mediates() {
    var reader = employeecontract("aaa");
    var intermediary = customer("VM");
    intermediary.setIntermediary(true);
    var mediated = customerorder(customer, "ALPHA");
    mediated.setIntermediary(intermediary);
    var onMediated = book(reader, suborder(mediated, "ALPHA/01", true));
    var direct = book(reader, suborder(customerorder(otherCustomer, "BETA"), "BETA/01", true));

    assertThat(timereportListDAO.findRows(withCustomers(List.of(intermediary.getId())), ownBookingsOf(reader)))
        .extracting(Timereport::getId)
        .containsExactly(onMediated.getId());
    assertThat(timereportListDAO.findRows(withCustomers(List.of(customer.getId())), ownBookingsOf(reader)))
        .extracting(Timereport::getId)
        .containsExactly(onMediated.getId());
    assertThat(timereportListDAO.findRows(withCustomers(List.of(otherCustomer.getId())), ownBookingsOf(reader)))
        .extracting(Timereport::getId)
        .containsExactly(direct.getId());
    assertThat(timereportListDAO.findFilterValues(ownBookingsOf(reader)).customerIds())
        .containsExactlyInAnyOrder(customer.getId(), intermediary.getId(), otherCustomer.getId());
  }

  private static TimereportListFilter withCustomers(List<Long> customerIds) {
    return new TimereportListFilter(List.of(), customerIds, List.of(), List.of(), List.of(), true, TagSelection.NONE,
        DAY.withDayOfMonth(1), DAY.withDayOfMonth(30), Billable.ALL, Sort.DATE, false,
        TimereportListFilter.UNLIMITED);
  }

  private static TimereportListFilter withTags(TagSelection tags, List<Long> employeeIds) {
    return new TimereportListFilter(employeeIds, List.of(), List.of(), List.of(), List.of(), true, tags,
        DAY.withDayOfMonth(1), DAY.withDayOfMonth(30), Billable.ALL, Sort.DATE, false,
        TimereportListFilter.UNLIMITED);
  }

  private static TimereportListFilter page(int offset, int maxResults) {
    return new TimereportListFilter(List.of(), List.of(), List.of(), List.of(), List.of(), true, TagSelection.NONE,
        DAY.withDayOfMonth(1), DAY.withDayOfMonth(30), Billable.ALL, Sort.DATE, false, maxResults, offset);
  }

  private static TimereportVisibility ownBookingsOf(Employeecontract contract) {
    return TimereportVisibility.of(List.of(Clause.forEmployees(Set.of(contract.getEmployee().getId()))));
  }

  private static TimereportListFilter wholeMonth() {
    return new TimereportListFilter(List.of(), List.of(), List.of(), List.of(), List.of(), true, TagSelection.NONE,
        DAY.withDayOfMonth(1), DAY.withDayOfMonth(30), Billable.ALL, Sort.DATE, false,
        TimereportListFilter.UNLIMITED);
  }

  private Timereport book(Employeecontract contract, Suborder suborder) {
    return book(contract, suborder, DAY, 1);
  }

  private Timereport book(Employeecontract contract, Suborder suborder, LocalDate day, int hours) {
    var referenceday = referenceday(entityManager, day);

    var timereport = new Timereport();
    timereport.setEmployeeorder(employeeorder(contract, suborder));
    timereport.setReferenceday(referenceday);
    timereport.setDuration(Duration.ofHours(hours));
    timereport.setStatus(GlobalConstants.TIMEREPORT_STATUS_OPEN);
    timereport.setTaskdescription("");
    timereport.setTraining(false);
    entityManager.persist(timereport);
    entityManager.flush();
    return timereport;
  }

  private Employeeorder employeeorder(Employeecontract contract, Suborder suborder) {
    var employeeorder = new Employeeorder();
    employeeorder.setSuborder(suborder);
    employeeorder.setEmployeecontract(contract);
    employeeorder.setFromDate(DAY.minusYears(1));
    return entityManager.persist(employeeorder);
  }

  private Suborder suborder(Customerorder order, String sign, boolean billable) {
    var suborder = new Suborder();
    suborder.setCustomerorder(order);
    suborder.setSign(sign);
    suborder.setDescription(sign);
    suborder.setShortdescription(sign);
    suborder.setInvoice(billable ? GlobalConstants.INVOICE_YES : GlobalConstants.YESNO_NO);
    suborder.setFromDate(DAY.minusYears(1));
    suborder.setDebithours(Duration.ZERO);
    suborder.setHide(false);
    return entityManager.persist(suborder);
  }

  private Customerorder customerorder(Customer owner, String sign) {
    var order = new Customerorder();
    order.setCustomer(owner);
    order.setSign(sign);
    order.setDescription(sign);
    order.setFromDate(DAY.minusYears(1));
    order.setDebithours(Duration.ZERO);
    order.setHide(false);
    return entityManager.persist(order);
  }

  private Customer customer(String shortname) {
    var newCustomer = new Customer();
    newCustomer.setName("Kunde " + shortname);
    newCustomer.setShortname(shortname);
    newCustomer.setAddress("Teststraße 1");
    return entityManager.persist(newCustomer);
  }

  private Employeecontract employeecontract(String sign) {
    var employee = new Employee();
    employee.setSign(sign);
    employee.setFirstname("Vorname");
    employee.setLastname("Nachname");
    employee.setGender(GlobalConstants.GENDER_FEMALE);
    employee.setHide(false);
    entityManager.persist(employee);

    var employeecontract = new Employeecontract();
    employeecontract.setEmployee(employee);
    employeecontract.setValidFrom(DAY.minusYears(1));
    employeecontract.setDailyWorkingTime(Duration.ofHours(8));
    employeecontract.setHide(false);
    return entityManager.persist(employeecontract);
  }
}
