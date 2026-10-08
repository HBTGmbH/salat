package de.hbt.salat.budget.service;

import static de.hbt.salat.common.exception.ServiceFeedbackMessage.info;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import de.hbt.salat.auth.domain.Authorized;
import de.hbt.salat.budget.auth.BudgetAuthorization;
import de.hbt.salat.budget.domain.BudgetScope;
import de.hbt.salat.budget.domain.CalculationLineData;
import de.hbt.salat.budget.domain.CostCategory;
import de.hbt.salat.budget.domain.EmployeeCost;
import de.hbt.salat.budget.domain.EmployeeCostLookup;
import de.hbt.salat.budget.domain.FixedPriceCalculation;
import de.hbt.salat.budget.domain.FixedPriceEvaluation;
import de.hbt.salat.budget.domain.FixedPriceRateConflict;
import de.hbt.salat.budget.domain.FlatRateAllocation;
import de.hbt.salat.budget.domain.FlatRateDueAmount;
import de.hbt.salat.budget.domain.OrderBudget;
import de.hbt.salat.budget.domain.OrderBudgetBinding.PositionedSuborder;
import de.hbt.salat.budget.domain.OrderBudgetCalculation;
import de.hbt.salat.budget.domain.OrderFlatRate;
import de.hbt.salat.budget.domain.OrderPosition;
import de.hbt.salat.budget.domain.OrderPricing;
import de.hbt.salat.budget.domain.OrderPricingData;
import de.hbt.salat.budget.domain.PlanBooking;
import de.hbt.salat.budget.persistence.CostCategoryRepository;
import de.hbt.salat.budget.persistence.EmployeeCostAssignmentRepository;
import de.hbt.salat.budget.persistence.EmployeeCostRepository;
import de.hbt.salat.budget.persistence.MasterDataReferences;
import de.hbt.salat.budget.persistence.OrderBudgetRepository;
import de.hbt.salat.budget.persistence.OrderPricingRepository;
import de.hbt.salat.budget.persistence.TimereportBudgetAssignmentRepository;
import de.hbt.salat.common.exception.BusinessRuleException;
import de.hbt.salat.common.exception.ErrorCode;
import de.hbt.salat.common.exception.InvalidDataException;
import de.hbt.salat.common.exception.ServiceFeedbackMessage;
import de.hbt.salat.common.util.DateUtils;
import de.hbt.salat.order.domain.SuborderReadModel;
import de.hbt.salat.order.service.SuborderService;

