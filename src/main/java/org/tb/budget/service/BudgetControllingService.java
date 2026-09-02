package org.tb.budget.service;

import static java.util.Comparator.naturalOrder;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.tb.auth.domain.Authorized;
import org.tb.budget.domain.BudgetControllingResult;
import org.tb.budget.domain.BudgetControllingRow;
import org.tb.budget.domain.EmployeeCostLookup;
import org.tb.budget.domain.ForecastStatus;
import org.tb.budget.domain.OrderBudget;
import org.tb.budget.domain.OrderBudgetAdjustment;
import org.tb.budget.domain.OrderBudgetScopeEntry;
import org.tb.budget.domain.OrderPricingLookup;
import org.tb.budget.domain.ProgressMode;
import org.tb.budget.domain.ProgressStatus;
import org.tb.budget.persistence.OrderBudgetRepository;
import org.tb.common.util.DateUtils;
import org.tb.common.LocalDateRange;
import org.tb.dailyreport.domain.TimereportDTO;
import org.tb.dailyreport.service.PublicholidayService;
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
    private final PublicholidayService publicholidayService;

    public BudgetControllingResult compute(String customerorderSign, LocalDate from, LocalDate until, boolean includeCosts) {
        var today = DateUtils.today();
        var forecastAvailable = until.getYear() < 2100;

        Set<LocalDate> holidays = publicholidayService.getPublicHolidaysBetween(from, until).stream()
            .map(h -> h.getRefdate()).collect(Collectors.toSet());

        var customerorder = customerorderService.getCustomerorderBySign(customerorderSign);
        var suborders = suborderService.getSubordersByCustomerorderId(customerorder.getId());
        var timereports = timereportService.getTimereportsByDatesAndCustomerOrderId(from, until, customerorder.getId());
        var budgets = orderBudgetRepository.findByCustomerorderSign(customerorderSign);

        // Rates and costs are resolved once per time report. Loading both tables up front keeps
        // that in memory instead of issuing up to five statements per report.
        var pricingLookup = orderPricingService.lookupFor(List.of(customerorderSign));
        var costLookup = includeCosts ? employeeCostService.lookup() : null;

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
            // Budgets, prices and costs are keyed by the complete order sign. Resolving it walks the
            // lazily fetched parent chain, so do that once per suborder instead of per budget below.
            var soSign = suborder.getCompleteOrderSign();
            var invoiceable = suborder.isInvoiceable();
            var reports = bySuborder.getOrDefault(suborder.getId(), List.of());
            var booked = sumDuration(reports);
            var revenue = computeRevenue(reports, customerorderSign, soSign, invoiceable, pricingLookup);
            // Costs accrue whether or not the work is billed, so they are not gated on invoiceable.
            var cost = includeCosts ? computeCost(reports, soSign, costLookup) : null;
            var suborderBudgets = budgets.stream()
                .filter(b -> soSign.equals(b.getSuborderSign()))
                .toList();
            var hasOwnBudget = suborderBudgets.stream().anyMatch(b -> Boolean.TRUE.equals(b.getActive()));
            // An own plan only takes the suborder out of the order-level coverage for the periods it
            // actually covers; the order level still covers the gaps. Both lists together express
            // that, because the coverage periods are merged rather than summed. Own plans come
            // first so that the progress below keeps picking the suborder's own plan.
            var effectiveBudgets = Stream.concat(suborderBudgets.stream(), orderLevelBudgets.stream()).toList();
            var budget = hasOwnBudget ? computeEffectiveBudget(suborderBudgets, from, until) : null;
            BiFunction<LocalDate, LocalDate, BigDecimal> revenueInRange =
                (start, end) -> computeRevenue(inRange(reports, start, end),
                    customerorderSign, soSign, invoiceable, pricingLookup);
            var coveredRevenue = computeCoveredRevenue(effectiveBudgets, revenueInRange, from, until);
            // What the row's own budget covers. The rest of coveredRevenue falls into the gaps that
            // the order-level plan closes, and must not be charged against the suborder's budget.
            var budgetCoveredRevenue = hasOwnBudget
                ? computeCoveredRevenue(suborderBudgets, revenueInRange, from, until)
                : coveredRevenue;

            Duration forecastHours = null;
            BigDecimal forecastRevenue = null;
            BigDecimal forecastUncoveredRevenue = null;
            if (forecastAvailable) {
                var fc = forecast(booked, coveredRevenue, customerorderSign, soSign, invoiceable, from, until, today, holidays, effectiveBudgets, pricingLookup);
                forecastHours = fc.hours();
                forecastRevenue = fc.coveredRevenue();
                forecastUncoveredRevenue = fc.uncoveredRevenue();
                if (forecastRevenue == null) totalForecastKnown = false;
                else totalForecastRevenue = totalForecastRevenue.add(forecastRevenue);
                if (forecastUncoveredRevenue != null) totalForecastUncoveredRevenue = totalForecastUncoveredRevenue.add(forecastUncoveredRevenue);
                if (forecastHours != null) totalForecastHours = totalForecastHours.plus(forecastHours);
            }

            var planned = suborder.getDebithours() != null ? suborder.getDebithours() : Duration.ZERO;
            var activeSuborderBudget = effectiveBudgets.stream()
                .filter(b -> Boolean.TRUE.equals(b.getActive())).findFirst().orElse(null);
            var progressPercent = computeProgress(activeSuborderBudget, from, until, today, holidays);
            var budgetUsedPct = (budgetCoveredRevenue != null && budget != null && budget.signum() != 0)
                ? budgetCoveredRevenue.divide(budget, 6, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100)).doubleValue()
                : null;
            var progressStatus = computeProgressStatus(progressPercent, budgetUsedPct);
            var row = new BudgetControllingRow(
                soSign,
                suborder.getShortdescription(),
                true, planned, booked, budget, revenue, coveredRevenue, budgetCoveredRevenue, cost,
                forecastHours, forecastRevenue, forecastUncoveredRevenue,
                forecastStatus(forecastRevenue, budget, revenue.subtract(coveredRevenue), forecastUncoveredRevenue),
                progressPercent, progressStatus);
            // A suborder without booked time, budget and planned hours contributes nothing but a row
            // of dashes. It is still added to the totals below, where it counts as zero anyway.
            if (row.hasContent()) {
                suborderRows.add(row);
            }

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

        var activeOrderBudget = orderLevelBudgets.stream()
            .filter(b -> Boolean.TRUE.equals(b.getActive())).findFirst().orElse(null);
        var totalProgressPercent = computeProgress(activeOrderBudget, from, until, today, holidays);
        var totalBudgetUsedPct = (totalBudget != null && totalBudget.signum() != 0)
            ? totalCoveredRevenue.divide(totalBudget, 6, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100)).doubleValue()
            : null;
        var totalProgressStatus = computeProgressStatus(totalProgressPercent, totalBudgetUsedPct);

        var totalRow = new BudgetControllingRow(
            customerorderSign,
            customerorder.getShortdescription(),
            false,
            coPlanned,
            totalBooked,
            totalBudget,
            totalRevenue,
            totalCoveredRevenue,
            // The total budget is the sum of all plans, so all covered revenue counts against it.
            totalCoveredRevenue,
            includeCosts ? totalCost : null,
            totalForecastHoursFinal,
            totalForecastRevenueFinal,
            totalForecastUncoveredFinal,
            forecastStatus(totalForecastRevenueFinal, totalBudget, totalRevenue.subtract(totalCoveredRevenue), totalForecastUncoveredFinal),
            totalProgressPercent, totalProgressStatus);

        return new BudgetControllingResult(totalRow, suborderRows, forecastAvailable);
    }

    public record UtilizationInfo(BigDecimal budgetEuro, BigDecimal coveredRevenueEuro) {
        public double percent() {
            if (budgetEuro == null || budgetEuro.signum() == 0) return 0.0;
            return coveredRevenueEuro.divide(budgetEuro, 6, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100)).doubleValue();
        }
    }

    /** Utilization of a budget plus the short description of its customer order. */
    public record BudgetUtilization(UtilizationInfo info, String customerorderDescription) {}

    public UtilizationInfo computeUtilizationInfo(OrderBudget budget) {
        return computeUtilizationInfo(budget,
            loadOrderData(budget.getCustomerorderSign(), List.of(budget)),
            orderPricingService.lookupFor(List.of(budget.getCustomerorderSign())));
    }

    /**
     * Utilization for several budgets at once. Budgets on the same customer order share one set of
     * customer order, suborder and time report queries, and all of them share one pricing lookup —
     * resolving each budget on its own multiplied every one of those by the number of budgets.
     */
    public Map<Long, BudgetUtilization> computeUtilizationInfos(List<OrderBudget> budgets) {
        var pricingLookup = orderPricingService.lookupFor(
            budgets.stream().map(OrderBudget::getCustomerorderSign).distinct().toList());
        Map<String, OrderData> orderDataBySign = new HashMap<>();
        Map<Long, BudgetUtilization> result = new LinkedHashMap<>();
        for (var budget : budgets) {
            var orderData = orderDataBySign.computeIfAbsent(budget.getCustomerorderSign(),
                sign -> loadOrderData(sign, budgets));
            result.put(budget.getId(), new BudgetUtilization(
                computeUtilizationInfo(budget, orderData, pricingLookup),
                orderData.customerorder().getShortdescription()));
        }
        return result;
    }

    /**
     * Customer order, its visible suborders and its time reports grouped by suborder. The complete
     * order signs are resolved once here because every one of them walks the lazily fetched parent
     * chain, and the same order data is reused for all budgets of that customer order.
     */
    private record OrderData(Customerorder customerorder, List<Suborder> suborders,
                             Map<Long, String> completeSignBySuborderId,
                             Map<Long, List<TimereportDTO>> reportsBySuborder,
                             List<OrderBudget> budgets) {}

    /**
     * Loads the data of one customer order over the union of the validity ranges of all its budgets.
     * Every budget filters the reports down to its own range again, so the wider range does not
     * change any result.
     */
    private OrderData loadOrderData(String customerorderSign, List<OrderBudget> budgets) {
        var ownBudgets = budgets.stream()
            .filter(b -> customerorderSign.equals(b.getCustomerorderSign()))
            .toList();
        var from = ownBudgets.stream().map(OrderBudget::getValidFrom).min(naturalOrder()).orElseThrow();
        var until = ownBudgets.stream().map(OrderBudget::getValidUntil).max(naturalOrder()).orElseThrow();

        var customerorder = customerorderService.getCustomerorderBySign(customerorderSign);
        var suborders = suborderService.getSubordersByCustomerorderId(customerorder.getId());
        var timereports = timereportService.getTimereportsByDatesAndCustomerOrderId(from, until, customerorder.getId());
        return new OrderData(customerorder, suborders,
            suborders.stream().collect(Collectors.toMap(Suborder::getId, Suborder::getCompleteOrderSign)),
            timereports.stream().collect(Collectors.groupingBy(TimereportDTO::getSuborderId)),
            ownBudgets);
    }

    private UtilizationInfo computeUtilizationInfo(OrderBudget budget, OrderData orderData,
                                                   OrderPricingLookup pricingLookup) {
        var from = budget.getValidFrom();
        var until = budget.getValidUntil();
        var coSign = budget.getCustomerorderSign();
        var soSign = budget.getSuborderSign();

        var effectiveBudget = computeEffectiveBudget(List.of(budget), from, until);

        BigDecimal coveredRevenue;
        if (soSign == null || soSign.isBlank()) {
            var planPeriods = coveredPeriods(List.of(budget), from, until);
            coveredRevenue = BigDecimal.ZERO;
            for (var so : orderData.suborders()) {
                var reports = orderData.reportsBySuborder().getOrDefault(so.getId(), List.of());
                var soCompleteSign = orderData.completeSignBySuborderId().get(so.getId());
                var invoiceable = so.isInvoiceable();
                // Where the suborder has a plan of its own, the revenue belongs to that plan's row,
                // not to this one — otherwise the same euros appear in two rows of the dashboard.
                var ownPeriods = coveredPeriods(orderData.budgets().stream()
                    .filter(b -> soCompleteSign.equals(b.getSuborderSign()))
                    .toList(), from, until);
                coveredRevenue = coveredRevenue.add(sumOver(without(planPeriods, ownPeriods),
                    (start, end) -> computeRevenue(inRange(reports, start, end), coSign, soCompleteSign,
                        invoiceable, pricingLookup)));
            }
        } else {
            var suborder = orderData.suborders().stream()
                .filter(so -> soSign.equals(orderData.completeSignBySuborderId().get(so.getId())))
                .findFirst().orElse(null);
            var reports = suborder == null ? List.<TimereportDTO>of()
                : orderData.reportsBySuborder().getOrDefault(suborder.getId(), List.of());
            // An unresolvable sign earns nothing anyway, so treating it as not invoiceable is right.
            var invoiceable = suborder != null && suborder.isInvoiceable();
            coveredRevenue = computeCoveredRevenue(List.of(budget),
                (start, end) -> computeRevenue(inRange(reports, start, end), coSign, soSign,
                    invoiceable, pricingLookup),
                from, until);
        }

        return new UtilizationInfo(effectiveBudget, coveredRevenue);
    }

    private record ForecastData(Duration hours, BigDecimal coveredRevenue, BigDecimal uncoveredRevenue) {}

    private ForecastData forecast(Duration booked, BigDecimal coveredRevenue,
                                  String coSign, String soSign, boolean invoiceable,
                                  LocalDate from, LocalDate until, LocalDate today,
                                  Set<LocalDate> holidays, List<OrderBudget> effectiveBudgets,
                                  OrderPricingLookup pricingLookup) {
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

        // Hours keep being projected, but they will never be billed — a definite zero, not "unknown".
        if (!invoiceable) {
            return new ForecastData(forecastHours, BigDecimal.ZERO, null);
        }

        var effectiveRate = pricingLookup.findEffectiveRate(coSign, soSign, null, today);
        if (effectiveRate.isEmpty()) return new ForecastData(forecastHours, null, null);

        var revenuePerDay = minutesToHours((long) burnMinutesPerDay)
            .multiply(new BigDecimal(effectiveRate.get().getPriceCentsPerHour()).movePointLeft(2));
        var forecastCovered = coveredRevenue.add(revenuePerDay.multiply(BigDecimal.valueOf(remainingCovered)));
        var forecastUncovered = revenuePerDay.multiply(BigDecimal.valueOf(remainingUncovered));
        return new ForecastData(forecastHours, forecastCovered, forecastUncovered.signum() > 0 ? forecastUncovered : null);
    }

    private Double computeProgress(OrderBudget budget, LocalDate from, LocalDate until,
                                    LocalDate today, Set<LocalDate> holidays) {
        if (budget == null || budget.getProgressMode() == null) return null;
        if (budget.getProgressMode() == ProgressMode.TIME) {
            return computeTimeProgress(from, until, today, holidays);
        }
        return computeScopeProgress(budget, today);
    }

    private Double computeTimeProgress(LocalDate from, LocalDate until, LocalDate today, Set<LocalDate> holidays) {
        var effectiveUntil = today.isBefore(until) ? today : until;
        var total = workingDays(from, until, holidays);
        if (total <= 0) return null;
        var elapsed = workingDays(from, effectiveUntil, holidays);
        return 100.0 * elapsed / total;
    }

    private Double computeScopeProgress(OrderBudget budget, LocalDate today) {
        return budget.getScopeEntries().stream()
            .filter(e -> !e.getRefdate().isAfter(today))
            .max(Comparator.comparing(OrderBudgetScopeEntry::getRefdate))
            .map(e -> (double) e.getPercent())
            .orElse(null);
    }

    private ProgressStatus computeProgressStatus(Double progressPercent, Double budgetUsedPercent) {
        if (progressPercent == null || budgetUsedPercent == null) return ProgressStatus.UNKNOWN;
        var diff = progressPercent - budgetUsedPercent;
        if (diff >= 10.0) return ProgressStatus.AHEAD;
        if (diff <= -10.0) return ProgressStatus.BEHIND;
        return ProgressStatus.ON_TRACK;
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

    private static List<TimereportDTO> inRange(List<TimereportDTO> reports, LocalDate start, LocalDate end) {
        return reports.stream()
            .filter(r -> !r.getReferenceday().isBefore(start) && !r.getReferenceday().isAfter(end))
            .toList();
    }

    /**
     * Revenue of the given time reports. Work on a suborder that is not invoiceable is not billed to
     * the customer, so it earns nothing no matter which rate would match — hence the flag is a
     * parameter rather than a check at the call sites, so that no revenue path can forget it.
     */
    private BigDecimal computeRevenue(List<TimereportDTO> reports, String coSign, String soSign,
                                      boolean invoiceable, OrderPricingLookup pricingLookup) {
        if (!invoiceable) {
            return BigDecimal.ZERO;
        }
        return reports.stream()
            .map(r -> {
                var hours = minutesToHours(r.getDuration().toMinutes());
                return pricingLookup
                    .findEffectiveRate(coSign, soSign, r.getEmployeeSign(), r.getReferenceday())
                    .map(p -> hours.multiply(new BigDecimal(p.getPriceCentsPerHour())).movePointLeft(2))
                    .orElse(BigDecimal.ZERO);
            })
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    @Authorized(requiresManager = true)
    BigDecimal computeCost(List<TimereportDTO> reports, String soSign, EmployeeCostLookup costLookup) {
        return reports.stream()
            .map(r -> {
                var hours = minutesToHours(r.getDuration().toMinutes());
                return costLookup
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

    /**
     * Revenue covered by any of the given plans. The plans' validity periods are merged first: a
     * booking covered by two overlapping plans belongs to the covered revenue once, not twice.
     * Summing per plan inflated an order's utilization to 145.9 % where it was really 85.6 % (#903).
     */
    private BigDecimal computeCoveredRevenue(List<OrderBudget> budgets,
                                              BiFunction<LocalDate, LocalDate, BigDecimal> revenueInRange,
                                              LocalDate from, LocalDate until) {
        return sumOver(coveredPeriods(budgets, from, until), revenueInRange);
    }

    /** The periods the given plans cover within {@code [from, until]}, overlaps merged away. */
    private static List<LocalDateRange> coveredPeriods(List<OrderBudget> budgets, LocalDate from, LocalDate until) {
        return merge(budgets.stream()
            .filter(b -> Boolean.TRUE.equals(b.getActive()))
            .filter(b -> !b.getValidUntil().isBefore(from) && !b.getValidFrom().isAfter(until))
            .map(b -> new LocalDateRange(
                b.getValidFrom().isAfter(from) ? b.getValidFrom() : from,
                b.getValidUntil().isBefore(until) ? b.getValidUntil() : until))
            .toList());
    }

    private static BigDecimal sumOver(List<LocalDateRange> periods,
                                      BiFunction<LocalDate, LocalDate, BigDecimal> revenueInRange) {
        return periods.stream()
            .map(p -> revenueInRange.apply(p.getFrom(), p.getUntil()))
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /** The parts of {@code periods} not covered by any of {@code holes}. */
    private static List<LocalDateRange> without(List<LocalDateRange> periods, List<LocalDateRange> holes) {
        var remaining = periods;
        for (var hole : holes) {
            remaining = remaining.stream().flatMap(p -> p.minus(hole).stream()).toList();
        }
        return remaining;
    }

    /**
     * Merges overlapping and directly adjacent periods into single periods, so that revenue summed
     * per period counts every booking exactly once.
     */
    private static List<LocalDateRange> merge(List<LocalDateRange> periods) {
        var merged = new ArrayList<LocalDateRange>();
        periods.stream().sorted().forEach(period -> {
            var last = merged.isEmpty() ? null : merged.get(merged.size() - 1);
            if (last != null && last.isConnected(period)) {
                merged.set(merged.size() - 1, last.plus(period));
            } else {
                merged.add(period);
            }
        });
        return merged;
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
