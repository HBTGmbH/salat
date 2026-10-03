package de.hbt.salat.budget.listener;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static de.hbt.salat.testutils.EmployeeTestUtils.BOSS_SIGN;
import static de.hbt.salat.testutils.EmployeeTestUtils.TESTY_SIGN;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.StreamSupport;
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
 * (#966). Cost assignments and customer rates therefore reference their person by id (#968), and a
 * changed sign has no bearing on what the work costs or earns — budgets reach into the past and have
 * to keep counting the work an anonymized person did.
 *
 * <p>The sign column stays next to the id while views, ETL definitions and reports still join on
 * it, and has to follow the person there. That following is all the listener still does.
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
  private static final long ORDER_ID = 4711L;

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
   * what is actually stored, which is what the reports read too.
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
    givenAssignment(employee);

    whenAnonymized(employee);

    assertThat(employeeCostService.findEffectiveCost(employee.getId(), null, OrderType.STANDARD, WORKDAY)).isPresent();
  }

  @Test
  public void anonymizing_an_employee_leaves_their_customer_rate_resolvable() {
    var employee = givenEmployee(TESTY_SIGN);
    givenPricing(employee);

    whenAnonymized(employee);

    assertThat(orderPricingService.lookupFor(List.of(ORDER_ID))
        .findEffectiveRate(ORDER_ID, null, employee.getId(), null, WORKDAY)).isPresent();
  }

  /**
   * The rates stay with the person, not with the sign: whoever is given the old sign afterwards is
   * somebody else and must not be priced with the rates of the anonymized person.
   */
  @Test
  public void whoever_takes_over_the_old_sign_inherits_nothing() {
    var employee = givenEmployee(TESTY_SIGN);
    givenCostRate();
    givenAssignment(employee);
    givenPricing(employee);

    whenAnonymized(employee);
    var successor = givenEmployee(TESTY_SIGN);

    assertThat(employeeCostService.findEffectiveCost(successor.getId(), null, OrderType.STANDARD, WORKDAY)).isEmpty();
    assertThat(orderPricingService.lookupFor(List.of(ORDER_ID))
        .findEffectiveRate(ORDER_ID, null, successor.getId(), null, WORKDAY)).isEmpty();
  }

  /** The sign column follows the person, for the readers outside the application that join on it. */
  @Test
  public void anonymizing_an_employee_writes_the_new_sign_next_to_the_id() {
    var employee = givenEmployee(TESTY_SIGN);
    givenCostRate();
    givenAssignment(employee);
    givenPricing(employee);

    var newSign = whenAnonymized(employee);

    assertThat(assignmentSigns()).containsExactly(newSign);
    assertThat(pricingSigns()).containsExactly(newSign);
  }

  /** Anonymizing one person must not drag the records of anybody else along. */
  @Test
  public void leaves_the_records_of_other_employees_alone() {
    var employee = givenEmployee(TESTY_SIGN);
    var boss = givenEmployee(BOSS_SIGN);
    givenCostRate();
    givenAssignment(employee);
    givenAssignment(boss);

    var newSign = whenAnonymized(employee);

    assertThat(assignmentSigns()).containsExactlyInAnyOrder(newSign, BOSS_SIGN);
    assertThat(employeeCostService.findEffectiveCost(boss.getId(), null, OrderType.STANDARD, WORKDAY)).isPresent();
  }

  /** The same has to hold for an ordinary correction of the sign, which is the commoner case. */
  @Test
  public void correcting_a_sign_writes_it_next_to_the_id() {
    var employee = givenEmployee(TESTY_SIGN);
    givenCostRate();
    givenAssignment(employee);
    givenPricing(employee);

    var previousSign = employee.getSign();
    employee.setSign("newby");
    employeeService.createOrUpdate(employee, previousSign);
    entityManager.flush();
    entityManager.clear();

    assertThat(assignmentSigns()).containsExactly("newby");
    assertThat(pricingSigns()).containsExactly("newby");
    assertThat(employeeCostService.findEffectiveCost(employee.getId(), null, OrderType.STANDARD, WORKDAY)).isPresent();
  }

  /** A save that leaves the sign alone must not rewrite anything. */
  @Test
  public void a_save_without_a_sign_change_carries_nothing() {
    var employee = givenEmployee(TESTY_SIGN);
    givenCostRate();
    givenAssignment(employee);

    employee.setLastname("Neu");
    employeeService.createOrUpdate(employee);
    entityManager.flush();
    entityManager.clear();

    assertThat(assignmentSigns()).containsExactly(TESTY_SIGN);
  }

  private List<String> assignmentSigns() {
    return StreamSupport.stream(assignmentRepository.findAll().spliterator(), false)
        .map(EmployeeCostAssignment::getEmployeeSign)
        .toList();
  }

  private List<String> pricingSigns() {
    return StreamSupport.stream(pricingRepository.findAll().spliterator(), false)
        .map(OrderPricing::getEmployeeSign)
        .toList();
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

  private void givenAssignment(Employee employee) {
    var assignment = new EmployeeCostAssignment();
    assignment.setEmployeeCostName(CATEGORY);
    assignment.setEmployeeId(employee.getId());
    assignment.setEmployeeSign(employee.getSign());
    assignment.setValidFrom(FROM);
    assignment.setValidUntil(UNTIL);
    assignmentRepository.save(assignment);
  }

  private void givenPricing(Employee employee) {
    var pricing = new OrderPricing();
    pricing.setCustomerorderId(ORDER_ID);
    pricing.setCustomerorderSign(ORDER_SIGN);
    pricing.setEmployeeId(employee.getId());
    pricing.setEmployeeSign(employee.getSign());
    pricing.setPriceCentsPerHour(10000);
    pricing.setValidFrom(FROM);
    pricing.setValidUntil(UNTIL);
    pricingRepository.save(pricing);
  }

}