/**
 * Fixed-price plans (#1404, #1405): their calculation, what was booked against it, what an hour of
 * them is worth, and the customer rates that would count their revenue twice.
 *
 * <p><b>Cost categories without the cost privilege.</b> Which category a booking belongs to is read
 * through {@link EmployeeCostLookup}, built here from the repositories rather than through
 * {@code EmployeeCostService}, which is reserved for managers. The calculation shows hours and
 * categories to everybody who may see the plan — the order responsible tracks it — and cost amounts
 * only with {@code includeCosts}. Only aggregates per line leave this class, never a person.
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
@Authorized
public class FixedPriceCalculationService {

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    private final OrderBudgetRepository orderBudgetRepository;
    private final TimereportBudgetAssignmentRepository assignmentRepository;
    private final OrderPricingRepository orderPricingRepository;
    private final EmployeeCostRepository employeeCostRepository;
    private final EmployeeCostAssignmentRepository employeeCostAssignmentRepository;
    private final CostCategoryRepository categoryRepository;
    private final SuborderService suborderService;
    private final OrderFlatRateService orderFlatRateService;
    private final OrderPositions orderPositions;
    private final BudgetAuthorization budgetAuthorization;
    private final MasterDataReferences masterDataReferences;

    /**
     * Where a fixed-price plan stands; empty for any other plan.
     *
     * @param until        the last day whose bookings count — the end of the evaluated window in the
     *                     controlling, the end of the plan on its own page
     * @param includeCosts whether cost figures are reported (managers only)
     */
    public Optional<FixedPriceEvaluation> evaluate(OrderBudget plan, LocalDate until, boolean includeCosts) {
        budgetAuthorization.checkAuthorized(plan);
        if (!plan.isFixedPrice()) {
            return Optional.empty();
        }
        var calculation = calculate(plan, assignmentRepository.findPlanBookings(List.of(plan.getId()), until),
            suborderReadModels(plan.getCustomerorderId()), costLookup(), includeCosts);

        // What has been billed of the price: the flat rates fallen due, never later than today (#1436).
        var billedUntil = BudgetControllingService.notAfterToday(until);
        var billed = flatRatesOf(plan).stream()
            .filter(amount -> !amount.due().isAfter(billedUntil))
            .map(FlatRateDueAmount::amount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        // The progress of today, as the controlling reads it for every plan (→ computeProgress).
        var progress = plan.scopeProgressPercentOn(DateUtils.today());
        var status = BudgetControllingService.computeProgressStatus(progress, calculation.total().consumedPercent());
        return Optional.of(new FixedPriceEvaluation(calculation.rows(), calculation.total(),
            plan.totalOfAdjustments(), billed, progress, status, includeCosts));
    }

    /**
     * How much of its calculated hours each fixed-price plan has consumed, by plan id (#1404) — what
     * the dashboard and the budget alert judge a fixed price by, instead of the euro budget its
     * instalments fill up. The same calculation as {@link #evaluate}, up to the end of the plan but
     * never later than today, as the dashboard reads every plan ({@code evaluatedUntil}).
     *
     * <p>A plan that is not a fixed price, or one without calculated hours, is absent: there is
     * nothing its consumption could be measured against, so it gets neither a status nor an alert —
     * the way a plan without a budget amount gets none.
     *
     * <p>One cost lookup and one read of the bookings for all plans. <b>Authorization:</b> the caller
     * passes plans the current user may see — the dashboard gets them from {@code OrderBudgetService},
     * the alert job runs as manager — as in {@code BudgetControllingService#computeUtilizationInfos}.
     */
    public Map<Long, Double> getHoursConsumedPercents(Collection<OrderBudget> plans) {
        var calculated = plans.stream()
            .filter(OrderBudget::isFixedPrice)
            .filter(plan -> !plan.getCalculations().isEmpty())
            .toList();
        if (calculated.isEmpty()) {
            return Map.of();
        }
        var today = DateUtils.today();
        Map<Long, LocalDate> untilByPlan = new HashMap<>();
        calculated.forEach(plan -> untilByPlan.put(plan.getId(),
            plan.getValidUntil().isBefore(today) ? plan.getValidUntil() : today));
        var latest = untilByPlan.values().stream().max(Comparator.naturalOrder()).orElse(today);
        var bookingsByPlan = assignmentRepository.findPlanBookings(untilByPlan.keySet(), latest).stream()
            .filter(booking -> !booking.day().isAfter(untilByPlan.get(booking.orderBudgetId())))
            .collect(Collectors.groupingBy(PlanBooking::orderBudgetId));
        var costLookup = costLookup();
        Map<Long, Map<Long, SuborderReadModel>> subordersByOrder = new HashMap<>();
        Map<Long, Double> consumed = new LinkedHashMap<>();
        for (var plan : calculated) {
            var suborders = subordersByOrder.computeIfAbsent(plan.getCustomerorderId(), this::suborderReadModels);
            var percent = calculate(plan, bookingsByPlan.getOrDefault(plan.getId(), List.of()), suborders,
                costLookup, false).total().consumedPercent();
            if (percent != null) {
                consumed.put(plan.getId(), percent);
            }
        }
        return consumed;
    }

    /** The calculation of the plan with the given bookings set against it — the one rule for every view. */
    private static FixedPriceCalculation.Result calculate(OrderBudget plan, List<PlanBooking> planBookings,
                                                          Map<Long, SuborderReadModel> suborders,
                                                          EmployeeCostLookup costLookup, boolean includeCosts) {
        long customerorderId = plan.getCustomerorderId();
        var lines = plan.getCalculations().stream()
            .map(line -> lineOf(plan, line, suborders, costLookup, includeCosts))
            .toList();
        var bookings = planBookings.stream()
            .map(booking -> bookingOf(booking, customerorderId, suborders, costLookup, includeCosts))
            .toList();
        return FixedPriceCalculation.evaluate(lines, bookings, includeCosts);
    }

    /** Every suborder of the order by id, hidden ones included — bookings stay on a hidden suborder. */
    private Map<Long, SuborderReadModel> suborderReadModels(long customerorderId) {
        Map<Long, SuborderReadModel> suborders = new HashMap<>();
        suborderService.getAllSuborderReadModelsByCustomerorderId(customerorderId)
            .forEach(suborder -> suborders.put(suborder.id(), suborder));
        return suborders;
    }

    private EmployeeCostLookup costLookup() {
        return EmployeeCostLookup.of(employeeCostAssignmentRepository.findAllByOrderByCategoryNameAscIdAsc(),
            employeeCostRepository.findAllByOrderByCategoryNameAscValidFromAsc());
    }

    /**
     * The calculated hours are priced at the rate the category has at the start of the plan — when
     * the fixed price was calculated — and, where the category had none then, at today's.
     */
    private static FixedPriceCalculation.Line lineOf(OrderBudget plan, OrderBudgetCalculation line,
                                                     Map<Long, SuborderReadModel> suborders,
                                                     EmployeeCostLookup costLookup, boolean includeCosts) {
        var suborder = suborders.get(line.getSuborderId());
        var sign = suborder != null ? suborder.completeOrderSign() : line.getSuborder().getCompleteOrderSign();
        var label = suborder != null ? suborder.shortdescription() : line.getSuborder().getShortdescription();
        BigDecimal costPerHour = null;
        if (includeCosts) {
            costPerHour = costLookup.findCategoryCost(line.getCategoryId(), plan.getValidFrom())
                .or(() -> costLookup.findCategoryCost(line.getCategoryId(), DateUtils.today()))
                .map(cost -> new BigDecimal(cost.getCostCentsPerHour()).movePointLeft(2))
                .orElse(null);
        }
        return new FixedPriceCalculation.Line(line.getId(), line.getSuborderId(), sign, label,
            line.getCategoryId(), line.getCategory().getName(), line.getCalculatedHours(), costPerHour);
    }

    /** A booking with the cost category of its person on its day (→ {@link EmployeeCostLookup}). */
    private static FixedPriceCalculation.Booking bookingOf(PlanBooking booking, long customerorderId,
                                                           Map<Long, SuborderReadModel> suborders,
                                                           EmployeeCostLookup costLookup, boolean includeCosts) {
        var suborder = suborders.get(booking.suborderId());
        var path = suborder != null ? suborder.path() : List.of(booking.suborderId());
        var cost = costLookup.findEffectiveCost(booking.employeeId(), customerorderId, booking.suborderId(),
            booking.day());
        BigDecimal costEuro = null;
        if (includeCosts) {
            costEuro = cost.map(c -> hoursOf(booking.duration()).multiply(new BigDecimal(c.getCostCentsPerHour()))
                    .movePointLeft(2))
                .orElse(BigDecimal.ZERO);
        }
        return new FixedPriceCalculation.Booking(booking.suborderId(), path,
            suborder != null ? suborder.completeOrderSign() : null,
            suborder != null ? suborder.shortdescription() : null,
            booking.duration(),
            cost.map(c -> c.getCategory().getId()).orElse(null),
            cost.map(EmployeeCost::getName).orElse(null),
            costEuro);
    }

    /**
     * Every flat rate amount the plan holds over its whole validity — what is billed of the fixed
     * price — allocated by the rule the controlling uses (→ {@link FlatRateAllocation}), against every
     * plan of the order.
     */
    private List<FlatRateDueAmount> flatRatesOf(OrderBudget plan) {
        long customerorderId = plan.getCustomerorderId();
        var lookup = orderFlatRateService.lookupFor(List.of(customerorderId));
        var plans = orderBudgetRepository.findByCustomerorderId(customerorderId);
        Map<Long, Optional<OrderPosition>> positions = new HashMap<>();
        Function<OrderFlatRate, Optional<OrderPosition>> positionOf =
            flatRate -> positions.computeIfAbsent(flatRate.getId(), id -> orderPositions.of(flatRate));
        return lookup.dueAmounts(customerorderId, plan.getValidFrom(), plan.getValidUntil()).stream()
            .filter(amount -> FlatRateAllocation.uniquePlanFor(amount, plans, positionOf)
                .map(holder -> holder.getId().equals(plan.getId()))
                .orElse(false))
            .toList();
    }

    private static BigDecimal hoursOf(Duration duration) {
        return BigDecimal.valueOf(duration.toMinutes()).divide(BigDecimal.valueOf(60), 6, RoundingMode.HALF_UP);
    }

    // ---------------------------------------------------------------------------------------------
    // Maintaining the calculation
    // ---------------------------------------------------------------------------------------------

    /**
     * The suborders a line of the plan's calculation may name: the plan's scope, hidden ones left out
     * unless the line already names one (→ AGENTS.md, „The hide Flag").
     */
    public List<SuborderReadModel> getCalculableSuborders(OrderBudget plan, Long keepSuborderId) {
        budgetAuthorization.checkAuthorized(plan);
        return suborderService.getAllSuborderReadModelsByCustomerorderId(plan.getCustomerorderId()).stream()
            .filter(suborder -> !suborder.hide() || Objects.equals(suborder.id(), keepSuborderId))
            .filter(suborder -> BudgetScope.covers(plan, OrderPosition.of(suborder)))
            .toList();
    }

    /** Every cost category, by name — what a line of the calculation can be calculated with. */
    public List<CostCategory> getCategories() {
        return categoryRepository.findAllByOrderByNameAsc();
    }

    /**
     * Adds a line to the calculation of a fixed-price plan, or changes one ({@code data.id()}).
     * The suborder has to lie in the plan's scope, and suborder and category together name at most one
     * line per plan.
     */
    @Authorized(requiresManager = true)
    @Transactional
    public void storeLine(long planId, CalculationLineData data) {
        var plan = fixedPricePlan(planId);
        // Read as a value of the plan's order (ADR-0021): a suborder of another order is not found
        // here, and it could not lie in the plan's scope anyway.
        var suborder = data.suborderId() == null ? null
            : suborderService.getAllSuborderReadModelsByCustomerorderId(plan.getCustomerorderId()).stream()
                .filter(candidate -> candidate.id() == data.suborderId())
                .findFirst().orElse(null);
        if (suborder == null) {
            throw new InvalidDataException(ErrorCode.SO_NOT_FOUND, data.suborderId());
        }
        if (!BudgetScope.covers(plan, OrderPosition.of(suborder))) {
            throw new BusinessRuleException(ErrorCode.BU_CALCULATION_SUBORDER_NOT_IN_SCOPE,
                suborder.completeOrderSign(), plan.getName());
        }
        var category = data.categoryId() == null ? null : categoryRepository.findById(data.categoryId()).orElse(null);
        if (category == null) {
            throw new InvalidDataException(ErrorCode.BU_EMPLOYEE_COST_NAME_UNKNOWN, data.categoryId());
        }
        var minutes = data.hours() == null ? 0 : data.hours().multiply(BigDecimal.valueOf(60))
            .setScale(0, RoundingMode.HALF_UP).longValue();
        if (minutes <= 0) {
            throw new BusinessRuleException(ErrorCode.BU_CALCULATION_HOURS_REQUIRED);
        }
        var duplicate = plan.getCalculations().stream()
            .anyMatch(line -> !line.getId().equals(data.id())
                && line.getSuborderId() == suborder.id()
                && line.getCategoryId().equals(category.getId()));
        if (duplicate) {
            throw new BusinessRuleException(ErrorCode.BU_CALCULATION_LINE_EXISTS,
                suborder.completeOrderSign(), category.getName());
        }
        var line = data.id() == null ? newLine(plan) : existingLine(plan, data.id());
        line.setSuborder(masterDataReferences.suborder(suborder.id()));
        line.setCategory(category);
        line.setCalculatedHours(Duration.ofMinutes(minutes));
        orderBudgetRepository.save(plan);
    }

    @Authorized(requiresManager = true)
    @Transactional
    public void removeLine(long planId, long lineId) {
        var plan = planById(planId);
        var line = existingLine(plan, lineId);
        plan.getCalculations().remove(line);
        orderBudgetRepository.save(plan);
    }

    private OrderBudget planById(long planId) {
        var plan = orderBudgetRepository.findById(planId)
            .orElseThrow(() -> new InvalidDataException(ErrorCode.BU_BUDGET_NOT_FOUND, planId));
        budgetAuthorization.checkAuthorized(plan);
        return plan;
    }

    private OrderBudget fixedPricePlan(long planId) {
        var plan = planById(planId);
        if (!plan.isFixedPrice()) {
            throw new BusinessRuleException(ErrorCode.BU_CALCULATION_NOT_FIXED_PRICE, plan.getName());
        }
        return plan;
    }

    private static OrderBudgetCalculation newLine(OrderBudget plan) {
        var line = new OrderBudgetCalculation();
        line.setOrderBudget(plan);
        plan.getCalculations().add(line);
        return line;
    }

    private static OrderBudgetCalculation existingLine(OrderBudget plan, long lineId) {
        return plan.getCalculations().stream()
            .filter(line -> line.getId() != null && line.getId() == lineId)
            .findFirst()
            .orElseThrow(() -> new InvalidDataException(ErrorCode.BU_CALCULATION_LINE_NOT_FOUND, lineId));
    }

    // ---------------------------------------------------------------------------------------------
    // Customer rates in the scope of a fixed price (→ FixedPriceRateConflict)
    // ---------------------------------------------------------------------------------------------

    /** The customer rates above 0 EUR that apply in the scope of the plan; empty for any other plan. */
    public List<OrderPricing> getConflictingRates(OrderBudget plan) {
        budgetAuthorization.checkAuthorized(plan);
        return conflictingRates(plan);
    }

    /** What the success message of saving the plan names below itself. */
    public List<ServiceFeedbackMessage> noticesForPlan(long planId) {
        var plan = planById(planId);
        return conflictingRates(plan).stream()
            .map(rate -> notice(plan, rate.getSuborderSign(), rate.getPriceCentsPerHour(), rate.getValidFrom()))
            .toList();
    }

    /** What the success message of saving the rate names below itself. */
    public List<ServiceFeedbackMessage> noticesForRate(OrderPricingData rate) {
        if (rate.customerorderId() == null) {
            return List.of();
        }
        var plans = orderBudgetRepository.findByCustomerorderId(rate.customerorderId()).stream()
            .filter(OrderBudget::isFixedPrice)
            .filter(budgetAuthorization::isAuthorized)
            .toList();
        if (plans.isEmpty()) {
            return List.of();
        }
        var suborders = positionedSuborders(rate.customerorderId());
        var until = rate.validUntil() != null ? rate.validUntil() : OPEN_END;
        return plans.stream()
            .filter(plan -> FixedPriceRateConflict.conflicts(plan, rate.customerorderId(), rate.suborderSign(),
                rate.orderBudgetId(), rate.priceCentsPerHour(), rate.validFrom(), until, suborders))
            .map(plan -> notice(plan, rate.suborderSign(), rate.priceCentsPerHour(), rate.validFrom()))
            .toList();
    }

    /**
     * For each of the given rates that applies in the scope of a fixed-price plan, the names of those
     * plans — what the list of customer rates marks. The fixed-price plans are read once, and the
     * suborders only of the orders that have one.
     */
    public Map<Long, List<String>> getFixedPricePlanNamesByRateId(Collection<OrderPricing> rates) {
        var plansByOrder = orderBudgetRepository.findFixedPrice().stream()
            .filter(budgetAuthorization::isAuthorized)
            .collect(Collectors.groupingBy(OrderBudget::getCustomerorderId));
        Map<Long, List<PositionedSuborder>> suborders = new HashMap<>();
        Map<Long, List<String>> result = new LinkedHashMap<>();
        for (var rate : rates) {
            var plans = plansByOrder.getOrDefault(rate.getCustomerorderId(), List.of());
            if (plans.isEmpty()) {
                continue;
            }
            var positioned = suborders.computeIfAbsent(rate.getCustomerorderId(), this::positionedSuborders);
            var names = plans.stream()
                .filter(plan -> FixedPriceRateConflict.conflicts(plan, rate, positioned))
                .map(OrderBudget::getName)
                .sorted()
                .toList();
            if (!names.isEmpty()) {
                result.put(rate.getId(), names);
            }
        }
        return result;
    }

    private static final LocalDate OPEN_END = LocalDate.of(2999, 12, 31);

    private List<OrderPricing> conflictingRates(OrderBudget plan) {
        if (!plan.isFixedPrice()) {
            return List.of();
        }
        var suborders = positionedSuborders(plan.getCustomerorderId());
        return orderPricingRepository.findByCustomerorderIdOrderByValidFromAsc(plan.getCustomerorderId()).stream()
            .filter(rate -> FixedPriceRateConflict.conflicts(plan, rate, suborders))
            .sorted(Comparator.comparing(OrderPricing::getValidFrom))
            .toList();
    }

    /**
     * The visible suborders of the order with sign and position — the set the binding of a rate to a
     * plan is judged against (→ {@code OrderPricingService#orderOf}).
     */
    private List<PositionedSuborder> positionedSuborders(long customerorderId) {
        return suborderService.getSuborderReadModelsByCustomerorderId(customerorderId).stream()
            .map(suborder -> new PositionedSuborder(suborder.completeOrderSign(), OrderPosition.of(suborder)))
            .toList();
    }

    private static ServiceFeedbackMessage notice(OrderBudget plan, String suborderPattern, int priceCentsPerHour,
                                                 LocalDate validFrom) {
        // A rate without pattern prices the whole order, and the order's sign is what names that.
        return info(ErrorCode.BU_FIXED_PRICE_WITH_HOURLY_RATE, plan.getName(),
            suborderPattern == null || suborderPattern.isBlank() ? plan.getCustomerorder().getSign() : suborderPattern,
            new BigDecimal(priceCentsPerHour).movePointLeft(2), validFrom.format(DAY));
    }

}
