package de.hbt.salat.budget.service;

import static de.hbt.salat.testutils.CustomerTestUtils.uniqueShortname;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyIterable;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import de.hbt.salat.auth.domain.AuthorizedUser;
import de.hbt.salat.budget.domain.FlatRateRhythm;
import de.hbt.salat.budget.domain.OrderBudget;
import de.hbt.salat.budget.domain.OrderBudgetAdjustment;
import de.hbt.salat.budget.domain.OrderBudgetDeletion;
import de.hbt.salat.budget.domain.OrderBudgetScopeEntry;
import de.hbt.salat.budget.domain.OrderFlatRate;
import de.hbt.salat.budget.domain.OrderFlatRateInstalment;
import de.hbt.salat.budget.domain.OrderPricing;
import de.hbt.salat.budget.domain.TimereportBudgetAssignment;
import de.hbt.salat.budget.persistence.OrderFlatRateRepository;
import de.hbt.salat.common.exception.AuthorizationException;
import de.hbt.salat.common.exception.ErrorCode;
import de.hbt.salat.common.exception.InvalidDataException;
import de.hbt.salat.customer.domain.Customer;
import de.hbt.salat.order.domain.Customerorder;
import de.hbt.salat.order.domain.OrderType;

/**
 * Deleting a budget plan (#1424) against the real database, through the service as the
 * application wires it: with the authorization aspect, the transaction of the service and the
 * foreign keys that point at the plan.
 *
 * <p>Two plans of one order, each with an adjustment, a progress entry, a bound rate, a bound flat
 * rate with an instalment and assigned bookings, plus a rate and a flat rate bound to no plan. Only
 * what belongs to the deleted plan may go; the bookings are represented by their ids alone, which
 * is all an assignment holds of them.
 *
 * <p>Not transactional itself — the rollback of a failing deletion is one of the things checked,
 * and a test transaction around it would hide it. The data is removed after every test.
 */
@SpringBootTest
@DisplayNameGeneration(ReplaceUnderscores.class)
class OrderBudgetDeletionTest {

  private static final LocalDate JAN = LocalDate.of(2026, 1, 1);
  private static final LocalDate DEC = LocalDate.of(2026, 12, 31);
  private static final String NAME = "Relaunch Portal";

  @MockitoBean
  private AuthorizedUser authorizedUser;

  @MockitoSpyBean
  private OrderFlatRateRepository flatRateRepository;

  @Autowired
  private OrderBudgetService service;

  @Autowired
  private EntityManager entityManager;

  @Autowired
  private PlatformTransactionManager transactionManager;

  private TransactionTemplate tx;
  private Customerorder order;
  private OrderBudget plan;
  private OrderBudget otherPlan;
  private OrderPricing planRate;
  private OrderPricing otherPlanRate;
  private OrderPricing planlessRate;
  private OrderFlatRate planFlatRate;
  private OrderFlatRate otherPlanFlatRate;
  private OrderFlatRate planlessFlatRate;
  private long nextTimereportId = 9_424_000L;

  @BeforeEach
  void setUp() {
    when(authorizedUser.isAuthenticated()).thenReturn(true);
    when(authorizedUser.isManager()).thenReturn(true);
    when(authorizedUser.getLoginSign()).thenReturn("mgr");
    tx = new TransactionTemplate(transactionManager);
    tx.executeWithoutResult(status -> {
      order = order();
      plan = plan(NAME);
      otherPlan = plan("Folgeauftrag");
      planRate = rate(plan);
      otherPlanRate = rate(otherPlan);
      planlessRate = rate(null);
      planFlatRate = flatRate(plan);
      otherPlanFlatRate = flatRate(otherPlan);
      planlessFlatRate = flatRate(null);
      assign(plan);
      assign(plan);
      assign(otherPlan);
    });
  }

