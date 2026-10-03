package de.hbt.salat.budget.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

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
  private static final long MUSTER_01 = 101L;
  private static final long MUSTER_02 = 102L;
  private static final long MUSTER_03 = 103L;

  @Autowired
  private OrderBudgetRepository orderBudgetRepository;

  @MockitoBean
  private AuthorizedUser authorizedUser;

  @BeforeEach
  void setUp() {
    when(authorizedUser.isAuthenticated()).thenReturn(true);
    when(authorizedUser.getLoginSign()).thenReturn("test");
  }

  @Test
  void reports_an_order_as_active_when_one_of_its_plans_is() {
    plan(MUSTER_01, false);
    plan(MUSTER_01, true);
    plan(MUSTER_01, false);

    var presence = orderBudgetRepository.findPlanPresenceByCustomerorderIds(List.of(MUSTER_01));

    assertThat(presence).containsExactly(new BudgetPlanPresence(MUSTER_01, 1));
    assertThat(presence.getFirst().hasActivePlan()).isTrue();
  }

  @Test
  void reports_an_order_with_only_inactive_plans_as_inactive() {
    plan(MUSTER_01, false);
    plan(MUSTER_01, false);

    var presence = orderBudgetRepository.findPlanPresenceByCustomerorderIds(List.of(MUSTER_01));

    assertThat(presence).containsExactly(new BudgetPlanPresence(MUSTER_01, 0));
    assertThat(presence.getFirst().hasActivePlan()).isFalse();
  }

  @Test
  void names_an_order_with_several_active_plans_only_once() {
    plan(MUSTER_01, true);
    plan(MUSTER_01, true);

    assertThat(orderBudgetRepository.findPlanPresenceByCustomerorderIds(List.of(MUSTER_01)))
        .containsExactly(new BudgetPlanPresence(MUSTER_01, 1));
  }

  @Test
  void leaves_out_an_order_without_plans() {
    plan(MUSTER_02, true);

    assertThat(orderBudgetRepository.findPlanPresenceByCustomerorderIds(List.of(MUSTER_01, MUSTER_02)))
        .containsExactly(new BudgetPlanPresence(MUSTER_02, 1));
  }

  @Test
  void answers_only_for_the_orders_asked_for() {
    plan(MUSTER_01, true);
    plan(MUSTER_02, true);

    assertThat(orderBudgetRepository.findPlanPresenceByCustomerorderIds(List.of(MUSTER_01)))
        .containsExactly(new BudgetPlanPresence(MUSTER_01, 1));
  }

  /** A plan on one of the suborders is a plan of the order: the plan list of the order shows it. */
  @Test
  void counts_a_plan_on_a_suborder_for_its_order() {
    plan(MUSTER_01, 17L, false);

    assertThat(orderBudgetRepository.findPlanPresenceByCustomerorderIds(List.of(MUSTER_01)))
        .containsExactly(new BudgetPlanPresence(MUSTER_01, 0));
  }

  @Test
  void groups_the_plans_of_several_orders_by_order() {
    plan(MUSTER_01, true);
    plan(MUSTER_01, false);
    plan(MUSTER_02, false);

    assertThat(orderBudgetRepository.findPlanPresenceByCustomerorderIds(List.of(MUSTER_01, MUSTER_02, MUSTER_03)))
        .containsExactlyInAnyOrder(
            new BudgetPlanPresence(MUSTER_01, 1),
            new BudgetPlanPresence(MUSTER_02, 0));
  }

  private void plan(long customerorderId, boolean active) {
    plan(customerorderId, null, active);
  }

  private void plan(long customerorderId, Long suborderId, boolean active) {
    var plan = new OrderBudget();
    plan.setName("Plan");
    plan.setCustomerorderId(customerorderId);
    plan.setCustomerorderSign("MUSTER-" + customerorderId);
    plan.setSuborderId(suborderId);
    plan.setSuborderSign(suborderId == null ? null : "MUSTER-" + customerorderId + "/" + suborderId);
    plan.setValidFrom(FROM);
    plan.setValidUntil(UNTIL);
    plan.setActive(active);
    orderBudgetRepository.save(plan);
  }
}
