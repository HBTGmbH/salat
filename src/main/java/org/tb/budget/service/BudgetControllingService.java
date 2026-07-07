package org.tb.budget.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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

        var orderLevelBudgets = budgets.stream()
            .filter(b -> b.getSuborderSign() == null || b.getSuborderSign().isBlank())
            .toList();
        var orderLevelBudget = computeEffectiveBudget(orderLevelBudgets, from, until);

        var suborderRows = new ArrayList<BudgetControllingRow>();
        var totalBooked = Duration.ZERO;
        var totalPlanned = Duration.ZERO;
        var totalRevenue = BigDecimal.ZERO;
        var totalCoveredRevenue = BigDecimal.ZERO;
        var totalCost = BigDecimal.ZERO;
        var totalBudget = orderLevelBudget;
        var totalForecastRevenue = BigDecimal.ZERO;
        var totalForecastUncoveredRevenue = BigDecimal.ZERO;
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
            var hasOwnBudget = suborderBudgets.stream().anyMatch(b -> Boolean.TRUE.equals(b.getActive()));
            var effectiveBudgets = hasOwnBudget ? suborderBudgets : orderLevelBudgets;
            var budget = hasOwnBudget ? computeEffectiveBudget(suborderBudgets, from, until) : null;
            var coveredRevenue = computeCoveredRevenue(effectiveBudgets,
                (start, end) -> computeRevenue(
                    reports.stream()
                        .filter(r -> !r.getReferenceday().isBefore(start) && !r.getReferenceday().isAfter(end))
                        .toList(),
                    customerorderSign, suborder.getSign()),
                from, until);

            Duration forecastHours = null;
            BigDecimal forecastRevenue = null;
            BigDecimal forecastUncoveredRevenue = null;
            if (forecastAvailable) {
                var fc = forecast(booked, coveredRevenue, customerorderSign, suborder.getSign(), from, until, today, holidays, effectiveBudgets);
                forecastHours = fc.hours();
                forecastRevenue = fc.coveredRevenue();
                forecastUncoveredRevenue = fc.uncoveredRevenue();
                if (forecastRevenue == null) totalForecastKnown = false;
                else totalForecastRevenue = totalForecastRevenue.add(forecastRevenue);
                if (forecastUncoveredRevenue != null) totalForecastUncoveredRevenue = totalForecastUncoveredRevenue.add(forecastUncoveredRevenue);
                if (forecastHours != null) totalForecastHours = totalForecastHours.plus(forecastHours);
            }

            var planned = suborder.getDebithours() != null ? suborder.getDebithours() : Duration.ZERO;
            suborderRows.add(new BudgetControllingRow(
                suborder.getCompleteOrderSign(),
                suborder.getShortdescription(),
                true, planned, booked, budget, revenue, coveredRevenue, cost,
                forecastHours, forecastRevenue, forecastUncoveredRevenue,
                forecastStatus(forecastRevenue, budget, revenue.subtract(coveredRevenue), forecastUncoveredRevenue)));

            totalBooked = totalBooked.plus(booked);
            totalPlanned = totalPlanned.plus(planned);
            totalRevenue = totalRevenue.add(revenue);
            totalCoveredRevenue = totalCoveredRevenue.add(coveredRevenue);
            if (includeCosts) totalCost = totalCost.add(cost);
            if (hasOwnBudget) totalBudget = totalBudget.add(budget);
        }

        var coPlanned = customerorder.getDebithours() != null ? customerorder.getDebithours() : Duration.ZERO;
        var totalForecastRevenueFinal = forecastAvailable && totalForecastKnown ? totalForecastRevenue : null;
        var totalForecastUncoveredFinal = forecastAvailable && totalForecastUncoveredRevenue.signum() > 0 ? totalForecastUncoveredRevenue : null;
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
            totalForecastUncoveredFinal,
            forecastStatus(totalForecastRevenueFinal, totalBudget, totalRevenue.subtract(totalCoveredRevenue), totalForecastUncoveredFinal));

        return new BudgetControllingResult(totalRow, suborderRows, forecastAvailable);
    }

    public record UtilizationInfo(BigDecimal budgetEuro, BigDecimal coveredRevenueEuro) {
        public double percent() {
            if (budgetEuro == null || budgetEuro.signum() == 0) return 0.0;
            return coveredRevenueEuro.divide(budgetEuro, 6, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100)).doubleValue();
        }
    }

    public UtilizationInfo computeUtilizationInfo(OrderBudget budget) {
        var from = budget.getValidFrom();
        var until = budget.getValidUntil();
        var coSign = budget.getCustomerorderSign();
        var soSign = budget.getSuborderSign();

        var effectiveBudget = computeEffectiveBudget(List.of(budget), from, until);

        var customerorder = customerorderService.getCustomerorderBySign(coSign);
        var allTimereports = timereportService.getTimereportsByDatesAndCustomerOrderId(from, until, customerorder.getId());
        var suborders = suborderService.getSubordersByCustomerorderId(customerorder.getId());

        BigDecimal coveredRevenue;
        if (soSign == null || soSign.isBlank()) {
            Map<Long, List<TimereportDTO>> bySuborder = allTimereports.stream()
                .collect(Collectors.groupingBy(TimereportDTO::getSuborderId));
            coveredRevenue = BigDecimal.ZERO;
            for (var so : suborders) {
                var reports = bySuborder.getOrDefault(so.getId(), List.of());
                coveredRevenue = coveredRevenue.add(computeCoveredRevenue(List.of(budget),
                    (start, end) -> computeRevenue(
                        reports.stream()
                            .filter(r -> !r.getReferenceday().isBefore(start) && !r.getReferenceday().isAfter(end))
                            .toList(),
                        coSign, so.getSign()),
                    from, until));
            }
        } else {
            var soId = suborders.stream()
                .filter(s -> soSign.equals(s.getSign()))
                .map(s -> s.getId())
                .findFirst().orElse(null);
            var reports = soId == null ? List.<TimereportDTO>of() :
                allTimereports.stream().filter(r -> Objects.equals(r.getSuborderId(), soId)).toList();
            coveredRevenue = computeCoveredRevenue(List.of(budget),
                (start, end) -> computeRevenue(
                    reports.stream()
                        .filter(r -> !r.getReferenceday().isBefore(start) && !r.getReferenceday().isAfter(end))
                        .toList(),
                    coSign, soSign),
                from, until);
        }

        return new UtilizationInfo(effectiveBudget, coveredRevenue);
    }

    private record ForecastData(Duration hours, BigDecimal coveredRevenue, BigDecimal uncoveredRevenue) {}

    private ForecastData forecast(Duration booked, BigDecimal coveredRevenue,
                                  String coSign, String soSign,
                                  LocalDate from, LocalDate until, LocalDate today,
                                  Set<LocalDate> holidays, List<OrderBudget> effectiveBudgets) {
        var elapsedEnd = today.isBefore(until) ? today : until;
        var elapsed = workingDays(from, elapsedEnd, holidays);
        if (elapsed <= 0) return new ForecastData(Duration.ZERO, coveredRevenue, BigDecimal.ZERO);

        long remainingCovered = 0, remainingUncovered = 0;
        if (today.isBefore(until)) {
            for (var d = today; d.isBefore(until); d = d.plusDays(1)) {
                var dow = d.getDayOfWeek();
                if (dow == DayOfWeek.SATURDAY || dow == DayOfWeek.SUNDAY || holidays.contains(d)) continue;
                final var day = d;
                if (effectiveBudgets.stream().anyMatch(b -> Boolean.TRUE.equals(b.getActive())
                        && !day.isBefore(b.getValidFrom()) && !day.isAfter(b.getValidUntil()))) remainingCovered++;
                else remainingUncovered++;
            }
        }
        if (remainingCovered + remainingUncovered == 0) return new ForecastData(Duration.ZERO, coveredRevenue, BigDecimal.ZERO);

        double burnMinutesPerDay = (double) booked.toMinutes() / elapsed;
        var forecastMinutes = (long) (burnMinutesPerDay * (remainingCovered + remainingUncovered));
        var forecastHours = Duration.ofMinutes(forecastMinutes);

        var effectiveRate = orderPricingService.findEffectiveRate(coSign, soSign, null, today);
        if (effectiveRate.isEmpty()) return new ForecastData(forecastHours, null, null);

        var revenuePerDay = minutesToHours((long) burnMinutesPerDay)
            .multiply(new BigDecimal(effectiveRate.get().getPriceCentsPerHour()).movePointLeft(2));
        var forecastCovered = coveredRevenue.add(revenuePerDay.multiply(BigDecimal.valueOf(remainingCovered)));
        var forecastUncovered = revenuePerDay.multiply(BigDecimal.valueOf(remainingUncovered));
        return new ForecastData(forecastHours, forecastCovered, forecastUncovered.signum() > 0 ? forecastUncovered : null);
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

    private ForecastStatus forecastStatus(BigDecimal forecastRevenue, BigDecimal budget,
                                           BigDecimal uncoveredRevenue, BigDecimal forecastUncoveredRevenue) {
        if ((uncoveredRevenue != null && uncoveredRevenue.signum() > 0)
                || (forecastUncoveredRevenue != null && forecastUncoveredRevenue.signum() > 0)) return ForecastStatus.RED;
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