  @AfterEach
  void tearDown() {
    tx.executeWithoutResult(status -> {
      entityManager.createQuery("delete from TimereportBudgetAssignment a where a.orderBudget.customerorder.id = :id")
          .setParameter("id", order.getId()).executeUpdate();
      entityManager.createQuery("delete from OrderPricing p where p.customerorder.id = :id")
          .setParameter("id", order.getId()).executeUpdate();
      // removed one by one, so that the instalments, adjustments and progress entries go with them
      entityManager.createQuery("select f from OrderFlatRate f where f.customerorder.id = :id", OrderFlatRate.class)
          .setParameter("id", order.getId()).getResultList().forEach(entityManager::remove);
      entityManager.createQuery("select b from OrderBudget b where b.customerorder.id = :id", OrderBudget.class)
          .setParameter("id", order.getId()).getResultList().forEach(entityManager::remove);
      var managedOrder = entityManager.find(Customerorder.class, order.getId());
      entityManager.remove(managedOrder);
      entityManager.remove(managedOrder.getCustomer());
    });
  }

  @Test
  void deletes_the_plan_with_everything_bound_to_it_and_leaves_the_rest() {
    var deleted = service.delete(plan.getId(), NAME);

    assertThat(deleted).isEqualTo(new OrderBudgetDeletion(1, 1, 0, 2, 1, 1));
    assertThat(exists(OrderBudget.class, plan.getId())).isFalse();
    assertThat(countFor("OrderBudgetAdjustment", plan)).isZero();
    assertThat(countFor("OrderBudgetScopeEntry", plan)).isZero();
    assertThat(countFor("TimereportBudgetAssignment", plan)).isZero();
    assertThat(exists(OrderPricing.class, planRate.getId())).isFalse();
    assertThat(exists(OrderFlatRate.class, planFlatRate.getId())).isFalse();
    assertThat(instalmentsOf(planFlatRate)).isZero();
  }

  @Test
  void leaves_the_other_plan_and_the_planless_conditions_untouched() {
    service.delete(plan.getId(), NAME);

    assertThat(exists(OrderBudget.class, otherPlan.getId())).isTrue();
    assertThat(countFor("OrderBudgetAdjustment", otherPlan)).isEqualTo(1);
    assertThat(countFor("OrderBudgetScopeEntry", otherPlan)).isEqualTo(1);
    assertThat(countFor("TimereportBudgetAssignment", otherPlan)).isEqualTo(1);
    assertThat(boundPlanOf(otherPlanRate)).isEqualTo(otherPlan.getId());
    assertThat(boundPlanOf(otherPlanFlatRate)).isEqualTo(otherPlan.getId());
    assertThat(instalmentsOf(otherPlanFlatRate)).isEqualTo(1);
    assertThat(exists(OrderPricing.class, planlessRate.getId())).isTrue();
    assertThat(exists(OrderFlatRate.class, planlessFlatRate.getId())).isTrue();
  }

  @Test
  void refuses_a_name_that_does_not_match_and_deletes_nothing() {
    assertThatThrownBy(() -> service.delete(plan.getId(), NAME + " "))
        .isInstanceOf(InvalidDataException.class)
        .hasMessageContaining(ErrorCode.BU_BUDGET_DELETE_WRONG_NAME.getCode());

    assertPlanIntact();
  }

  @Test
  void refuses_anybody_who_is_not_a_manager() {
    when(authorizedUser.isManager()).thenReturn(false);

    assertThatThrownBy(() -> service.delete(plan.getId(), NAME))
        .isInstanceOf(AuthorizationException.class)
        .hasMessageContaining(ErrorCode.AA_NEEDS_MANAGER.getCode());

    assertPlanIntact();
  }

  /**
   * The assignments are dissolved first, in a statement of their own; a failure further on must
   * bring them back along with everything else.
   */
  @Test
  void keeps_everything_when_a_later_part_fails() {
    doThrow(new IllegalStateException("simulated")).when(flatRateRepository).deleteAll(anyIterable());

    // the repository translates the exception; its message is what identifies it
    assertThatThrownBy(() -> service.delete(plan.getId(), NAME)).hasMessageContaining("simulated");

    assertPlanIntact();
  }

  private void assertPlanIntact() {
    assertThat(exists(OrderBudget.class, plan.getId())).isTrue();
    assertThat(countFor("OrderBudgetAdjustment", plan)).isEqualTo(1);
    assertThat(countFor("OrderBudgetScopeEntry", plan)).isEqualTo(1);
    assertThat(countFor("TimereportBudgetAssignment", plan)).isEqualTo(2);
    assertThat(boundPlanOf(planRate)).isEqualTo(plan.getId());
    assertThat(boundPlanOf(planFlatRate)).isEqualTo(plan.getId());
    assertThat(instalmentsOf(planFlatRate)).isEqualTo(1);
  }

