package de.hbt.salat.budget.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.MessageSourceAccessor;
import org.springframework.test.util.ReflectionTestUtils;
import de.hbt.salat.budget.domain.OrderBudget;
import de.hbt.salat.budget.persistence.OrderBudgetRepository;
import de.hbt.salat.budget.service.BudgetControllingService.UtilizationInfo;
import de.hbt.salat.common.SalatProperties;
import de.hbt.salat.common.service.MailService;
import de.hbt.salat.employee.domain.Employee;
import de.hbt.salat.employee.preferences.EmployeePreferenceService;
import de.hbt.salat.notification.service.NotificationService;
import de.hbt.salat.order.domain.Customerorder;
import de.hbt.salat.order.service.CustomerorderService;

/** The alert names the order of a plan as it is called today, read by the plan's order id (#1212). */
@DisplayNameGeneration(ReplaceUnderscores.class)
public class BudgetAlertServiceTest {

  private static final long CUSTOMERORDER_ID = 7L;
  private static final long RESPONSIBLE_USER_ID = 5L;

  private final OrderBudgetRepository orderBudgetRepository = mock(OrderBudgetRepository.class);
  private final BudgetControllingService budgetControllingService = mock(BudgetControllingService.class);
  private final CustomerorderService customerorderService = mock(CustomerorderService.class);
  private final NotificationService notificationService = mock(NotificationService.class);
  private final OrderBudgetService orderBudgetService = mock(OrderBudgetService.class);
  private BudgetAlertService service;

  @BeforeEach
  public void setUp() {
    service = new BudgetAlertService(orderBudgetRepository, budgetControllingService, customerorderService,
        notificationService, mock(MailService.class), mock(EmployeePreferenceService.class), orderBudgetService,
        mock(MessageSourceAccessor.class), new SalatProperties());
    when(budgetControllingService.computeUtilizationInfo(any())).thenReturn(
        new UtilizationInfo(new BigDecimal("1000"), new BigDecimal("900"), LocalDate.of(2026, 6, 15)));

    var responsible = mock(Employee.class, RETURNS_DEEP_STUBS);
    when(responsible.getSalatUser().getId()).thenReturn(RESPONSIBLE_USER_ID);
    var order = mock(Customerorder.class);
    when(order.getResponsibleHbt()).thenReturn(List.of(responsible));
    when(order.getId()).thenReturn(CUSTOMERORDER_ID);
    when(customerorderService.getCustomerorderSignsByIds(List.of(CUSTOMERORDER_ID)))
        .thenReturn(Map.of(CUSTOMERORDER_ID, "co"));
    when(customerorderService.getCustomerorderBySign("co")).thenReturn(order);
  }

  @Test
  public void alerts_the_responsibles_of_the_order_behind_the_id_named_as_it_is_called_today() {
    givenPlans(plan(CUSTOMERORDER_ID));

    service.checkAndNotify();

    verify(notificationService).emitNotification(eq(List.of(RESPONSIBLE_USER_ID)), anyString(), any(),
        anyString(), any(), eq("/budget/controlling?fBudgetCustomerOrderId=" + CUSTOMERORDER_ID + "&evaluate=true"), any());
  }

  private void givenPlans(OrderBudget... plans) {
    when(orderBudgetRepository.findByActiveAndAlertThresholdPercentIsNotNull(Boolean.TRUE)).thenReturn(List.of(plans));
  }

  private static OrderBudget plan(long customerorderId) {
    var plan = new OrderBudget();
    ReflectionTestUtils.setField(plan, "id", 1L);
    plan.setName("plan");
    plan.setCustomerorderId(customerorderId);
    plan.setAlertThresholdPercent(80);
    return plan;
  }
}
