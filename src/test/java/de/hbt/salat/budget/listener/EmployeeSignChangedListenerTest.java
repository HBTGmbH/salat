package de.hbt.salat.budget.listener;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static de.hbt.salat.testutils.EmployeeTestUtils.BOSS_SIGN;
import static de.hbt.salat.testutils.EmployeeTestUtils.TESTY_SIGN;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import de.hbt.salat.auth.domain.AuthorizedUser;
import de.hbt.salat.auth.persistence.AuthorizedUserAuditorAware;
import de.hbt.salat.auth.service.AuthService;
import de.hbt.salat.budget.domain.EmployeeCost;
import de.hbt.salat.budget.domain.EmployeeCostAssignment;
import de.hbt.salat.budget.domain.OrderPricing;
import de.hbt.salat.budget.persistence.EmployeeCostAssignmentRepository;
import de.hbt.salat.budget.persistence.EmployeeCostRepository;
import de.hbt.salat.budget.persistence.OrderPricingRepository;
import de.hbt.salat.budget.service.EmployeeCostService;
import de.hbt.salat.order.domain.OrderType;
import de.hbt.salat.budget.service.OrderPricingService;
import de.hbt.salat.common.SalatProperties;
import de.hbt.salat.employee.auth.EmployeeAuthorization;
import de.hbt.salat.employee.auth.EmployeecontractAuthorization;
import de.hbt.salat.employee.domain.Employee;
import de.hbt.salat.employee.persistence.EmployeeDAO;
import de.hbt.salat.employee.persistence.EmployeecontractDAO;
import de.hbt.salat.employee.service.EmployeeService;
import de.hbt.salat.order.service.CustomerorderService;
import de.hbt.salat.order.service.SuborderService;
import de.hbt.salat.testutils.EmployeeTestUtils;

/**
 * The sign is not a key: it can be corrected, and anonymizing an employee overwrites it by design
 * (#966). Cost assignments and customer rates name their person by it, while the booking side reads
 * it live off the employee — so a sign that moves on one side and not on the other makes both
 * lookups resolve nothing, and the work costs 0 EUR in controlling without a word (#922).
 *
 * <p>That must not happen to an anonymized person in particular: budgets reach into the past and
 * have to keep counting the work they did.
 *
 * <p>The test goes through the real services and the real event, because the point is precisely
 * that the two modules stay in step — mocking the listener away would test nothing.
 */
@DataJpaTest
@Import({
    AuthorizedUserAuditorAware.class,
    AuthorizedUser.class,
    AuthService.class,
    SalatProperties.class,
    EmployeeDAO.class,
    EmployeecontractDAO.class,
    EmployeeAuthorization.class,
    EmployeecontractAuthorization.class,
    EmployeeService.class,
    EmployeeCostService.class,
    OrderPricingService.class,
    EmployeeSignChangedListener.class
})
@DisplayNameGeneration(ReplaceUnderscores.class)
public class EmployeeSignChangedListenerTest {

  private static final LocalDate FROM = LocalDate.of(2026, 1, 1);
  private static final LocalDate UNTIL = LocalDate.of(2026, 12, 31);
  private static final LocalDate WORKDAY = LocalDate.of(2026, 6, 25);
  private static final String CATEGORY = "Standard";
  private static final String ORDER_SIGN = "co-one";

  @Autowired
  private EmployeeService employeeService;

  @Autowired
  private EmployeeCostService employeeCostService;

  @Autowired
  private OrderPricingService orderPricingService;

  @Autowired
  private EmployeeCostRepository costRepository;

  @Autowired
  private EmployeeCostAssignmentRepository assignmentRepository;

  @Autowired
  private OrderPricingRepository pricingRepository;

  /**
   * The signs are rewritten with a bulk statement, which the persistence context does not see —
   * the entities it already holds would keep the old sign. Clearing it makes the assertions read
   * what is actually stored, which is what controlling reads too.
   */
  @PersistenceContext
  private EntityManager entityManager;

  @MockitoBean
  private AuthorizedUser authorizedUser;

  @MockitoBean
  private SuborderService suborderService;

  @MockitoBean
  private CustomerorderService customerorderService;

  @MockitoBean
  private de.hbt.salat.common.web.UiState uiState;

  /** OrderPricingService reads plans and their authorization since #1065; neither is under test here. */
  @MockitoBean
  private de.hbt.salat.budget.auth.BudgetAuthorization budgetAuthorization;

  @BeforeEach
  public void initAuthorizedUser() {
    when(authorizedUser.isAuthenticated()).thenReturn(true);
    when(authorizedUser.isManager()).thenReturn(true);
    when(authorizedUser.getLoginSign()).thenReturn("test");
  }

