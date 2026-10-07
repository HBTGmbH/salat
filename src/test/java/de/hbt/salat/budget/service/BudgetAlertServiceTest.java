package de.hbt.salat.budget.service;

import static de.hbt.salat.testutils.ReferenceTestUtils.customerorderWithId;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
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
import org.springframework.test.util.ReflectionTestUtils;
import de.hbt.salat.budget.domain.OrderBudget;
import de.hbt.salat.budget.persistence.OrderBudgetRepository;
import de.hbt.salat.budget.service.BudgetControllingService.UtilizationInfo;
import de.hbt.salat.common.SalatProperties;
import de.hbt.salat.common.service.MailService;
import de.hbt.salat.common.service.MailService.MailContact;
import de.hbt.salat.employee.preferences.EmployeePreferenceService;
import de.hbt.salat.notification.service.NotificationService;
import de.hbt.salat.order.domain.CustomerorderResponsible;
import de.hbt.salat.order.service.CustomerorderService;

/**
 * The alert names the order of a plan as it is called today, read by the plan's order id (#1212).
 * Order and responsibles come by that id as values from the module {@code order} (#1340).
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
  private final MailService mailService = mock(MailService.class);
  private final EmployeePreferenceService employeePreferenceService = mock(EmployeePreferenceService.class);
  private final OrderBudgetService orderBudgetService = mock(OrderBudgetService.class);
  private final MessageSourceAccessor messages = mock(MessageSourceAccessor.class);
  private final FixedPriceCalculationService fixedPriceCalculationService = mock(FixedPriceCalculationService.class);
  private BudgetAlertService service;

  @BeforeEach
  public void setUp() {
    service = new BudgetAlertService(orderBudgetRepository, budgetControllingService, customerorderService,
        notificationService, mailService, employeePreferenceService, orderBudgetService,
        messages, new SalatProperties(), fixedPriceCalculationService);
    when(fixedPriceCalculationService.getHoursConsumedPercents(any())).thenReturn(Map.of());
    when(budgetControllingService.computeUtilizationInfo(any())).thenReturn(
        new UtilizationInfo(new BigDecimal("1000"), new BigDecimal("900"), LocalDate.of(2026, 6, 15)));
    when(messages.getMessage("main.budget.alert.email.subject")).thenReturn("Alert {0}");
    when(messages.getMessage("main.budget.alert.email.body")).thenReturn("{0} of {1}");
    when(messages.getMessage("main.budget.alert.fixedprice.email.body")).thenReturn("hours {0} of {1}");

    when(customerorderService.getResponsiblesByCustomerorderId(CUSTOMERORDER_ID)).thenReturn(List.of(
        new CustomerorderResponsible(RESPONSIBLE_EMPLOYEE_ID, "Responsible Person", RESPONSIBLE_USER_ID)));
    when(employeePreferenceService.getNotificationEmailForEmployeeId(RESPONSIBLE_EMPLOYEE_ID))
        .thenReturn("responsible@example.com");
  }

  @Test
  public void alerts_the_responsibles_of_the_order_behind_the_id_named_as_it_is_called_today() {
    givenPlans(plan(CUSTOMERORDER_ID));

    service.checkAndNotify();

    verify(notificationService).emitNotification(eq(List.of(RESPONSIBLE_USER_ID)), anyString(), any(),
        anyString(), any(), eq("/budget/controlling?fBudgetCustomerOrderId=" + CUSTOMERORDER_ID + "&evaluate=true"), any());
  }

  @Test
  public void mails_the_responsibles_naming_the_order_by_the_sign_it_has_today() {
    givenPlans(plan(CUSTOMERORDER_ID));

    service.checkAndNotify();

    verify(mailService).sendEmail(eq("Alert plan"), eq("plan of co"), any(),
        eq(new MailContact("Responsible Person", "responsible@example.com")));
  }

  @Test
  public void a_responsible_without_a_login_is_mailed_but_not_notified() {
    when(customerorderService.getResponsiblesByCustomerorderId(CUSTOMERORDER_ID)).thenReturn(List.of(
        new CustomerorderResponsible(RESPONSIBLE_EMPLOYEE_ID, "Responsible Person", null)));
    givenPlans(plan(CUSTOMERORDER_ID));

    service.checkAndNotify();

    verify(notificationService).emitNotification(eq(List.of()), anyString(), any(), anyString(), any(), anyString(),
        any());
    verify(mailService).sendEmail(anyString(), anyString(), any(), any());
  }

  @Test
  public void an_order_without_responsibles_raises_no_alert() {
    when(customerorderService.getResponsiblesByCustomerorderId(CUSTOMERORDER_ID)).thenReturn(List.of());
    givenPlans(plan(CUSTOMERORDER_ID));

    service.checkAndNotify();

    verifyNoInteractions(notificationService, mailService);
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

    verifyNoInteractions(notificationService, mailService);
  }

  @Test
  public void alerts_a_fixed_price_plan_over_its_threshold_of_calculated_hours_in_words_of_hours() {
    var plan = plan(CUSTOMERORDER_ID);
    plan.setFixedPrice(true);
    givenPlans(plan);
    when(fixedPriceCalculationService.getHoursConsumedPercents(any())).thenReturn(Map.of(1L, 85.0));

    service.checkAndNotify();

    verify(notificationService).emitNotification(anyList(), anyString(), any(),
        eq("main.budget.alert.fixedprice.notification.description"), any(), anyString(), any());
    verify(mailService).sendEmail(eq("Alert plan"), eq("hours plan of co"), any(), any());
  }

  /** Without a calculation there is nothing to measure against — no alarm, as without a budget. */
  @Test
  public void raises_no_alert_for_a_fixed_price_plan_without_a_calculation() {
    var plan = plan(CUSTOMERORDER_ID);
    plan.setFixedPrice(true);
    givenPlans(plan);

    service.checkAndNotify();

    verifyNoInteractions(notificationService, mailService);
    verify(budgetControllingService, never()).computeUtilizationInfo(any());
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
