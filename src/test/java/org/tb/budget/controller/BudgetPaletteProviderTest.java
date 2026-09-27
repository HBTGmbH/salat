package org.tb.budget.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.tb.common.palette.PaletteKind.CUSTOMERORDER;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.tb.budget.auth.BudgetAuthorization;
import org.tb.budget.service.OrderBudgetService;
import org.tb.common.palette.PaletteHit;
import org.tb.common.palette.PaletteKind;
import org.tb.common.palette.PaletteTarget;
import org.tb.common.palette.PaletteText;

/**
 * The budget targets the command palette adds to an order the order module found (#1157): the
 * controlling, evaluated at once, and the plan list where the order has plans. Whether a user may
 * see an order's figures is decided by {@link BudgetAuthorization}; here it answers the way it does
 * for the four roles — a manager sees every order, an employee only the orders they are responsible
 * for, a people lead no more than that, restricted nothing. Evaluating a foreign order answers 403,
 * so such an order gets no target at all, and its plans are not even looked up.
 */
@ExtendWith(MockitoExtension.class)
@DisplayNameGeneration(ReplaceUnderscores.class)
class BudgetPaletteProviderTest {

  private static final String OWN_ORDER = "MUSTER-01";
  private static final String FOREIGN_ORDER = "MUSTER-02";

  @Mock
  private BudgetAuthorization budgetAuthorization;
  @Mock
  private OrderBudgetService orderBudgetService;

  @InjectMocks
  private BudgetPaletteProvider provider;

  @ParameterizedTest
  @EnumSource(value = PaletteKind.class, names = "CUSTOMERORDER", mode = EnumSource.Mode.EXCLUDE)
  void answers_nothing_for_other_kinds_than_orders(PaletteKind kind) {
    var targets = provider.targetsFor(kind, List.of(hit(kind, OWN_ORDER)));

    assertThat(targets).isEmpty();
    verifyNoInteractions(budgetAuthorization, orderBudgetService);
  }

  // --- the four roles ---------------------------------------------------------------------------

  @Test
  void offers_a_manager_the_controlling_of_every_order() {
    when(budgetAuthorization.isAuthorizedForCustomerorder(OWN_ORDER)).thenReturn(true);
    when(budgetAuthorization.isAuthorizedForCustomerorder(FOREIGN_ORDER)).thenReturn(true);
    when(orderBudgetService.getPlanPresence(List.of(OWN_ORDER, FOREIGN_ORDER))).thenReturn(Map.of());

    var targets = provider.targetsFor(CUSTOMERORDER, List.of(order(OWN_ORDER), order(FOREIGN_ORDER)));

    assertThat(targets).containsOnlyKeys(OWN_ORDER, FOREIGN_ORDER);
    assertThat(targets.get(FOREIGN_ORDER)).containsExactly(
        controlling("/budget/controlling?fCustomerOrderSign=MUSTER-02&evaluate=true"));
  }

  /** The plans are only asked for the order the employee may see — the stub names exactly that one. */
  @Test
  void offers_an_employee_only_the_orders_they_are_responsible_for() {
    when(budgetAuthorization.isAuthorizedForCustomerorder(OWN_ORDER)).thenReturn(true);
    when(budgetAuthorization.isAuthorizedForCustomerorder(FOREIGN_ORDER)).thenReturn(false);
    when(orderBudgetService.getPlanPresence(List.of(OWN_ORDER))).thenReturn(Map.of());

    var targets = provider.targetsFor(CUSTOMERORDER, List.of(order(OWN_ORDER), order(FOREIGN_ORDER)));

    assertThat(targets).containsOnlyKeys(OWN_ORDER);
    assertThat(targets.get(OWN_ORDER)).containsExactly(
        controlling("/budget/controlling?fCustomerOrderSign=MUSTER-01&evaluate=true"));
  }

  /** The role grants no budget access of its own; without a responsibility nothing is offered. */
  @Test
  void offers_a_people_lead_without_a_responsibility_nothing() {
    when(budgetAuthorization.isAuthorizedForCustomerorder(OWN_ORDER)).thenReturn(false);
    when(budgetAuthorization.isAuthorizedForCustomerorder(FOREIGN_ORDER)).thenReturn(false);

    var targets = provider.targetsFor(CUSTOMERORDER, List.of(order(OWN_ORDER), order(FOREIGN_ORDER)));

    assertThat(targets).isEmpty();
    verifyNoInteractions(orderBudgetService);
  }

