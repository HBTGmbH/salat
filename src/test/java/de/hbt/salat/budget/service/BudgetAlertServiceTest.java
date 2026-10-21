package de.hbt.salat.budget.service;

import static de.hbt.salat.budget.service.BudgetNotificationKinds.BUDGET_ALERT;
import static de.hbt.salat.testutils.ReferenceTestUtils.customerorderWithId;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
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
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import de.hbt.salat.budget.domain.OrderBudget;
import de.hbt.salat.budget.persistence.OrderBudgetRepository;
import de.hbt.salat.budget.service.BudgetControllingService.UtilizationInfo;
import de.hbt.salat.notification.domain.NotificationMessage;
import de.hbt.salat.notification.service.NotificationService;
import de.hbt.salat.order.domain.CustomerorderResponsible;
import de.hbt.salat.order.service.CustomerorderService;

/**
 * The alert names the order of a plan as it is called today, read by the plan's order id (#1212).
 * Order and responsibles come by that id as values from the module {@code order} (#1340). Bell and
 * mail go the common way of all notifications since #828: whether the alert is mailed is the
 * responsible's choice, made in {@code NotificationService}; the mail names the order as well.
 */
@DisplayNameGeneration(ReplaceUnderscores.class)
public class BudgetAlertServiceTest {

  private static final long CUSTOMERORDER_ID = 7L;
  private static final long RESPONSIBLE_EMPLOYEE_ID = 4L;
  private static final long RESPONSIBLE_USER_ID = 5L;

  private final OrderBudgetRepository orderBudgetRepository = mock(OrderBudgetRepository.class);
  private final BudgetControllingService budgetControllingService = mock(BudgetControllingService.class);
  private final CustomerorderService customerorderService = mock(CustomerorderService.class);
  private final NotificationService notificationService = mock(NotificationService.class);
  private final OrderBudgetService orderBudgetService = mock(OrderBudgetService.class);
  private final MessageSourceAccessor messages = mock(MessageSourceAccessor.class);
  private final FixedPriceCalculationService fixedPriceCalculationService = mock(FixedPriceCalculationService.class);
  private BudgetAlertService service;

  @BeforeEach
  public void setUp() {
    service = new BudgetAlertService(orderBudgetRepository, budgetControllingService, customerorderService,
        notificationService, orderBudgetService, messages, fixedPriceCalculationService);
    when(fixedPriceCalculationService.getHoursConsumedPercents(any())).thenReturn(Map.of());
    when(budgetControllingService.computeUtilizationInfo(any())).thenReturn(
        new UtilizationInfo(new BigDecimal("1000"), new BigDecimal("900"), LocalDate.of(2026, 6, 15)));

    when(customerorderService.getResponsiblesByCustomerorderId(CUSTOMERORDER_ID)).thenReturn(List.of(
        new CustomerorderResponsible(RESPONSIBLE_EMPLOYEE_ID, "Responsible Person", RESPONSIBLE_USER_ID)));
  }

  @Test
  public void alerts_the_responsibles_of_the_order_behind_the_id_named_as_it_is_called_today() {
    givenPlans(plan(CUSTOMERORDER_ID));

    service.checkAndNotify();

    var message = notified();
    assertThat(message.actionUrl())
        .isEqualTo("/budget/controlling?fBudgetCustomerOrderId=" + CUSTOMERORDER_ID + "&evaluate=true");
    assertThat(message.descriptionKey()).isEqualTo("main.budget.alert.notification.description");
    assertThat(message.descriptionParams()).hasSize(3).startsWith("plan").endsWith("80");
  }

  @Test
  void mails_the_responsibles_naming_the_order_by_the_sign_it_has_today() {
    givenPlans(plan(CUSTOMERORDER_ID));

    service.checkAndNotify();

    var message = notified();
    assertThat(message.mailDescriptionKey()).isEqualTo("main.budget.alert.email.body");
    assertThat(message.mailDescriptionParams()).hasSize(4).startsWith("plan", "co").endsWith("80");
  }

  /** The mail went to the employee before #828; it now goes to the login, like the bell. */
  @Test
  public void a_responsible_without_a_login_is_neither_notified_nor_mailed() {
    when(customerorderService.getResponsiblesByCustomerorderId(CUSTOMERORDER_ID)).thenReturn(List.of(
        new CustomerorderResponsible(RESPONSIBLE_EMPLOYEE_ID, "Responsible Person", null)));
    givenPlans(plan(CUSTOMERORDER_ID));

    service.checkAndNotify();

    verify(notificationService).emitNotification(eq(BUDGET_ALERT), eq(List.of()), any());
  }

  @Test
  public void an_order_without_responsibles_raises_no_alert() {
    when(customerorderService.getResponsiblesByCustomerorderId(CUSTOMERORDER_ID)).thenReturn(List.of());
    givenPlans(plan(CUSTOMERORDER_ID));

    service.checkAndNotify();

    verifyNoInteractions(notificationService);
  }

  /**
   * #1404: a fixed price is measured by its calculated hours. The euro utilization of 90 % would
   * raise the alert; 50 % of the calculated hours do not.
   */
  @Test
  public void judges_a_fixed_price_plan_by_the_consumption_of_its_calculated_hours() {
    var plan = plan(CUSTOMERORDER_ID);
    plan.setFixedPrice(true);
    givenPlans(plan);
    when(fixedPriceCalculationService.getHoursConsumedPercents(any())).thenReturn(Map.of(1L, 50.0));

    service.checkAndNotify();

    verifyNoInteractions(notificationService);
  }

  @Test
  public void alerts_a_fixed_price_plan_over_its_threshold_of_calculated_hours_in_words_of_hours() {
    var plan = plan(CUSTOMERORDER_ID);
    plan.setFixedPrice(true);
    givenPlans(plan);
    when(fixedPriceCalculationService.getHoursConsumedPercents(any())).thenReturn(Map.of(1L, 85.0));

    service.checkAndNotify();

    var message = notified();
    assertThat(message.descriptionKey()).isEqualTo("main.budget.alert.fixedprice.notification.description");
    assertThat(message.mailDescriptionKey()).isEqualTo("main.budget.alert.fixedprice.email.body");
  }

  /** Without a calculation there is nothing to measure against — no alarm, as without a budget. */
  @Test
  public void raises_no_alert_for_a_fixed_price_plan_without_a_calculation() {
    var plan = plan(CUSTOMERORDER_ID);
    plan.setFixedPrice(true);
    givenPlans(plan);

    service.checkAndNotify();

    verifyNoInteractions(notificationService);
    verify(budgetControllingService, never()).computeUtilizationInfo(any());
  }

  private NotificationMessage notified() {
    var message = ArgumentCaptor.forClass(NotificationMessage.class);
    verify(notificationService).emitNotification(eq(BUDGET_ALERT), eq(List.of(RESPONSIBLE_USER_ID)), message.capture());
    return message.getValue();
  }

  private void givenPlans(OrderBudget... plans) {
    when(orderBudgetRepository.findByActiveAndAlertThresholdPercentIsNotNull(Boolean.TRUE)).thenReturn(List.of(plans));
  }

  private static OrderBudget plan(long customerorderId) {
    var plan = new OrderBudget();
    ReflectionTestUtils.setField(plan, "id", 1L);
    plan.setName("plan");
    var customerorder = customerorderWithId(customerorderId);
    customerorder.setSign("co");
    plan.setCustomerorder(customerorder);
    plan.setAlertThresholdPercent(80);
    return plan;
  }
}
