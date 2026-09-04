package org.tb.budget.service;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
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

    /**
     * @param customerSegmentId    only plans of orders whose customer belongs to this segment,
     *                             {@code null} for all
     * @param responsibleEmployeeId only plans of orders this employee is responsible for,
     *                             {@code null} for all
     */
    public List<BudgetDashboardRow> computeDashboard(Long customerSegmentId, Long responsibleEmployeeId) {
        // Filtered before the utilizations are computed — otherwise the dashboard would price
        // orders the user is not allowed to see, only to drop the rows afterwards.
        var budgets = orderBudgetService.getAllActiveVisible(
            restrictionFor(customerSegmentId, responsibleEmployeeId));
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

    /**
     * The customer order signs the filters agree on, or {@code null} when neither is set — that is
     * what tells {@code getAllActiveVisible} to apply no restriction at all. Two set filters
     * intersect: the order has to belong to the segment <em>and</em> have that responsible. An empty
     * result is a legitimate answer ("no order matches") and must stay distinguishable from
     * {@code null}.
     */
    private Collection<String> restrictionFor(Long customerSegmentId, Long responsibleEmployeeId) {
        if (customerSegmentId == null && responsibleEmployeeId == null) {
            return null;
        }
        Set<String> signs = null;
        if (customerSegmentId != null) {
            signs = new LinkedHashSet<>(customerorderService.getSignsByCustomerSegmentId(customerSegmentId));
        }
        if (responsibleEmployeeId != null) {
            var responsibleSigns = customerorderService.getSignsByResponsibleEmployeeId(responsibleEmployeeId);
            if (signs == null) {
                signs = new LinkedHashSet<>(responsibleSigns);
            } else {
                signs.retainAll(Set.copyOf(responsibleSigns));
            }
        }
        return signs;
    }
}
