package de.hbt.salat.budget.service;

import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import de.hbt.salat.auth.domain.Authorized;
import de.hbt.salat.budget.domain.BudgetDashboardRow;
import de.hbt.salat.budget.service.BudgetControllingService.UtilizationInfo;
import de.hbt.salat.order.service.CustomerorderService;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
@Authorized
public class BudgetDashboardService {

    private final OrderBudgetService orderBudgetService;
    private final BudgetControllingService budgetControllingService;
    private final CustomerorderService customerorderService;
    private final FixedPriceCalculationService fixedPriceCalculationService;

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
        // How far each plan has come, judged by the same rule the controlling evaluation uses — a
        // plan that has spent more of its budget than of its progress is behind its plan.
        var progressPercents = budgetControllingService.computeProgressPercents(budgets);
        // A fixed price is judged by the consumption of its calculated hours (#1404), with the
        // calculation the controlling and the plan's page use.
        var hoursConsumed = fixedPriceCalculationService.getHoursConsumedPercents(budgets);
        return budgets.stream()
            .map(b -> {
                var utilization = utilizations.get(b.getId());
                var info = utilization.info();
                var progressPercent = progressPercents.get(b.getId());
                var consumed = hoursConsumed.get(b.getId());
                return new BudgetDashboardRow(
                    b.getId(),
                    b.getName(),
                    b.getCustomerorderId(),
                    // the order's own sign (#1212)
                    utilization.customerorderSign(),
                    utilization.customerorderDescription(),
                    b.getValidFrom(),
                    b.getValidUntil(),
                    info.evaluatedUntil(),
                    info.budgetEuro(),
                    info.coveredRevenueEuro(),
                    b.getAlertThresholdPercent(),
                    info.percent(),
                    progressPercent,
                    // Without a budget amount there is no share of it that could be compared to the
                    // progress, so such a plan has no status either.
                    BudgetControllingService.computeProgressStatus(progressPercent,
                        b.isFixedPrice() ? consumed : (hasBudget(info) ? info.percent() : null)),
                    b.isFixedPrice(),
                    consumed
                );
            })
            // By order sign, then by start of validity — sorted here, because the plans come by
            // validity only: the sign is the order's, not a column of the plan (#1212).
            .sorted(Comparator.comparing(BudgetDashboardRow::customerorderSign,
                    Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(BudgetDashboardRow::validFrom, Comparator.nullsLast(Comparator.naturalOrder())))
            .toList();
    }

    private static boolean hasBudget(UtilizationInfo info) {
        return info.budgetEuro() != null && info.budgetEuro().signum() != 0;
    }

    /**
     * The ids of the customer orders the filters agree on, or {@code null} when neither is set — that
     * is what tells {@code getAllActiveVisible} to apply no restriction at all. Two set filters
     * intersect: the order has to belong to the segment <em>and</em> have that responsible. An empty
     * result is a legitimate answer ("no order matches") and must stay distinguishable from
     * {@code null}. By id, the way the plans refer to their order (#1340).
     */
    private Collection<Long> restrictionFor(Long customerSegmentId, Long responsibleEmployeeId) {
        if (customerSegmentId == null && responsibleEmployeeId == null) {
            return null;
        }
        Set<Long> customerorderIds = null;
        if (customerSegmentId != null) {
            customerorderIds = new LinkedHashSet<>(customerorderService.getIdsByCustomerSegmentId(customerSegmentId));
        }
        if (responsibleEmployeeId != null) {
            var responsibleOrderIds = customerorderService.getIdsByResponsibleHbtEmployeeId(responsibleEmployeeId);
            if (customerorderIds == null) {
                customerorderIds = new LinkedHashSet<>(responsibleOrderIds);
            } else {
                customerorderIds.retainAll(Set.copyOf(responsibleOrderIds));
            }
        }
        return customerorderIds;
    }
}
