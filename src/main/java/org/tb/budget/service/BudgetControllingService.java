package org.tb.budget.service;

import static java.util.Comparator.naturalOrder;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.tb.auth.domain.Authorized;
import org.tb.budget.auth.BudgetAuthorization;
import org.tb.budget.domain.BudgetControllingGroup;
import org.tb.budget.domain.BudgetControllingResult;
import org.tb.budget.domain.BudgetControllingRow;
import org.tb.budget.domain.BudgetControllingSection;
import org.tb.budget.domain.EmployeeCostLookup;
import org.tb.budget.domain.OrderBudget;
import org.tb.budget.domain.OrderBudgetAdjustment;
import org.tb.budget.domain.OrderBudgetScopeEntry;
import org.tb.budget.domain.OrderPricingLookup;
import org.tb.budget.domain.ProgressMode;
import org.tb.budget.domain.ProgressStatus;
import org.tb.budget.domain.SectionKind;
import org.tb.budget.persistence.OrderBudgetRepository;
import org.tb.common.LocalDateRange;
import org.tb.common.util.DateUtils;
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
    private final BudgetAuthorization budgetAuthorization;

    public BudgetControllingResult compute(String customerorderSign, LocalDate from, LocalDate until, boolean includeCosts) {
        budgetAuthorization.checkAuthorizedForCustomerorder(customerorderSign);
        var today = DateUtils.today();
        var filter = new LocalDateRange(from, until);

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

        // Every report is priced exactly once here. Sections then only filter and add, which matters
        // because the same report is looked at by every section it could fall into.
        var scored = scoreReports(suborders, timereports, customerorderSign, pricingLookup, costLookup);

        var coverage = assignCoverage(budgets, suborders, filter);

        var sections = new ArrayList<BudgetControllingSection>();
        for (var planned : coverage.plannedSections()) {
            sections.add(plannedSection(planned, suborders, scored, today, holidays, includeCosts));
        }
        var unplanned = unplannedSection(coverage.unplannedBySuborderId(), suborders, scored, includeCosts);
        if (unplanned != null) {
            sections.add(unplanned);
        }

        return new BudgetControllingResult(customerorderSign, customerorder.getShortdescription(), filter,
            sections.stream().filter(BudgetControllingSection::hasContent).toList());
    }

    /** A time report with its revenue and cost already resolved. */
    private record ScoredReport(LocalDate day, Duration duration, BigDecimal revenue, BigDecimal cost) {}

    private Map<Long, List<ScoredReport>> scoreReports(List<Suborder> suborders, List<TimereportDTO> timereports,
                                                       String customerorderSign, OrderPricingLookup pricingLookup,
                                                       EmployeeCostLookup costLookup) {
        Map<Long, List<TimereportDTO>> bySuborder = timereports.stream()
            .collect(Collectors.groupingBy(TimereportDTO::getSuborderId));
        Map<Long, List<ScoredReport>> scored = new HashMap<>();
        for (var suborder : suborders) {
            // Resolving the complete order sign walks the lazily fetched parent chain, so do it once.
            var soSign = suborder.getCompleteOrderSign();
            var invoiceable = suborder.isInvoiceable();
            scored.put(suborder.getId(), bySuborder.getOrDefault(suborder.getId(), List.<TimereportDTO>of()).stream()
                .map(r -> new ScoredReport(r.getReferenceday(), r.getDuration(),
                    // Work on a suborder that is not invoiceable is never billed, whatever rate matches.
                    invoiceable ? rateOf(r, customerorderSign, soSign, pricingLookup) : BigDecimal.ZERO,
                    // Costs accrue whether or not the work is billed.
                    costLookup == null ? BigDecimal.ZERO : costOf(r, soSign, costLookup)))
                .toList());
        }
        return scored;
    }

    private static BigDecimal rateOf(TimereportDTO report, String coSign, String soSign, OrderPricingLookup lookup) {
        var hours = minutesToHours(report.getDuration().toMinutes());
        return lookup.findEffectiveRate(coSign, soSign, report.getEmployeeSign(), report.getReferenceday())
            .map(p -> hours.multiply(new BigDecimal(p.getPriceCentsPerHour())).movePointLeft(2))
            .orElse(BigDecimal.ZERO);
    }

    private static BigDecimal costOf(TimereportDTO report, String soSign, EmployeeCostLookup lookup) {
        var hours = minutesToHours(report.getDuration().toMinutes());
        return lookup.findEffectiveCost(report.getEmployeeSign(), soSign, report.getReferenceday())
            .map(c -> hours.multiply(new BigDecimal(c.getCostCentsPerHour())).movePointLeft(2))
            .orElse(BigDecimal.ZERO);
    }

    /** One plan with the periods it covers per suborder. */
    private record PlanCoverage(OrderBudget plan, LocalDateRange period, boolean orderWide,
                                Map<Long, List<LocalDateRange>> periodsBySuborderId) {}

    /** Plans grouped into sections, plus what no plan covers. */
    private record Coverage(List<List<PlanCoverage>> plannedSections,
                            Map<Long, List<LocalDateRange>> unplannedBySuborderId) {}

    /**
     * Works out which plan covers which suborder when. Every {@code (suborder, day)} is claimed by at
     * most one plan: the rules forbid overlaps (#905), but legacy data may still contain them and a
     * booking must never be counted twice. Suborder plans win over order-wide ones, then the earlier
     * one, then the lower id — a fixed order so the result does not depend on query order.
     */
    private Coverage assignCoverage(List<OrderBudget> budgets, List<Suborder> suborders, LocalDateRange filter) {
        var firstLevelSignBySuborderId = suborders.stream()
            .collect(Collectors.toMap(Suborder::getId, BudgetControllingService::firstLevelSignOf));

        var candidates = budgets.stream()
            .filter(b -> Boolean.TRUE.equals(b.getActive()))
            .map(b -> new AbstractMap.SimpleEntry<>(b,
                new LocalDateRange(b.getValidFrom(), b.getValidUntil()).intersection(filter)))
            .filter(e -> e.getValue() != null && e.getValue().isValid())
            .sorted(Comparator
                .comparing((AbstractMap.SimpleEntry<OrderBudget, LocalDateRange> e) -> isOrderWide(e.getKey().getSuborderSign()))
                .thenComparing(e -> e.getKey().getValidFrom())
                .thenComparing(e -> e.getKey().getId(), Comparator.nullsLast(Comparator.naturalOrder())))
            .toList();

        Map<Long, List<LocalDateRange>> claimed = new HashMap<>();
        var covered = new ArrayList<PlanCoverage>();
        for (var candidate : candidates) {
            var plan = candidate.getKey();
            var period = candidate.getValue();
            var orderWide = isOrderWide(plan.getSuborderSign());
            Map<Long, List<LocalDateRange>> bySuborder = new LinkedHashMap<>();
            for (var suborder : suborders) {
                // A plan on a first level suborder also covers everything below it — plans only live
                // on that level, but bookings happen further down.
                if (!orderWide && !plan.getSuborderSign().equals(firstLevelSignBySuborderId.get(suborder.getId()))) {
                    continue;
                }
                var free = minusAll(List.of(period), claimed.getOrDefault(suborder.getId(), List.of()));
                if (!free.isEmpty()) {
                    bySuborder.put(suborder.getId(), free);
                    claimed.computeIfAbsent(suborder.getId(), k -> new ArrayList<>()).addAll(free);
                }
            }
            covered.add(new PlanCoverage(plan, period, orderWide, bySuborder));
        }

        // Plans of the same level and the same period share one section.
        Map<String, List<PlanCoverage>> grouped = new LinkedHashMap<>();
        for (var planCoverage : covered) {
            grouped.computeIfAbsent(planCoverage.orderWide() + "|" + planCoverage.period(), k -> new ArrayList<>())
                .add(planCoverage);
        }
        var sections = grouped.values().stream()
            .sorted(Comparator
                .comparing((List<PlanCoverage> s) -> s.get(0).period().getFrom())
                .thenComparing(s -> s.get(0).period().getUntil())
                .thenComparing(s -> !s.get(0).orderWide()))
            .toList();

        Map<Long, List<LocalDateRange>> unplanned = new LinkedHashMap<>();
        for (var suborder : suborders) {
            var gaps = minusAll(List.of(filter), claimed.getOrDefault(suborder.getId(), List.of()));
            if (!gaps.isEmpty()) {
                unplanned.put(suborder.getId(), gaps);
            }
        }
        return new Coverage(sections, unplanned);
    }

    /** The complete order sign of the suborder's first level ancestor, or its own if it is one. */
    private static String firstLevelSignOf(Suborder suborder) {
        return suborder.withParents().get(0).getCompleteOrderSign();
    }

    private static boolean isOrderWide(String suborderSign) {
        return suborderSign == null || suborderSign.isBlank();
    }

    private BudgetControllingSection plannedSection(List<PlanCoverage> plans, List<Suborder> suborders,
                                                    Map<Long, List<ScoredReport>> scored, LocalDate today,
                                                    Set<LocalDate> holidays, boolean includeCosts) {
        var orderWide = plans.get(0).orderWide();
        var period = plans.get(0).period();
        var groups = new ArrayList<BudgetControllingGroup>();

        for (var planCoverage : plans) {
            var plan = planCoverage.plan();
            var rows = suborders.stream()
                .filter(s -> planCoverage.periodsBySuborderId().containsKey(s.getId()))
                .map(s -> row(s, planCoverage.periodsBySuborderId().get(s.getId()), scored, includeCosts, null))
                .filter(BudgetControllingRow::hasContent)
                .toList();
            var budget = budgetOf(plan, period);
            var progress = computeProgress(plan, period.getFrom(), period.getUntil(), today, holidays);
            // An order-wide plan is the whole section, so its figures belong on the section total.
            var subtotal = orderWide ? null
                : aggregate(plan.getSuborderSign(), plan.getName(), rows, budget, progress, includeCosts);
            groups.add(new BudgetControllingGroup(plan.getSuborderSign(), plan.getName(), rows, subtotal));
        }

        var allRows = groups.stream().flatMap(g -> g.rows().stream()).toList();
        var totalBudget = plans.stream().map(p -> budgetOf(p.plan(), period))
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        var totalProgress = orderWide
            ? computeProgress(plans.get(0).plan(), period.getFrom(), period.getUntil(), today, holidays)
            : null;
        var total = aggregate(null, null, allRows, totalBudget, totalProgress, includeCosts);

        return new BudgetControllingSection(
            orderWide ? SectionKind.ORDER_LEVEL : SectionKind.SUBORDER_LEVEL,
            period,
            plans.stream().map(p -> p.plan().getName()).toList(),
            groups, total);
    }

    private BudgetControllingSection unplannedSection(Map<Long, List<LocalDateRange>> gapsBySuborderId,
                                                      List<Suborder> suborders,
                                                      Map<Long, List<ScoredReport>> scored, boolean includeCosts) {
        var rows = suborders.stream()
            .filter(s -> gapsBySuborderId.containsKey(s.getId()))
            .map(s -> row(s, gapsBySuborderId.get(s.getId()), scored, includeCosts, gapsBySuborderId.get(s.getId())))
            .filter(BudgetControllingRow::hasContent)
            .toList();
        if (rows.isEmpty()) {
            return null;
        }
        var total = aggregate(null, null, rows, null, null, includeCosts);
        return new BudgetControllingSection(SectionKind.UNPLANNED, null, List.of(),
            List.of(new BudgetControllingGroup(null, null, rows, null)), total);
    }

    private BudgetControllingRow row(Suborder suborder, List<LocalDateRange> periods,
                                     Map<Long, List<ScoredReport>> scored, boolean includeCosts,
                                     List<LocalDateRange> shownPeriods) {
        var reports = scored.getOrDefault(suborder.getId(), List.of()).stream()
            .filter(r -> periods.stream().anyMatch(p -> p.contains(r.day())))
            .toList();
        return BudgetControllingRow.builder()
            .sign(suborder.getCompleteOrderSign())
            .label(suborder.getShortdescription())
            .periods(shownPeriods)
            .plannedHours(suborder.getDebithours() != null ? suborder.getDebithours() : Duration.ZERO)
            .bookedHours(reports.stream().map(ScoredReport::duration).reduce(Duration.ZERO, Duration::plus))
            .revenueEuro(reports.stream().map(ScoredReport::revenue).reduce(BigDecimal.ZERO, BigDecimal::add))
            .costEuro(includeCosts
                ? reports.stream().map(ScoredReport::cost).reduce(BigDecimal.ZERO, BigDecimal::add) : null)
            .build();
    }

    private BudgetControllingRow aggregate(String sign, String label, List<BudgetControllingRow> rows,
                                           BigDecimal budget, Double progressPercent, boolean includeCosts) {
        var revenue = rows.stream().map(BudgetControllingRow::revenueEuro).reduce(BigDecimal.ZERO, BigDecimal::add);
        var row = BudgetControllingRow.builder()
            .sign(sign)
            .label(label)
            .plannedHours(rows.stream().map(BudgetControllingRow::plannedHours).reduce(Duration.ZERO, Duration::plus))
            .bookedHours(rows.stream().map(BudgetControllingRow::bookedHours).reduce(Duration.ZERO, Duration::plus))
            .budgetEuro(budget)
            .revenueEuro(revenue)
            .costEuro(includeCosts
                ? rows.stream().map(BudgetControllingRow::costEuro).reduce(BigDecimal.ZERO, BigDecimal::add) : null)
            .progressPercent(progressPercent)
            .build();
        return BudgetControllingRow.builder()
            .sign(row.sign()).label(row.label())
            .plannedHours(row.plannedHours()).bookedHours(row.bookedHours())
            .budgetEuro(row.budgetEuro()).revenueEuro(row.revenueEuro()).costEuro(row.costEuro())
            .progressPercent(progressPercent)
            .progressStatus(computeProgressStatus(progressPercent,
                row.hasBudgetPercent() ? row.budgetUsedPercent() : null))
            .build();
    }

    /** The parts of {@code periods} that none of {@code holes} covers. */
    private static List<LocalDateRange> minusAll(List<LocalDateRange> periods, List<LocalDateRange> holes) {
        var remaining = periods;
        for (var hole : holes) {
            remaining = remaining.stream().flatMap(p -> p.minus(hole).stream()).toList();
        }
        return remaining;
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
                             Map<Long, List<TimereportDTO>> reportsBySuborder) {}

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
            timereports.stream().collect(Collectors.groupingBy(TimereportDTO::getSuborderId)));
    }

    /**
     * Utilization of one plan over its own validity. The scope has to be resolved exactly as the
     * controlling does it — a plan on a first level suborder covers everything below it, because
     * plans only live on the customer order or on that first level while bookings happen deeper.
     * Matching the sign exactly here would report nothing for orders that book on the second level.
     */
    private UtilizationInfo computeUtilizationInfo(OrderBudget budget, OrderData orderData,
                                                   OrderPricingLookup pricingLookup) {
        var period = new LocalDateRange(budget.getValidFrom(), budget.getValidUntil());
        var coSign = budget.getCustomerorderSign();
        var soSign = budget.getSuborderSign();

        var revenue = BigDecimal.ZERO;
        for (var suborder : orderData.suborders()) {
            if (!isOrderWide(soSign) && !soSign.equals(firstLevelSignOf(suborder))) {
                continue;
            }
            if (!suborder.isInvoiceable()) {
                continue;
            }
            var soCompleteSign = orderData.completeSignBySuborderId().get(suborder.getId());
            for (var report : orderData.reportsBySuborder().getOrDefault(suborder.getId(), List.<TimereportDTO>of())) {
                if (period.contains(report.getReferenceday())) {
                    revenue = revenue.add(rateOf(report, coSign, soCompleteSign, pricingLookup));
                }
            }
        }
        return new UtilizationInfo(budgetOf(budget, period), revenue);
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

    /** The plan's budget within the given period: the adjustments that take effect inside it. */
    private static BigDecimal budgetOf(OrderBudget budget, LocalDateRange period) {
        return budget.getAdjustments().stream()
            .filter(a -> period.contains(a.getEffective()))
            .map(OrderBudgetAdjustment::getAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static BigDecimal minutesToHours(long minutes) {
        return BigDecimal.valueOf(minutes).divide(BigDecimal.valueOf(60), 6, RoundingMode.HALF_UP);
    }
}
