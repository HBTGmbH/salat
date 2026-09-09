package org.tb.budget.controller;

import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
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

    @GetMapping
    public String show(@ModelAttribute("filter") ControllingFilterForm filter, Model model) {
        model.addAttribute("customerorders", budgetAuthorization.authorizedCustomerorders());
        model.addAttribute("isManager", authorizedUser.isManager());

        if (filter.getCustomerorderSign() != null && !filter.getCustomerorderSign().isBlank()) {
            // Deliberately outside the try below: a missing privilege must not degrade into a hint
            // next to an empty evaluation.
            budgetAuthorization.checkAuthorizedForCustomerorder(filter.getCustomerorderSign());
            var from = filter.getFrom() != null ? filter.getFrom() : LocalDate.of(2000, 1, 1);
            var until = filter.getUntil() != null ? filter.getUntil() : LocalDate.of(2999, 12, 31);
            try {
                var result = budgetControllingService.compute(
                    filter.getCustomerorderSign(), from, until, authorizedUser.isManager());
                model.addAttribute("result", result);
            } catch (Exception ex) {
                model.addAttribute("controllingError", ex.getMessage());
            }
        }

        return "budget/controlling";
    }
}
