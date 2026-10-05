package de.hbt.salat.budget.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import de.hbt.salat.budget.auth.BudgetAuthorization;
import de.hbt.salat.budget.domain.InvoicableBudget;
import de.hbt.salat.budget.domain.OrderBudget;
import de.hbt.salat.budget.persistence.OrderBudgetRepository;
import de.hbt.salat.budget.persistence.TimereportBudgetAssignmentRepository;

/**
 * The plans the invoice offers are asked by the id of the order the invoice has chosen (#1340): the
 * permission is checked by that id, and the order is not loaded to get there.
 */
@DisplayNameGeneration(ReplaceUnderscores.class)
public class BudgetQueryServiceTest {

  private static final long CUSTOMERORDER_ID = 7L;

  private final OrderBudgetRepository orderBudgetRepository = mock(OrderBudgetRepository.class);
  private final BudgetAuthorization budgetAuthorization = mock(BudgetAuthorization.class);
  private BudgetQueryService service;

  @BeforeEach
  public void setUp() {
    service = new BudgetQueryService(orderBudgetRepository, mock(TimereportBudgetAssignmentRepository.class),
        budgetAuthorization);
  }

  @Test
  public void offers_the_active_plans_of_the_order_with_that_id() {
    when(budgetAuthorization.isAuthorizedForCustomerorderId(CUSTOMERORDER_ID)).thenReturn(true);
    when(orderBudgetRepository.findByCustomerorderIdAndActive(CUSTOMERORDER_ID, Boolean.TRUE))
        .thenReturn(List.of(plan(11L, "plan")));

    assertThat(service.getActivePlans(CUSTOMERORDER_ID))
        .extracting(InvoicableBudget::id, InvoicableBudget::name)
        .containsExactly(tuple(11L, "plan"));
  }

  @Test
  public void offers_nothing_for_an_order_the_user_may_not_see() {
    when(budgetAuthorization.isAuthorizedForCustomerorderId(CUSTOMERORDER_ID)).thenReturn(false);

    assertThat(service.getActivePlans(CUSTOMERORDER_ID)).isEmpty();
    verify(orderBudgetRepository, never()).findByCustomerorderIdAndActive(anyLong(), any());
  }

  private static OrderBudget plan(long id, String name) {
    var plan = new OrderBudget();
    ReflectionTestUtils.setField(plan, "id", id);
    plan.setName(name);
    plan.setCustomerorderId(CUSTOMERORDER_ID);
    return plan;
  }
}
