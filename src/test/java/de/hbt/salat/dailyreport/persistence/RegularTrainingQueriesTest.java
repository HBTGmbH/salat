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
import de.hbt.salat.dailyreport.domain.RegularTrainingDay;
import de.hbt.salat.dailyreport.domain.Timereport;
import de.hbt.salat.dailyreport.domain.TrainingInformation;
import de.hbt.salat.employee.domain.Employee;
import de.hbt.salat.employee.domain.Employeecontract;
import de.hbt.salat.order.domain.Customerorder;
import de.hbt.salat.order.domain.Employeeorder;
import de.hbt.salat.order.domain.Suborder;
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

/**
 * The regular training the yearly training achievement (#1512) and the benchmark of the training
 * account (#827) count, against the database: bookings on a suborder of the regular training only,
 * none on a freelancer contract, each row with the daily working time of its contract.
 */
@DataJpaTest
@Import(AuthorizedUserAuditorAware.class)
@DisplayNameGeneration(ReplaceUnderscores.class)
class RegularTrainingQueriesTest {

  private static final LocalDate TODAY = LocalDate.of(2026, 10, 20);

  @Autowired
  private TrainingRepository trainingRepository;

  @Autowired
  private TestEntityManager entityManager;

  @MockitoBean
  private AuthorizedUser authorizedUser;

  private Suborder regularTraining;
  private Suborder project;
  private Employee anna;

  @BeforeEach
  void setUp() {
    when(authorizedUser.isAuthenticated()).thenReturn(true);
    when(authorizedUser.getLoginSign()).thenReturn("test");
    var customerorder = customerorder();
    regularTraining = suborder(customerorder, "FORTBILDUNG");
    project = suborder(customerorder, "PROJEKT");
    anna = employee("aa");
  }

  @Test
  void the_job_reads_the_regular_training_per_day_with_the_daily_working_time_of_the_contract() {
    var contract = contract(anna, "2020-01-01", null, Duration.ofHours(6), false);
    book(contract, regularTraining, "2026-03-02", 3, false);
    book(contract, regularTraining, "2026-03-02", 2, false);
    book(contract, regularTraining, "2026-03-03", 1, false);

    assertThat(employedOnToday()).extracting(RegularTrainingDay::employeeId, RegularTrainingDay::day,
            RegularTrainingDay::minutes, RegularTrainingDay::dailyWorkingTime)
        .containsExactly(
            tuple(anna.getId(), LocalDate.parse("2026-03-02"), 300L, Duration.ofHours(6)),
            tuple(anna.getId(), LocalDate.parse("2026-03-03"), 60L, Duration.ofHours(6)));
  }

  @Test
  void training_booked_on_a_project_does_not_count() {
    var contract = contract(anna, "2020-01-01", null, Duration.ofHours(8), false);
    book(contract, project, "2026-03-02", 8, true);

    assertThat(employedOnToday()).isEmpty();
  }

  @Test
  void training_on_a_freelancer_contract_does_not_count_next_to_another_contract() {
    var freelance = contract(anna, "2020-01-01", "2024-12-31", Duration.ofHours(8), true);
    var employed = contract(anna, "2025-01-01", null, Duration.ofHours(8), false);
    book(freelance, regularTraining, "2024-06-03", 8, false);
    book(employed, regularTraining, "2026-03-02", 8, false);

    assertThat(employedOnToday()).extracting(RegularTrainingDay::day).containsExactly(LocalDate.parse("2026-03-02"));
  }

  @Test
  void a_change_of_contract_keeps_the_working_time_of_each_contract() {
    var partTime = contract(anna, "2020-01-01", "2026-06-30", Duration.ofHours(6), false);
    var fullTime = contract(anna, "2026-07-01", null, Duration.ofHours(8), false);
    book(partTime, regularTraining, "2026-03-02", 6, false);
    book(fullTime, regularTraining, "2026-08-03", 8, false);

    assertThat(employedOnToday()).extracting(RegularTrainingDay::day, RegularTrainingDay::dailyWorkingTime)
        .containsExactly(tuple(LocalDate.parse("2026-03-02"), Duration.ofHours(6)),
            tuple(LocalDate.parse("2026-08-03"), Duration.ofHours(8)));
  }

