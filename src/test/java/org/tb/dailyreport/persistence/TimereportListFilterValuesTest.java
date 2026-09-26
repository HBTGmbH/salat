package org.tb.dailyreport.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

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
import org.tb.auth.domain.AuthorizedUser;
import org.tb.auth.persistence.AuthorizedUserAuditorAware;
import org.tb.common.GlobalConstants;
import org.tb.customer.domain.Customer;
import org.tb.dailyreport.auth.TimereportVisibility;
import org.tb.dailyreport.auth.TimereportVisibility.Clause;
import org.tb.dailyreport.domain.Referenceday;
import org.tb.dailyreport.domain.Timereport;
import org.tb.dailyreport.domain.TimereportListFilter;
import org.tb.dailyreport.domain.TimereportListFilter.Billable;
import org.tb.dailyreport.domain.TimereportListFilter.Sort;
import org.tb.employee.domain.Employee;
import org.tb.employee.domain.Employeecontract;
import org.tb.order.domain.Customerorder;
import org.tb.order.domain.Employeeorder;
import org.tb.order.domain.Suborder;

/**
 * The values the booking list offers in its filters, asked of the employee orders instead of the bookings (#1127).
 *
 * <p>The answer must stay the one the bookings give: an employee order is only a cheaper way to the same question,
 * so one that nobody booked on offers nothing, and the visibility restricts it exactly as it restricts a booking —
 * clause by clause, never as a cross product. The last two tests hold the rows and the sums to the same condition,
 * because the booking query and this one share it.
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

  @Test
  void the_rows_still_take_the_filter_on_top_of_the_visibility() {
    var reader = employeecontract("aaa");
    var billable = book(reader, suborder(customerorder(customer, "ALPHA"), "ALPHA/01", true));
    book(reader, suborder(customerorder(otherCustomer, "BETA"), "BETA/01", false));

    var onlyBillable = new TimereportListFilter(List.of(), List.of(), List.of(), List.of(), List.of(), true,
        DAY.withDayOfMonth(1), DAY.withDayOfMonth(30), Billable.BILLABLE, Sort.DATE, false,
        TimereportListFilter.UNLIMITED);

    assertThat(timereportListDAO.findRows(onlyBillable, ownBookingsOf(reader)))
        .extracting(Timereport::getId)
        .containsExactly(billable.getId());
  }

  private static TimereportVisibility ownBookingsOf(Employeecontract contract) {
    return TimereportVisibility.of(List.of(Clause.forEmployees(Set.of(contract.getEmployee().getId()))));
  }

  private static TimereportListFilter wholeMonth() {
    return new TimereportListFilter(List.of(), List.of(), List.of(), List.of(), List.of(), true,
        DAY.withDayOfMonth(1), DAY.withDayOfMonth(30), Billable.ALL, Sort.DATE, false,
        TimereportListFilter.UNLIMITED);
  }

  private Timereport book(Employeecontract contract, Suborder suborder) {
    var referenceday = new Referenceday();
    referenceday.setRefdate(DAY);
    entityManager.persist(referenceday);

    var timereport = new Timereport();
    timereport.setEmployeecontract(contract);
    timereport.setEmployeeorder(employeeorder(contract, suborder));
    timereport.setSuborder(suborder);
    timereport.setReferenceday(referenceday);
    timereport.setDurationhours(1);
    timereport.setDurationminutes(0);
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
    employeeorder.setSign(suborder.getSign());
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
