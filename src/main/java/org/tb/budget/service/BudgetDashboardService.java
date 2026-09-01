package org.tb.budget.service;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.tb.auth.domain.Authorized;
import org.tb.budget.domain.BudgetDashboardRow;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
@Authorized
public class BudgetDashboardService {

    private final OrderBudgetService orderBudgetService;
    private final BudgetControllingService budgetControllingService;

    public List<BudgetDashboardRow> computeDashboard() {
        var budgets = orderBudgetService.getAllActive();
        var utilizations = budgetControllingService.computeUtilizationInfos(budgets);
        return budgets.stream()
            .map(b -> {
                var utilization = utilizations.get(b.getId());
                var info = utilization.info();
                return new BudgetDashboardRow(
                    b.getId(),
                    b.getName(),
                    b.getCustomerorderSign(),
                    utilization.customerorderDescription(),
                    b.getValidFrom(),
                    b.getValidUntil(),
                    info.budgetEuro(),
                    info.coveredRevenueEuro(),
                    b.getAlertThresholdPercent(),
                    info.percent()
                );
            })
            .toList();
    }
}
