package de.hbt.salat.dailyreport.persistence;

import static de.hbt.salat.testutils.CustomerTestUtils.uniqueShortname;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static de.hbt.salat.testutils.ReferencedayTestUtils.referenceday;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
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
import de.hbt.salat.customer.domain.Customer;
import de.hbt.salat.dailyreport.domain.Timereport;
import de.hbt.salat.dailyreport.service.TicketDaySums;
import de.hbt.salat.employee.domain.Employee;
import de.hbt.salat.employee.domain.Employeecontract;
import de.hbt.salat.jira.command.TicketDaySum;
import de.hbt.salat.order.domain.Customerorder;
import de.hbt.salat.order.domain.Employeeorder;
import de.hbt.salat.order.domain.Suborder;

/**
 * The sums the JIRA worklog sync is built from (#1007): per day and ticket reference, over all
 * people, and nothing else — a worklog in JIRA says how much, never who and never what. A booking
 * with several references has its duration split evenly among them (#1326).
 */
@DataJpaTest
@Import(AuthorizedUserAuditorAware.class)
@DisplayNameGeneration(ReplaceUnderscores.class)
public class TimereportTicketDaySumTest {

  private static final LocalDate DAY = LocalDate.of(2026, 6, 15);

  @Autowired
  private TimereportRepository timereportRepository;

  @Autowired
  private TestEntityManager entityManager;

  @MockitoBean
  private AuthorizedUser authorizedUser;

  private Customerorder customerorder;
  private Suborder suborder;
  private Suborder otherSuborder;

  @BeforeEach
  public void setUp() {
    when(authorizedUser.isAuthenticated()).thenReturn(true);
    when(authorizedUser.getLoginSign()).thenReturn("test");
    customerorder = customerorder();
    suborder = suborder("so");
    otherSuborder = suborder("so-other");
  }

  @Test
  public void adds_up_what_several_people_booked_on_one_ticket_that_day() {
    book(suborder, employeecontract("aaa"), DAY, 2, 30, "ALPHA-1");
    book(suborder, employeecontract("bbb"), DAY, 1, 0, "ALPHA-1");

    assertThat(sums()).containsExactly(new TicketDaySum(DAY, "ALPHA-1", 210));
  }

  @Test
  public void keeps_the_days_apart() {
    var contract = employeecontract("aaa");
    book(suborder, contract, DAY, 1, 0, "ALPHA-1");
    book(suborder, contract, DAY.plusDays(1), 2, 0, "ALPHA-1");

    assertThat(sums()).containsExactlyInAnyOrder(
        new TicketDaySum(DAY, "ALPHA-1", 60),
        new TicketDaySum(DAY.plusDays(1), "ALPHA-1", 120));
  }

  @Test
  public void keeps_the_tickets_apart() {
    var contract = employeecontract("aaa");
    book(suborder, contract, DAY, 1, 0, "ALPHA-1");
    book(suborder, contract, DAY, 2, 0, "ALPHA-2");

    assertThat(sums()).containsExactlyInAnyOrder(
        new TicketDaySum(DAY, "ALPHA-1", 60),
        new TicketDaySum(DAY, "ALPHA-2", 120));
  }

  /** 10 minutes on three tickets are 4, 3 and 3: JIRA shows the time booked, not three times it. */
  @Test
  public void splits_a_booking_with_several_references_evenly_the_remainder_to_the_front() {
    book(suborder, employeecontract("aaa"), DAY, 0, 10, "ALPHA-1", "ALPHA-2", "ALPHA-3");

    assertThat(sums()).containsExactlyInAnyOrder(
        new TicketDaySum(DAY, "ALPHA-1", 4),
        new TicketDaySum(DAY, "ALPHA-2", 3),
        new TicketDaySum(DAY, "ALPHA-3", 3));
  }

  /** The split happens per booking, before summing - the position counts within each booking. */
  @Test
  public void splits_each_booking_on_its_own_before_adding_up() {
    book(suborder, employeecontract("aaa"), DAY, 0, 3, "ALPHA-1", "ALPHA-2");
    book(suborder, employeecontract("bbb"), DAY, 0, 3, "ALPHA-2", "ALPHA-1");
    book(suborder, employeecontract("ccc"), DAY, 1, 0, "ALPHA-2");

    assertThat(sums()).containsExactlyInAnyOrder(
        new TicketDaySum(DAY, "ALPHA-1", 2 + 1),
        new TicketDaySum(DAY, "ALPHA-2", 1 + 2 + 60));
  }

  @Test
  public void leaves_out_a_booking_without_a_ticket_reference() {
    book(suborder, employeecontract("aaa"), DAY, 3, 0);

    assertThat(sums()).isEmpty();
  }

  @Test
  public void leaves_out_a_deleted_booking() {
    // This is the whole reason the sums are recomputed instead of tracked: a soft-deleted booking
    // moves no timestamp, and only its absence from here lowers the sum.
    var contract = employeecontract("aaa");
    var kept = book(suborder, contract, DAY, 1, 0, "ALPHA-1");
    var deleted = book(suborder, contract, DAY, 2, 0, "ALPHA-1");
    timereportRepository.delete(deleted);
    entityManager.flush();
    entityManager.clear();

    assertThat(kept.getId()).isNotNull();
    assertThat(sums()).containsExactly(new TicketDaySum(DAY, "ALPHA-1", 60));
  }

