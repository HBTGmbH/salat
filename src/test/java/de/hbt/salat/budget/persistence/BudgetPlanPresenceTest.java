package de.hbt.salat.budget.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import jakarta.persistence.EntityManager;
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
import de.hbt.salat.budget.domain.BudgetPlanPresence;
import de.hbt.salat.budget.domain.OrderBudget;
import de.hbt.salat.order.domain.Customerorder;
import de.hbt.salat.order.domain.Suborder;
import de.hbt.salat.testutils.MasterDataTestTree;

/**
 * Which orders have plans, and an active one among them (#1157). The command palette offers the
 * plan list only for an order that has plans, and opens it with the inactive ones shown where none
 * is active. One grouped query answers this for all orders of a search: one row per order, its
 * {@code active} the largest of the plans' flags, no row for an order without plans.
 */
@DataJpaTest
@Import(AuthorizedUserAuditorAware.class)
@DisplayNameGeneration(ReplaceUnderscores.class)
class BudgetPlanPresenceTest {

  private static final LocalDate FROM = LocalDate.of(2026, 1, 1);
  private static final LocalDate UNTIL = LocalDate.of(2026, 12, 31);

  /** The orders by id — the query groups by the id of the order since #1205, not by its sign. */
  private long muster01;
  private long muster02;
  private long muster03;
  private long muster01Suborder;

  @Autowired
  private OrderBudgetRepository orderBudgetRepository;

  @Autowired
  private EntityManager entityManager;

  @MockitoBean
  private AuthorizedUser authorizedUser;

  @BeforeEach
  void setUp() {
    when(authorizedUser.isAuthenticated()).thenReturn(true);
    when(authorizedUser.getLoginSign()).thenReturn("test");
    var tree = new MasterDataTestTree(entityManager);
    muster01 = tree.customerorder("MUSTER-01").getId();
    muster02 = tree.customerorder("MUSTER-02").getId();
    muster03 = tree.customerorder("MUSTER-03").getId();
    muster01Suborder = tree.suborder("MUSTER-01", "01").getId();
  }

  @Test
  void reports_an_order_as_active_when_one_of_its_plans_is() {
    plan(muster01, false);
    plan(muster01, true);
    plan(muster01, false);

    var presence = orderBudgetRepository.findPlanPresenceByCustomerorderIds(List.of(muster01));

    assertThat(presence).containsExactly(new BudgetPlanPresence(muster01, 1));
    assertThat(presence.getFirst().hasActivePlan()).isTrue();
  }

  @Test
  void reports_an_order_with_only_inactive_plans_as_inactive() {
    plan(muster01, false);
    plan(muster01, false);

    var presence = orderBudgetRepository.findPlanPresenceByCustomerorderIds(List.of(muster01));

    assertThat(presence).containsExactly(new BudgetPlanPresence(muster01, 0));
    assertThat(presence.getFirst().hasActivePlan()).isFalse();
  }

  @Test
  void names_an_order_with_several_active_plans_only_once() {
    plan(muster01, true);
    plan(muster01, true);

    assertThat(orderBudgetRepository.findPlanPresenceByCustomerorderIds(List.of(muster01)))
        .containsExactly(new BudgetPlanPresence(muster01, 1));
  }

  @Test
  void leaves_out_an_order_without_plans() {
    plan(muster02, true);

    assertThat(orderBudgetRepository.findPlanPresenceByCustomerorderIds(List.of(muster01, muster02)))
        .containsExactly(new BudgetPlanPresence(muster02, 1));
  }

  @Test
  void answers_only_for_the_orders_asked_for() {
    plan(muster01, true);
    plan(muster02, true);

    assertThat(orderBudgetRepository.findPlanPresenceByCustomerorderIds(List.of(muster01)))
        .containsExactly(new BudgetPlanPresence(muster01, 1));
  }

  /** A plan on one of the suborders is a plan of the order: the plan list of the order shows it. */
  @Test
  void counts_a_plan_on_a_suborder_for_its_order() {
    plan(muster01, muster01Suborder, false);

    assertThat(orderBudgetRepository.findPlanPresenceByCustomerorderIds(List.of(muster01)))
        .containsExactly(new BudgetPlanPresence(muster01, 0));
  }

  @Test
  void groups_the_plans_of_several_orders_by_order() {
    plan(muster01, true);
    plan(muster01, false);
    plan(muster02, false);

    assertThat(orderBudgetRepository.findPlanPresenceByCustomerorderIds(List.of(muster01, muster02, muster03)))
        .containsExactlyInAnyOrder(
            new BudgetPlanPresence(muster01, 1),
            new BudgetPlanPresence(muster02, 0));
  }

  private void plan(long customerorderId, boolean active) {
    plan(customerorderId, null, active);
  }

  private void plan(long customerorderId, Long suborderId, boolean active) {
    var plan = new OrderBudget();
    plan.setName("Plan");
    plan.setCustomerorder(entityManager.find(Customerorder.class, customerorderId));
    plan.setSuborder(suborderId == null ? null : entityManager.find(Suborder.class, suborderId));
    plan.setValidFrom(FROM);
    plan.setValidUntil(UNTIL);
    plan.setActive(active);
    orderBudgetRepository.save(plan);
  }
}
