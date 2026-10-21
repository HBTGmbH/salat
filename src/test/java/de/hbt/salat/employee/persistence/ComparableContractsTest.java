package de.hbt.salat.employee.persistence;

import static de.hbt.salat.common.GlobalConstants.EMPLOYEE_STATUS_ADM;
import static de.hbt.salat.common.GlobalConstants.EMPLOYEE_STATUS_MA;
import static de.hbt.salat.common.GlobalConstants.EMPLOYEE_STATUS_RESTRICTED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import de.hbt.salat.auth.domain.AuthorizedUser;
import de.hbt.salat.auth.domain.SalatUser;
import de.hbt.salat.auth.persistence.AuthorizedUserAuditorAware;
import de.hbt.salat.common.GlobalConstants;
import de.hbt.salat.employee.domain.ContractWorkingTime;
import de.hbt.salat.employee.domain.Employee;
import de.hbt.salat.employee.domain.Employeecontract;
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

/**
 * Who takes part in the benchmark of the training account (#827): contracts that cover the whole
 * period, no freelancer contract, at least the given daily working time, no restricted login and not
 * the administrator, nothing hidden.
 */
@DataJpaTest
@Import(AuthorizedUserAuditorAware.class)
@DisplayNameGeneration(ReplaceUnderscores.class)
class ComparableContractsTest {

  private static final LocalDate FROM = LocalDate.of(2026, 4, 1);
  private static final LocalDate UNTIL = LocalDate.of(2026, 10, 20);
  private static final Duration MINIMUM = Duration.ofHours(4);

  @Autowired
  private EmployeecontractRepository repository;

  @Autowired
  private TestEntityManager entityManager;

  @MockitoBean
  private AuthorizedUser authorizedUser;

  @BeforeEach
  void setUp() {
    when(authorizedUser.isAuthenticated()).thenReturn(true);
    when(authorizedUser.getLoginSign()).thenReturn("test");
  }

  @Test
  void a_contract_covering_the_period_takes_part_with_its_working_time() {
    var contract = contract(employee("aa", EMPLOYEE_STATUS_MA), "2020-01-01", null, Duration.ofHours(6));

    assertThat(comparable()).containsExactly(new ContractWorkingTime(contract.getId(), Duration.ofHours(6)));
  }

  /* Who started after the beginning of the period or left before its end would pull the median down. */
  @Test
  void a_contract_that_begins_or_ends_within_the_period_does_not() {
    contract(employee("aa", EMPLOYEE_STATUS_MA), "2026-05-01", null, Duration.ofHours(8));
    contract(employee("bb", EMPLOYEE_STATUS_MA), "2020-01-01", "2026-09-30", Duration.ofHours(8));

    assertThat(comparable()).isEmpty();
  }

  @Test
  void twenty_hours_a_week_take_part_and_less_do_not() {
    var twenty = contract(employee("aa", EMPLOYEE_STATUS_MA), "2020-01-01", null, Duration.ofHours(4));
    contract(employee("bb", EMPLOYEE_STATUS_MA), "2020-01-01", null, Duration.ofMinutes(239));

    assertThat(comparable()).extracting(ContractWorkingTime::employeecontractId).containsExactly(twenty.getId());
  }

  @Test
  void a_freelancer_contract_does_not() {
    var contract = contract(employee("aa", EMPLOYEE_STATUS_MA), "2020-01-01", null, Duration.ofHours(8));
    contract.setFreelancer(true);

    assertThat(comparable()).isEmpty();
  }

  @Test
  void interns_externals_and_the_administrator_do_not() {
    contract(employee("aa", EMPLOYEE_STATUS_RESTRICTED), "2020-01-01", null, Duration.ofHours(8));
    contract(employee("bb", EMPLOYEE_STATUS_ADM), "2020-01-01", null, Duration.ofHours(8));

    assertThat(comparable()).isEmpty();
  }

  @Test
  void a_hidden_contract_or_person_does_not() {
    var hiddenContract = contract(employee("aa", EMPLOYEE_STATUS_MA), "2020-01-01", null, Duration.ofHours(8));
    hiddenContract.setHide(true);
    var hiddenPerson = employee("bb", EMPLOYEE_STATUS_MA);
    hiddenPerson.setHide(true);
    contract(hiddenPerson, "2020-01-01", null, Duration.ofHours(8));

    assertThat(comparable()).isEmpty();
  }

  private List<ContractWorkingTime> comparable() {
    entityManager.flush();
    return repository.findComparableCovering(FROM, UNTIL, MINIMUM, List.of(EMPLOYEE_STATUS_RESTRICTED, EMPLOYEE_STATUS_ADM));
  }

  private Employee employee(String sign, String status) {
    var user = new SalatUser();
    user.setLoginname(sign);
    user.setStatus(status);
    entityManager.persist(user);

    var employee = new Employee();
    employee.setSign(sign);
    employee.setFirstname("Vorname");
    employee.setLastname(sign);
    employee.setGender(GlobalConstants.GENDER_FEMALE);
    employee.setHide(false);
    employee.setSalatUser(user);
    return entityManager.persist(employee);
  }

  private Employeecontract contract(Employee employee, String from, String until, Duration dailyWorkingTime) {
    var contract = new Employeecontract();
    contract.setEmployee(employee);
    contract.setValidFrom(LocalDate.parse(from));
    contract.setValidUntil(until != null ? LocalDate.parse(until) : null);
    contract.setDailyWorkingTime(dailyWorkingTime);
    contract.setFreelancer(false);
    contract.setHide(false);
    return entityManager.persist(contract);
  }
}