  @Test
  public void leaves_out_a_suborder_outside_the_scope() {
    book(suborder, employeecontract("aaa"), DAY, 1, 0, "ALPHA-1");
    book(otherSuborder, employeecontract("bbb"), DAY, 5, 0, "ALPHA-9");

    assertThat(sums()).containsExactly(new TicketDaySum(DAY, "ALPHA-1", 60));
  }

  @Test
  public void leaves_out_a_day_outside_the_period() {
    var contract = employeecontract("aaa");
    book(suborder, contract, DAY, 1, 0, "ALPHA-1");
    book(suborder, contract, DAY.minusDays(10), 4, 0, "ALPHA-1");

    assertThat(sums(List.of(suborder.getId()), DAY.minusDays(1), DAY.plusDays(1), false))
        .containsExactly(new TicketDaySum(DAY, "ALPHA-1", 60));
  }

  @Test
  public void keeps_a_non_invoiceable_suborder_without_the_restriction() {
    var internal = suborder("so-internal", GlobalConstants.YESNO_NO);
    book(suborder, employeecontract("aaa"), DAY, 1, 0, "ALPHA-1");
    book(internal, employeecontract("bbb"), DAY, 0, 30, "ALPHA-1");

    assertThat(sums(List.of(suborder.getId(), internal.getId()), DAY.minusDays(1), DAY.plusDays(1), false))
        .containsExactly(new TicketDaySum(DAY, "ALPHA-1", 90));
  }

  @Test
  public void leaves_out_a_non_invoiceable_suborder_with_the_restriction() {
    // #1218: the same ticket and day, but only the billed part reaches JIRA.
    var internal = suborder("so-internal", GlobalConstants.YESNO_NO);
    book(suborder, employeecontract("aaa"), DAY, 1, 0, "ALPHA-1");
    book(internal, employeecontract("bbb"), DAY, 0, 30, "ALPHA-1");
    book(internal, employeecontract("ccc"), DAY, 2, 0, "ALPHA-2");

    assertThat(sums(List.of(suborder.getId(), internal.getId()), DAY.minusDays(1), DAY.plusDays(1), true))
        .containsExactly(new TicketDaySum(DAY, "ALPHA-1", 60));
  }

  private List<TicketDaySum> sums() {
    return sums(List.of(suborder.getId()), DAY.minusDays(30), DAY.plusDays(30), false);
  }

  private List<TicketDaySum> sums(List<Long> suborderIds, LocalDate from, LocalDate until, boolean invoiceableOnly) {
    entityManager.clear();
    return TicketDaySums.of(timereportRepository.getBookedTicketReferences(suborderIds, from, until, invoiceableOnly));
  }

  private Timereport book(Suborder onSuborder, Employeecontract contract, LocalDate date,
                          int hours, int minutes, String... ticketReferences) {
    var employeeorder = new Employeeorder();
    employeeorder.setSuborder(onSuborder);
    employeeorder.setEmployeecontract(contract);
    employeeorder.setFromDate(DAY.minusYears(1));
    entityManager.persist(employeeorder);

    var referenceday = referenceday(entityManager, date);

    var timereport = new Timereport();
    timereport.setEmployeeorder(employeeorder);
    timereport.setReferenceday(referenceday);
    timereport.setDurationhours(hours);
    timereport.setDurationminutes(minutes);
    timereport.setStatus(GlobalConstants.TIMEREPORT_STATUS_OPEN);
    timereport.setTaskdescription("");
    timereport.setTicketReferences(List.of(ticketReferences));
    timereport.setTraining(false);
    entityManager.persist(timereport);
    entityManager.flush();
    return timereport;
  }

  private Suborder suborder(String sign) {
    return suborder(sign, GlobalConstants.INVOICE_YES);
  }

  private Suborder suborder(String sign, char invoice) {
    var newSuborder = new Suborder();
    newSuborder.setCustomerorder(customerorder);
    newSuborder.setSign(sign);
    newSuborder.setDescription(sign);
    newSuborder.setShortdescription(sign);
    newSuborder.setInvoice(invoice);
    newSuborder.setFromDate(DAY.minusYears(1));
    newSuborder.setDebithours(Duration.ZERO);
    newSuborder.setHide(false);
    return entityManager.persist(newSuborder);
  }

  private Customerorder customerorder() {
    var customer = new Customer();
    customer.setName("Testkunde");
    customer.setShortname(uniqueShortname("TK"));
    customer.setAddress("Teststraße 1");
    entityManager.persist(customer);

    var order = new Customerorder();
    order.setCustomer(customer);
    order.setSign("ALPHA");
    order.setDescription("ALPHA");
    order.setFromDate(DAY.minusYears(1));
    order.setDebithours(Duration.ZERO);
    order.setHide(false);
    return entityManager.persist(order);
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
