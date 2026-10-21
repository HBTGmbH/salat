package de.hbt.salat.dailyreport.persistence;

import static de.hbt.salat.testutils.CustomerTestUtils.uniqueShortname;
import static de.hbt.salat.testutils.ReferencedayTestUtils.referenceday;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;
import static org.mockito.Mockito.when;

import de.hbt.salat.auth.domain.AuthorizedUser;
import de.hbt.salat.auth.persistence.AuthorizedUserAuditorAware;
import de.hbt.salat.common.GlobalConstants;
import de.hbt.salat.customer.domain.Customer;
import de.hbt.salat.dailyreport.domain.AccountBooking;
import de.hbt.salat.dailyreport.domain.Timereport;
import de.hbt.salat.employee.domain.Employee;
import de.hbt.salat.employee.domain.Employeecontract;
import de.hbt.salat.order.domain.Customerorder;
import de.hbt.salat.order.domain.Employeeorder;
import de.hbt.salat.order.domain.Suborder;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * „Meine Konten“ reads the bookings of a contract as values, in one query per tab (#1514), instead of a
 * {@code TimereportDTO} for which each booking fetched its day and its orders one at a time.
 */
@DataJpaTest
@Import(AuthorizedUserAuditorAware.class)
@DisplayNameGeneration(ReplaceUnderscores.class)
class AccountBookingsTest {

  private static final LocalDate FROM = LocalDate.parse("2026-01-01");
  private static final LocalDate UNTIL = LocalDate.parse("2026-12-31");

  @Autowired
  private TimereportRepository repository;

  @Autowired
  private TestEntityManager entityManager;

  @MockitoBean
  private AuthorizedUser authorizedUser;

  private Customerorder internal;
  private Customerorder project;
  private Employeecontract contract;

  @BeforeEach
  void setUp() {
    when(authorizedUser.isAuthenticated()).thenReturn(true);
    when(authorizedUser.getLoginSign()).thenReturn("test");
    internal = customerorder("i976", "HBT");
    project = customerorder("a100", "ABC");
    contract = contract(employee("aa"));
  }

  @Test
  void a_booking_carries_what_the_tabs_count_and_list() {
    var training = employeeorder(contract, suborder(internal, "FORTBILDUNG"));
    book(training, "2026-03-02", Duration.ofMinutes(150), true, "Schulung");

    assertThat(repository.findAccountBookingsOfContractBetween(contract.getId(), FROM, UNTIL))
        .containsExactly(new AccountBooking(training.getId(), training.getSuborder().getId(),
            LocalDate.parse("2026-03-02"), Duration.ofMinutes(150), true, "i976/FORTBILDUNG",
            internal.getCustomer().getShortname(), "Schulung"));
  }

  /** The order of the booking list until then: by day, then customer order and suborder. */
  @Test
  void the_bookings_of_the_contract_come_by_day_then_order() {
    var onProject = employeeorder(contract, suborder(project, "01"));
    var onInternal = employeeorder(contract, suborder(internal, "01"));
    book(onProject, "2026-03-03", Duration.ofHours(1), false, "b");
    book(onProject, "2026-03-02", Duration.ofHours(2), false, "a");
    book(onInternal, "2026-03-02", Duration.ofHours(3), false, "c");

    assertThat(repository.findAccountBookingsOfContractBetween(contract.getId(), FROM, UNTIL))
        .extracting(AccountBooking::day, AccountBooking::completeOrderSign)
        .containsExactly(
            tuple(LocalDate.parse("2026-03-02"), "a100/01"),
            tuple(LocalDate.parse("2026-03-02"), "i976/01"),
            tuple(LocalDate.parse("2026-03-03"), "a100/01"));
  }

  @Test
  void bookings_outside_the_range_deleted_ones_and_those_of_another_contract_are_left_out() {
    var order = employeeorder(contract, suborder(project, "01"));
    book(order, "2025-12-31", Duration.ofHours(1), false, "");
    book(order, "2027-01-01", Duration.ofHours(1), false, "");
    book(order, "2026-05-04", Duration.ofHours(1), false, "").setDeleted(true);
    book(employeeorder(contract(employee("bb")), order.getSuborder()), "2026-05-04", Duration.ofHours(1), false, "");
    book(order, "2026-05-05", Duration.ofHours(4), false, "");
    entityManager.flush();

    assertThat(repository.findAccountBookingsOfContractBetween(contract.getId(), FROM, UNTIL))
        .extracting(AccountBooking::day).containsExactly(LocalDate.parse("2026-05-05"));
  }

  /** The vacation chart reads its orders in one query, and an order of another contract gives nothing away. */
  @Test
  void the_bookings_of_several_orders_come_together_but_only_of_the_contract() {
    var vacation = employeeorder(contract, suborder(internal, "URLAUB"));
    var special = employeeorder(contract, suborder(internal, "SONDERURLAUB"));
    var foreign = employeeorder(contract(employee("bb")), vacation.getSuborder());
    book(vacation, "2026-06-10", Duration.ofHours(8), false, "");
    book(special, "2026-10-15", Duration.ofHours(8), false, "");
    book(foreign, "2026-06-11", Duration.ofHours(8), false, "");

    assertThat(repository.findAccountBookingsOfEmployeeordersBetween(contract.getId(),
            List.of(vacation.getId(), special.getId(), foreign.getId()), FROM, UNTIL))
        .extracting(AccountBooking::employeeorderId, AccountBooking::day)
        .containsExactly(
            tuple(vacation.getId(), LocalDate.parse("2026-06-10")),
            tuple(special.getId(), LocalDate.parse("2026-10-15")));
  }

  @Test
  void many_bookings_take_one_statement() {
    var order = employeeorder(contract, suborder(project, "01"));
    for (int day = 1; day <= 20; day++) {
      book(order, "2026-03-%02d".formatted(day), Duration.ofHours(8), false, "");
    }
    entityManager.clear();
    var statistics = entityManager.getEntityManager().getEntityManagerFactory().unwrap(SessionFactory.class)
        .getStatistics();
    statistics.setStatisticsEnabled(true);
    statistics.clear();

    assertThat(repository.findAccountBookingsOfContractBetween(contract.getId(), FROM, UNTIL)).hasSize(20);
    assertThat(statistics.getPrepareStatementCount()).isEqualTo(1);
  }

  private Timereport book(Employeeorder employeeorder, String day, Duration duration, boolean training,
      String taskdescription) {
    var timereport = new Timereport();
    timereport.setEmployeeorder(employeeorder);
    timereport.setReferenceday(referenceday(entityManager, LocalDate.parse(day)));
    timereport.setDuration(duration);
    timereport.setStatus(GlobalConstants.TIMEREPORT_STATUS_OPEN);
    timereport.setTaskdescription(taskdescription);
    timereport.setTraining(training);
    entityManager.persist(timereport);
    entityManager.flush();
    return timereport;
  }

  private Employeeorder employeeorder(Employeecontract contract, Suborder suborder) {
    var employeeorder = new Employeeorder();
    employeeorder.setSuborder(suborder);
    employeeorder.setEmployeecontract(contract);
    employeeorder.setFromDate(contract.getValidFrom());
    return entityManager.persist(employeeorder);
  }

  private Suborder suborder(Customerorder customerorder, String sign) {
    var suborder = new Suborder();
    suborder.setCustomerorder(customerorder);
    suborder.setSign(sign);
    suborder.setDescription(sign);
    suborder.setShortdescription(sign);
    suborder.setInvoice(GlobalConstants.INVOICE_YES);
    suborder.setFromDate(LocalDate.parse("2020-01-01"));
    suborder.setDebithours(Duration.ZERO);
    suborder.setHide(false);
    return entityManager.persist(suborder);
  }

  private Customerorder customerorder(String sign, String customerShortname) {
    var customer = new Customer();
    customer.setName("Kunde " + customerShortname);
    customer.setShortname(uniqueShortname(customerShortname));
    customer.setAddress("Teststraße 1");
    entityManager.persist(customer);

    var customerorder = new Customerorder();
    customerorder.setCustomer(customer);
    customerorder.setSign(sign);
    customerorder.setDescription(sign);
    customerorder.setFromDate(LocalDate.parse("2020-01-01"));
    customerorder.setDebithours(Duration.ZERO);
    customerorder.setHide(false);
    return entityManager.persist(customerorder);
  }

  private Employee employee(String sign) {
    var employee = new Employee();
    employee.setSign(sign);
    employee.setFirstname("Vorname");
    employee.setLastname(sign);
    employee.setGender(GlobalConstants.GENDER_FEMALE);
    employee.setHide(false);
    return entityManager.persist(employee);
  }

  private Employeecontract contract(Employee employee) {
    var contract = new Employeecontract();
    contract.setEmployee(employee);
    contract.setValidFrom(LocalDate.parse("2020-01-01"));
    contract.setDailyWorkingTime(Duration.ofHours(8));
    contract.setFreelancer(false);
    contract.setHide(false);
    return entityManager.persist(contract);
  }
}
