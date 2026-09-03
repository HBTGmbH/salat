package org.tb.budget.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.tb.auth.domain.AuthorizedUser;
import org.tb.budget.domain.OrderBudget;
import org.tb.common.exception.AuthorizationException;
import org.tb.common.exception.ErrorCode;
import org.tb.employee.domain.AuthorizedEmployee;
import org.tb.order.domain.Customerorder;
import org.tb.order.service.CustomerorderService;

/**
 * Managers see every customer order; everyone else sees exactly the orders they are responsible
 * for. Backoffice grants no budget access of its own, restricted users see nothing (#919).
 */
@DisplayNameGeneration(ReplaceUnderscores.class)
public class BudgetAuthorizationTest {

  private static final String OWN = "own-order";
  private static final String FOREIGN = "foreign-order";
  private static final long EMPLOYEE_ID = 42L;

  private AuthorizedUser authorizedUser;
  private AuthorizedEmployee authorizedEmployee;
  private CustomerorderService customerorderService;
  private BudgetAuthorization authorization;

  @BeforeEach
  public void setUp() {
    authorizedUser = mock(AuthorizedUser.class);
    authorizedEmployee = mock(AuthorizedEmployee.class);
    customerorderService = mock(CustomerorderService.class);
    authorization = new BudgetAuthorization(authorizedUser, authorizedEmployee, customerorderService);
  }

  @Test
  public void manager_sees_every_customer_order() {
    when(authorizedUser.isManager()).thenReturn(true);

    assertThat(authorization.seesAllCustomerorders()).isTrue();
    assertThat(authorization.isAuthorizedForCustomerorder(FOREIGN)).isTrue();
    assertThat(authorization.isAuthorizedForAnyBudget()).isTrue();
  }

  /**
   * Backoffice carries no budget access of its own. A backoffice employee who is responsible for an
   * order reaches it through that responsibility, like anyone else.
   */
  @Test
  public void backoffice_alone_grants_no_budget_access() {
    when(authorizedUser.isBackoffice()).thenReturn(true);
    givenResponsibleFor();

    assertThat(authorization.seesAllCustomerorders()).isFalse();
    assertThat(authorization.isAuthorizedForCustomerorder(FOREIGN)).isFalse();
    assertThat(authorization.isAuthorizedForAnyBudget()).isFalse();
  }

  @Test
  public void backoffice_reaches_an_order_it_is_responsible_for() {
    when(authorizedUser.isBackoffice()).thenReturn(true);
    givenResponsibleFor(OWN);

    assertThat(authorization.isAuthorizedForCustomerorder(OWN)).isTrue();
    assertThat(authorization.isAuthorizedForCustomerorder(FOREIGN)).isFalse();
  }

  @Test
  public void order_responsible_sees_the_own_order_but_not_a_foreign_one() {
    givenResponsibleFor(OWN);

    assertThat(authorization.isAuthorizedForCustomerorder(OWN)).isTrue();
    assertThat(authorization.isAuthorizedForCustomerorder(FOREIGN)).isFalse();
    assertThat(authorization.seesAllCustomerorders()).isFalse();
    assertThat(authorization.isAuthorizedForAnyBudget()).isTrue();
  }

  @Test
  public void a_budget_is_authorized_via_the_customer_order_of_its_plan() {
    givenResponsibleFor(OWN);

    assertThat(authorization.isAuthorized(budgetOn(OWN))).isTrue();
    assertThat(authorization.isAuthorized(budgetOn(FOREIGN))).isFalse();
  }

  @Test
  public void a_user_without_any_responsibility_gets_no_budget_menu() {
    givenResponsibleFor();

    assertThat(authorization.isAuthorizedForAnyBudget()).isFalse();
    assertThat(authorization.authorizedCustomerorders()).isEmpty();
  }

