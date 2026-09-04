package org.tb.budget.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.tb.auth.domain.Authorized;
import org.tb.budget.auth.BudgetAuthorization;
import org.tb.budget.service.BudgetDashboardService;
import org.tb.customer.service.CustomerSegmentService;
import org.tb.order.service.CustomerorderService;

@Controller
@RequestMapping("/budget/dashboard")
@RequiredArgsConstructor
@Authorized(requireUnrestricted = true)
public class BudgetDashboardController {

    private final BudgetDashboardService budgetDashboardService;
    private final CustomerSegmentService customerSegmentService;
    private final CustomerorderService customerorderService;
    private final BudgetAuthorization budgetAuthorization;

    @GetMapping
    public String dashboard(@ModelAttribute("filter") DashboardFilterForm filter, Model model) {
        model.addAttribute("segments", customerSegmentService.getAll());

        // Order responsibles only ever see their own orders, so the responsible filter would have a
        // single choice for them and is offered only to those who see every order. Where it is not
        // offered it is not applied either: the select would not submit its parameter, and the
        // remembered UiState value would otherwise keep filtering invisibly.
        var showResponsibleFilter = budgetAuthorization.seesAllCustomerorders();
        model.addAttribute("showResponsibleFilter", showResponsibleFilter);
        if (showResponsibleFilter) {
            model.addAttribute("responsibles", customerorderService.getResponsibleEmployees());
        }
        var responsibleId = showResponsibleFilter ? filter.getBudgetResponsibleId() : null;

        model.addAttribute("rows",
            budgetDashboardService.computeDashboard(filter.getBudgetSegmentId(), responsibleId));
        return "budget/dashboard";
    }
}
