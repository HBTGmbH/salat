package org.tb.budget.service;

import static java.lang.Boolean.TRUE;
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
import java.util.function.Predicate;
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
import org.tb.budget.domain.BudgetScope;
import org.tb.budget.domain.EmployeeCostLookup;
import org.tb.budget.domain.FlatRateAllocation;
import org.tb.budget.domain.FlatRateDueAmount;
import org.tb.budget.domain.OrderBudget;
import org.tb.budget.domain.OrderBudgetAdjustment;
import org.tb.budget.domain.OrderBudgetScopeEntry;
import org.tb.budget.domain.OrderFlatRate;
import org.tb.budget.domain.OrderFlatRateLookup;
import org.tb.budget.domain.OrderPricingLookup;
import org.tb.budget.domain.ProgressMode;
import org.tb.budget.domain.ProgressStatus;
import org.tb.budget.domain.SectionKind;
import org.tb.budget.domain.TimereportBudgetLink;
import org.tb.budget.persistence.OrderBudgetRepository;
import org.tb.budget.persistence.TimereportBudgetAssignmentRepository;
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
    private final TimereportBudgetAssignmentRepository assignmentRepository;
    private final OrderPricingService orderPricingService;
    private final OrderFlatRateService orderFlatRateService;
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
        var budgets = orderBudgetRepository.findByCustomerorderSign(customerorderSign);

        // Rates and costs are resolved once per time report. Loading both tables up front keeps
        // that in memory instead of issuing up to five statements per report.
        var pricingLookup = orderPricingService.lookupFor(List.of(customerorderSign));
        var costLookup = includeCosts ? employeeCostService.lookup() : null;

        // Which plan a booking counts against is read, not derived (#913). That is what lets a
        // booking appear in exactly one section without anyone cutting periods against each other,
        // and it is what allows plans to overlap from #914 on.
        var planOfBooking = planOfBooking(customerorderSign);

        var plans = evaluatedPlans(budgets, filter);
        var evaluatedPlanIds = plans.stream().map(p -> p.plan().getId()).collect(Collectors.toSet());

        // One read over the whole span this evaluation talks about: from the earliest plan start to
        // the end of the window (#917). The amounts are reported in full over that span, while the
        // hours are split into what was booked before the window and what inside it — a second query
        // for the earlier part would only add a round trip.
        var readFrom = plans.stream().map(p -> p.plan().getValidFrom()).min(naturalOrder())
            .filter(planStart -> planStart.isBefore(from))
            .orElse(from);
        var timereports = timereportService.getTimereportsByDatesAndCustomerOrderId(
            readFrom, until, customerorder.getId());

        // Every report is priced exactly once here. Sections then only filter and add, which matters
        // because the same report is looked at by every section it could fall into.
        var scored = scoreReports(suborders, timereports, customerorderSign, pricingLookup, costLookup, from);

        // Flat rates over the same span, allocated to a plan by due date and scope (#972). Judged
        // against the active plans of the order rather than against the evaluated ones, so that the
        // allocation of an amount does not depend on the window somebody is looking at.
        var flatRatesByPlan = allocateFlatRates(customerorderSign, activePlans(budgets), readFrom, until);

        var sections = new ArrayList<BudgetControllingSection>();
        for (var group : sectionGroups(plans)) {
            sections.add(plannedSection(group, suborders, scored, planOfBooking, flatRatesByPlan, filter,
                today, holidays, includeCosts));
        }
        var withoutBudget = withoutBudgetSection(suborders, scored, planOfBooking, flatRatesByPlan,
            evaluatedPlanIds, includeCosts);
        if (withoutBudget != null) {
            sections.add(withoutBudget);
        }

        var customer = customerorder.getCustomer();
        return new BudgetControllingResult(customerorderSign, customerorder.getShortdescription(),
            customer == null ? null : customer.getShortname(),
            customer == null ? null : customer.getName(),
            filter,
            sections.stream().filter(BudgetControllingSection::hasContent).toList());
    }

    /** The stored assignment of every booking of the customer order, by time report id. */
    private Map<Long, Long> planOfBooking(String customerorderSign) {
        return assignmentRepository.findLinksByCustomerorderSign(customerorderSign).stream()
            .collect(Collectors.toMap(TimereportBudgetLink::timereportId, TimereportBudgetLink::orderBudgetId));
    }

    /**
     * A time report with its revenue and cost already resolved, and whether it lies before the
     * evaluated window: the hours of the two are reported apart, the amounts together (#917).
     */
    private record ScoredReport(long timereportId, LocalDate day, Duration duration,
                                BigDecimal revenue, BigDecimal cost, boolean beforeWindow) {}

    private Map<Long, List<ScoredReport>> scoreReports(List<Suborder> suborders, List<TimereportDTO> timereports,
                                                       String customerorderSign, OrderPricingLookup pricingLookup,
                                                       EmployeeCostLookup costLookup, LocalDate windowStart) {
        Map<Long, List<TimereportDTO>> bySuborder = timereports.stream()
            .collect(Collectors.groupingBy(TimereportDTO::getSuborderId));
        Map<Long, List<ScoredReport>> scored = new HashMap<>();
        for (var suborder : suborders) {
            // Resolving the complete order sign walks the lazily fetched parent chain, so do it once.
            var soSign = suborder.getCompleteOrderSign();
            var invoiceable = suborder.isInvoiceable();
            scored.put(suborder.getId(), bySuborder.getOrDefault(suborder.getId(), List.<TimereportDTO>of()).stream()
                .map(r -> new ScoredReport(r.getId(), r.getReferenceday(), r.getDuration(),
                    // Work on a suborder that is not invoiceable is never billed, whatever rate matches.
                    invoiceable ? rateOf(r, customerorderSign, soSign, pricingLookup) : BigDecimal.ZERO,
                    // Costs accrue whether or not the work is billed.
                    costLookup == null ? BigDecimal.ZERO : costOf(r, soSign, costLookup),
                    r.getReferenceday().isBefore(windowStart)))
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

    /**
     * The flat rate amounts of one customer order, sorted into the plan they count against (#972).
     * An amount no single plan covers is kept apart rather than dropped — it is reported as being
     * without a budget, the same way an unassigned booking is.
     */
    private record AllocatedFlatRates(Map<Long, List<FlatRateDueAmount>> byPlanId,
                                      List<FlatRateDueAmount> unallocated) {

        List<FlatRateDueAmount> of(Long planId) {
            return byPlanId.getOrDefault(planId, List.of());
        }
    }

    private static List<OrderBudget> activePlans(List<OrderBudget> budgets) {
        return budgets.stream().filter(b -> TRUE.equals(b.getActive())).toList();
    }

    /**
     * Expands the flat rates of the order over the span and allocates every amount to the one plan
     * that may hold it (→ {@link FlatRateAllocation}).
     */
    private AllocatedFlatRates allocateFlatRates(String customerorderSign, List<OrderBudget> plans,
                                                 LocalDate from, LocalDate until) {
        var dueAmounts = orderFlatRateService.lookupFor(List.of(customerorderSign))
            .dueAmounts(customerorderSign, from, until);
        return allocate(dueAmounts, plans);
    }

    private static AllocatedFlatRates allocate(List<FlatRateDueAmount> dueAmounts, List<OrderBudget> plans) {
        Map<Long, List<FlatRateDueAmount>> byPlanId = new LinkedHashMap<>();
        var unallocated = new ArrayList<FlatRateDueAmount>();
        for (var dueAmount : dueAmounts) {
            FlatRateAllocation.uniquePlanFor(dueAmount, plans).ifPresentOrElse(
                plan -> byPlanId.computeIfAbsent(plan.getId(), id -> new ArrayList<>()).add(dueAmount),
                () -> unallocated.add(dueAmount));
        }
        return new AllocatedFlatRates(byPlanId, List.copyOf(unallocated));
    }

    /**
     * One line per flat rate, carrying what it puts on the calendar within the span. Grouped by
     * definition rather than by due date: twelve monthly amounts are one agreement, and listing them
     * one by one would bury the suborders they sit next to.
     */
    private static List<BudgetControllingRow> flatRateRows(List<FlatRateDueAmount> dueAmounts,
                                                           boolean includeCosts) {
        Map<OrderFlatRate, List<FlatRateDueAmount>> byFlatRate = new LinkedHashMap<>();
        for (var dueAmount : dueAmounts) {
            byFlatRate.computeIfAbsent(dueAmount.flatRate(), rate -> new ArrayList<>()).add(dueAmount);
        }
        return byFlatRate.entrySet().stream()
            .map(entry -> flatRateRow(entry.getKey(), entry.getValue(), includeCosts))
            .toList();
    }

    /**
     * A flat rate as a controlling line. It has no hours and no cost of its own — an agreed amount
     * is not worked — so only the amount is filled and the view marks the line as a flat rate.
     */
    private static BudgetControllingRow flatRateRow(OrderFlatRate flatRate,
                                                    List<FlatRateDueAmount> dueAmounts,
                                                    boolean includeCosts) {
        return BudgetControllingRow.builder()
            .sign(flatRate.isOrderWide() ? flatRate.getCustomerorderSign() : flatRate.getSuborderSign())
            .label(flatRate.getDescription())
            .plannedHours(Duration.ZERO)
            .bookedHoursBeforeWindow(Duration.ZERO)
            .bookedHours(Duration.ZERO)
            .revenueEuro(BigDecimal.ZERO)
            .flatRateRevenueEuro(dueAmounts.stream().map(FlatRateDueAmount::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add))
            .costEuro(includeCosts ? BigDecimal.ZERO : null)
            .flatRate(true)
            .build();
    }

    /** One plan with the part of its validity that falls inside the evaluated period. */
    private record PlanPeriod(OrderBudget plan, LocalDateRange period) {
        boolean orderWide() {
            return isOrderWide(plan.getSuborderSign());
        }
    }

    /**
     * The plans that take part in this evaluation: active, and with a validity that reaches into the
     * evaluated period.
     *
     * <p>This is a filter, not a coverage derivation — no plan takes anything away from another one
     * any more. A booking assigned to a plan that is excluded here is reported as being without a
     * budget, so deactivating a plan does not make its hours disappear from every number.
     */
    private List<PlanPeriod> evaluatedPlans(List<OrderBudget> budgets, LocalDateRange filter) {
        return budgets.stream()
            .filter(b -> Boolean.TRUE.equals(b.getActive()))
            // A plan takes part when its validity touches the window, whether or not it began inside
            // it (#916). The clipped period below is only what the section header shows.
            .filter(b -> new LocalDateRange(b.getValidFrom(), b.getValidUntil()).overlaps(filter))
            .map(b -> new PlanPeriod(b,
                new LocalDateRange(b.getValidFrom(), b.getValidUntil()).intersection(filter)))
            .filter(p -> p.period() != null && p.period().isValid())
            .sorted(Comparator
                .comparing((PlanPeriod p) -> p.period().getFrom())
                .thenComparing(p -> p.period().getUntil())
                .thenComparing(PlanPeriod::orderWide)
                .thenComparing(p -> p.plan().getId(), Comparator.nullsLast(Comparator.naturalOrder())))
            .toList();
    }

    /**
     * Plans of the same level and the same period share one section, as they always have — the
     * section total over them is the number a reader compares against the order. Only what fills the
     * rows changed.
     */
    private List<List<PlanPeriod>> sectionGroups(List<PlanPeriod> plans) {
        Map<String, List<PlanPeriod>> grouped = new LinkedHashMap<>();
        for (var plan : plans) {
            grouped.computeIfAbsent(plan.orderWide() + "|" + plan.period(), k -> new ArrayList<>()).add(plan);
        }
        return List.copyOf(grouped.values());
    }

    /** Whether the plan's scope contains the suborder — where its planned hours come from. */
    private static boolean covers(OrderBudget plan, Suborder suborder) {
        return isOrderWide(plan.getSuborderSign())
            || plan.getSuborderSign().equals(firstLevelSignOf(suborder));
    }

    /**
     * The complete order sign of the suborder's first level ancestor, or its own if it is one.
     * Shared with the stored assignment via {@link BudgetScope}, so that the derived coverage here
     * and the explicit assignment cannot resolve a scope differently (#931).
     */
    private static String firstLevelSignOf(Suborder suborder) {
        return BudgetScope.firstLevelSignOf(suborder);
    }

    private static boolean isOrderWide(String suborderSign) {
        return BudgetScope.isOrderWide(suborderSign);
    }

    private BudgetControllingSection plannedSection(List<PlanPeriod> plans, List<Suborder> suborders,
                                                    Map<Long, List<ScoredReport>> scored,
                                                    Map<Long, Long> planOfBooking,
                                                    AllocatedFlatRates flatRates, LocalDateRange window,
                                                    LocalDate today,
                                                    Set<LocalDate> holidays, boolean includeCosts) {
        var orderWide = plans.get(0).orderWide();
        var period = plans.get(0).period();
        var groups = new ArrayList<BudgetControllingGroup>();

        for (var planPeriod : plans) {
            var plan = planPeriod.plan();
            // The rows span the plan's scope, because that is where its planned hours come from; what
            // is booked against it comes from the assignment alone.
            var suborderRows = suborders.stream()
                .filter(suborder -> covers(plan, suborder))
                .map(suborder -> row(suborder,
                    reportsOf(suborder, scored, r -> plan.getId().equals(planOfBooking.get(r.timereportId()))),
                    includeCosts))
                .filter(BudgetControllingRow::hasContent)
                .toList();
            // The flat rates allocated to this plan follow its suborders: they belong to the same
            // budget and have to count towards the same subtotal (#972).
            var rows = concat(suborderRows, flatRateRows(flatRates.of(plan.getId()), includeCosts));
            var budget = cumulativeBudgetOf(plan, window.getUntil());
            var progress = computeProgress(plan, period.getFrom(), period.getUntil(), today, holidays);
            // An order-wide plan is the whole section, so its figures belong on the section total.
            var subtotal = orderWide ? null
                : aggregate(plan.getSuborderSign(), plan.getName(), rows, budget, progress, includeCosts);
            groups.add(new BudgetControllingGroup(plan.getSuborderSign(), plan.getName(), rows, subtotal));
        }

        var allRows = groups.stream().flatMap(g -> g.rows().stream()).toList();
        var totalBudget = plans.stream()
            .map(p -> cumulativeBudgetOf(p.plan(), window.getUntil()))
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        var totalProgress = orderWide
            ? computeProgress(plans.get(0).plan(), period.getFrom(), period.getUntil(), today, holidays)
            : null;
        var total = aggregate(null, null, allRows, totalBudget, totalProgress, includeCosts);

        return new BudgetControllingSection(
            orderWide ? SectionKind.ORDER_LEVEL : SectionKind.SUBORDER_LEVEL,
            period,
            plans.stream().map(p -> p.plan().getName()).toList(),
            plans.stream().map(p -> p.plan().getValidFrom()).min(naturalOrder()).orElse(null),
            plans.stream().map(p -> p.plan().getValidUntil()).max(naturalOrder()).orElse(null),
            groups, total);
    }

    /**
     * The bookings that belong to no budget: no assignment at all, or one pointing at a plan this
     * evaluation excludes — an inactive plan, or one whose validity lies outside the period. Both
     * cases have to surface, otherwise hours would silently stop appearing in any number, which is
     * exactly what the explicit assignment must not cost us (#913).
     */
    private BudgetControllingSection withoutBudgetSection(List<Suborder> suborders,
                                                          Map<Long, List<ScoredReport>> scored,
                                                          Map<Long, Long> planOfBooking,
                                                          AllocatedFlatRates flatRates,
                                                          Set<Long> evaluatedPlanIds, boolean includeCosts) {
        var bookedRows = suborders.stream()
            .map(suborder -> row(suborder, reportsOf(suborder, scored, report -> {
                var planId = planOfBooking.get(report.timereportId());
                return planId == null || !evaluatedPlanIds.contains(planId);
            }), includeCosts))
            // Only the booked side counts here: a suborder with planned hours but no unassigned
            // booking has nothing to answer for and would otherwise show up in every evaluation.
            .filter(row -> !row.bookedHours().isZero() || row.hasBookedBeforeWindow())
            .toList();
        // Flat rates land here for the two reasons a booking does: no plan covers the amount, or
        // several do and none was picked, or the plan holding it is excluded from this evaluation.
        var rows = concat(bookedRows, flatRateRows(orphanedFlatRates(flatRates, evaluatedPlanIds), includeCosts));
        if (rows.isEmpty()) {
            return null;
        }
        var total = aggregate(null, null, rows, null, null, includeCosts);
        return new BudgetControllingSection(SectionKind.UNPLANNED, null, List.of(), null, null,
            List.of(new BudgetControllingGroup(null, null, rows, null)), total);
    }

    /** The flat rate amounts this evaluation cannot put under any of its sections. */
    private static List<FlatRateDueAmount> orphanedFlatRates(AllocatedFlatRates flatRates,
                                                             Set<Long> evaluatedPlanIds) {
        var orphaned = new ArrayList<>(flatRates.unallocated());
        flatRates.byPlanId().entrySet().stream()
            .filter(entry -> !evaluatedPlanIds.contains(entry.getKey()))
            .forEach(entry -> orphaned.addAll(entry.getValue()));
        return orphaned;
    }

    private static List<BudgetControllingRow> concat(List<BudgetControllingRow> first,
                                                     List<BudgetControllingRow> second) {
        if (second.isEmpty()) {
            return first;
        }
        var rows = new ArrayList<>(first);
        rows.addAll(second);
        return List.copyOf(rows);
    }

    private static List<ScoredReport> reportsOf(Suborder suborder, Map<Long, List<ScoredReport>> scored,
                                                Predicate<ScoredReport> belongsHere) {
        return scored.getOrDefault(suborder.getId(), List.of()).stream().filter(belongsHere).toList();
    }

    /**
     * One line of a section. The hours are split — what was booked before the window and what inside
     * it — while revenue and cost are the full figures over both (#917). That way the budget and its
     * utilization read against the whole plan, and the extra hours column says how much of the work
     * already predates the window.
     */
    private BudgetControllingRow row(Suborder suborder, List<ScoredReport> reports, boolean includeCosts) {
        return BudgetControllingRow.builder()
            .sign(suborder.getCompleteOrderSign())
            .label(suborder.getShortdescription())
            .plannedHours(suborder.getDebithours() != null ? suborder.getDebithours() : Duration.ZERO)
            .bookedHoursBeforeWindow(hoursOf(reports, ScoredReport::beforeWindow))
            .bookedHours(hoursOf(reports, report -> !report.beforeWindow()))
            .revenueEuro(reports.stream().map(ScoredReport::revenue).reduce(BigDecimal.ZERO, BigDecimal::add))
            .flatRateRevenueEuro(BigDecimal.ZERO)
            .costEuro(includeCosts
                ? reports.stream().map(ScoredReport::cost).reduce(BigDecimal.ZERO, BigDecimal::add) : null)
            .build();
    }

    private static Duration hoursOf(List<ScoredReport> reports, Predicate<ScoredReport> selected) {
        return reports.stream().filter(selected)
            .map(ScoredReport::duration)
            .reduce(Duration.ZERO, Duration::plus);
    }

    private BudgetControllingRow aggregate(String sign, String label, List<BudgetControllingRow> rows,
                                           BigDecimal budget, Double progressPercent, boolean includeCosts) {
        var revenue = rows.stream().map(BudgetControllingRow::revenueEuro).reduce(BigDecimal.ZERO, BigDecimal::add);
        var flatRateRevenue = rows.stream().map(BudgetControllingRow::flatRateRevenueEuro)
            .map(amount -> amount == null ? BigDecimal.ZERO : amount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        var row = BudgetControllingRow.builder()
            .sign(sign)
            .label(label)
            .plannedHours(rows.stream().map(BudgetControllingRow::plannedHours).reduce(Duration.ZERO, Duration::plus))
            .bookedHoursBeforeWindow(rows.stream().map(BudgetControllingRow::bookedHoursBeforeWindow)
                .reduce(Duration.ZERO, Duration::plus))
            .bookedHours(rows.stream().map(BudgetControllingRow::bookedHours).reduce(Duration.ZERO, Duration::plus))
            .budgetEuro(budget)
            .revenueEuro(revenue)
            .flatRateRevenueEuro(flatRateRevenue)
            .costEuro(includeCosts
                ? rows.stream().map(BudgetControllingRow::costEuro).reduce(BigDecimal.ZERO, BigDecimal::add) : null)
            .progressPercent(progressPercent)
            .build();
        return BudgetControllingRow.builder()
            .sign(row.sign()).label(row.label())
            .plannedHours(row.plannedHours())
            .bookedHoursBeforeWindow(row.bookedHoursBeforeWindow()).bookedHours(row.bookedHours())
            .budgetEuro(row.budgetEuro()).revenueEuro(row.revenueEuro())
            .flatRateRevenueEuro(row.flatRateRevenueEuro()).costEuro(row.costEuro())
            .progressPercent(progressPercent)
            .progressStatus(computeProgressStatus(progressPercent,
                row.hasBudgetPercent() ? row.budgetUsedPercent() : null))
            .build();
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
        var sign = budget.getCustomerorderSign();
        return computeUtilizationInfo(budget, loadOrderData(sign, List.of(budget)),
            orderPricingService.lookupFor(List.of(sign)), orderFlatRateService.lookupFor(List.of(sign)));
    }

    /**
     * Utilization for several budgets at once. Budgets on the same customer order share one set of
     * customer order, suborder and time report queries, and all of them share one pricing and one
     * flat rate lookup — resolving each budget on its own multiplied every one of those by the
     * number of budgets.
     */
    public Map<Long, BudgetUtilization> computeUtilizationInfos(List<OrderBudget> budgets) {
        var signs = budgets.stream().map(OrderBudget::getCustomerorderSign).distinct().toList();
        var pricingLookup = orderPricingService.lookupFor(signs);
        var flatRateLookup = orderFlatRateService.lookupFor(signs);
        Map<String, OrderData> orderDataBySign = new HashMap<>();
        Map<Long, BudgetUtilization> result = new LinkedHashMap<>();
        for (var budget : budgets) {
            var orderData = orderDataBySign.computeIfAbsent(budget.getCustomerorderSign(),
                sign -> loadOrderData(sign, budgets));
            result.put(budget.getId(), new BudgetUtilization(
                computeUtilizationInfo(budget, orderData, pricingLookup, flatRateLookup),
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
                             Map<Long, Long> planOfBooking,
                             /**
                              * Every active plan of the order, not only the ones being asked about:
                              * a flat rate amount counts against a plan only if that plan is the
                              * single one covering it (#972), and judging that against a filtered
                              * set would allocate an ambiguous amount to whichever plan the caller
                              * happened to pass.
                              */
                             List<OrderBudget> activePlans) {}

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
            planOfBooking(customerorderSign),
            orderBudgetRepository.findByCustomerorderSignAndActive(customerorderSign, TRUE));
    }

    /**
     * Utilization of one plan: the revenue of the bookings assigned to it (#913) plus the flat rates
     * falling due inside it (#972).
     *
     * <p>Dashboard and alerts therefore rest on exactly the same basis as the evaluation. Scope and
     * period are no longer re-derived here — the assignment already guarantees both, which is what
     * removes the risk that this and the section calculation disagree. Only the invoiceable check
     * stays: work that is never billed earns nothing, whatever plan it belongs to.
     */
    private UtilizationInfo computeUtilizationInfo(OrderBudget budget, OrderData orderData,
                                                   OrderPricingLookup pricingLookup,
                                                   OrderFlatRateLookup flatRateLookup) {
        var period = new LocalDateRange(budget.getValidFrom(), budget.getValidUntil());
        var coSign = budget.getCustomerorderSign();

        var revenue = BigDecimal.ZERO;
        for (var suborder : orderData.suborders()) {
            if (!suborder.isInvoiceable()) {
                continue;
            }
            var soCompleteSign = orderData.completeSignBySuborderId().get(suborder.getId());
            for (var report : orderData.reportsBySuborder().getOrDefault(suborder.getId(), List.<TimereportDTO>of())) {
                if (budget.getId().equals(orderData.planOfBooking().get(report.getId()))) {
                    revenue = revenue.add(rateOf(report, coSign, soCompleteSign, pricingLookup));
                }
            }
        }
        // The flat rates of the plan's own period, allocated through the same rule the sections use —
        // an amount several plans could hold counts against none of them.
        var flatRates = allocate(
            flatRateLookup.dueAmounts(coSign, period.getFrom(), period.getUntil()),
            orderData.activePlans());
        for (var dueAmount : flatRates.of(budget.getId())) {
            revenue = revenue.add(dueAmount.amount());
        }
        // The window here is the plan's own validity, so nothing can have been consumed before it —
        // an assignment always sits inside the plan's period.
        return new UtilizationInfo(cumulativeBudgetOf(budget, period.getUntil()), revenue);
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
    /**
     * Everything the plan was granted up to the end of the window: every adjustment that has taken
     * effect by then, including the ones from before the window (#916). Cutting them off at the
     * window start reported 0 EUR for a plan that had been running for months — while the bookings
     * inside the window counted against it.
     */
    private static BigDecimal cumulativeBudgetOf(OrderBudget budget, LocalDate windowEnd) {
        return budget.getAdjustments().stream()
            .filter(a -> !a.getEffective().isAfter(windowEnd))
            .map(OrderBudgetAdjustment::getAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static BigDecimal minutesToHours(long minutes) {
        return BigDecimal.valueOf(minutes).divide(BigDecimal.valueOf(60), 6, RoundingMode.HALF_UP);
    }
}
