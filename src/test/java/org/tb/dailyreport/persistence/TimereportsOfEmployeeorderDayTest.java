package org.tb.dailyreport.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.LocalDate;
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
import org.tb.order.domain.Suborder;

/**
 * The bookings {@code PUT /list} of the REST API replaces (#1131): those of one employee order on
 * exactly one day. Whatever the query returns beyond that is deleted, or fails the authorization
 * check for a booking nobody meant to touch.
 */
@DataJpaTest
@Import(AuthorizedUserAuditorAware.class)
@DisplayNameGeneration(ReplaceUnderscores.class)
public class TimereportsOfEmployeeorderDayTest {

  private static final LocalDate DAY = LocalDate.of(2026, 6, 15);

  @Autowired
  private TimereportRepository timereportRepository;

  @Autowired
  private TestEntityManager entityManager;

  @MockitoBean
  private AuthorizedUser authorizedUser;

  private Employeecontract employeecontract;
  private Employeeorder employeeorder;

  @BeforeEach
  public void setUp() {
    when(authorizedUser.isAuthenticated()).thenReturn(true);
    when(authorizedUser.getLoginSign()).thenReturn("test");
    employeecontract = employeecontract();
    employeeorder = employeeorder(suborder("so"));
  }

  @Test
  public void returns_every_booking_of_the_employeeorder_on_that_day() {
    var first = book(employeeorder, DAY);
    var second = book(employeeorder, DAY);

    assertThat(timereportRepository.findAllByEmployeeorderIdAndReferencedayRefdate(employeeorder.getId(), DAY))
        .containsExactlyInAnyOrder(first, second);
  }

  @Test
  public void leaves_out_the_neighbouring_days() {
    var booking = book(employeeorder, DAY);
    book(employeeorder, DAY.minusDays(1));
    book(employeeorder, DAY.plusDays(1));

    assertThat(timereportRepository.findAllByEmployeeorderIdAndReferencedayRefdate(employeeorder.getId(), DAY))
        .containsExactly(booking);
  }

  @Test
  public void leaves_out_another_employeeorder_on_the_same_day() {
    var booking = book(employeeorder, DAY);
    book(employeeorder(suborder("so-other")), DAY);

    assertThat(timereportRepository.findAllByEmployeeorderIdAndReferencedayRefdate(employeeorder.getId(), DAY))
        .containsExactly(booking);
  }

  @Test
  public void leaves_out_a_deleted_booking() {
    var kept = book(employeeorder, DAY);
    var deleted = book(employeeorder, DAY);
    timereportRepository.delete(deleted);
    entityManager.flush();
    entityManager.clear();

    assertThat(timereportRepository.findAllByEmployeeorderIdAndReferencedayRefdate(employeeorder.getId(), DAY))
        .extracting(Timereport::getId)
        .containsExactly(kept.getId());
  }

  @Test
  public void returns_nothing_for_a_day_without_bookings() {
    book(employeeorder, DAY);

    assertThat(timereportRepository.findAllByEmployeeorderIdAndReferencedayRefdate(employeeorder.getId(), DAY.plusDays(7)))
        .isEmpty();
  }

  private Timereport book(Employeeorder onEmployeeorder, LocalDate date) {
    var referenceday = new Referenceday();
    referenceday.setRefdate(date);
    entityManager.persist(referenceday);

    var timereport = new Timereport();
    timereport.setEmployeecontract(employeecontract);
    timereport.setEmployeeorder(onEmployeeorder);
    timereport.setSuborder(onEmployeeorder.getSuborder());
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

  private Employeeorder employeeorder(Suborder suborder) {
    var newEmployeeorder = new Employeeorder();
    newEmployeeorder.setSuborder(suborder);
    newEmployeeorder.setEmployeecontract(employeecontract);
    newEmployeeorder.setSign(suborder.getSign());
    newEmployeeorder.setFromDate(DAY.minusYears(1));
    return entityManager.persist(newEmployeeorder);
  }

  private Suborder suborder(String sign) {
    var customer = new Customer();
    customer.setName("Testkunde");
    customer.setShortname("TK");
    customer.setAddress("Teststraße 1");
    entityManager.persist(customer);

    var customerorder = new Customerorder();
    customerorder.setCustomer(customer);
    customerorder.setSign(sign);
    customerorder.setDescription(sign);
    customerorder.setFromDate(DAY.minusYears(1));
    customerorder.setDebithours(Duration.ZERO);
    customerorder.setHide(false);
    entityManager.persist(customerorder);

    var suborder = new Suborder();
    suborder.setCustomerorder(customerorder);
    suborder.setSign(sign);
    suborder.setDescription(sign);
    suborder.setShortdescription(sign);
    suborder.setInvoice(GlobalConstants.INVOICE_YES);
    suborder.setFromDate(DAY.minusYears(1));
    suborder.setDebithours(Duration.ZERO);
    suborder.setHide(false);
    return entityManager.persist(suborder);
  }

  private Employeecontract employeecontract() {
    var employee = new Employee();
    employee.setSign("aaa");
    employee.setFirstname("Vorname");
    employee.setLastname("Nachname");
    employee.setGender(GlobalConstants.GENDER_FEMALE);
    employee.setHide(false);
    entityManager.persist(employee);

    var contract = new Employeecontract();
    contract.setEmployee(employee);
    contract.setValidFrom(DAY.minusYears(1));
    contract.setDailyWorkingTime(Duration.ofHours(8));
    contract.setHide(false);
    return entityManager.persist(contract);
  }
}