  @Test
  public void anonymizing_an_employee_leaves_their_cost_assignment_resolvable() {
    var employee = givenEmployee(TESTY_SIGN);
    givenCostRate();
    givenAssignment(TESTY_SIGN);

    var newSign = whenAnonymized(employee);

    assertThat(employeeCostService.findEffectiveCost(newSign, null, OrderType.STANDARD, WORKDAY)).isPresent();
  }

  @Test
  public void anonymizing_an_employee_leaves_their_customer_rate_resolvable() {
    var employee = givenEmployee(TESTY_SIGN);
    givenPricing(TESTY_SIGN);

    var newSign = whenAnonymized(employee);

    assertThat(orderPricingService.lookupFor(List.of(ORDER_SIGN))
        .findEffectiveRate(ORDER_SIGN, null, newSign, null, WORKDAY)).isPresent();
  }

  /**
   * The rate has to travel, not be copied: left behind on the old sign it would come back to life
   * the day somebody is given that sign — priced with the rate of a different person.
   */
  @Test
  public void the_old_sign_keeps_nothing_behind() {
    var employee = givenEmployee(TESTY_SIGN);
    givenCostRate();
    givenAssignment(TESTY_SIGN);
    givenPricing(TESTY_SIGN);

    whenAnonymized(employee);

    assertThat(employeeCostService.findEffectiveCost(TESTY_SIGN, null, OrderType.STANDARD, WORKDAY)).isEmpty();
    assertThat(orderPricingService.lookupFor(List.of(ORDER_SIGN))
        .findEffectiveRate(ORDER_SIGN, null, TESTY_SIGN, null, WORKDAY)).isEmpty();
  }

  /** Anonymizing one person must not drag the records of anybody else along. */
  @Test
  public void leaves_the_records_of_other_employees_alone() {
    var employee = givenEmployee(TESTY_SIGN);
    givenEmployee(BOSS_SIGN);
    givenCostRate();
    givenAssignment(TESTY_SIGN);
    givenAssignment(BOSS_SIGN);

    whenAnonymized(employee);

    assertThat(employeeCostService.findEffectiveCost(BOSS_SIGN, null, OrderType.STANDARD, WORKDAY)).isPresent();
  }

  /** The same has to hold for an ordinary correction of the sign, which is the commoner case. */
  @Test
  public void correcting_a_sign_carries_the_records_along() {
    var employee = givenEmployee(TESTY_SIGN);
    givenCostRate();
    givenAssignment(TESTY_SIGN);
    givenPricing(TESTY_SIGN);

    var previousSign = employee.getSign();
    employee.setSign("newby");
    employeeService.createOrUpdate(employee, previousSign);
    entityManager.flush();
    entityManager.clear();

    assertThat(employeeCostService.findEffectiveCost("newby", null, OrderType.STANDARD, WORKDAY)).isPresent();
    assertThat(orderPricingService.lookupFor(List.of(ORDER_SIGN))
        .findEffectiveRate(ORDER_SIGN, null, "newby", null, WORKDAY)).isPresent();
  }

  /** A save that leaves the sign alone must not rewrite anything. */
  @Test
  public void a_save_without_a_sign_change_carries_nothing() {
    var employee = givenEmployee(TESTY_SIGN);
    givenCostRate();
    givenAssignment(TESTY_SIGN);

    employee.setLastname("Neu");
    employeeService.createOrUpdate(employee);
    entityManager.flush();
    entityManager.clear();

    assertThat(employeeCostService.findEffectiveCost(TESTY_SIGN, null, OrderType.STANDARD, WORKDAY)).isPresent();
  }

  private String whenAnonymized(Employee employee) {
    employeeService.anonymizeEmployee(employee.getId(), employee.getSign());
    entityManager.flush();
    entityManager.clear();
    return employeeService.getEmployeeById(employee.getId()).getSign();
  }

  private Employee givenEmployee(String sign) {
    var employee = EmployeeTestUtils.createEmployee(sign);
    employeeService.createOrUpdate(employee);
    return employee;
  }

  private void givenCostRate() {
    var cost = new EmployeeCost();
    cost.setName(CATEGORY);
    cost.setCostCentsPerHour(5000);
    cost.setValidFrom(FROM);
    cost.setValidUntil(UNTIL);
    costRepository.save(cost);
  }

  private void givenAssignment(String employeeSign) {
    var assignment = new EmployeeCostAssignment();
    assignment.setEmployeeCostName(CATEGORY);
    assignment.setEmployeeSign(employeeSign);
    assignment.setValidFrom(FROM);
    assignment.setValidUntil(UNTIL);
    assignmentRepository.save(assignment);
  }

  private void givenPricing(String employeeSign) {
    var pricing = new OrderPricing();
    pricing.setCustomerorderSign(ORDER_SIGN);
    pricing.setEmployeeSign(employeeSign);
    pricing.setPriceCentsPerHour(10000);
    pricing.setValidFrom(FROM);
    pricing.setValidUntil(UNTIL);
    pricingRepository.save(pricing);
  }

}
