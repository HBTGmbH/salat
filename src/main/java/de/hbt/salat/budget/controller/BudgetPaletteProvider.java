package de.hbt.salat.budget.controller;

import static de.hbt.salat.common.palette.PaletteKind.CUSTOMERORDER;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import de.hbt.salat.budget.auth.BudgetAuthorization;
import de.hbt.salat.budget.service.OrderBudgetService;
import de.hbt.salat.common.Hiding;
import de.hbt.salat.common.Validity;
import de.hbt.salat.common.palette.PaletteCommand;
import de.hbt.salat.common.palette.PaletteHit;
import de.hbt.salat.common.palette.PaletteKind;
import de.hbt.salat.common.palette.PaletteLink;
import de.hbt.salat.common.palette.PaletteParameter;
import de.hbt.salat.common.palette.PaletteProvider;
import de.hbt.salat.common.palette.PaletteSuggestion;
import de.hbt.salat.common.palette.PaletteSuggestionRequest;
import de.hbt.salat.common.palette.PaletteTarget;
import de.hbt.salat.common.palette.PaletteText;
import de.hbt.salat.order.domain.CustomerorderSearchRow;
import de.hbt.salat.order.service.CustomerorderService;

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
  private final CustomerorderService customerorderService;

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

  /**
   * The orders for {@code controlling} (#1158): the order search of the palette, narrowed to those
   * whose figures the user may see — the same question as for the controlling target above. Current
   * orders before ended and hidden ones, then by how well they match.
   */
  @Override
  public List<PaletteSuggestion> suggest(PaletteSuggestionRequest request) {
    if (!request.is(PaletteCommand.CONTROLLING, PaletteParameter.CUSTOMERORDER)) {
      return List.of();
    }
    var query = request.query();
    return customerorderService.getPaletteCandidates(query).stream()
        .filter(row -> budgetAuthorization.isAuthorizedForCustomerorder(row.sign()))
        .sorted(Comparator.comparing((CustomerorderSearchRow row) -> Hiding.isHidden(row.hide()))
            .thenComparing(row -> Validity.isInactive(row.untilDate()))
            .thenComparing(Comparator.comparingInt((CustomerorderSearchRow row) -> query.match(row.sign(),
                row.shortdescription(), row.description(), row.customerShortname())).reversed()))
        .map(row -> new PaletteSuggestion(row.sign(), row.sign(),
            row.shortdescription() != null && !row.shortdescription().isBlank() ? row.shortdescription() : row.description(),
            null, false, false, query.isKey(row.sign())))
        .toList();
  }
}
