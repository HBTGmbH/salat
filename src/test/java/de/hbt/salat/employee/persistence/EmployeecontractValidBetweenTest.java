package de.hbt.salat.employee.persistence;

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
import de.hbt.salat.auth.domain.AuthorizedUser;
import de.hbt.salat.auth.domain.SalatUser;
import de.hbt.salat.auth.persistence.AuthorizedUserAuditorAware;
import de.hbt.salat.common.GlobalConstants;
import de.hbt.salat.common.LocalDateRange;
import de.hbt.salat.employee.domain.Employee;
import de.hbt.salat.employee.domain.Employeecontract;

/**
 * Die Verträge einer Person, die einen Zeitraum überschneiden (#1450): die Grundlage der REST-Listen,
 * die über einen Vertragswechsel hinweg lesen. Ein Vertrag zählt, sobald er an einem Tag des Zeitraums
 * gilt — auch nur am ersten oder letzten.
 */
@DataJpaTest
@Import(AuthorizedUserAuditorAware.class)
@DisplayNameGeneration(ReplaceUnderscores.class)
class EmployeecontractValidBetweenTest {

  private static final LocalDate FIRST_DAY_OF_OLD = LocalDate.of(2019, 4, 1);
  private static final LocalDate LAST_DAY_OF_OLD = LocalDate.of(2022, 3, 31);
  private static final LocalDate FIRST_DAY_OF_NEW = LocalDate.of(2022, 4, 1);

  @Autowired
  private EmployeecontractRepository employeecontractRepository;

  @Autowired
  private TestEntityManager entityManager;

  @MockitoBean
  private AuthorizedUser authorizedUser;

  private Employee employee;
  private Employeecontract oldContract;
  private Employeecontract newContract;

  @BeforeEach
  void setUp() {
    when(authorizedUser.isAuthenticated()).thenReturn(true);
    when(authorizedUser.getLoginSign()).thenReturn("test");
    employee = employee("emp");
    oldContract = contract(employee, FIRST_DAY_OF_OLD, LAST_DAY_OF_OLD);
    newContract = contract(employee, FIRST_DAY_OF_NEW, LocalDateRange.FINIT_UNTIL_BOUNDARY);
    contract(employee("oth"), FIRST_DAY_OF_OLD, null);
  }

  @Test
  void finds_both_contracts_of_a_period_across_the_change() {
    assertThat(validBetween(LAST_DAY_OF_OLD.minusMonths(1), FIRST_DAY_OF_NEW.plusMonths(1)))
        .containsExactly(oldContract, newContract);
  }

  @Test
  void finds_a_contract_starting_on_the_last_day_of_the_period() {
    assertThat(validBetween(FIRST_DAY_OF_OLD.minusMonths(1), FIRST_DAY_OF_OLD)).containsExactly(oldContract);
  }

  @Test
  void finds_a_contract_ending_on_the_first_day_of_the_period() {
    assertThat(validBetween(LAST_DAY_OF_OLD, LAST_DAY_OF_OLD)).containsExactly(oldContract);
  }

  @Test
  void finds_an_open_ended_contract_long_after_its_start() {
    assertThat(validBetween(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 12, 31))).containsExactly(newContract);
  }

  @Test
  void finds_nothing_before_the_first_contract() {
    assertThat(validBetween(LocalDate.of(2016, 10, 8), FIRST_DAY_OF_OLD.minusDays(1))).isEmpty();
  }

  private List<Employeecontract> validBetween(LocalDate from, LocalDate until) {
    return employeecontractRepository.findAllByEmployeeIdAndValidBetween(employee.getId(), from, until);
  }

  private Employeecontract contract(Employee employee, LocalDate validFrom, LocalDate validUntil) {
    var contract = new Employeecontract();
    contract.setEmployee(employee);
    contract.setValidFrom(validFrom);
    contract.setValidUntil(validUntil);
    contract.setDailyWorkingTime(Duration.ofHours(8));
    contract.setOvertimeStatic(Duration.ZERO);
    contract.setHide(false);
    entityManager.persist(contract);
    entityManager.flush();
    return contract;
  }

  private Employee employee(String sign) {
    var user = new SalatUser();
    user.setLoginname(sign);
    user.setStatus(GlobalConstants.EMPLOYEE_STATUS_MA);
    entityManager.persist(user);

    var employee = new Employee();
    employee.setSign(sign);
    employee.setFirstname("Vorname");
    employee.setLastname("Nachname-" + sign);
    employee.setGender(GlobalConstants.GENDER_FEMALE);
    employee.setHide(false);
    employee.setSalatUser(user);
    return entityManager.persist(employee);
  }
}
