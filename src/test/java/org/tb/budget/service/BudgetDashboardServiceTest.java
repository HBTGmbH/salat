package org.tb.budget.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.tb.order.service.CustomerorderService;

/**
 * The dashboard filters by customer segment and by order responsible (#920). Both filters are
 * resolved to customer order signs and handed to the query; two set filters intersect. What the
 * user is allowed to see is decided further down, in {@code OrderBudgetService}.
 */
@DisplayNameGeneration(ReplaceUnderscores.class)
public class BudgetDashboardServiceTest {

  private static final long SEGMENT_ID = 7L;
  private static final long RESPONSIBLE_ID = 42L;

  private OrderBudgetService orderBudgetService;
  private BudgetControllingService budgetControllingService;
  private CustomerorderService customerorderService;
  private BudgetDashboardService service;

  @BeforeEach
  public void setUp() {
    orderBudgetService = mock(OrderBudgetService.class);
    budgetControllingService = mock(BudgetControllingService.class);
    customerorderService = mock(CustomerorderService.class);
    service = new BudgetDashboardService(orderBudgetService, budgetControllingService, customerorderService);

    when(orderBudgetService.getAllActiveVisible(any())).thenReturn(List.of());
    when(budgetControllingService.computeUtilizationInfos(anyList())).thenReturn(Map.of());
  }

  @Test
  public void without_a_filter_nothing_is_restricted() {
    service.computeDashboard(null, null);

    assertThat(capturedRestriction()).isNull();
    verify(customerorderService, never()).getSignsByCustomerSegmentId(SEGMENT_ID);
    verify(customerorderService, never()).getSignsByResponsibleEmployeeId(RESPONSIBLE_ID);
  }

  @Test
  public void the_segment_filter_restricts_to_the_orders_of_that_segment() {
    when(customerorderService.getSignsByCustomerSegmentId(SEGMENT_ID)).thenReturn(List.of("A", "B"));

    service.computeDashboard(SEGMENT_ID, null);

    assertThat(capturedRestriction()).containsExactlyInAnyOrder("A", "B");
  }

  @Test
  public void the_responsible_filter_restricts_to_the_orders_of_that_employee() {
    when(customerorderService.getSignsByResponsibleEmployeeId(RESPONSIBLE_ID)).thenReturn(List.of("B", "C"));

    service.computeDashboard(null, RESPONSIBLE_ID);

    assertThat(capturedRestriction()).containsExactlyInAnyOrder("B", "C");
  }

  @Test
  public void both_filters_together_keep_only_what_they_agree_on() {
    when(customerorderService.getSignsByCustomerSegmentId(SEGMENT_ID)).thenReturn(List.of("A", "B"));
    when(customerorderService.getSignsByResponsibleEmployeeId(RESPONSIBLE_ID)).thenReturn(List.of("B", "C"));

    service.computeDashboard(SEGMENT_ID, RESPONSIBLE_ID);

    assertThat(capturedRestriction()).containsExactly("B");
  }

  /**
   * An empty restriction is an answer, not a missing filter: no order matches, so no plan may show
   * up. Were it turned into {@code null} the dashboard would list everything instead.
   */
  @Test
  public void a_filter_matching_no_order_yields_an_empty_restriction_not_an_absent_one() {
    when(customerorderService.getSignsByCustomerSegmentId(SEGMENT_ID)).thenReturn(List.of());

    service.computeDashboard(SEGMENT_ID, null);

    assertThat(capturedRestriction()).isNotNull().isEmpty();
  }

  @Test
  public void filters_that_have_no_order_in_common_yield_an_empty_restriction() {
    when(customerorderService.getSignsByCustomerSegmentId(SEGMENT_ID)).thenReturn(List.of("A"));
    when(customerorderService.getSignsByResponsibleEmployeeId(RESPONSIBLE_ID)).thenReturn(List.of("C"));

    service.computeDashboard(SEGMENT_ID, RESPONSIBLE_ID);

    assertThat(capturedRestriction()).isNotNull().isEmpty();
  }

  @SuppressWarnings("unchecked")
  private Collection<String> capturedRestriction() {
    var captor = ArgumentCaptor.forClass(Collection.class);
    verify(orderBudgetService).getAllActiveVisible(captor.capture());
    return captor.getValue();
  }
}
