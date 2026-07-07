package org.tb.budget.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.tb.auth.domain.Authorized;
import org.tb.budget.domain.BudgetControllingResult;
import org.tb.budget.domain.BudgetControllingRow;
import org.tb.budget.domain.ForecastStatus;
import org.tb.budget.domain.OrderBudget;
import org.tb.budget.domain.OrderBudgetAdjustment;
import org.tb.budget.persistence.OrderBudgetRepository;
import org.tb.common.util.DateUtils;
import org.tb.dailyreport.domain.TimereportDTO;
import org.tb.dailyreport.service.PublicholidayService;
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
    private final PublicholidayService publicholidayService;

    public BudgetControllingResult compute(String customerorderSign, LocalDate from, LocalDate until, boolean includeCosts) {
        var today = DateUtils.today();
        var forecastAvailable = until.getYear() < 2100;

        Set<LocalDate> holidays = forecastAvailable
            ? publicholidayService.getPublicHolidaysBetween(from, until).stream()
                .map(h -> h.getRefdate()).collect(Collectors.toSet())
            : Set.of();

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
        var totalCoveredRevenue = BigDecimal.ZERO;
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
            var suborderBudgets = budgets.stream()
                .filter(b -> suborder.getSign().equals(b.getSuborderSign()))
                .toList();
            var budget = computeEffectiveBudget(suborderBudgets, from, until);
            var coveredRevenue = computeCoveredRevenue(suborderBudgets,
                (start, end) -> computeRevenue(
                    reports.stream()
                        .filter(r -> !r.getReferenceday().isBefore(start) && !r.getReferenceday().isAfter(end))
                        .toList(),
                    customerorderSign, suborder.getSign()),
                from, until);

            Duration forecastHours = null;
            BigDecimal forecastRevenue = null;
            if (forecastAvailable) {
                var fc = forecast(booked, revenue, customerorderSign, suborder.getSign(), from, until, today, holidays);
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
                true, planned, booked, budget, revenue, coveredRevenue, cost,
                forecastHours, forecastRevenue,
                forecastStatus(forecastRevenue, budget)));

            totalBooked = totalBooked.plus(booked);
            totalPlanned = totalPlanned.plus(planned);
            totalRevenue = totalRevenue.add(revenue);
            totalCoveredRevenue = totalCoveredRevenue.add(coveredRevenue);
            if (includeCosts) totalCost = totalCost.add(cost);
            totalBudget = totalBudget.add(budget);
        }

        var orderLevelBudgets = budgets.stream()
            .filter(b -> b.getSuborderSign() == null || b.getSuborderSign().isBlank())
            .toList();
        var orderLevelBudget = computeEffectiveBudget(orderLevelBudgets, from, until);
        var orderLevelCoveredRevenue = computeCoveredRevenue(orderLevelBudgets,
            (start, end) -> suborders.stream()
                .map(so -> computeRevenue(
                    bySuborder.getOrDefault(so.getId(), List.of()).stream()
                        .filter(r -> !r.getReferenceday().isBefore(start) && !r.getReferenceday().isAfter(end))
                        .toList(),
                    customerorderSign, so.getSign()))
                .reduce(BigDecimal.ZERO, BigDecimal::add),
            from, until);
        totalBudget = totalBudget.add(orderLevelBudget);
        totalCoveredRevenue = totalCoveredRevenue.add(orderLevelCoveredRevenue);

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
            totalCoveredRevenue,
            includeCosts ? totalCost : null,
            totalForecastHoursFinal,
            totalForecastRevenueFinal,
            forecastStatus(totalForecastRevenueFinal, totalBudget));

        return new BudgetControllingResult(totalRow, suborderRows, forecastAvailable);
    }

    private record ForecastData(Duration hours, BigDecimal revenue) {}

    private ForecastData forecast(Duration booked, BigDecimal currentRevenue,
                                  String coSign, String soSign,
                                  LocalDate from, LocalDate until, LocalDate today,
                                  Set<LocalDate> holidays) {
        var elapsedEnd = today.isBefore(until) ? today : until;
        var elapsed = workingDays(from, elapsedEnd, holidays);
        if (elapsed <= 0) return new ForecastData(Duration.ZERO, currentRevenue);

        var remaining = today.isBefore(until) ? workingDays(today, until, holidays) : 0;
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

    private long workingDays(LocalDate from, LocalDate until, Set<LocalDate> holidays) {
        long count = 0;
        for (var d = from; d.isBefore(until); d = d.plusDays(1)) {
            var dow = d.getDayOfWeek();
            if (dow != DayOfWeek.SATURDAY && dow != DayOfWeek.SUNDAY && !holidays.contains(d)) {
                count++;
            }
        }
        return count;
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

    private BigDecimal computeEffectiveBudget(List<OrderBudget> budgets, LocalDate from, LocalDate until) {
        var total = BigDecimal.ZERO;
        for (var budget : budgets) {
            if (!Boolean.TRUE.equals(budget.getActive())) continue;
            if (budget.getValidUntil().isBefore(from)) continue;
            if (budget.getValidFrom().isAfter(until)) continue;

            var adjFrom = budget.getValidFrom().isAfter(from) ? budget.getValidFrom() : from;
            var adjUntil = budget.getValidUntil().isBefore(until) ? budget.getValidUntil() : until;
            total = total.add(sumBudget(budget.getAdjustments().stream()
                .filter(a -> !a.getEffective().isBefore(adjFrom) && !a.getEffective().isAfter(adjUntil))
                .toList()));
        }
        return total;
    }

    private BigDecimal computeCoveredRevenue(List<OrderBudget> budgets,
                                              BiFunction<LocalDate, LocalDate, BigDecimal> revenueInRange,
                                              LocalDate from, LocalDate until) {
        return budgets.stream()
            .filter(b -> Boolean.TRUE.equals(b.getActive()))
            .filter(b -> !b.getValidUntil().isBefore(from) && !b.getValidFrom().isAfter(until))
            .map(b -> {
                var coverStart = b.getValidFrom().isAfter(from) ? b.getValidFrom() : from;
                var coverEnd = b.getValidUntil().isBefore(until) ? b.getValidUntil() : until;
                return revenueInRange.apply(coverStart, coverEnd);
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
