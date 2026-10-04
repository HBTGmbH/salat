package de.hbt.salat.budget.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ui.ConcurrentModel;
import de.hbt.salat.auth.domain.AuthorizedUser;
import de.hbt.salat.budget.auth.BudgetAuthorization;
import de.hbt.salat.budget.domain.BudgetControllingResult;
import de.hbt.salat.budget.service.BudgetControllingService;
import de.hbt.salat.common.LocalDateRange;
import de.hbt.salat.common.exception.AuthorizationException;
import de.hbt.salat.common.exception.ErrorCode;
import de.hbt.salat.order.service.CustomerorderService;

/**
 * The order arrives through the registered UiState mapping, so it is present on every request as
 * soon as one is remembered (#1009). What decides whether anything is computed is therefore the
 * {@code evaluate} marker of the filter form — and it has to, because an evaluation without a period
 * spans 2000 to 2999 and is the most expensive thing the module does. Opening the page must stay
 * free.
 *
 * <p>The filter carries the id of the order (#1334); the evaluation is asked by the sign the order
 * has today.
 */
@DisplayNameGeneration(ReplaceUnderscores.class)
@ExtendWith(MockitoExtension.class)
class BudgetControllingControllerTest {

  private static final long ORDER_ID = 42L;

  @Mock
  private BudgetControllingService budgetControllingService;

  @Mock
  private BudgetAuthorization budgetAuthorization;

  @Mock
  private AuthorizedUser authorizedUser;

  @Mock
  private CustomerorderService customerorderService;

  @InjectMocks
  private BudgetControllingController controller;

  @Test
  void a_remembered_order_is_preselected_without_being_evaluated() {
    var model = new ConcurrentModel();

    controller.show(ORDER_ID, false, new ControllingFilterForm(), model);

    assertThat(model.getAttribute("fBudgetCustomerOrderId")).isEqualTo(ORDER_ID);
    assertThat(model.containsAttribute("result")).isFalse();
    verifyNoInteractions(budgetControllingService);
  }

  @Test
  void submitting_the_form_evaluates_the_chosen_order() {
    givenOrder();
    when(budgetControllingService.compute(eq("1612"), any(), any(), anyBoolean()))
        .thenReturn(result());
    var filter = new ControllingFilterForm();
    filter.setFrom(LocalDate.of(2026, 1, 1));
    filter.setUntil(LocalDate.of(2026, 3, 31));
    var model = new ConcurrentModel();

    controller.show(ORDER_ID, true, filter, model);

    verify(budgetAuthorization).checkAuthorizedForCustomerorderId(ORDER_ID);
    verify(budgetControllingService)
        .compute("1612", LocalDate.of(2026, 1, 1), LocalDate.of(2026, 3, 31), false);
    assertThat(model.getAttribute("result")).isNotNull();
  }

  /**
   * The links from the dashboard, from the segment listing and from the alert mails carry no period
   * of their own in every case; without one the evaluation spans the whole range the module allows.
   */
  @Test
  void submitting_without_a_period_evaluates_the_full_range() {
    givenOrder();
    when(budgetControllingService.compute(any(), any(), any(), anyBoolean())).thenReturn(result());

    controller.show(ORDER_ID, true, new ControllingFilterForm(), new ConcurrentModel());

    verify(budgetControllingService)
        .compute("1612", LocalDate.of(2000, 1, 1), LocalDate.of(2999, 12, 31), false);
  }

  @Test
  void submitting_without_an_order_evaluates_nothing() {
    var model = new ConcurrentModel();

    controller.show(null, true, new ControllingFilterForm(), model);

    assertThat(model.containsAttribute("result")).isFalse();
    verifyNoInteractions(budgetControllingService);
  }

  /** A remembered id outlives its order; there is nothing left to evaluate. */
  @Test
  void submitting_an_order_that_no_longer_exists_evaluates_nothing() {
    when(customerorderService.getCustomerorderSignsByIds(List.of(ORDER_ID))).thenReturn(Map.of());
    var model = new ConcurrentModel();

    controller.show(ORDER_ID, true, new ControllingFilterForm(), model);

    assertThat(model.containsAttribute("result")).isFalse();
    verifyNoInteractions(budgetControllingService);
  }

  /** A missing privilege is answered as such, not as a hint next to an empty evaluation. */
  @Test
  void submitting_a_foreign_order_is_refused() {
    doThrow(new AuthorizationException(ErrorCode.BU_ORDER_NOT_AUTHORIZED, "1612"))
        .when(budgetAuthorization).checkAuthorizedForCustomerorderId(ORDER_ID);

    assertThatThrownBy(() -> controller.show(ORDER_ID, true, new ControllingFilterForm(), new ConcurrentModel()))
        .isInstanceOf(AuthorizationException.class);
    verifyNoInteractions(budgetControllingService);
  }

  private void givenOrder() {
    when(customerorderService.getCustomerorderSignsByIds(List.of(ORDER_ID))).thenReturn(Map.of(ORDER_ID, "1612"));
  }

  private static BudgetControllingResult result() {
    return new BudgetControllingResult("1612", "Auftrag", null, null,
        new LocalDateRange(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 3, 31)), List.of());
  }

}