  @Test
  void offers_a_restricted_user_nothing() {
    when(budgetAuthorization.isAuthorizedForCustomerorder(OWN_ORDER)).thenReturn(false);

    var targets = provider.targetsFor(CUSTOMERORDER, List.of(order(OWN_ORDER)));

    assertThat(targets).isEmpty();
    verifyNoInteractions(orderBudgetService);
  }

  // --- the links --------------------------------------------------------------------------------

  /** An order's sign may contain a slash, a space or an ampersand; the key stays the sign itself. */
  @Test
  void encodes_the_sign_in_both_links() {
    var sign = "MUSTER/01 A&B";
    when(budgetAuthorization.isAuthorizedForCustomerorder(sign)).thenReturn(true);
    when(orderBudgetService.getPlanPresence(List.of(sign))).thenReturn(Map.of(sign, true));

    var targets = provider.targetsFor(CUSTOMERORDER, List.of(order(sign)));

    assertThat(targets.get(sign)).containsExactly(
        controlling("/budget/controlling?fCustomerOrderSign=MUSTER%2F01+A%26B&evaluate=true"),
        budget("/budget?fCustomerOrderSign=MUSTER%2F01+A%26B"));
  }

  /**
   * The switch for the inactive plans only widens the list: it is not sent where an active plan
   * exists, so a remembered "show inactive" stays as the user left it.
   */
  @Test
  void offers_the_plans_after_the_controlling_where_an_active_plan_exists() {
    when(budgetAuthorization.isAuthorizedForCustomerorder(OWN_ORDER)).thenReturn(true);
    when(orderBudgetService.getPlanPresence(List.of(OWN_ORDER))).thenReturn(Map.of(OWN_ORDER, true));

    var targets = provider.targetsFor(CUSTOMERORDER, List.of(order(OWN_ORDER)));

    assertThat(targets.get(OWN_ORDER)).containsExactly(
        controlling("/budget/controlling?fCustomerOrderSign=MUSTER-01&evaluate=true"),
        budget("/budget?fCustomerOrderSign=MUSTER-01"));
  }

  /** Otherwise the list would open with the active plans only and say that nothing was found. */
  @Test
  void opens_the_plans_with_the_inactive_ones_shown_where_none_is_active() {
    when(budgetAuthorization.isAuthorizedForCustomerorder(OWN_ORDER)).thenReturn(true);
    when(orderBudgetService.getPlanPresence(List.of(OWN_ORDER))).thenReturn(Map.of(OWN_ORDER, false));

    var targets = provider.targetsFor(CUSTOMERORDER, List.of(order(OWN_ORDER)));

    assertThat(targets.get(OWN_ORDER)).containsExactly(
        controlling("/budget/controlling?fCustomerOrderSign=MUSTER-01&evaluate=true"),
        budget("/budget?fCustomerOrderSign=MUSTER-01&fBudgetShowInactive=true"));
  }

  @Test
  void offers_no_plans_for_an_order_without_any() {
    when(budgetAuthorization.isAuthorizedForCustomerorder(OWN_ORDER)).thenReturn(true);
    when(budgetAuthorization.isAuthorizedForCustomerorder(FOREIGN_ORDER)).thenReturn(true);
    when(orderBudgetService.getPlanPresence(List.of(OWN_ORDER, FOREIGN_ORDER)))
        .thenReturn(Map.of(OWN_ORDER, true));

    var targets = provider.targetsFor(CUSTOMERORDER, List.of(order(OWN_ORDER), order(FOREIGN_ORDER)));

    assertThat(targets.get(OWN_ORDER)).hasSize(2);
    assertThat(targets.get(FOREIGN_ORDER)).containsExactly(
        controlling("/budget/controlling?fCustomerOrderSign=MUSTER-02&evaluate=true"));
  }

  private static PaletteTarget controlling(String href) {
    return new PaletteTarget(PaletteText.of("main.palette.target.customerorder.controlling"), href, 2);
  }

  private static PaletteTarget budget(String href) {
    return new PaletteTarget(PaletteText.of("main.palette.target.customerorder.budget"), href, 3);
  }

  private static PaletteHit order(String sign) {
    return hit(CUSTOMERORDER, sign);
  }

  private static PaletteHit hit(PaletteKind kind, String key) {
    return new PaletteHit(kind, key, key, "Wartungsvertrag", null, false, false, 4, List.of());
  }
}
