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
import org.tb.order.domain.OrderType;
import org.tb.order.domain.Suborder;

/**
 * The sums the overtime account is built from (#463). Standby is booked like any other time but is
 * no working time, so it must not reach these sums — and the order type of the suborder has to win
 * over the one of its customer order here just as it does in {@code Suborder#getEffectiveOrderType}.
 */
@DataJpaTest
@Import(AuthorizedUserAuditorAware.class)
@DisplayNameGeneration(ReplaceUnderscores.class)
public class TimereportWorkingTimeSumTest {

  private static final LocalDate DAY = LocalDate.of(2026, 6, 15);

  @Autowired
  private TimereportRepository timereportRepository;

  @Autowired
  private TestEntityManager entityManager;

  @MockitoBean
  private AuthorizedUser authorizedUser;

  private Employeecontract contract;
  private Customerorder standardOrder;
  private Customerorder standbyOrder;

  @BeforeEach
  public void setUp() {
    when(authorizedUser.isAuthenticated()).thenReturn(true);
    when(authorizedUser.getLoginSign()).thenReturn("test");
    contract = employeecontract();
    standardOrder = customerorder("co-standard", OrderType.STANDARD);
    standbyOrder = customerorder("co-standby", OrderType.BEREITSCHAFT);
  }

  @Test
  public void counts_the_hours_of_a_standard_order() {
    book(suborder(standardOrder, "so", null), 8);

    assertThat(reportedMinutes()).isEqualTo(480);
  }

  @Test
  public void counts_sickness_vacation_and_absence_as_working_time() {
    book(suborder(standardOrder, "so", OrderType.KRANK_URLAUB_ABWESEND), 8);

    assertThat(reportedMinutes()).isEqualTo(480);
  }

  @Test
  public void leaves_out_a_standby_suborder() {
    book(suborder(standardOrder, "so-standby", OrderType.BEREITSCHAFT), 12);

    assertThat(reportedMinutes()).isZero();
  }

  @Test
  public void leaves_out_a_suborder_that_inherits_standby_from_its_customer_order() {
    book(suborder(standbyOrder, "so", null), 12);

    assertThat(reportedMinutes()).isZero();
  }

  /** The type of the suborder wins: a standard suborder of a standby order is working time. */
  @Test
  public void counts_a_standard_suborder_of_a_standby_customer_order() {
    book(suborder(standbyOrder, "so-standard", OrderType.STANDARD), 8);

    assertThat(reportedMinutes()).isEqualTo(480);
  }

  @Test
  public void adds_up_only_the_working_time_of_a_mixed_day() {
    book(suborder(standardOrder, "so", null), 8);
    book(suborder(standardOrder, "so-standby", OrderType.BEREITSCHAFT), 12);

    assertThat(reportedMinutes()).isEqualTo(480);
  }

  @Test
  public void leaves_standby_out_of_the_monthly_sum_as_well() {
    book(suborder(standardOrder, "so", null), 8);
    book(suborder(standardOrder, "so-standby", OrderType.BEREITSCHAFT), 12);

    var months = timereportRepository.getReportedMinutesByMonthForEmployeecontract(
        contract.getId(), DAY.withDayOfMonth(1), DAY.withDayOfMonth(30));

    assertThat(months).hasSize(1);
    assertThat(months.getFirst().minutes()).isEqualTo(480);
  }

  @Test
  public void reports_no_sum_for_a_month_of_standby_alone() {
    book(suborder(standardOrder, "so-standby", OrderType.BEREITSCHAFT), 12);

    assertThat(timereportRepository.getReportedMinutesByMonthForEmployeecontract(
        contract.getId(), DAY.withDayOfMonth(1), DAY.withDayOfMonth(30))).isEmpty();
  }

  private Long reportedMinutes() {
    return timereportRepository
        .getReportedMinutesForEmployeecontractAndBetween(contract.getId(), DAY.minusDays(1), DAY.plusDays(1))
        .orElse(0L);
  }

  private void book(Suborder suborder, int hours) {
    var employeeorder = new Employeeorder();
    employeeorder.setSuborder(suborder);
    employeeorder.setEmployeecontract(contract);
    employeeorder.setSign(suborder.getSign());
    employeeorder.setFromDate(DAY.minusYears(1));
    entityManager.persist(employeeorder);

    var referenceday = new Referenceday();
    referenceday.setRefdate(DAY);
    entityManager.persist(referenceday);

    var timereport = new Timereport();
    timereport.setEmployeecontract(contract);
    timereport.setEmployeeorder(employeeorder);
    timereport.setSuborder(suborder);
    timereport.setReferenceday(referenceday);
    timereport.setDurationhours(hours);
    timereport.setDurationminutes(0);
    timereport.setStatus(GlobalConstants.TIMEREPORT_STATUS_OPEN);
    timereport.setTaskdescription("");
    timereport.setTraining(false);
    entityManager.persist(timereport);
    entityManager.flush();
  }

  private Suborder suborder(Customerorder customerorder, String sign, OrderType orderType) {
    var suborder = new Suborder();
    suborder.setCustomerorder(customerorder);
    suborder.setSign(sign);
    suborder.setDescription(sign);
    suborder.setShortdescription(sign);
    suborder.setInvoice(GlobalConstants.INVOICE_YES);
    suborder.setFromDate(DAY.minusYears(1));
    suborder.setDebithours(Duration.ZERO);
    suborder.setOrderType(orderType);
    suborder.setHide(false);
    return entityManager.persist(suborder);
  }

  private Customerorder customerorder(String sign, OrderType orderType) {
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
    customerorder.setOrderType(orderType);
    customerorder.setDebithours(Duration.ZERO);
    customerorder.setHide(false);
    return entityManager.persist(customerorder);
  }

  private Employeecontract employeecontract() {
    var employee = new Employee();
    employee.setSign("tst");
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
