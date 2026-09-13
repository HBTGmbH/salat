package org.tb.budget.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.tb.auth.domain.Authorized;
import org.tb.budget.service.BudgetSegmentControllingService;
import org.tb.common.util.DateUtils;

/**
 * The controlling of all budgeted orders, grouped by customer segment (#779).
 *
 * <p>Under the controlling path rather than under a reporting branch of its own: this is the
 * cross-order view of the very evaluation {@code /budget/controlling} shows for a single order, and
 * every line links back to it.
 *
 * <p>Managers and admins only. The page reports cost and profit across orders the reader has no
 * responsibility for, which is what order responsibles are explicitly not shown (#919); a
 * segment-wide listing cannot be narrowed to their own orders and still be a segment listing.
 */
@Controller
@RequestMapping("/budget/controlling/by-segment")
@RequiredArgsConstructor
@Authorized(requiresManager = true)
public class BudgetSegmentControllingController {

    private final BudgetSegmentControllingService budgetSegmentControllingService;

    @GetMapping
    public String show(@ModelAttribute("filter") SegmentControllingFilterForm filter, Model model) {
        // The clock is read here rather than in the form or the template, so the page has a single
        // date source — the same reason the dashboard passes "today" in.
        filter.applyDefaults(DateUtils.today());
        model.addAttribute("result",
            budgetSegmentControllingService.compute(filter.getFrom(), filter.getUntil()));
        return "budget/controlling-by-segment";
    }
}
