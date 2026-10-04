package de.hbt.salat.dailyreport.service;

import static de.hbt.salat.testutils.CustomerTestUtils.uniqueShortname;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static de.hbt.salat.testutils.ReferencedayTestUtils.referenceday;

import java.time.Duration;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import de.hbt.salat.auth.domain.AccessLevel;
import de.hbt.salat.auth.domain.AuthorizedUser;
import de.hbt.salat.auth.persistence.AuthorizedUserAuditorAware;
import de.hbt.salat.common.GlobalConstants;
import de.hbt.salat.customer.domain.Customer;
import de.hbt.salat.dailyreport.auth.TimereportAuthorization;
import de.hbt.salat.dailyreport.domain.Timereport;
import de.hbt.salat.dailyreport.persistence.PublicholidayDAO;
import de.hbt.salat.dailyreport.persistence.TimereportDAO;
import de.hbt.salat.dailyreport.persistence.TimereportRepository;
import de.hbt.salat.dailyreport.persistence.WorkingdayDAO;
import de.hbt.salat.employee.domain.Employee;
import de.hbt.salat.employee.domain.Employeecontract;
import de.hbt.salat.employee.persistence.EmployeecontractDAO;
import de.hbt.salat.order.domain.Customerorder;
import de.hbt.salat.order.domain.Employeeorder;
import de.hbt.salat.order.domain.Suborder;
import de.hbt.salat.order.persistence.EmployeeorderDAO;

/**
 * {@code deleteTimeReports} is the first half of {@code PUT /list} in the REST API (#1131): it
 * clears one employee order on one day before the new bookings are written. It runs against the
 * real query here, because a mocked repository let a query that could not even be executed pass.
 */
@DataJpaTest
@Import({AuthorizedUserAuditorAware.class, TimereportService.class})
@DisplayNameGeneration(ReplaceUnderscores.class)
class TimereportServiceDeleteDayTest {

  private static final LocalDate DAY = LocalDate.of(2026, 6, 15);

  @Autowired
  private TimereportService timereportService;
  @Autowired
  private TimereportRepository timereportRepository;
  @Autowired
  private TestEntityManager entityManager;

  @MockitoBean
  private AuthorizedUser authorizedUser;
  @MockitoBean
  private TimereportAuthorization timereportAuthorization;
  @MockitoBean
  private EmployeecontractDAO employeecontractDAO;
  @MockitoBean
  private EmployeeorderDAO employeeorderDAO;
  @MockitoBean
  private TimereportDAO timereportDAO;
  @MockitoBean
  private PublicholidayDAO publicholidayDAO;
  @MockitoBean
  private WorkingdayDAO workingdayDAO;

  private Employeecontract employeecontract;
  private Employeeorder employeeorder;

  @BeforeEach
  void setUp() {
    when(authorizedUser.isAuthenticated()).thenReturn(true);
    when(authorizedUser.getLoginSign()).thenReturn("test");
    employeecontract = employeecontract();
    employeeorder = employeeorder(suborder("so"));
  }

  @Test
  void deletes_the_bookings_of_the_employeeorder_on_that_day_and_nothing_else() {
    var first = book(employeeorder, DAY);
    var second = book(employeeorder, DAY);
    var dayBefore = book(employeeorder, DAY.minusDays(1));
    var dayAfter = book(employeeorder, DAY.plusDays(1));
    var otherOrder = book(employeeorder(suborder("so-other")), DAY);

    timereportService.deleteTimeReports(DAY, employeeorder.getId());
    entityManager.flush();
    entityManager.clear();

    assertThat(timereportRepository.findAllById(ids(first, second, dayBefore, dayAfter, otherOrder)))
        .extracting(Timereport::getId)
        .containsExactlyInAnyOrder(dayBefore.getId(), dayAfter.getId(), otherOrder.getId());
  }

  @Test
  void checks_the_authorization_only_for_the_bookings_it_deletes() {
    var booking = book(employeeorder, DAY);
    book(employeeorder, DAY.plusDays(1));
    book(employeeorder(suborder("so-other")), DAY);

    timereportService.deleteTimeReports(DAY, employeeorder.getId());

    @SuppressWarnings("unchecked")
    ArgumentCaptor<List<Timereport>> checked = ArgumentCaptor.forClass(List.class);
    verify(timereportAuthorization, atLeastOnce()).checkAuthorized(checked.capture(), eq(AccessLevel.DELETE));
    assertThat(checked.getAllValues()).allSatisfy(timereports ->
        assertThat(timereports).extracting(Timereport::getId).containsExactly(booking.getId()));
  }

  private static List<Long> ids(Timereport... timereports) {
    return Arrays.stream(timereports).map(Timereport::getId).toList();
  }

  private Timereport book(Employeeorder onEmployeeorder, LocalDate date) {
    var referenceday = referenceday(entityManager, date);

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
    customer.setShortname(uniqueShortname("TK"));
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
