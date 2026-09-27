package org.tb.budget.controller;

import static org.tb.common.palette.PaletteKind.CUSTOMERORDER;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.tb.budget.auth.BudgetAuthorization;
import org.tb.budget.service.OrderBudgetService;
import org.tb.common.palette.PaletteHit;
import org.tb.common.palette.PaletteKind;
import org.tb.common.palette.PaletteLink;
import org.tb.common.palette.PaletteProvider;
import org.tb.common.palette.PaletteTarget;
import org.tb.common.palette.PaletteText;

/**
 * The budget targets of an order found by the command palette (#1157, ADR-0031): its controlling,
 * evaluated at once, and its plans.
 *
 * <p>The order module finds the order but may not import this one, which alone knows who may see an
 * order's figures: managers, and whoever is responsible for the order ({@link BudgetAuthorization}).
 * The controlling page itself opens for everyone, but evaluating a foreign order answers 403 — so
 * the target is offered per order, never for the page as such.
 *
 * <p>The controlling link has no period: from 2000 to 2999, as the budget alert's link has it. The
 * plans are only a target where an order has any; where none of them is active, the list is opened
 * with the inactive ones shown, otherwise it would say "nothing found".
 */
@Component
@RequiredArgsConstructor
public class BudgetPaletteProvider implements PaletteProvider {

  private final BudgetAuthorization budgetAuthorization;
  private final OrderBudgetService orderBudgetService;

  @Override
  public Map<String, List<PaletteTarget>> targetsFor(PaletteKind kind, List<PaletteHit> hits) {
    if (kind != CUSTOMERORDER) {
      return Map.of();
    }
    var signs = hits.stream().map(PaletteHit::key)
        .filter(budgetAuthorization::isAuthorizedForCustomerorder)
        .toList();
    if (signs.isEmpty()) {
      return Map.of();
    }
    var plans = orderBudgetService.getPlanPresence(signs);
    var targets = new HashMap<String, List<PaletteTarget>>();
    for (var sign : signs) {
      var controlling = new PaletteTarget(PaletteText.of("main.palette.target.customerorder.controlling"),
          PaletteLink.to("/budget/controlling").param("fCustomerOrderSign", sign).param("evaluate", true).build(), 2);
      var hasActivePlan = plans.get(sign);
      targets.put(sign, hasActivePlan == null ? List.of(controlling) : List.of(controlling,
          new PaletteTarget(PaletteText.of("main.palette.target.customerorder.budget"),
              PaletteLink.to("/budget").param("fCustomerOrderSign", sign)
                  .paramIf(!hasActivePlan, "fBudgetShowInactive", true).build(), 3)));
    }
    return targets;
  }
}
