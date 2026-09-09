package org.tb.budget.controller;

import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toMap;

import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.tb.auth.domain.Authorized;
import org.tb.budget.auth.BudgetAuthorization;
import org.tb.budget.domain.BudgetDashboardRow;
import org.tb.budget.service.BudgetDashboardService;
import org.tb.customer.service.CustomerSegmentService;
import org.tb.employee.domain.Employee;
import org.tb.order.domain.Customerorder;
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

        // The choices follow the chosen segment (#952), so a remembered responsible can drop out of
        // them. It is then dropped rather than applied: the select would show "all" while the list
        // stayed filtered by a value nobody can see.
        Long responsibleId = null;
        if (showResponsibleFilter) {
            var responsibles = customerorderService.getVisibleResponsibleEmployees(filter.getBudgetSegmentId());
            model.addAttribute("responsibles", responsibles);
            responsibleId = offeredResponsibleId(filter.getBudgetResponsibleId(), responsibles);
            filter.setBudgetResponsibleId(responsibleId);
        }

        var rows = budgetDashboardService.computeDashboard(filter.getBudgetSegmentId(), responsibleId);
        model.addAttribute("rows", rows);
        model.addAttribute("customerorders", customerordersOf(rows));
        return "budget/dashboard";
    }

    /** Package-private for the test: the rule matters and the controller has no other seam. */
    static Long offeredResponsibleId(Long responsibleId, List<Employee> responsibles) {
        if (responsibleId == null) {
            return null;
        }
        return responsibles.stream().anyMatch(e -> e.getId().equals(responsibleId)) ? responsibleId : null;
    }

    /**
     * The orders of the listed plans, by sign — a row names its order by sign, and the customer
     * hangs off the order. One query for the whole page instead of one per row.
     */
    private Map<String, Customerorder> customerordersOf(List<BudgetDashboardRow> rows) {
        var signs = rows.stream().map(BudgetDashboardRow::customerorderSign).distinct().toList();
        return customerorderService.getCustomerordersBySigns(signs).stream()
            .collect(toMap(Customerorder::getSign, identity(), (a, b) -> a));
    }
}
