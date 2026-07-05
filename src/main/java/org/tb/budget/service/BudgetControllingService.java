package org.tb.budget.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.tb.auth.domain.Authorized;
import org.tb.budget.domain.BudgetControllingResult;
import org.tb.budget.domain.BudgetControllingRow;
import org.tb.budget.domain.OrderBudgetAdjustment;
import org.tb.budget.persistence.OrderBudgetRepository;
import org.tb.budget.persistence.OrderPricingRepository;
import org.tb.dailyreport.domain.TimereportDTO;
import org.tb.dailyreport.service.TimereportService;
import org.tb.order.domain.Customerorder;
import org.tb.order.domain.Suborder;
import org.tb.order.service.CustomerorderService;
import org.tb.order.service.SuborderService;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
@Authorized
public class BudgetControllingService {

    private final CustomerorderService customerorderService;
    private final SuborderService suborderService;
    private final TimereportService timereportService;
    private final OrderBudgetRepository orderBudgetRepository;
    private final OrderPricingService orderPricingService;
    private final EmployeeCostService employeeCostService;

    public BudgetControllingResult compute(String customerorderSign, LocalDate from, LocalDate until, boolean includeCosts) {
        var customerorder = customerorderService.getCustomerorderBySign(customerorderSign);
        var suborders = suborderService.getSubordersByCustomerorderId(customerorder.getId());
        var timereports = timereportService.getTimereportsByDatesAndCustomerOrderId(from, until, customerorder.getId());
        var budgets = orderBudgetRepository.findByCustomerorderSign(customerorderSign);

        // map suborderId -> Suborder for O(1) lookup
        Map<Long, Suborder> suborderById = suborders.stream()
            .collect(Collectors.toMap(Suborder::getId, s -> s));

        // group timereports by suborderId
        Map<Long, List<TimereportDTO>> bySuborder = timereports.stream()
            .collect(Collectors.groupingBy(TimereportDTO::getSuborderId));

        var suborderRows = new ArrayList<BudgetControllingRow>();
        var totalBooked = Duration.ZERO;
        var totalPlanned = Duration.ZERO;
        var totalRevenue = BigDecimal.ZERO;
        var totalCost = BigDecimal.ZERO;
        var totalBudget = BigDecimal.ZERO;

        for (var suborder : suborders) {
            var reports = bySuborder.getOrDefault(suborder.getId(), List.of());
            var booked = sumDuration(reports);
            var revenue = computeRevenue(reports, customerorderSign, suborder.getSign());
            var cost = includeCosts ? computeCost(reports, suborder.getSign()) : null;
            var budget = sumBudget(budgets.stream()
                .filter(b -> suborder.getSign().equals(b.getSuborderSign()))
                .filter(b -> Boolean.TRUE.equals(b.getActive()))
                .flatMap(b -> b.getAdjustments().stream())
                .filter(a -> !a.getEffective().isBefore(from) && !a.getEffective().isAfter(until))
                .toList());

            var planned = suborder.getDebithours() != null ? suborder.getDebithours() : Duration.ZERO;
            suborderRows.add(new BudgetControllingRow(
                suborder.getCompleteOrderSign(),
                suborder.getShortdescription(),
                true, planned, booked, budget, revenue, cost));

            totalBooked = totalBooked.plus(booked);
            totalPlanned = totalPlanned.plus(planned);
            totalRevenue = totalRevenue.add(revenue);
            if (includeCosts) totalCost = totalCost.add(cost);
            totalBudget = totalBudget.add(budget);
        }

        // add budget adjustments scoped to the order itself (no suborder)
        var orderLevelBudget = sumBudget(budgets.stream()
            .filter(b -> b.getSuborderSign() == null)
            .filter(b -> Boolean.TRUE.equals(b.getActive()))
            .flatMap(b -> b.getAdjustments().stream())
            .filter(a -> !a.getEffective().isBefore(from) && !a.getEffective().isAfter(until))
            .toList());
        totalBudget = totalBudget.add(orderLevelBudget);

        var coPlanned = customerorder.getDebithours() != null ? customerorder.getDebithours() : Duration.ZERO;
        var totalRow = new BudgetControllingRow(
            customerorderSign,
            customerorder.getShortdescription(),
            false,
            coPlanned,
            totalBooked,
            totalBudget,
            totalRevenue,
            includeCosts ? totalCost : null);

        return new BudgetControllingResult(totalRow, suborderRows);
    }

    private Duration sumDuration(List<TimereportDTO> reports) {
        return reports.stream()
            .map(TimereportDTO::getDuration)
            .reduce(Duration.ZERO, Duration::plus);
    }

    private BigDecimal computeRevenue(List<TimereportDTO> reports, String coSign, String soSign) {
        return reports.stream()
            .map(r -> {
                var hours = minutesToHours(r.getDuration().toMinutes());
                return orderPricingService
                    .findEffectiveRate(coSign, soSign, r.getEmployeeSign(), r.getReferenceday())
                    .map(p -> hours.multiply(new BigDecimal(p.getPriceCentsPerHour())).movePointLeft(2))
                    .orElse(BigDecimal.ZERO);
            })
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    @Authorized(requiresManager = true)
    BigDecimal computeCost(List<TimereportDTO> reports, String soSign) {
        return reports.stream()
            .map(r -> {
                var hours = minutesToHours(r.getDuration().toMinutes());
                return employeeCostService
                    .findEffectiveCost(r.getEmployeeSign(), soSign, r.getReferenceday())
                    .map(c -> hours.multiply(new BigDecimal(c.getCostCentsPerHour())).movePointLeft(2))
                    .orElse(BigDecimal.ZERO);
            })
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal sumBudget(List<OrderBudgetAdjustment> adjustments) {
        return adjustments.stream()
            .map(OrderBudgetAdjustment::getAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static BigDecimal minutesToHours(long minutes) {
        return BigDecimal.valueOf(minutes).divide(BigDecimal.valueOf(60), 6, RoundingMode.HALF_UP);
    }
}
