package org.tb.budget.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
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
import org.tb.budget.domain.ForecastStatus;
import org.tb.budget.domain.OrderBudgetAdjustment;
import org.tb.budget.persistence.OrderBudgetRepository;
import org.tb.dailyreport.domain.TimereportDTO;
import org.tb.dailyreport.service.TimereportService;
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
        var today = LocalDate.now();
        var forecastAvailable = until.getYear() < 2100;

        var customerorder = customerorderService.getCustomerorderBySign(customerorderSign);
        var suborders = suborderService.getSubordersByCustomerorderId(customerorder.getId());
        var timereports = timereportService.getTimereportsByDatesAndCustomerOrderId(from, until, customerorder.getId());
        var budgets = orderBudgetRepository.findByCustomerorderSign(customerorderSign);

        Map<Long, List<TimereportDTO>> bySuborder = timereports.stream()
            .collect(Collectors.groupingBy(TimereportDTO::getSuborderId));

        var suborderRows = new ArrayList<BudgetControllingRow>();
        var totalBooked = Duration.ZERO;
        var totalPlanned = Duration.ZERO;
        var totalRevenue = BigDecimal.ZERO;
        var totalCost = BigDecimal.ZERO;
        var totalBudget = BigDecimal.ZERO;
        var totalForecastRevenue = BigDecimal.ZERO;
        var totalForecastHours = Duration.ZERO;
        var totalForecastKnown = true;

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

            Duration forecastHours = null;
            BigDecimal forecastRevenue = null;
            if (forecastAvailable) {
                var fc = forecast(booked, revenue, customerorderSign, suborder.getSign(), from, until, today);
                forecastHours = fc.hours();
                forecastRevenue = fc.revenue();
                if (forecastRevenue == null) totalForecastKnown = false;
                else totalForecastRevenue = totalForecastRevenue.add(forecastRevenue);
                if (forecastHours != null) totalForecastHours = totalForecastHours.plus(forecastHours);
            }

            var planned = suborder.getDebithours() != null ? suborder.getDebithours() : Duration.ZERO;
            suborderRows.add(new BudgetControllingRow(
                suborder.getCompleteOrderSign(),
                suborder.getShortdescription(),
                true, planned, booked, budget, revenue, cost,
                forecastHours, forecastRevenue,
                forecastStatus(forecastRevenue, budget)));

            totalBooked = totalBooked.plus(booked);
            totalPlanned = totalPlanned.plus(planned);
            totalRevenue = totalRevenue.add(revenue);
            if (includeCosts) totalCost = totalCost.add(cost);
            totalBudget = totalBudget.add(budget);
        }

        var orderLevelBudget = sumBudget(budgets.stream()
            .filter(b -> b.getSuborderSign() == null || b.getSuborderSign().isBlank())
            .filter(b -> Boolean.TRUE.equals(b.getActive()))
            .flatMap(b -> b.getAdjustments().stream())
            .filter(a -> !a.getEffective().isBefore(from) && !a.getEffective().isAfter(until))
            .toList());
        totalBudget = totalBudget.add(orderLevelBudget);

        var coPlanned = customerorder.getDebithours() != null ? customerorder.getDebithours() : Duration.ZERO;
        var totalForecastRevenueFinal = forecastAvailable && totalForecastKnown ? totalForecastRevenue : null;
        var totalForecastHoursFinal = forecastAvailable ? totalForecastHours : null;

        var totalRow = new BudgetControllingRow(
            customerorderSign,
            customerorder.getShortdescription(),
            false,
            coPlanned,
            totalBooked,
            totalBudget,
            totalRevenue,
            includeCosts ? totalCost : null,
            totalForecastHoursFinal,
            totalForecastRevenueFinal,
            forecastStatus(totalForecastRevenueFinal, totalBudget));

        return new BudgetControllingResult(totalRow, suborderRows, forecastAvailable);
    }

    private record ForecastData(Duration hours, BigDecimal revenue) {}

    private ForecastData forecast(Duration booked, BigDecimal currentRevenue,
                                  String coSign, String soSign,
                                  LocalDate from, LocalDate until, LocalDate today) {
        var elapsed = ChronoUnit.DAYS.between(from, today.isBefore(until) ? today : until);
        if (elapsed <= 0) return new ForecastData(Duration.ZERO, currentRevenue);

        var remaining = Math.max(0, ChronoUnit.DAYS.between(today, until));
        if (remaining == 0) return new ForecastData(Duration.ZERO, currentRevenue);

        // burn rate in minutes per day
        double burnMinutesPerDay = (double) booked.toMinutes() / elapsed;
        var forecastMinutes = (long) (burnMinutesPerDay * remaining);
        var forecastHours = Duration.ofMinutes(forecastMinutes);

        var effectiveRate = orderPricingService.findEffectiveRate(coSign, soSign, null, today);
        if (effectiveRate.isEmpty()) return new ForecastData(forecastHours, null);

        var rateEuro = new BigDecimal(effectiveRate.get().getPriceCentsPerHour()).movePointLeft(2);
        var forecastRevenue = currentRevenue.add(minutesToHours(forecastMinutes).multiply(rateEuro));
        return new ForecastData(forecastHours, forecastRevenue);
    }

    private ForecastStatus forecastStatus(BigDecimal forecastRevenue, BigDecimal budget) {
        if (forecastRevenue == null || budget == null || budget.signum() == 0) return ForecastStatus.UNKNOWN;
        var pct = forecastRevenue.divide(budget, 4, RoundingMode.HALF_UP).doubleValue();
        if (pct <= 0.80) return ForecastStatus.GREEN;
        if (pct <= 1.00) return ForecastStatus.YELLOW;
        return ForecastStatus.RED;
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
