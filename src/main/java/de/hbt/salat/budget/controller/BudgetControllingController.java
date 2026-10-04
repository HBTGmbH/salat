package de.hbt.salat.budget.controller;

import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import de.hbt.salat.auth.domain.Authorized;
import de.hbt.salat.auth.domain.AuthorizedUser;
import de.hbt.salat.budget.auth.BudgetAuthorization;
import de.hbt.salat.budget.service.BudgetControllingService;

@Controller
@RequestMapping("/budget/controlling")
@RequiredArgsConstructor
@Authorized(requireUnrestricted = true)
public class BudgetControllingController {

    private final BudgetControllingService budgetControllingService;
    private final BudgetAuthorization budgetAuthorization;
    private final AuthorizedUser authorizedUser;

    /**
     * The chosen order arrives as {@code fBudgetCustomerOrderId} through the registered UiState
     * mapping, the same way the plan list and the rate list get theirs (#1009) — they share one
     * remembered value (#952). The filter carries the id because a sign can be changed (#1334), and the
     * evaluation is asked by that id (#1338).
     *
     * <p>A remembered order is only preselected, never evaluated. Without a period an evaluation
     * spans 2000 to 2999 — the most expensive one the module has — and merely navigating to the page
     * must not trigger it. What distinguishes the two is {@code evaluate}, a hidden field of the
     * filter form that only travels on submit. It cannot be read off the presence of the order
     * parameter: the UiState fallback supplies that on every request as soon as an order is
     * remembered, so its presence says nothing about who asked for what. The links that lead here to
     * see an evaluation — from the dashboard, from the segment listing and from the budget alert
     * mails — set {@code evaluate} themselves.
     */
    @GetMapping
    public String show(@RequestParam(required = false) Long fBudgetCustomerOrderId,
                       @RequestParam(defaultValue = "false") boolean evaluate,
                       @ModelAttribute("filter") ControllingFilterForm filter,
                       Model model) {
        model.addAttribute("customerorders", budgetAuthorization.authorizedCustomerorders());
        model.addAttribute("isManager", authorizedUser.isManager());
        model.addAttribute("fBudgetCustomerOrderId", fBudgetCustomerOrderId);

        if (evaluate && fBudgetCustomerOrderId != null) {
            // Deliberately outside the try below: a missing privilege must not degrade into a hint
            // next to an empty evaluation.
            budgetAuthorization.checkAuthorizedForCustomerorderId(fBudgetCustomerOrderId);
            var from = filter.getFrom() != null ? filter.getFrom() : LocalDate.of(2000, 1, 1);
            var until = filter.getUntil() != null ? filter.getUntil() : LocalDate.of(2999, 12, 31);
            try {
                // Empty when the order no longer exists: there is nothing to evaluate, and the filter
                // offers no entry for it.
                budgetControllingService.compute(fBudgetCustomerOrderId, from, until, authorizedUser.isManager())
                    .ifPresent(result -> model.addAttribute("result", result));
            } catch (Exception ex) {
                model.addAttribute("controllingError", ex.getMessage());
            }
        }

        return "budget/controlling";
    }
}
