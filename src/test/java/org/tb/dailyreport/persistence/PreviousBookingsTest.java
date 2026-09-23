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
import org.tb.dailyreport.domain.PreviousBooking;
import org.tb.dailyreport.domain.Referenceday;
import org.tb.dailyreport.domain.Timereport;
import org.tb.employee.domain.Employee;
import org.tb.employee.domain.Employeecontract;
import org.tb.order.domain.Customerorder;
import org.tb.order.domain.Employeeorder;
import org.tb.order.domain.Suborder;

/**
 * What the days before the one on screen offer a booking of that day (#1017). Whoever sits on the
 * same task for several days had to leaf back through the days to read order, text and ticket off
 * the earlier booking; this query is what puts them within one click instead.
 */
@DataJpaTest
@Import(AuthorizedUserAuditorAware.class)
@DisplayNameGeneration(ReplaceUnderscores.class)
public class PreviousBookingsTest {

  private static final LocalDate DAY = LocalDate.of(2026, 6, 15);

  @Autowired
  private TimereportRepository timereportRepository;

  @Autowired
  private TestEntityManager entityManager;

  @MockitoBean
  private AuthorizedUser authorizedUser;

  /** the query touches no authorization, so the DAO can be built without one */
  private TimereportDAO classUnderTest;

  private Customerorder customerorder;
  private Employeecontract contract;
  private Employeeorder employeeorder;
  private Employeeorder otherEmployeeorder;

  @BeforeEach
  public void setUp() {
    when(authorizedUser.isAuthenticated()).thenReturn(true);
    when(authorizedUser.getLoginSign()).thenReturn("test");
    classUnderTest = new TimereportDAO(timereportRepository, null);
    customerorder = customerorder();
    contract = employeecontract("aaa");
    employeeorder = employeeorder(suborder("so"), contract);
    otherEmployeeorder = employeeorder(suborder("so-other"), contract);
  }

  @Test
  public void an_earlier_booking_offers_its_order_comment_ticket_and_duration() {
    book(employeeorder, DAY.minusDays(1), "Refactoring", "PROJ-123", 1, 30);

    assertThat(previousBookings()).containsExactly(new PreviousBooking(
        employeeorder.getId(), "Refactoring", "PROJ-123", Duration.ofMinutes(90)));
  }

  /** what is booked on the day itself already stands in the list below the offer */
  @Test
  public void the_day_itself_is_no_offer() {
    book(employeeorder, DAY, "Heute", null, 1, 0);

    assertThat(previousBookings()).isEmpty();
  }

  @Test
  public void the_last_day_of_the_window_still_counts_the_one_before_it_does_not() {
    book(employeeorder, DAY.minusDays(14), "Gerade noch", null, 1, 0);
    book(employeeorder, DAY.minusDays(15), "Zu alt", null, 1, 0);

    assertThat(previousBookings()).extracting(PreviousBooking::comment)
        .containsExactly("Gerade noch");
  }

  @Test
  public void the_most_recent_day_comes_first() {
    book(employeeorder, DAY.minusDays(5), "Vorletzte", null, 1, 0);
    book(employeeorder, DAY.minusDays(1), "Letzte", null, 1, 0);

    assertThat(previousBookings()).extracting(PreviousBooking::comment)
        .containsExactly("Letzte", "Vorletzte");
  }

  /**
   * The duration does not tell two offers apart — the same task on Monday and on Tuesday is one
   * entry, and the duration it carries is the one last booked.
   */
  @Test
  public void the_same_task_on_two_days_is_one_offer_with_the_most_recent_duration() {
    book(employeeorder, DAY.minusDays(3), "Refactoring", "PROJ-123", 4, 0);
    book(employeeorder, DAY.minusDays(1), "Refactoring", "PROJ-123", 1, 30);

    assertThat(previousBookings()).containsExactly(new PreviousBooking(
        employeeorder.getId(), "Refactoring", "PROJ-123", Duration.ofMinutes(90)));
  }

