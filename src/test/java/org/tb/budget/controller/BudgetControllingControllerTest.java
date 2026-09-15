package org.tb.budget.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ui.ConcurrentModel;
import org.tb.auth.domain.AuthorizedUser;
import org.tb.budget.auth.BudgetAuthorization;
import org.tb.budget.domain.BudgetControllingResult;
import org.tb.budget.service.BudgetControllingService;
import org.tb.common.LocalDateRange;

/**
 * The order arrives through the registered UiState mapping, so it is present on every request as
 * soon as one is remembered (#1009). What decides whether anything is computed is therefore the
 * {@code evaluate} marker of the filter form — and it has to, because an evaluation without a period
 * spans 2000 to 2999 and is the most expensive thing the module does. Opening the page must stay
 * free.
 */
@DisplayNameGeneration(ReplaceUnderscores.class)
@ExtendWith(MockitoExtension.class)
class BudgetControllingControllerTest {

  @Mock
  private BudgetControllingService budgetControllingService;

  @Mock
  private BudgetAuthorization budgetAuthorization;

  @Mock
  private AuthorizedUser authorizedUser;

  @InjectMocks
  private BudgetControllingController controller;

  @Test
  void a_remembered_order_is_preselected_without_being_evaluated() {
    var model = new ConcurrentModel();

    controller.show("1612", false, new ControllingFilterForm(), model);

    assertThat(model.getAttribute("fCustomerOrderSign")).isEqualTo("1612");
    assertThat(model.containsAttribute("result")).isFalse();
    verifyNoInteractions(budgetControllingService);
  }

  @Test
  void submitting_the_form_evaluates_the_chosen_order() {
    when(budgetControllingService.compute(eq("1612"), any(), any(), anyBoolean()))
        .thenReturn(result());
    var filter = new ControllingFilterForm();
    filter.setFrom(LocalDate.of(2026, 1, 1));
    filter.setUntil(LocalDate.of(2026, 3, 31));
    var model = new ConcurrentModel();

    controller.show("1612", true, filter, model);

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
    when(budgetControllingService.compute(any(), any(), any(), anyBoolean())).thenReturn(result());

    controller.show("1612", true, new ControllingFilterForm(), new ConcurrentModel());

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

  /** A blank selection is no selection — clearing the filter must not evaluate the empty order. */
  @Test
  void submitting_a_cleared_order_evaluates_nothing() {
    var model = new ConcurrentModel();

    controller.show("  ", true, new ControllingFilterForm(), model);

    assertThat(model.getAttribute("fCustomerOrderSign")).isNull();
    verifyNoInteractions(budgetControllingService);
  }

  private static BudgetControllingResult result() {
    return new BudgetControllingResult("1612", "Auftrag", null, null,
        new LocalDateRange(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 3, 31)), List.of());
  }

}
