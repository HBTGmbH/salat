package org.tb.budget.controller;

import static org.apache.commons.lang3.StringUtils.trimToNull;

import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.tb.auth.domain.Authorized;
import org.tb.auth.domain.AuthorizedUser;
import org.tb.budget.auth.BudgetAuthorization;
import org.tb.budget.service.BudgetControllingService;

@Controller
@RequestMapping("/budget/controlling")
@RequiredArgsConstructor
@Authorized(requireUnrestricted = true)
public class BudgetControllingController {

    private final BudgetControllingService budgetControllingService;
    private final BudgetAuthorization budgetAuthorization;
    private final AuthorizedUser authorizedUser;

    /**
     * The chosen order arrives as {@code fCustomerOrderSign} through the registered UiState mapping,
     * the same way the plan list and the rate list get theirs (#1009) — the three share one
     * remembered value (#952).
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
    public String show(@RequestParam(required = false) String fCustomerOrderSign,
                       @RequestParam(defaultValue = "false") boolean evaluate,
                       @ModelAttribute("filter") ControllingFilterForm filter,
                       Model model) {
        model.addAttribute("customerorders", budgetAuthorization.authorizedCustomerorders());
        model.addAttribute("isManager", authorizedUser.isManager());

        var sign = trimToNull(fCustomerOrderSign);
        model.addAttribute("fCustomerOrderSign", sign);

        if (evaluate && sign != null) {
            // Deliberately outside the try below: a missing privilege must not degrade into a hint
            // next to an empty evaluation.
            budgetAuthorization.checkAuthorizedForCustomerorder(sign);
            var from = filter.getFrom() != null ? filter.getFrom() : LocalDate.of(2000, 1, 1);
            var until = filter.getUntil() != null ? filter.getUntil() : LocalDate.of(2999, 12, 31);
            try {
                var result = budgetControllingService.compute(sign, from, until, authorizedUser.isManager());
                model.addAttribute("result", result);
            } catch (Exception ex) {
                model.addAttribute("controllingError", ex.getMessage());
            }
        }

        return "budget/controlling";
    }
}
