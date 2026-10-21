package de.hbt.salat.employee.persistence;

import static de.hbt.salat.auth.domain.AccessLevel.READ;
import static java.util.Comparator.comparing;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import de.hbt.salat.auth.domain.AuthorizedUser;
import de.hbt.salat.auth.domain.SalatUser;
import de.hbt.salat.auth.persistence.AuthorizedUserAuditorAware;
import de.hbt.salat.common.GlobalConstants;
import de.hbt.salat.common.Hiding;
import de.hbt.salat.employee.auth.EmployeecontractAuthorization;
import de.hbt.salat.employee.domain.Employee;
import de.hbt.salat.employee.domain.Employeecontract;
import de.hbt.salat.employee.domain.Employeecontract_;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * The contract selector of every page under „Buchungen“ asks the database for the contracts the user may read (#1514).
 * Until then it loaded every contract that is not hidden and checked each against
 * {@link EmployeecontractAuthorization}, which read its people leads one contract at a time - 89 queries on a page of
 * „Meine Konten“. The condition of the query must let through what that rule let through: for each role, the
 * contracts are exactly those of the old way, in the same order.
 */
@DataJpaTest
@Import({AuthorizedUserAuditorAware.class, EmployeecontractDAO.class, EmployeecontractAuthorization.class})
@DisplayNameGeneration(ReplaceUnderscores.class)
class EmployeecontractReadableTest {

  private static final LocalDate FROM = LocalDate.of(2020, 1, 1);
  private static final int MANY = 12;

  @Autowired
  private EmployeecontractDAO dao;

  @Autowired
  private EmployeecontractRepository repository;

  @Autowired
  private EmployeecontractAuthorization authorization;

  @Autowired
  private TestEntityManager entityManager;

  @MockitoBean
  private AuthorizedUser authorizedUser;

  @Test
  void a_manager_sees_every_contract_that_is_not_hidden() {
    contracts();
    as("mgr", true, false);

    assertThat(selector()).containsExactly("foreign", "ldx", "led", "own", "pl");
    assertThat(selector()).containsExactlyElementsOf(selectorByRule());
    assertThat(visible()).containsExactlyElementsOf(visibleByRule());
  }

  @Test
  void a_people_lead_sees_their_own_and_those_they_supervise() {
    contracts();
    as("pl", false, true);

    assertThat(selector()).containsExactly("ldx", "led", "pl");
    assertThat(selector()).containsExactlyElementsOf(selectorByRule());
    assertThat(visible()).containsExactlyElementsOf(visibleByRule());
  }

  /** Being named supervisor is not enough — without the role it gives no right to read. */
  @Test
  void a_supervisor_without_the_role_sees_only_their_own() {
    contracts();
    as("pl", false, false);

    assertThat(selector()).containsExactly("pl");
    assertThat(selector()).containsExactlyElementsOf(selectorByRule());
    assertThat(visible()).containsExactlyElementsOf(visibleByRule());
  }

  @Test
  void an_employee_sees_only_their_own() {
    contracts();
    as("own", false, false);

    assertThat(selector()).containsExactly("own");
    assertThat(selector()).containsExactlyElementsOf(selectorByRule());
    assertThat(visible()).containsExactlyElementsOf(visibleByRule());
  }

  /** The case that cost the most: a manager reads every contract, and the rule nothing but the role. */
  @Test
  void the_contracts_of_a_manager_come_in_one_query() {
    var lead = employee("pl");
    for (int i = 0; i < MANY; i++) {
      contract(employee("e" + i), lead);
    }
    as("mgr", true, false);
    var statistics = freshStatistics();

    var contracts = dao.getVisibleEmployeeContractsForAuthorizedUser();
    contracts.forEach(contract -> contract.getSupervisors().forEach(s -> s.getSalatUser().getLoginname()));

    assertThat(contracts).hasSize(MANY);
    assertThat(statistics.getPrepareStatementCount()).isEqualTo(1);
  }

  /** And the one the issue measured: a people lead, whose rule reads the people leads of each contract. */
  @Test
  void the_contracts_of_a_people_lead_come_in_one_query() {
    var lead = employee("pl");
    for (int i = 0; i < MANY; i++) {
      contract(employee("e" + i), i % 2 == 0 ? lead : employee("x" + i));
    }
    as("pl", false, true);
    var statistics = freshStatistics();

    var contracts = dao.getVisibleEmployeeContractsForAuthorizedUser();

    assertThat(contracts).hasSize(MANY / 2);
    assertThat(statistics.getPrepareStatementCount()).isEqualTo(1);
  }

  private void contracts() {
    var lead = employee("pl");
    var other = employee("other");
    contract(employee("own"));
    contract(employee("led"), lead);
    contract(employee("ldx"), other, lead);
    contract(employee("foreign"), employee("other2"));
    contract(lead);
    contract(employee("hidden"), lead).setHide(true);
    contract(employee(GlobalConstants.EMPLOYEE_SIGN_ADM, GlobalConstants.EMPLOYEE_STATUS_ADM));
    entityManager.flush();
    entityManager.clear();
  }

  private void as(String loginSign, boolean manager, boolean peopleLead) {
    when(authorizedUser.getEffectiveLoginSign()).thenReturn(loginSign);
    when(authorizedUser.isManager()).thenReturn(manager);
    when(authorizedUser.isPeopleLead()).thenReturn(peopleLead);
  }

  private Statistics freshStatistics() {
    entityManager.flush();
    entityManager.clear();
    var statistics = entityManager.getEntityManager().getEntityManagerFactory().unwrap(SessionFactory.class)
        .getStatistics();
    statistics.setStatisticsEnabled(true);
    statistics.clear();
    return statistics;
  }

  private List<String> selector() {
    return signs(dao.getVisibleEmployeeContractsForAuthorizedUser());
  }

  private List<String> visible() {
    return signs(dao.getVisibleEmployeeContracts());
  }

  /** The way before #1514: every contract that is not hidden, each checked against the rule in Java. */
  private List<String> selectorByRule() {
    return byRule(ec -> !ec.getEmployee().getSign().equals(GlobalConstants.EMPLOYEE_SIGN_ADM));
  }

  private List<String> visibleByRule() {
    return byRule(ec -> !Objects.equals(ec.getEmployee().getStatus(), GlobalConstants.EMPLOYEE_STATUS_ADM));
  }

  private List<String> byRule(Predicate<Employeecontract> notAdministrator) {
    return signs(repository.findAll(Hiding.notHidden(Employeecontract_.hide)).stream()
        .filter(notAdministrator)
        .filter(ec -> authorization.isAuthorized(ec, READ))
        .sorted(comparing((Employeecontract ec) -> ec.getEmployee().getName())
            .thenComparing(Employeecontract::getValidFrom))
        .toList());
  }

  private static List<String> signs(List<Employeecontract> contracts) {
    return contracts.stream().map(ec -> ec.getEmployee().getSign()).toList();
  }

  private Employee employee(String sign) {
    return employee(sign, GlobalConstants.EMPLOYEE_STATUS_MA);
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

  private Employeecontract contract(Employee employee, Employee... supervisors) {
    var contract = new Employeecontract();
    contract.setEmployee(employee);
    contract.setValidFrom(FROM);
    contract.setDailyWorkingTime(Duration.ofHours(8));
    contract.setFreelancer(false);
    contract.setHide(false);
    contract.setSupervisors(List.of(supervisors));
    return entityManager.persist(contract);
  }
}