  @Test
  public void the_same_comment_on_two_tickets_is_two_offers() {
    book(employeeorder, DAY.minusDays(1), "Daily", "PROJ-123", 0, 15);
    book(employeeorder, DAY.minusDays(2), "Daily", "PROJ-456", 0, 15);

    assertThat(previousBookings()).extracting(PreviousBooking::ticketReference)
        .containsExactly("PROJ-123", "PROJ-456");
  }

  @Test
  public void the_same_comment_on_two_orders_is_two_offers() {
    book(employeeorder, DAY.minusDays(1), "Daily", null, 0, 15);
    book(otherEmployeeorder, DAY.minusDays(2), "Daily", null, 0, 15);

    assertThat(previousBookings()).extracting(PreviousBooking::employeeorderId)
        .containsExactly(employeeorder.getId(), otherEmployeeorder.getId());
  }

  /** without a comment the order carries the entry, so the booking is still worth offering */
  @Test
  public void a_booking_without_a_comment_is_still_an_offer() {
    book(employeeorder, DAY.minusDays(1), "", null, 2, 0);

    assertThat(previousBookings()).containsExactly(new PreviousBooking(
        employeeorder.getId(), "", null, Duration.ofHours(2)));
  }

  @Test
  public void a_deleted_booking_is_no_offer() {
    var deleted = book(employeeorder, DAY.minusDays(1), "Verworfen", null, 1, 0);
    timereportRepository.delete(deleted);
    entityManager.flush();
    entityManager.clear();

    assertThat(previousBookings()).isEmpty();
  }

  /** a manager looking at someone else's day must be offered that person's bookings, not their own */
  @Test
  public void another_contract_is_no_offer() {
    var otherContract = employeecontract("bbb");
    book(employeeorder(suborder("so-third"), otherContract), DAY.minusDays(1), "Fremd", null, 1, 0);

    assertThat(previousBookings()).isEmpty();
  }

  @Test
  public void at_most_five_entries_are_offered() {
    for (int day = 1; day <= 6; day++) {
      book(employeeorder, DAY.minusDays(day), "Aufgabe " + day, null, 1, 0);
    }

    assertThat(previousBookings()).hasSize(5);
  }

  private List<PreviousBooking> previousBookings() {
    entityManager.flush();
    entityManager.clear();
    return classUnderTest.getPreviousBookingsByEmployeeContractId(contract.getId(), DAY);
  }

  private Timereport book(Employeeorder onEmployeeorder, LocalDate date, String comment,
                          String ticketReference, int hours, int minutes) {
    var referenceday = new Referenceday();
    referenceday.setRefdate(date);
    entityManager.persist(referenceday);

    var timereport = new Timereport();
    timereport.setEmployeecontract(onEmployeeorder.getEmployeecontract());
    timereport.setEmployeeorder(onEmployeeorder);
    timereport.setSuborder(onEmployeeorder.getSuborder());
    timereport.setReferenceday(referenceday);
    timereport.setDurationhours(hours);
    timereport.setDurationminutes(minutes);
    timereport.setStatus(GlobalConstants.TIMEREPORT_STATUS_OPEN);
    timereport.setTaskdescription(comment);
    timereport.setTicketReference(ticketReference);
    timereport.setTraining(false);
    entityManager.persist(timereport);
    entityManager.flush();
    return timereport;
  }

  private Employeeorder employeeorder(Suborder onSuborder, Employeecontract onContract) {
    var newEmployeeorder = new Employeeorder();
    newEmployeeorder.setSuborder(onSuborder);
    newEmployeeorder.setEmployeecontract(onContract);
    newEmployeeorder.setSign(onSuborder.getSign());
    newEmployeeorder.setFromDate(DAY.minusYears(1));
    return entityManager.persist(newEmployeeorder);
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

    var newContract = new Employeecontract();
    newContract.setEmployee(employee);
    newContract.setValidFrom(DAY.minusYears(1));
    newContract.setDailyWorkingTime(Duration.ofHours(8));
    newContract.setHide(false);
    return entityManager.persist(newContract);
  }
}
