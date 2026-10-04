package de.hbt.salat.budget.service;

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
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import de.hbt.salat.auth.domain.Authorized;
import de.hbt.salat.budget.auth.BudgetAuthorization;
import de.hbt.salat.budget.domain.BudgetControllingGroup;
import de.hbt.salat.budget.domain.BudgetControllingResult;
import de.hbt.salat.budget.domain.BudgetControllingRow;
import de.hbt.salat.budget.domain.BudgetControllingSection;
import de.hbt.salat.budget.domain.BudgetScope;
import de.hbt.salat.budget.domain.EmployeeCostLookup;
import de.hbt.salat.budget.domain.FlatRateAllocation;
import de.hbt.salat.budget.domain.FlatRateDueAmount;
import de.hbt.salat.budget.domain.OrderBudget;
import de.hbt.salat.budget.domain.OrderBudgetAdjustment;
import de.hbt.salat.budget.domain.OrderBudgetScopeEntry;
import de.hbt.salat.budget.domain.OrderFlatRate;
import de.hbt.salat.budget.domain.OrderPosition;
import de.hbt.salat.budget.domain.OrderFlatRateLookup;
import de.hbt.salat.budget.domain.OrderPricingLookup;
import de.hbt.salat.budget.domain.PlanBooking;
import de.hbt.salat.budget.domain.ProgressMode;
import de.hbt.salat.budget.domain.ProgressStatus;
import de.hbt.salat.budget.domain.SectionKind;
import de.hbt.salat.budget.domain.TimereportBudgetLink;
import de.hbt.salat.budget.persistence.OrderBudgetRepository;
import de.hbt.salat.budget.persistence.TimereportBudgetAssignmentRepository;
import de.hbt.salat.common.LocalDateRange;
import de.hbt.salat.common.util.DateUtils;
import de.hbt.salat.dailyreport.domain.TimereportDTO;
import de.hbt.salat.dailyreport.service.PublicholidayService;
import de.hbt.salat.dailyreport.service.TimereportService;
import de.hbt.salat.order.domain.Customerorder;
import de.hbt.salat.order.domain.CustomerorderOption;
import de.hbt.salat.order.domain.OrderType;
import de.hbt.salat.order.domain.SuborderReadModel;
import de.hbt.salat.order.service.CustomerorderService;
import de.hbt.salat.order.service.SuborderService;

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
    private final OrderPositions orderPositions;

    /**
     * The controlling of one customer order, empty when there is no such order (#1338).
     *
     * <p>Asked by the id the budget filter carries (#1334), and read by id throughout (#1205), so a
     * renamed order or suborder changes nothing about what counts where. Order, suborders and holidays
     * come as plain values from the modules owning them (→ ADR-0021, Nachtrag #1338): the complete
     * order sign and the effective order type are their rules, not this module's.
     */
    public Optional<BudgetControllingResult> compute(long customerorderId, LocalDate from, LocalDate until,
                                                     boolean includeCosts) {
        budgetAuthorization.checkAuthorizedForCustomerorderId(customerorderId);
        var customerorder = customerorderService.getCustomerorderOptionsByIds(List.of(customerorderId)).stream()
            .findFirst().orElse(null);
        if (customerorder == null) {
            return Optional.empty();
        }
        var today = DateUtils.today();
        var filter = new LocalDateRange(from, until);

        Set<LocalDate> holidays = publicholidayService.getPublicHolidayDatesBetween(from, until);

        var suborders = suborderService.getSuborderReadModelsByCustomerorderId(customerorderId);
        var budgets = orderBudgetRepository.findByCustomerorderId(customerorderId);

        // Rates and costs are resolved once per time report. Loading both tables up front keeps
        // that in memory instead of issuing up to five statements per report.
        var pricingLookup = orderPricingService.lookupFor(List.of(customerorderId));
        var costLookup = includeCosts ? employeeCostService.lookup() : null;

        // Which plan a booking counts against is read, not derived (#913). That is what lets a
        // booking appear in exactly one section without anyone cutting periods against each other,
        // and it is what allows plans to overlap from #914 on.
        var planOfBooking = planOfBooking(customerorderId);

        // A deactivated plan keeps its assignments (#1217), so every plan whose validity touches the
        // window is a candidate at first. Whether a deactivated one takes part depends on what it
        // holds inside the window, and that is only known once bookings and flat rates are read.
        var candidates = evaluatedPlans(budgets, filter);
        var candidateTimereports = timereportService.getTimereportsByDatesAndCustomerOrderId(
            readFrom(candidates, from), until, customerorderId);
        var flatRateLookup = orderFlatRateService.lookupFor(List.of(customerorderId));
        var positionOfFlatRate = flatRatePositions();
        var plans = withoutIdleDeactivatedPlans(candidates, candidateTimereports, planOfBooking,
            allocate(flatRateLookup.dueAmounts(customerorderId, from, until), budgets, positionOfFlatRate),
            from);
        var evaluatedPlanIds = plans.stream().map(p -> p.plan().getId()).collect(Collectors.toSet());

        // One read over the whole span this evaluation talks about: from the earliest plan start to
        // the end of the window. Everything before the window feeds exactly one figure — what was
        // earned back then (#779) — which is what the budget columns add to the window's own revenue;
        // hours, revenue and cost themselves stay inside the window. A second query for the earlier
        // part would only add a round trip. The read above spans the candidates, so it is cut back to
        // the plans that take part: one that dropped out must leave no trace, not even as revenue
        // from before the window in the section without a budget.
        var readFrom = readFrom(plans, from);
        var timereports = candidateTimereports.stream()
            .filter(report -> !report.getReferenceday().isBefore(readFrom))
            .toList();

        // Every report is priced exactly once here. Sections then only filter and add, which matters
        // because the same report is looked at by every section it could fall into.
        var scored = scoreReports(suborders, timereports, customerorderId, planOfBooking,
            pricingLookup, costLookup, from);

        // Flat rates over the same span, allocated to a plan by due date and scope (#972). Judged
        // against every plan of the order rather than against the evaluated ones, so that the
        // allocation of an amount does not depend on the window somebody is looking at. Only a flat
        // rate naming its plan can land on a deactivated one (→ FlatRateAllocation).
        var flatRatesByPlan = allocate(flatRateLookup.dueAmounts(customerorderId, readFrom, until), budgets,
            positionOfFlatRate);
        var scopeSigns = scopeSigns(customerorder, suborders, budgets, flatRateLookup);

        var sections = new ArrayList<BudgetControllingSection>();
        for (var group : sectionGroups(plans)) {
            sections.add(plannedSection(group, suborders, scored, planOfBooking, flatRatesByPlan, scopeSigns,
                filter, today, holidays, includeCosts));
        }
        var withoutBudget = withoutBudgetSection(suborders, scored, planOfBooking, flatRatesByPlan,
            scopeSigns, evaluatedPlanIds, from, includeCosts);
        if (withoutBudget != null) {
            sections.add(withoutBudget);
        }

        return Optional.of(new BudgetControllingResult(customerorder.sign(),
            customerorder.shortdescriptionOrDescription(),
            customerorder.customerShortname(),
            customerorder.customerName(),
            filter,
            sections.stream().filter(BudgetControllingSection::hasContent).toList()));
    }

    /** The stored assignment of every booking of the customer order, by time report id. */
    private Map<Long, Long> planOfBooking(long customerorderId) {
        return assignmentRepository.findLinksByCustomerorderId(customerorderId).stream()
            .collect(Collectors.toMap(TimereportBudgetLink::timereportId, TimereportBudgetLink::orderBudgetId));
    }

    /**
     * A time report with its revenue and cost already resolved, and whether it lies before the
     * evaluated window. Everything of a report that lies before it contributes to one figure only:
     * the revenue earned before the window (#779).
     */
    private record ScoredReport(long timereportId, LocalDate day, Duration duration,
                                BigDecimal revenue, BigDecimal cost, boolean beforeWindow) {}

    /**
     * Prices every report exactly once — with the plan it is assigned to, because a rate may be
     * bound to a plan (#1065). The assignment is the stored one; it is already loaded here and
     * costs no query of its own.
     */
    private Map<Long, List<ScoredReport>> scoreReports(List<SuborderReadModel> suborders, List<TimereportDTO> timereports,
                                                       long customerorderId, Map<Long, Long> planOfBooking,
                                                       OrderPricingLookup pricingLookup,
                                                       EmployeeCostLookup costLookup, LocalDate windowStart) {
        Map<Long, List<TimereportDTO>> bySuborder = timereports.stream()
            .collect(Collectors.groupingBy(TimereportDTO::getSuborderId));
        Map<Long, List<ScoredReport>> scored = new HashMap<>();
        for (var suborder : suborders) {
            var soSign = suborder.completeOrderSign();
            var invoiceable = suborder.invoiceable();
            scored.put(suborder.id(), bySuborder.getOrDefault(suborder.id(), List.<TimereportDTO>of()).stream()
                .map(r -> new ScoredReport(r.getId(), r.getReferenceday(), r.getDuration(),
                    // Work on a suborder that is not invoiceable is never billed, whatever rate matches.
                    invoiceable
                        ? rateOf(r, customerorderId, soSign, planOfBooking.get(r.getId()), pricingLookup)
                        : BigDecimal.ZERO,
                    // Costs accrue whether or not the work is billed.
                    costLookup == null ? BigDecimal.ZERO : costOf(r, suborder.id(), suborder.effectiveOrderType(), costLookup),
                    r.getReferenceday().isBefore(windowStart)))
                .toList());
        }
        return scored;
    }

    private static BigDecimal rateOf(TimereportDTO report, long customerorderId, String soSign, Long planId,
                                     OrderPricingLookup lookup) {
        return rateOf(report.getDuration(), report.getEmployeeId(), report.getReferenceday(), customerorderId, soSign,
            planId, lookup);
    }

    /** The revenue of one booking — the controlling and the dashboard price it through here alike. */
    private static BigDecimal rateOf(Duration duration, Long employeeId, LocalDate day, long customerorderId,
                                     String soSign, Long planId, OrderPricingLookup lookup) {
        var hours = minutesToHours(duration.toMinutes());
        return lookup.findEffectiveRate(customerorderId, soSign, employeeId, planId, day)
            .map(p -> hours.multiply(new BigDecimal(p.getPriceCentsPerHour())).movePointLeft(2))
            .orElse(BigDecimal.ZERO);
    }

    private static BigDecimal costOf(TimereportDTO report, long suborderId, OrderType orderType, EmployeeCostLookup lookup) {
        var hours = minutesToHours(report.getDuration().toMinutes());
        return lookup.findEffectiveCost(report.getEmployeeId(), suborderId, orderType, report.getReferenceday())
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

    /** Allocates every amount to the one plan that may hold it (→ {@link FlatRateAllocation}). */
    private static AllocatedFlatRates allocate(List<FlatRateDueAmount> dueAmounts, List<OrderBudget> plans,
                                               Function<OrderFlatRate, Optional<OrderPosition>> positionOf) {
        Map<Long, List<FlatRateDueAmount>> byPlanId = new LinkedHashMap<>();
        var unallocated = new ArrayList<FlatRateDueAmount>();
        for (var dueAmount : dueAmounts) {
            FlatRateAllocation.uniquePlanFor(dueAmount, plans, positionOf).ifPresentOrElse(
                plan -> byPlanId.computeIfAbsent(plan.getId(), id -> new ArrayList<>()).add(dueAmount),
                () -> unallocated.add(dueAmount));
        }
        return new AllocatedFlatRates(byPlanId, List.copyOf(unallocated));
    }

    /**
     * Where each flat rate sits in the order tree, read once per flat rate however many amounts it
     * puts on the calendar — a monthly rate would otherwise climb its parent chain every month.
     */
    private Function<OrderFlatRate, Optional<OrderPosition>> flatRatePositions() {
        Map<Long, Optional<OrderPosition>> byId = new HashMap<>();
        return flatRate -> byId.computeIfAbsent(flatRate.getId(), id -> orderPositions.of(flatRate));
    }

    /**
     * The signs the order and the suborders of its plans and flat rates have today, by id (#1212) —
     * the sign columns of plans and flat rates only mirror them for reports. A suborder that has been
     * moved to another order is no longer among the order's own and is asked for on its own.
     */
    private ScopeSigns scopeSigns(CustomerorderOption customerorder, List<SuborderReadModel> suborders,
                                  List<OrderBudget> budgets, OrderFlatRateLookup flatRateLookup) {
        var suborderSigns = new HashMap<Long, String>();
        suborders.forEach(suborder -> suborderSigns.put(suborder.id(), suborder.completeOrderSign()));
        var named = new HashSet<>(flatRateLookup.suborderIds(customerorder.id()));
        budgets.stream().map(OrderBudget::getSuborderId).filter(Objects::nonNull).forEach(named::add);
        named.removeAll(suborderSigns.keySet());
        suborderSigns.putAll(suborderService.getCompleteOrderSignsByIds(named));
        return new ScopeSigns(customerorder.sign(), suborderSigns);
    }

    /**
     * What plans and flat rates are labelled with: the complete sign of their suborder, the order's
     * sign for an order-wide flat rate. An order-wide plan has no label of its own — it is the
     * section.
     */
    private record ScopeSigns(String orderSign, Map<Long, String> suborderSigns) {

        String ofPlan(OrderBudget plan) {
            return plan.isOrderWide() ? null : suborderSigns.get(plan.getSuborderId());
        }

        String ofFlatRate(OrderFlatRate flatRate) {
            return flatRate.isOrderWide() ? orderSign : suborderSigns.get(flatRate.getSuborderId());
        }
    }

    /**
     * One line per flat rate, carrying what it puts on the calendar within the span. Grouped by
     * definition rather than by due date: twelve monthly amounts are one agreement, and listing them
     * one by one would bury the suborders they sit next to.
     */
    private static List<BudgetControllingRow> flatRateRows(List<FlatRateDueAmount> dueAmounts, ScopeSigns scopeSigns,
                                                           LocalDate windowStart, boolean includeCosts) {
        Map<OrderFlatRate, List<FlatRateDueAmount>> byFlatRate = new LinkedHashMap<>();
        for (var dueAmount : dueAmounts) {
            byFlatRate.computeIfAbsent(dueAmount.flatRate(), rate -> new ArrayList<>()).add(dueAmount);
        }
        return byFlatRate.entrySet().stream()
            .map(entry -> flatRateRow(entry.getKey(), entry.getValue(), scopeSigns, windowStart, includeCosts))
            .toList();
    }

    /**
     * A flat rate as a controlling line. It has no hours and no cost of its own — an agreed amount
     * is not worked — so only the amount is filled and the view marks the line as a flat rate.
     */
    private static BudgetControllingRow flatRateRow(OrderFlatRate flatRate,
                                                    List<FlatRateDueAmount> dueAmounts, ScopeSigns scopeSigns,
                                                    LocalDate windowStart, boolean includeCosts) {
        return BudgetControllingRow.builder()
            .sign(scopeSigns.ofFlatRate(flatRate))
            .label(flatRate.getDescription())
            .plannedHours(Duration.ZERO)
            // Split at the window start like the hourly revenue: an amount that fell due before the
            // period counts against the budget, but it is not what this period earned (#779).
            .revenueBeforeWindowEuro(flatRateAmount(dueAmounts, due -> due.isBefore(windowStart)))
            .bookedHours(Duration.ZERO)
            .revenueEuro(BigDecimal.ZERO)
            .flatRateRevenueEuro(flatRateAmount(dueAmounts, due -> !due.isBefore(windowStart)))
            .costEuro(includeCosts ? BigDecimal.ZERO : null)
            .flatRate(true)
            .build();
    }

    private static BigDecimal flatRateAmount(List<FlatRateDueAmount> dueAmounts,
                                             Predicate<LocalDate> selected) {
        return dueAmounts.stream().filter(due -> selected.test(due.due()))
            .map(FlatRateDueAmount::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * One plan with the part of its validity that falls inside the evaluated period.
     *
     * @param level 0 for an order-wide plan, otherwise the suborder level it sits on (→ {@link OrderPosition})
     */
    private record PlanPeriod(OrderBudget plan, LocalDateRange period, int level) {

        boolean deactivated() {
            return !TRUE.equals(plan.getActive());
        }
    }

    /**
     * The plans whose validity reaches into the evaluated period, active or not — the candidates of
     * this evaluation (→ {@link #withoutIdleDeactivatedPlans}).
     *
     * <p>This is a filter, not a coverage derivation — no plan takes anything away from another one
     * any more.
     */
    private List<PlanPeriod> evaluatedPlans(List<OrderBudget> budgets, LocalDateRange filter) {
        return budgets.stream()
            // A plan takes part when its validity touches the window, whether or not it began inside
            // it (#916). The clipped period below is only what the section header shows.
            .filter(b -> new LocalDateRange(b.getValidFrom(), b.getValidUntil()).overlaps(filter))
            .map(b -> new PlanPeriod(b,
                new LocalDateRange(b.getValidFrom(), b.getValidUntil()).intersection(filter),
                orderPositions.of(b).map(OrderPosition::level).orElse(0)))
            .filter(p -> p.period() != null && p.period().isValid())
            .sorted(Comparator
                .comparing((PlanPeriod p) -> p.period().getFrom())
                .thenComparing(p -> p.period().getUntil())
                // The level is a number, so it sorts naturally however many of them there are.
                .thenComparing(PlanPeriod::level)
                .thenComparing(p -> p.plan().getId(), Comparator.nullsLast(Comparator.naturalOrder())))
            .toList();
    }

    /**
     * The candidates without the deactivated plans that hold nothing inside the window: no booking
     * of the window assigned to them and no flat rate of the window allocated to them (#1217). Such
     * a plan has nothing to answer for — and the planned hours of its scope alone would otherwise
     * give it a section. An active plan always takes part, as it always has.
     *
     * <p>A deactivated plan that does hold something is reported under its own name, because its
     * bookings are assigned to it: reporting them as being without a budget offered to assign what
     * already is assigned.
     */
    private static List<PlanPeriod> withoutIdleDeactivatedPlans(List<PlanPeriod> candidates,
                                                                List<TimereportDTO> timereports,
                                                                Map<Long, Long> planOfBooking,
                                                                AllocatedFlatRates flatRatesInWindow,
                                                                LocalDate windowStart) {
        Set<Long> holding = new HashSet<>(flatRatesInWindow.byPlanId().keySet());
        timereports.stream()
            // The reports end with the window, so only its start needs checking.
            .filter(report -> !report.getReferenceday().isBefore(windowStart))
            .map(report -> planOfBooking.get(report.getId()))
            .filter(Objects::nonNull)
            .forEach(holding::add);
        return candidates.stream()
            .filter(p -> !p.deactivated() || holding.contains(p.plan().getId()))
            .toList();
    }

    /**
     * The first day the evaluation reads: the earliest start of the plans where it lies before the
     * window, otherwise the start of the window itself.
     */
    private static LocalDate readFrom(List<PlanPeriod> plans, LocalDate windowStart) {
        return plans.stream().map(p -> p.plan().getValidFrom()).min(naturalOrder())
            .filter(planStart -> planStart.isBefore(windowStart))
            .orElse(windowStart);
    }

    /**
     * Plans of the same level and the same period share one section, as they always have — the
     * section total over them is the number a reader compares against the order. Since all plans in
     * force at one time sit on the same level (→ {@code OrderBudgetService}), the level only ever
     * separates sections whose periods differ anyway; it is in the key so that a section stays one
     * level even where two periods merely touch.
     *
     * <p>A deactivated plan never shares a section with an active one (#1217): the section total
     * would add both budgets up. The level rule does not bind it either — it only holds among active
     * plans — so a deactivated plan may even sit on a level the active ones of its time do not.
     */
    private List<List<PlanPeriod>> sectionGroups(List<PlanPeriod> plans) {
        Map<String, List<PlanPeriod>> grouped = new LinkedHashMap<>();
        for (var plan : plans) {
            grouped.computeIfAbsent(plan.level() + "|" + plan.period() + "|" + plan.deactivated(),
                k -> new ArrayList<>()).add(plan);
        }
        return List.copyOf(grouped.values());
    }

    /**
     * Whether the plan's scope contains the suborder — where its planned hours come from. The plan
     * covers its suborder and everything below it; the same rule that decides the assignment of a
     * booking decides this, through {@link BudgetScope}, so the derived coverage here and the stored
     * assignment cannot drift apart (#931).
     *
     * <p>The suborders come from the plan's own customer order, so the order part of the comparison
     * holds by construction; what decides is the subtree.
     */
    private static boolean covers(OrderBudget plan, SuborderReadModel suborder) {
        return BudgetScope.covers(plan, new OrderPosition(suborder.customerorderId(), suborder.path()));
    }

    private BudgetControllingSection plannedSection(List<PlanPeriod> plans, List<SuborderReadModel> suborders,
                                                    Map<Long, List<ScoredReport>> scored,
                                                    Map<Long, Long> planOfBooking,
                                                    AllocatedFlatRates flatRates, ScopeSigns scopeSigns,
                                                    LocalDateRange window,
                                                    LocalDate today,
                                                    Set<LocalDate> holidays, boolean includeCosts) {
        // Every plan of a section sits on the same level, and level 0 is the order-wide one: such a
        // plan is the whole section and therefore has no subtotal of its own.
        var level = plans.get(0).level();
        var orderWide = level == 0;
        var period = plans.get(0).period();
        // A section never mixes deactivated and active plans (→ sectionGroups).
        var deactivated = plans.get(0).deactivated();
        // Collected first and turned into groups below: a group carries its plan's progress status,
        // and for an order-wide plan that status is judged against the section total, which does not
        // exist until every plan has been walked.
        var collected = new ArrayList<CollectedPlan>();

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
            var rows = concat(suborderRows, flatRateRows(flatRates.of(plan.getId()), scopeSigns, window.getFrom(),
                includeCosts));
            var budget = cumulativeBudgetOf(plan, window.getUntil());
            // An archived plan is behind nothing any more; judging it would raise an alarm nobody
            // can act on (#1217).
            var progress = deactivated ? null
                : computeProgress(plan, period.getFrom(), period.getUntil(), today, holidays);
            // An order-wide plan is the whole section, so its figures belong on the section total.
            // The complete sign its suborder has today, like the rows below it (#1205, #1212).
            var sign = scopeSigns.ofPlan(plan);
            var subtotal = orderWide ? null
                : aggregate(sign, plan.getName(), rows, budget, includeCosts);
            collected.add(new CollectedPlan(plan.getId(), sign, plan.getName(), rows, subtotal, progress));
        }

        var allRows = collected.stream().flatMap(c -> c.rows().stream()).toList();
        var totalBudget = plans.stream()
            .map(p -> cumulativeBudgetOf(p.plan(), window.getUntil()))
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        var total = aggregate(null, null, allRows, totalBudget, includeCosts);

        // The line a plan's budget consumption is read from: its own subtotal, or the section total
        // for an order-wide plan, which has no subtotal because it is the whole section.
        var groups = collected.stream()
            .map(c -> new BudgetControllingGroup(c.sign(), c.label(), c.budgetId(), c.rows(), c.subtotal(),
                c.progressPercent(),
                computeProgressStatus(c.progressPercent(),
                    budgetUsedPercentOf(orderWide ? total : c.subtotal()))))
            .toList();

        return new BudgetControllingSection(
            orderWide ? SectionKind.ORDER_LEVEL : SectionKind.SUBORDER_LEVEL,
            level,
            period,
            plans.stream().map(p -> p.plan().getName()).toList(),
            deactivated,
            plans.stream().map(p -> p.plan().getValidFrom()).min(naturalOrder()).orElse(null),
            plans.stream().map(p -> p.plan().getValidUntil()).max(naturalOrder()).orElse(null),
            groups, total);
    }

    /**
     * The bookings that belong to no budget: no assignment at all, or one pointing at a plan this
     * evaluation excludes. Both cases have to surface, otherwise hours would silently stop appearing
     * in any number, which is exactly what the explicit assignment must not cost us (#913).
     *
     * <p>A deactivated plan is no longer excluded as soon as it holds something in the window
     * (#1217), so what lands here really is unassigned — which is what the "assign" action of the
     * section offers to fix. The exclusion stays as the net for an assignment that points at a plan
     * whose validity does not reach into the window, a state the assignment rules do not produce.
     */
    private BudgetControllingSection withoutBudgetSection(List<SuborderReadModel> suborders,
                                                          Map<Long, List<ScoredReport>> scored,
                                                          Map<Long, Long> planOfBooking,
                                                          AllocatedFlatRates flatRates,
                                                          ScopeSigns scopeSigns,
                                                          Set<Long> evaluatedPlanIds,
                                                          LocalDate windowStart, boolean includeCosts) {
        var bookedRows = suborders.stream()
            .map(suborder -> row(suborder, reportsOf(suborder, scored, report -> {
                var planId = planOfBooking.get(report.timereportId());
                return planId == null || !evaluatedPlanIds.contains(planId);
            }), includeCosts))
            // Only the booked side counts here: a suborder with planned hours but no unassigned
            // booking has nothing to answer for and would otherwise show up in every evaluation.
            .filter(row -> !row.bookedHours().isZero() || row.hasRevenueBeforeWindow())
            .toList();
        // Flat rates land here for the two reasons a booking does: no plan covers the amount, or
        // several do and none was picked, or the plan holding it is excluded from this evaluation.
        var rows = concat(bookedRows,
            flatRateRows(orphanedFlatRates(flatRates, evaluatedPlanIds), scopeSigns, windowStart, includeCosts));
        if (rows.isEmpty()) {
            return null;
        }
        var total = aggregate(null, null, rows, null, includeCosts);
        // No plan, so no progress either — these bookings answer to nothing that could be behind.
        return new BudgetControllingSection(SectionKind.UNPLANNED, 0, null, List.of(), false, null, null,
            List.of(new BudgetControllingGroup(null, null, null, rows, null, null, null)), total);
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

    private static List<ScoredReport> reportsOf(SuborderReadModel suborder, Map<Long, List<ScoredReport>> scored,
                                                Predicate<ScoredReport> belongsHere) {
        return scored.getOrDefault(suborder.id(), List.of()).stream().filter(belongsHere).toList();
    }

    /**
     * One line of a section. Hours, revenue and cost all describe the evaluated window, and what was
     * earned before it stands next to them as an amount of its own (#779). The budget columns add the
     * two up, so they still read against the whole plan, while profit and margin divide figures that
     * cover the same period — which they did not while the amounts spanned years and the hours one
     * quarter.
     */
    private BudgetControllingRow row(SuborderReadModel suborder, List<ScoredReport> reports, boolean includeCosts) {
        return BudgetControllingRow.builder()
            .sign(suborder.completeOrderSign())
            .label(suborder.shortdescription())
            .plannedHours(suborder.debithours() != null ? suborder.debithours() : Duration.ZERO)
            .revenueBeforeWindowEuro(amountOf(reports, ScoredReport::beforeWindow, ScoredReport::revenue))
            .bookedHours(hoursOf(reports, report -> !report.beforeWindow()))
            .revenueEuro(amountOf(reports, report -> !report.beforeWindow(), ScoredReport::revenue))
            .flatRateRevenueEuro(BigDecimal.ZERO)
            .costEuro(includeCosts
                ? amountOf(reports, report -> !report.beforeWindow(), ScoredReport::cost) : null)
            .build();
    }

    private static Duration hoursOf(List<ScoredReport> reports, Predicate<ScoredReport> selected) {
        return reports.stream().filter(selected)
            .map(ScoredReport::duration)
            .reduce(Duration.ZERO, Duration::plus);
    }

    private static BigDecimal amountOf(List<ScoredReport> reports, Predicate<ScoredReport> selected,
                                       Function<ScoredReport, BigDecimal> amount) {
        return reports.stream().filter(selected).map(amount).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /** One plan of a section before its group is assembled (→ {@link #plannedSection}). */
    private record CollectedPlan(Long budgetId, String sign, String label,
                                 List<BudgetControllingRow> rows, BudgetControllingRow subtotal,
                                 Double progressPercent) {}

    /** The share of its budget a line has consumed, or {@code null} where there is no budget. */
    private static Double budgetUsedPercentOf(BudgetControllingRow row) {
        return row != null && row.hasBudgetPercent() ? row.budgetUsedPercent() : null;
    }

    /**
     * Subtotals and section totals. The summing itself lives on the row (#779), because the total
     * over the sections of an order and the one over the orders of a segment are the same operation
     * and must not be written a second time.
     */
    private BudgetControllingRow aggregate(String sign, String label, List<BudgetControllingRow> rows,
                                           BigDecimal budget, boolean includeCosts) {
        return BudgetControllingRow.sum(sign, label, rows, budget, includeCosts);
    }

    /**
     * @param evaluatedUntil the last day the figures cover (→ {@link #evaluatedUntil(LocalDate)}).
     *                       Carried along so that a view can name its reference date and link to a
     *                       controlling evaluation over the same window instead of a wider one.
     */
    public record UtilizationInfo(BigDecimal budgetEuro, BigDecimal coveredRevenueEuro,
                                  LocalDate evaluatedUntil) {
        public double percent() {
            if (budgetEuro == null || budgetEuro.signum() == 0) return 0.0;
            return coveredRevenueEuro.divide(budgetEuro, 6, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100)).doubleValue();
        }
    }

    /**
     * Utilization of a budget plus the sign and short description of its customer order, both read
     * from the order by id (#1212).
     */
    public record BudgetUtilization(UtilizationInfo info, String customerorderSign, String customerorderDescription) {}

    /** The utilization of a single plan — the same computation as for many, over a list of one. */
    public UtilizationInfo computeUtilizationInfo(OrderBudget budget) {
        return computeUtilizationInfos(List.of(budget)).get(budget.getId()).info();
    }

    /**
     * Utilization for several budgets at once, with the same handful of statements however many
     * budgets there are (#1222): the customer orders, their suborders, their active plans, the rates,
     * the flat rates, and the bookings assigned to the budgets. Loading the customer order, its
     * suborders, its bookings and their assignments once per order made the dashboard grow with the
     * number of orders on it — and the bookings came as entities, dragging day, contract, person and
     * employee order of each along.
     *
     * <p>The bookings are read over their assignment rather than over the customer order: the
     * utilization only ever counts bookings assigned to the plan (#913), so a booking assigned to
     * no plan or to another one never mattered here.
     *
     * <p><b>Authorization.</b> The caller passes plans the current user may see — the dashboard gets
     * them from {@code OrderBudgetService}, the alert job runs as manager — and nothing here widens
     * that set: every statement is restricted to their customer orders or their ids. The per-booking
     * read filter of {@code dailyreport}, which the entity path applied, cannot remove any of these
     * bookings (→ {@code TimereportBudgetAssignmentRepository#findPlanBookings}).
     */
    public Map<Long, BudgetUtilization> computeUtilizationInfos(List<OrderBudget> budgets) {
        if (budgets.isEmpty()) {
            return Map.of();
        }
        // Everything by the id of the order (#1205).
        var orderIds = budgets.stream().map(OrderBudget::getCustomerorderId).distinct().toList();
        var orders = orderIds.isEmpty() ? List.<Customerorder>of() : customerorderService.getCustomerordersByIds(orderIds);
        var pricingLookup = orderPricingService.lookupFor(orderIds);
        var flatRateLookup = orderFlatRateService.lookupFor(orderIds);
        // A description may be missing, so no Collectors.toMap, which rejects null values.
        Map<Long, Customerorder> orderById = new HashMap<>();
        orders.forEach(order -> orderById.put(order.getId(), order));
        var suborders = billableSuborders(orderIds);
        // Every active plan of the orders, not only the ones being asked about: a flat rate amount
        // counts against a plan only if that plan is the single one covering it (#972), and judging
        // that against a filtered set would allocate an ambiguous amount to whichever plan the
        // caller happened to pass.
        var activePlansByOrder = orderIds.isEmpty()
            ? Map.<Long, List<OrderBudget>>of()
            : orderBudgetRepository.findByCustomerorderIdInAndActive(orderIds, TRUE).stream()
                .collect(Collectors.groupingBy(OrderBudget::getCustomerorderId));
        // The window of an order starts with the earliest of its plans asked about, as it did while
        // the bookings were read per order; every plan cuts its own end below.
        Map<Long, LocalDate> fromByOrder = budgets.stream()
            .collect(Collectors.toMap(OrderBudget::getCustomerorderId, OrderBudget::getValidFrom,
                (a, b) -> a.isBefore(b) ? a : b));
        var until = budgets.stream().map(b -> evaluatedUntil(b.getValidUntil())).max(naturalOrder()).orElseThrow();
        var budgetIds = budgets.stream().map(OrderBudget::getId).toList();
        var bookingsByPlan = assignmentRepository.findPlanBookings(budgetIds, until).stream()
            .collect(Collectors.groupingBy(PlanBooking::orderBudgetId));
        var positionOfFlatRate = flatRatePositions();

        Map<Long, BudgetUtilization> result = new LinkedHashMap<>();
        for (var budget : budgets) {
            var orderId = budget.getCustomerorderId();
            var order = orderById.get(orderId);
            result.put(budget.getId(), new BudgetUtilization(
                computeUtilizationInfo(budget, bookingsByPlan.getOrDefault(budget.getId(), List.of()),
                    fromByOrder.get(orderId), suborders, activePlansByOrder.getOrDefault(orderId, List.of()),
                    pricingLookup, flatRateLookup, positionOfFlatRate),
                order == null ? null : order.getSign(),
                order == null ? null : order.getShortdescription()));
        }
        return result;
    }

    /** A suborder whose work is billed, with its customer order and complete order sign. */
    private record BillableSuborder(long customerorderId, String completeOrderSign) {}

    /**
     * The suborders of the orders that earn anything, by id: invoiceable and not hidden, as the
     * utilization has always counted them. The complete order sign walks the parent chain, so it is
     * resolved once per suborder here instead of once per booking.
     */
    private Map<Long, BillableSuborder> billableSuborders(List<Long> customerorderIds) {
        Map<Long, BillableSuborder> billable = new HashMap<>();
        for (var suborder : suborderService.getSubordersByCustomerorderIds(customerorderIds)) {
            if (!suborder.isHide() && suborder.isInvoiceable()) {
                billable.put(suborder.getId(), new BillableSuborder(suborder.getCustomerorder().getId(),
                    suborder.getCompleteOrderSign()));
            }
        }
        return billable;
    }

    /**
     * The end of the window the utilization is measured over: the plan's own end, but never later
     * than today (#972).
     *
     * <p>Dashboard and alerts answer "where does this plan stand", and that question is about the
     * present. Reading a plan to its own end counted what has not happened yet — with hourly work
     * that was rare enough to go unnoticed, because bookings in the future barely exist, but a
     * monthly flat rate made it plain: a retainer running to December contributed all twelve months
     * in June, and the plan looked used up while it was on track.
     *
     * <p>The cut applies to the budget as well, not only to the revenue. An adjustment that takes
     * effect in November has not been granted yet, and counting it today would understate the
     * utilization for the same reason. With both ends cut, the dashboard now says exactly what a
     * controlling evaluation up to today says.
     */
    private static LocalDate evaluatedUntil(LocalDate planUntil) {
        var today = DateUtils.today();
        return planUntil.isBefore(today) ? planUntil : today;
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
    private UtilizationInfo computeUtilizationInfo(OrderBudget budget, List<PlanBooking> bookings,
                                                   LocalDate orderFrom,
                                                   Map<Long, BillableSuborder> suborders,
                                                   List<OrderBudget> activePlansOfOrder,
                                                   OrderPricingLookup pricingLookup,
                                                   OrderFlatRateLookup flatRateLookup,
                                                   Function<OrderFlatRate, Optional<OrderPosition>> positionOfFlatRate) {
        long orderId = budget.getCustomerorderId();
        var until = evaluatedUntil(budget.getValidUntil());

        var revenue = BigDecimal.ZERO;
        for (var booking : bookings) {
            var suborder = suborders.get(booking.suborderId());
            // Work on a suborder that is not invoiceable is never billed, whatever rate matches, and
            // a hidden suborder never counted here. A booking of another order does not belong to
            // the plan, whatever its assignment says.
            if (suborder == null || suborder.customerorderId() != orderId) {
                continue;
            }
            // The date is checked here, not in the query: the query reads up to the latest end of
            // all plans asked about, and this is the one place that decides what counts towards
            // this plan.
            if (booking.day().isBefore(orderFrom) || booking.day().isAfter(until)) {
                continue;
            }
            // The booking belongs to this plan, so this plan is what a plan-bound rate is resolved
            // against (#1065).
            revenue = revenue.add(rateOf(booking.duration(), booking.employeeId(), booking.day(),
                suborder.customerorderId(), suborder.completeOrderSign(), budget.getId(), pricingLookup));
        }
        // The flat rates due by now, allocated through the same rule the sections use — an amount
        // several plans could hold counts against none of them.
        var flatRates = allocate(
            flatRateLookup.dueAmounts(orderId, budget.getValidFrom(), until),
            activePlansOfOrder, positionOfFlatRate);
        for (var dueAmount : flatRates.of(budget.getId())) {
            revenue = revenue.add(dueAmount.amount());
        }
        // Nothing can have been consumed before the plan started — an assignment always sits inside
        // the plan's period — so the window needs no start of its own.
        return new UtilizationInfo(cumulativeBudgetOf(budget, until), revenue, until);
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

    /**
     * How far each of the given plans has come, by plan id — plans without a progress mode and plans
     * whose progress cannot be determined are absent rather than mapped to {@code null}.
     *
     * <p>One holiday query for all of them instead of one per plan: the dashboard asks this for
     * every active plan at once.
     *
     * <p>A plan that runs open-ended has no time progress: the share of its running time that has
     * elapsed would be measured against the sentinel end 31.12.2999, which says "no end", not a
     * date. Such a plan is simply absent here, and the dashboard shows no progress for it.
     */
    public Map<Long, Double> computeProgressPercents(List<OrderBudget> budgets) {
        var relevant = budgets.stream()
            .filter(b -> b.getProgressMode() != null)
            .filter(b -> b.getProgressMode() != ProgressMode.TIME || hasEnd(b))
            .toList();
        if (relevant.isEmpty()) {
            return Map.of();
        }
        var today = DateUtils.today();
        var from = relevant.stream().map(OrderBudget::getValidFrom).min(naturalOrder()).orElse(today);
        var until = relevant.stream().filter(BudgetControllingService::hasEnd)
            .map(OrderBudget::getValidUntil).max(naturalOrder()).orElse(today);
        Set<LocalDate> holidays = publicholidayService.getPublicHolidayDatesBetween(from, until);
        // The scope entries of all plans measured by scope in one statement, rather than one lazy
        // load per plan (#1222).
        var byScope = relevant.stream()
            .filter(b -> b.getProgressMode() == ProgressMode.SCOPE)
            .map(OrderBudget::getId)
            .toList();
        if (!byScope.isEmpty()) {
            orderBudgetRepository.findWithScopeEntriesByIdIn(byScope);
        }

        var progressPercents = new LinkedHashMap<Long, Double>();
        for (var budget : relevant) {
            var progress = computeProgress(budget, budget.getValidFrom(), budget.getValidUntil(),
                today, holidays);
            if (progress != null) {
                progressPercents.put(budget.getId(), progress);
            }
        }
        return progressPercents;
    }

    private static boolean hasEnd(OrderBudget budget) {
        return budget.getValidUntil() != null
            && budget.getValidUntil().isBefore(LocalDateRange.FINIT_UNTIL_BOUNDARY);
    }

    /**
     * Where a plan stands against its own progress: {@code BEHIND} once it has consumed noticeably
     * more of its budget than of its planned progress. Public and static because the dashboard
     * judges its rows by exactly this rule — two thresholds that drift apart would have the two
     * views disagree about the same plan.
     */
    public static ProgressStatus computeProgressStatus(Double progressPercent, Double budgetUsedPercent) {
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
