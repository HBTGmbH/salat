package org.tb.budget.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.tb.auth.domain.Authorized;
import org.tb.budget.service.BudgetDashboardService;

@Controller
@RequestMapping("/budget/dashboard")
@RequiredArgsConstructor
@Authorized
public class BudgetDashboardController {

    private final BudgetDashboardService budgetDashboardService;

    @GetMapping
    public String dashboard(Model model) {
        model.addAttribute("rows", budgetDashboardService.computeDashboard());
        return "budget/dashboard";
    }
}
