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
import org.tb.order.domain.Customerorder;
import org.tb.order.domain.Employeeorder;
import org.tb.order.domain.OrderType;
import org.tb.order.domain.Suborder;

/**
 * The days on which a contract has something booked, as the dashboard hint on working days of the
 * previous week without a booking needs them (#1124). Unlike the release, which counts only open
 * bookings, every status counts here: a month end in the middle of the week leaves days behind that
 * are already released and still booked.
 */
@DataJpaTest
@Import(AuthorizedUserAuditorAware.class)
@DisplayNameGeneration(ReplaceUnderscores.class)
public class TimereportRepositoryBookedDaysTest {

  private static final LocalDate MONDAY = LocalDate.of(2026, 9, 21);
  private static final LocalDate TUESDAY = MONDAY.plusDays(1);
  private static final LocalDate WEDNESDAY = MONDAY.plusDays(2);
  private static final LocalDate SUNDAY = MONDAY.plusDays(6);

  @Autowired
  private TimereportRepository timereportRepository;

  @Autowired
  private TestEntityManager entityManager;

  @MockitoBean
  private AuthorizedUser authorizedUser;

  private Employeecontract employeecontract;
  private Employeeorder projectOrder;

  @BeforeEach
  public void setUp() {
    when(authorizedUser.isAuthenticated()).thenReturn(true);
    when(authorizedUser.getLoginSign()).thenReturn("test");
    employeecontract = employeecontract("aaa");
    projectOrder = employeeorder(employeecontract, "PROJ", OrderType.STANDARD);
  }

  @Test
  public void counts_bookings_of_every_status() {
    book(employeecontract, projectOrder, MONDAY, GlobalConstants.TIMEREPORT_STATUS_OPEN);
    book(employeecontract, projectOrder, TUESDAY, GlobalConstants.TIMEREPORT_STATUS_COMMITED);
    book(employeecontract, projectOrder, WEDNESDAY, GlobalConstants.TIMEREPORT_STATUS_CLOSED);

    assertThat(bookedDays()).containsExactlyInAnyOrder(MONDAY, TUESDAY, WEDNESDAY);
  }

  @Test
  public void counts_a_booked_absence() {
    var sickLeave = employeeorder(employeecontract, "KRANK", OrderType.KRANK_URLAUB_ABWESEND);
    book(employeecontract, sickLeave, TUESDAY, GlobalConstants.TIMEREPORT_STATUS_OPEN);

    assertThat(bookedDays()).containsExactly(TUESDAY);
  }

  @Test
  public void leaves_out_a_deleted_booking() {
    var deleted = book(employeecontract, projectOrder, MONDAY, GlobalConstants.TIMEREPORT_STATUS_OPEN);
    book(employeecontract, projectOrder, TUESDAY, GlobalConstants.TIMEREPORT_STATUS_OPEN);
    timereportRepository.delete(deleted);
    entityManager.flush();
    entityManager.clear();

    assertThat(bookedDays()).containsExactly(TUESDAY);
  }

  @Test
  public void names_a_day_with_several_bookings_once() {
    book(employeecontract, projectOrder, MONDAY, GlobalConstants.TIMEREPORT_STATUS_OPEN);
    book(employeecontract, projectOrder, MONDAY, GlobalConstants.TIMEREPORT_STATUS_OPEN);

    assertThat(bookedDays()).containsExactly(MONDAY);
  }

  @Test
  public void leaves_out_the_bookings_of_another_contract() {
    var otherContract = employeecontract("bbb");
    book(otherContract, employeeorder(otherContract, "OTHER", OrderType.STANDARD), MONDAY,
        GlobalConstants.TIMEREPORT_STATUS_OPEN);
    book(employeecontract, projectOrder, TUESDAY, GlobalConstants.TIMEREPORT_STATUS_OPEN);

    assertThat(bookedDays()).containsExactly(TUESDAY);
  }

  @Test
  public void includes_both_ends_of_the_period_and_nothing_beyond() {
    book(employeecontract, projectOrder, MONDAY.minusDays(1), GlobalConstants.TIMEREPORT_STATUS_OPEN);
    book(employeecontract, projectOrder, MONDAY, GlobalConstants.TIMEREPORT_STATUS_OPEN);
    book(employeecontract, projectOrder, SUNDAY, GlobalConstants.TIMEREPORT_STATUS_OPEN);
    book(employeecontract, projectOrder, SUNDAY.plusDays(1), GlobalConstants.TIMEREPORT_STATUS_OPEN);

    assertThat(bookedDays()).containsExactlyInAnyOrder(MONDAY, SUNDAY);
  }

  private List<LocalDate> bookedDays() {
    return timereportRepository.findBookedDaysBetween(employeecontract.getId(), MONDAY, SUNDAY);
  }

  private Timereport book(Employeecontract contract, Employeeorder employeeorder, LocalDate date, String status) {
    var referenceday = new Referenceday();
    referenceday.setRefdate(date);
    entityManager.persist(referenceday);

    var timereport = new Timereport();
    timereport.setEmployeecontract(contract);
    timereport.setEmployeeorder(employeeorder);
    timereport.setSuborder(employeeorder.getSuborder());
    timereport.setReferenceday(referenceday);
    timereport.setDurationhours(1);
    timereport.setDurationminutes(0);
    timereport.setStatus(status);
    timereport.setTaskdescription("");
    timereport.setTraining(false);
    entityManager.persist(timereport);
    entityManager.flush();
    return timereport;
  }

  private Employeeorder employeeorder(Employeecontract contract, String sign, OrderType orderType) {
    var customer = new Customer();
    customer.setName("Testkunde");
    customer.setShortname("TK");
    customer.setAddress("Teststraße 1");
    entityManager.persist(customer);

    var customerorder = new Customerorder();
    customerorder.setCustomer(customer);
    customerorder.setSign(sign);
    customerorder.setDescription(sign);
    customerorder.setFromDate(MONDAY.minusYears(1));
    customerorder.setDebithours(Duration.ZERO);
    customerorder.setOrderType(orderType);
    customerorder.setHide(false);
    entityManager.persist(customerorder);

    var suborder = new Suborder();
    suborder.setCustomerorder(customerorder);
    suborder.setSign(sign);
    suborder.setDescription(sign);
    suborder.setShortdescription(sign);
    suborder.setInvoice(GlobalConstants.INVOICE_YES);
    suborder.setFromDate(MONDAY.minusYears(1));
    suborder.setDebithours(Duration.ZERO);
    suborder.setHide(false);
    entityManager.persist(suborder);

    var employeeorder = new Employeeorder();
    employeeorder.setSuborder(suborder);
    employeeorder.setEmployeecontract(contract);
    employeeorder.setSign(sign);
    employeeorder.setFromDate(MONDAY.minusYears(1));
    return entityManager.persist(employeeorder);
  }

  private Employeecontract employeecontract(String sign) {
    var employee = new Employee();
    employee.setSign(sign);
    employee.setFirstname("Vorname");
    employee.setLastname("Nachname");
    employee.setGender(GlobalConstants.GENDER_FEMALE);
    employee.setHide(false);
    entityManager.persist(employee);

    var contract = new Employeecontract();
    contract.setEmployee(employee);
    contract.setValidFrom(MONDAY.minusYears(1));
    contract.setDailyWorkingTime(Duration.ofHours(8));
    contract.setHide(false);
    return entityManager.persist(contract);
  }
}
