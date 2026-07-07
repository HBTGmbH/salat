package org.tb.budget.service;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.tb.auth.domain.Authorized;
import org.tb.budget.domain.BudgetDashboardRow;
import org.tb.order.service.CustomerorderService;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
@Authorized
public class BudgetDashboardService {

    private final OrderBudgetService orderBudgetService;
    private final BudgetControllingService budgetControllingService;
    private final CustomerorderService customerorderService;

    public List<BudgetDashboardRow> computeDashboard() {
        return orderBudgetService.getAll().stream()
            .filter(b -> Boolean.TRUE.equals(b.getActive()))
            .map(b -> {
                var info = budgetControllingService.computeUtilizationInfo(b);
                var co = customerorderService.getCustomerorderBySign(b.getCustomerorderSign());
                return new BudgetDashboardRow(
                    b.getId(),
                    b.getName(),
                    b.getCustomerorderSign(),
                    co.getShortdescription(),
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
