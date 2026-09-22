package org.tb.dailyreport.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

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
import org.tb.auth.domain.AuthorizedUser;
import org.tb.auth.persistence.AuthorizedUserAuditorAware;
import org.tb.common.GlobalConstants;
import org.tb.customer.domain.Customer;
import org.tb.dailyreport.domain.Referenceday;
import org.tb.dailyreport.domain.Timereport;
import org.tb.employee.domain.Employee;
import org.tb.employee.domain.Employeecontract;
import org.tb.jira.command.TicketDaySum;
import org.tb.order.domain.Customerorder;
import org.tb.order.domain.Employeeorder;
import org.tb.order.domain.Suborder;

/**
 * The sums the JIRA worklog sync is built from (#1007): per day and ticket reference, over all
 * people, and nothing else — a worklog in JIRA says how much, never who and never what.
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
    book(suborder, employeecontract("aaa"), DAY, "ALPHA-1", 2, 30);
    book(suborder, employeecontract("bbb"), DAY, "ALPHA-1", 1, 0);

    assertThat(sums()).containsExactly(new TicketDaySum(DAY, "ALPHA-1", 210));
  }

  @Test
  public void keeps_the_days_apart() {
    var contract = employeecontract("aaa");
    book(suborder, contract, DAY, "ALPHA-1", 1, 0);
    book(suborder, contract, DAY.plusDays(1), "ALPHA-1", 2, 0);

    assertThat(sums()).containsExactlyInAnyOrder(
        new TicketDaySum(DAY, "ALPHA-1", 60),
        new TicketDaySum(DAY.plusDays(1), "ALPHA-1", 120));
  }

  @Test
  public void keeps_the_tickets_apart() {
    var contract = employeecontract("aaa");
    book(suborder, contract, DAY, "ALPHA-1", 1, 0);
    book(suborder, contract, DAY, "ALPHA-2", 2, 0);

    assertThat(sums()).containsExactlyInAnyOrder(
        new TicketDaySum(DAY, "ALPHA-1", 60),
        new TicketDaySum(DAY, "ALPHA-2", 120));
  }

  @Test
  public void leaves_out_a_booking_without_a_ticket_reference() {
    book(suborder, employeecontract("aaa"), DAY, null, 3, 0);

    assertThat(sums()).isEmpty();
  }

  @Test
  public void leaves_out_a_deleted_booking() {
    // This is the whole reason the sums are recomputed instead of tracked: a soft-deleted booking
    // moves no timestamp, and only its absence from here lowers the sum.
    var contract = employeecontract("aaa");
    var kept = book(suborder, contract, DAY, "ALPHA-1", 1, 0);
    var deleted = book(suborder, contract, DAY, "ALPHA-1", 2, 0);
    timereportRepository.delete(deleted);
    entityManager.flush();
    entityManager.clear();

    assertThat(kept.getId()).isNotNull();
    assertThat(sums()).containsExactly(new TicketDaySum(DAY, "ALPHA-1", 60));
  }

  @Test
  public void leaves_out_a_suborder_outside_the_scope() {
    book(suborder, employeecontract("aaa"), DAY, "ALPHA-1", 1, 0);
    book(otherSuborder, employeecontract("bbb"), DAY, "ALPHA-9", 5, 0);

    assertThat(sums()).containsExactly(new TicketDaySum(DAY, "ALPHA-1", 60));
  }

  @Test
  public void leaves_out_a_day_outside_the_period() {
    var contract = employeecontract("aaa");
    book(suborder, contract, DAY, "ALPHA-1", 1, 0);
    book(suborder, contract, DAY.minusDays(10), "ALPHA-1", 4, 0);

    assertThat(timereportRepository.getTicketDaySums(
        List.of(suborder.getId()), DAY.minusDays(1), DAY.plusDays(1)))
        .containsExactly(new TicketDaySum(DAY, "ALPHA-1", 60));
  }

  private List<TicketDaySum> sums() {
    return timereportRepository.getTicketDaySums(
        List.of(suborder.getId()), DAY.minusDays(30), DAY.plusDays(30));
  }

  private Timereport book(Suborder onSuborder, Employeecontract contract, LocalDate date,
                          String ticketReference, int hours, int minutes) {
    var employeeorder = new Employeeorder();
    employeeorder.setSuborder(onSuborder);
    employeeorder.setEmployeecontract(contract);
    employeeorder.setSign(onSuborder.getSign());
    employeeorder.setFromDate(DAY.minusYears(1));
    entityManager.persist(employeeorder);

    var referenceday = new Referenceday();
    referenceday.setRefdate(date);
    entityManager.persist(referenceday);

    var timereport = new Timereport();
    timereport.setEmployeecontract(contract);
    timereport.setEmployeeorder(employeeorder);
    timereport.setSuborder(onSuborder);
    timereport.setReferenceday(referenceday);
    timereport.setDurationhours(hours);
    timereport.setDurationminutes(minutes);
    timereport.setStatus(GlobalConstants.TIMEREPORT_STATUS_OPEN);
    timereport.setTaskdescription("");
    timereport.setTicketReference(ticketReference);
    timereport.setTraining(false);
    entityManager.persist(timereport);
    entityManager.flush();
    return timereport;
  }

  private Suborder suborder(String sign) {
    var newSuborder = new Suborder();
    newSuborder.setCustomerorder(customerorder);
    newSuborder.setSign(sign);
    newSuborder.setDescription(sign);
    newSuborder.setShortdescription(sign);
    newSuborder.setInvoice(GlobalConstants.INVOICE_YES);
    newSuborder.setFromDate(DAY.minusYears(1));
    newSuborder.setDebithours(Duration.ZERO);
    newSuborder.setHide(false);
    return entityManager.persist(newSuborder);
  }

  private Customerorder customerorder() {
    var customer = new Customer();
    customer.setName("Testkunde");
    customer.setShortname("TK");
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