  /**
   * Restricted users are external staff and interns. They stay out even when an order names them as
   * responsible — the restriction is the stronger statement.
   */
  @Test
  public void restricted_users_see_nothing_even_when_responsible() {
    when(authorizedUser.isRestricted()).thenReturn(true);
    when(authorizedUser.isManager()).thenReturn(true);
    givenResponsibleFor(OWN);

    assertThat(authorization.seesAllCustomerorders()).isFalse();
    assertThat(authorization.isAuthorizedForCustomerorder(OWN)).isFalse();
    assertThat(authorization.isAuthorizedForAnyBudget()).isFalse();
    assertThat(authorization.authorizedCustomerorders()).isEmpty();
  }

  /** Admins are not employees, so the lookup must not be attempted for them. */
  @Test
  public void an_admin_without_an_employee_is_authorized_through_the_role() {
    when(authorizedUser.isManager()).thenReturn(true);
    when(authorizedEmployee.getEmployeeId()).thenReturn(null);

    assertThat(authorization.isAuthorizedForCustomerorder(FOREIGN)).isTrue();
    verify(customerorderService, never()).getCustomerOrdersByResponsibleEmployeeId(null);
  }

  @Test
  public void the_check_names_the_customer_order_it_rejects() {
    givenResponsibleFor(OWN);

    assertThatThrownBy(() -> authorization.checkAuthorizedForCustomerorder(FOREIGN))
        .isInstanceOf(AuthorizationException.class)
        .hasMessageContaining(ErrorCode.BU_ORDER_NOT_AUTHORIZED.getCode());
    assertThatCode(() -> authorization.checkAuthorizedForCustomerorder(OWN))
        .doesNotThrowAnyException();
  }

  /** The responsible orders are asked for on every row, so they must be resolved once. */
  @Test
  public void the_responsible_orders_are_looked_up_only_once_per_request() {
    givenResponsibleFor(OWN);

    authorization.isAuthorizedForCustomerorder(OWN);
    authorization.isAuthorizedForCustomerorder(FOREIGN);
    authorization.isAuthorizedForAnyBudget();

    verify(customerorderService).getCustomerOrdersByResponsibleEmployeeId(EMPLOYEE_ID);
  }

  /**
   * A responsibility on a hidden order grants nothing — {@code hide} is the explicit decision that
   * the order is out of use. The validity range stays irrelevant, because controlling looks
   * backwards.
   */
  @Test
  public void a_responsibility_on_a_hidden_order_grants_nothing() {
    when(authorizedEmployee.getEmployeeId()).thenReturn(EMPLOYEE_ID);
    when(customerorderService.getCustomerOrdersByResponsibleEmployeeId(EMPLOYEE_ID))
        .thenReturn(List.of(hiddenOrderWithSign(OWN)));

    assertThat(authorization.isAuthorizedForCustomerorder(OWN)).isFalse();
    assertThat(authorization.isAuthorizedForAnyBudget()).isFalse();
    assertThat(authorization.authorizedCustomerorders()).isEmpty();
  }

  @Test
  public void an_expired_order_stays_visible_to_its_responsible() {
    when(authorizedEmployee.getEmployeeId()).thenReturn(EMPLOYEE_ID);
    var expired = orderWithSign(OWN);
    expired.setUntilDate(LocalDate.of(2020, 12, 31));
    when(customerorderService.getCustomerOrdersByResponsibleEmployeeId(EMPLOYEE_ID))
        .thenReturn(List.of(expired));

    assertThat(authorization.isAuthorizedForCustomerorder(OWN)).isTrue();
  }

  private void givenResponsibleFor(String... signs) {
    when(authorizedEmployee.getEmployeeId()).thenReturn(EMPLOYEE_ID);
    when(customerorderService.getCustomerOrdersByResponsibleEmployeeId(EMPLOYEE_ID))
        .thenReturn(List.of(signs).stream().map(BudgetAuthorizationTest::orderWithSign).toList());
  }

  private static Customerorder orderWithSign(String sign) {
    var customerorder = new Customerorder();
    customerorder.setSign(sign);
    return customerorder;
  }

  private static Customerorder hiddenOrderWithSign(String sign) {
    var customerorder = orderWithSign(sign);
    customerorder.setHide(true);
    return customerorder;
  }

  private static OrderBudget budgetOn(String customerorderSign) {
    var budget = new OrderBudget();
    budget.setCustomerorderSign(customerorderSign);
    return budget;
  }

}