  // --- reading back ---------------------------------------------------------------------------

  private boolean exists(Class<?> type, long id) {
    return tx.execute(status -> entityManager.find(type, id) != null);
  }

  private long countFor(String entity, OrderBudget budget) {
    return tx.execute(status -> entityManager
        .createQuery("select count(e) from " + entity + " e where e.orderBudget.id = :id", Long.class)
        .setParameter("id", budget.getId()).getSingleResult());
  }

  private Long boundPlanOf(OrderPricing rate) {
    return tx.execute(status -> entityManager.find(OrderPricing.class, rate.getId()).getOrderBudgetId());
  }

  private Long boundPlanOf(OrderFlatRate flatRate) {
    return tx.execute(status -> entityManager.find(OrderFlatRate.class, flatRate.getId()).getOrderBudgetId());
  }

  private long instalmentsOf(OrderFlatRate flatRate) {
    return tx.execute(status -> entityManager
        .createQuery("select count(i) from OrderFlatRateInstalment i where i.orderFlatRate.id = :id", Long.class)
        .setParameter("id", flatRate.getId()).getSingleResult());
  }

  // --- fixtures, inside the transaction of setUp ----------------------------------------------

  private Customerorder order() {
    var customer = new Customer();
    customer.setName("Testkunde");
    customer.setShortname(uniqueShortname("BDEL"));
    customer.setAddress("Teststraße 1");
    entityManager.persist(customer);
    var created = new Customerorder();
    created.setCustomer(customer);
    created.setSign(customer.getShortname());
    created.setShortdescription("Kurz");
    created.setDescription("Auftrag");
    created.setFromDate(JAN);
    created.setOrderType(OrderType.STANDARD);
    created.setDebithours(Duration.ZERO);
    created.setHide(false);
    entityManager.persist(created);
    return created;
  }

  private OrderBudget plan(String name) {
    var budget = new OrderBudget();
    budget.setName(name);
    budget.setCustomerorder(order);
    budget.setValidFrom(JAN);
    budget.setValidUntil(DEC);
    budget.setActive(true);
    var adjustment = new OrderBudgetAdjustment();
    adjustment.setOrderBudget(budget);
    adjustment.setAmount(new BigDecimal("1000.00"));
    adjustment.setEffective(JAN);
    budget.getAdjustments().add(adjustment);
    var entry = new OrderBudgetScopeEntry();
    entry.setOrderBudget(budget);
    entry.setRefdate(JAN);
    entry.setPercent(10);
    budget.getScopeEntries().add(entry);
    entityManager.persist(budget);
    return budget;
  }

  private OrderPricing rate(OrderBudget budget) {
    var rate = new OrderPricing();
    rate.setCustomerorder(order);
    rate.setOrderBudget(budget);
    rate.setPriceCentsPerHour(10_000);
    rate.setValidFrom(JAN);
    rate.setValidUntil(DEC);
    entityManager.persist(rate);
    return rate;
  }

  private OrderFlatRate flatRate(OrderBudget budget) {
    var flatRate = new OrderFlatRate();
    flatRate.setCustomerorder(order);
    flatRate.setOrderBudget(budget);
    flatRate.setRhythm(FlatRateRhythm.INSTALMENTS);
    flatRate.setValidFrom(JAN);
    flatRate.setValidUntil(DEC);
    var instalment = new OrderFlatRateInstalment();
    instalment.setOrderFlatRate(flatRate);
    instalment.setAmount(new BigDecimal("500.00"));
    instalment.setDue(JAN);
    flatRate.getInstalments().add(instalment);
    entityManager.persist(flatRate);
    return flatRate;
  }

  private void assign(OrderBudget budget) {
    var assignment = new TimereportBudgetAssignment();
    assignment.setTimereportId(nextTimereportId++);
    assignment.setOrderBudget(budget);
    entityManager.persist(assignment);
  }

}