  @Test
  void bookings_after_the_day_and_people_no_longer_employed_are_left_out() {
    var contract = contract(anna, "2020-01-01", null, Duration.ofHours(8), false);
    book(contract, regularTraining, "2026-10-21", 8, false);
    var bert = employee("bb");
    book(contract(bert, "2020-01-01", "2025-12-31", Duration.ofHours(8), false), regularTraining, "2025-03-02", 8, false);

    assertThat(employedOnToday()).isEmpty();
  }

  @Test
  void the_year_of_one_person_is_read_in_the_period_only() {
    var contract = contract(anna, "2020-01-01", null, Duration.ofHours(8), false);
    book(contract, regularTraining, "2025-12-31", 8, false);
    book(contract, regularTraining, "2026-01-02", 4, false);
    book(contract, regularTraining, "2026-10-21", 8, false);

    assertThat(trainingRepository.findRegularTrainingDaysOfEmployeeBetween(anna.getId(), Set.of(regularTraining.getId()),
            TODAY.withDayOfYear(1), TODAY))
        .extracting(RegularTrainingDay::day, RegularTrainingDay::minutes)
        .containsExactly(tuple(LocalDate.parse("2026-01-02"), 240L));
  }

  @Test
  void the_benchmark_sums_the_regular_training_per_contract_in_the_period() {
    var annasContract = contract(anna, "2020-01-01", null, Duration.ofHours(8), false);
    var bertsContract = contract(employee("bb"), "2020-01-01", null, Duration.ofHours(8), false);
    book(annasContract, regularTraining, "2026-04-01", 4, false);
    book(annasContract, regularTraining, "2026-05-04", 4, false);
    book(annasContract, regularTraining, "2026-03-31", 8, false);
    book(annasContract, project, "2026-05-05", 8, true);

    var sums = trainingRepository.sumRegularTrainingOfContractsBetween(
        List.of(annasContract.getId(), bertsContract.getId()), Set.of(regularTraining.getId()),
        LocalDate.parse("2026-04-01"), TODAY);

    assertThat(sums).extracting(TrainingInformation::getEmployeecontractId, TrainingInformation::getDurationMinutes)
        .containsExactly(tuple(annasContract.getId(), 480L));
  }

  private List<RegularTrainingDay> employedOnToday() {
    return trainingRepository.findRegularTrainingDaysOfEmployeesEmployedOn(Set.of(regularTraining.getId()), TODAY);
  }

  private void book(Employeecontract contract, Suborder suborder, String day, int hours, boolean training) {
    var employeeorder = new Employeeorder();
    employeeorder.setSuborder(suborder);
    employeeorder.setEmployeecontract(contract);
    employeeorder.setFromDate(contract.getValidFrom());
    entityManager.persist(employeeorder);

    var timereport = new Timereport();
    timereport.setEmployeeorder(employeeorder);
    timereport.setReferenceday(referenceday(entityManager, LocalDate.parse(day)));
    timereport.setDuration(Duration.ofHours(hours));
    timereport.setStatus(GlobalConstants.TIMEREPORT_STATUS_OPEN);
    timereport.setTaskdescription("");
    timereport.setTraining(training);
    entityManager.persist(timereport);
    entityManager.flush();
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

  private Customerorder customerorder() {
    var customer = new Customer();
    customer.setName("Testkunde");
    customer.setShortname(uniqueShortname("TK"));
    customer.setAddress("Teststraße 1");
    entityManager.persist(customer);

    var customerorder = new Customerorder();
    customerorder.setCustomer(customer);
    customerorder.setSign("i976");
    customerorder.setDescription("intern");
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

  private Employeecontract contract(Employee employee, String from, String until, Duration dailyWorkingTime,
      boolean freelancer) {
    var contract = new Employeecontract();
    contract.setEmployee(employee);
    contract.setValidFrom(LocalDate.parse(from));
    contract.setValidUntil(until != null ? LocalDate.parse(until) : null);
    contract.setDailyWorkingTime(dailyWorkingTime);
    contract.setFreelancer(freelancer);
    contract.setHide(false);
    return entityManager.persist(contract);
  }
}
