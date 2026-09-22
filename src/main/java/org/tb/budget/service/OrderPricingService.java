package org.tb.budget.service;

import static java.util.Comparator.comparing;
import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toMap;
import static org.apache.commons.lang3.StringUtils.trimToNull;

import static java.lang.Boolean.TRUE;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.StreamSupport;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.tb.auth.domain.Authorized;
import org.tb.budget.auth.BudgetAuthorization;
import org.tb.budget.domain.OrderBudget;
import org.tb.budget.domain.OrderBudgetBinding;
import org.tb.budget.domain.OrderPricing;
import org.tb.budget.domain.OrderPricingData;
import org.tb.budget.domain.OrderPricingDeviation;
import org.tb.budget.domain.OrderPricingLookup;
import org.tb.budget.domain.OrderPricingRow;
import org.tb.budget.persistence.OrderBudgetRepository;
import org.tb.budget.persistence.OrderPricingRepository;
import org.tb.common.exception.BusinessRuleException;
import org.tb.common.exception.ErrorCode;
import org.tb.common.exception.InvalidDataException;
import org.tb.employee.service.EmployeeService;
import org.tb.order.domain.Customerorder;
import org.tb.order.domain.Suborder;
import org.tb.order.service.CustomerorderService;
import org.tb.order.service.SuborderService;

@Service
@Transactional
@RequiredArgsConstructor
@Authorized
public class OrderPricingService {

    /** An open rate end, stored as a sentinel rather than as {@code null}. */
    private static final LocalDate OPEN_END = LocalDate.of(2999, 12, 31);

    private final OrderPricingRepository orderPricingRepository;
    private final OrderBudgetRepository orderBudgetRepository;
    private final SuborderService suborderService;
    private final CustomerorderService customerorderService;
    private final EmployeeService employeeService;
    private final BudgetAuthorization budgetAuthorization;

    @Transactional(readOnly = true)
    public List<OrderPricing> getAll() {
        return orderPricingRepository.findAllByOrderByCustomerorderSignAscValidFromAsc();
    }

    /**
     * The rows of the list view (#949, #957), optionally narrowed to one customer order. Two filters
     * apply independently (→ AGENTS.md, "List View Filter Toggles"): unless asked otherwise, rates
     * that have expired themselves are left out — see {@link OrderPricing#getCurrentlyValid()} —
     * and so are the rates of orders whose own validity has expired.
     *
     * <p>Every row carries how its validity disagrees with the order's (→
     * {@link OrderPricingDeviation}). The disagreement is judged against <em>all</em> stored rates of
     * the order, not against the filtered ones: a hidden expired rate still covers the period it
     * covered, and judging coverage by what the list happens to show would invent gaps.
     */
    @Transactional(readOnly = true)
    public List<OrderPricingRow> getRows(String customerorderSign, boolean showInactive,
                                         boolean showExpiredOrders) {
        var sign = trimToNull(customerorderSign);
        var pricings = sign == null ? getAll() : getByCustomerorderSign(sign);
        var ordersBySign = ordersOf(pricings);
        var coverage = OrderPricingLookup.of(pricings);
        var knownEmployeeSigns = employeeService.getAllEmployeeSigns();
        var planNames = planNamesOf(pricings);
        return pricings.stream()
            .filter(pricing -> showInactive || pricing.getCurrentlyValid())
            .map(pricing -> row(pricing, ordersBySign.get(pricing.getCustomerorderSign()), coverage,
                knownEmployeeSigns, planNames))
            .filter(row -> showExpiredOrders || orderStillValid(row))
            .toList();
    }

    private static OrderPricingRow row(OrderPricing pricing, Customerorder order,
                                       OrderPricingLookup coverage, Set<String> knownEmployeeSigns,
                                       Map<Long, String> planNames) {
        // Most rates carry no plan at all, and an immutable map refuses a null key outright.
        var planId = pricing.getOrderBudgetId();
        return new OrderPricingRow(pricing, order, OrderPricingDeviation.of(pricing, order, coverage),
            employeeUnknown(pricing, knownEmployeeSigns), planId == null ? null : planNames.get(planId));
    }

    /**
     * The names of the plans the given rates are bound to, by id. One query for the whole list
     * rather than a lazy load per row (#1065).
     */
    private Map<Long, String> planNamesOf(List<OrderPricing> pricings) {
        var ids = pricings.stream().map(OrderPricing::getOrderBudgetId).filter(Objects::nonNull)
            .distinct().toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        return StreamSupport.stream(orderBudgetRepository.findAllById(ids).spliterator(), false)
            .collect(toMap(OrderBudget::getId, OrderBudget::getName, (first, second) -> first));
    }

    /**
     * A rate naming a sign nobody carries is a leftover of #966 — since a sign change is followed,
     * it can only come from before. It resolves to nothing and lets the work fall back to the
     * order-wide rate, so the list marks it rather than leaving it to be discovered in a total.
     */
    private static boolean employeeUnknown(OrderPricing pricing, Set<String> knownEmployeeSigns) {
        var sign = pricing.getEmployeeSign();
        return sign != null && !sign.isBlank() && !knownEmployeeSigns.contains(sign);
    }

    private Map<String, Customerorder> ordersOf(List<OrderPricing> pricings) {
        var signs = pricings.stream().map(OrderPricing::getCustomerorderSign).distinct().toList();
        return customerorderService.getCustomerordersBySigns(signs).stream()
            .collect(toMap(Customerorder::getSign, identity(), (first, second) -> first));
    }

    /**
     * A rate whose order no longer exists stays visible: the order is the only way into the rate, so
     * hiding it would put the rate out of reach of the user interface for good.
     */
    private static boolean orderStillValid(OrderPricingRow row) {
        return row.customerorder() == null || row.customerorder().getCurrentlyValid();
    }

    /** The customer orders that have at least one pricing — the filter options of the list view. */
    @Transactional(readOnly = true)
    public List<String> getCustomerorderSignsWithPricing() {
        return orderPricingRepository.findDistinctCustomerorderSigns();
    }

    @Transactional(readOnly = true)
    public OrderPricing getById(long id) {
        return orderPricingRepository.findById(id)
            .orElseThrow(() -> new InvalidDataException(ErrorCode.BU_PRICING_NOT_FOUND, id));
    }

    @Transactional(readOnly = true)
    public List<OrderPricing> getByCustomerorderSign(String customerorderSign) {
        return orderPricingRepository.findByCustomerorderSignOrderByValidFromAsc(customerorderSign);
    }

    /**
     * The rates bound to one budget plan (#1065) — what the plan's detail page lists, so that a
     * condition negotiated for this work package is visible where the work package is.
     */
    @Transactional(readOnly = true)
    public List<OrderPricing> getByOrderBudgetId(long orderBudgetId) {
        return orderPricingRepository.findByOrderBudgetId(orderBudgetId);
    }

    /**
     * Loads the pricings of the given customer orders into an in-memory lookup, which resolves the
     * whole matching hierarchy. Rates are resolved once per time report, so they must not be
     * resolved by query.
     */
    @Transactional(readOnly = true)
    public OrderPricingLookup lookupFor(Collection<String> customerorderSigns) {
        if (customerorderSigns.isEmpty()) {
            return OrderPricingLookup.of(List.of());
        }
        return OrderPricingLookup.of(orderPricingRepository.findByCustomerorderSignInOrderByIdAsc(customerorderSigns));
    }

    /**
     * The budget plans a rate with this scope and validity may be bound to (#1065) — the options of
     * the select, and the very same set the saving judges by, so the two cannot disagree.
     *
     * <p>Only active plans are offered: no booking is assigned to an inactive plan, so a rate bound
     * to one would apply to nobody. The plan the rate already stores stays in the list regardless,
     * the way a stored record always survives a filter that would drop it (→ AGENTS.md, "The
     * {@code hide} Flag") — otherwise deactivating a plan would silently rewrite the rate hanging
     * off it on the next save.
     *
     * @param keepPlanId the plan the rate being edited already stores, or {@code null} when creating
     */
    @Transactional(readOnly = true)
    public List<OrderBudget> getSelectablePlans(String customerorderSign, String suborderPattern,
                                                LocalDate validFrom, LocalDate validUntil,
                                                Long keepPlanId) {
        var sign = trimToNull(customerorderSign);
        if (sign == null || validFrom == null) {
            return List.of();
        }
        var until = validUntil != null ? validUntil : OPEN_END;
        // Period first, scope second: the scope check is the one that has to read the suborders,
        // and where no plan survives the dates there is nothing left to read them for.
        var candidates = orderBudgetRepository.findByCustomerorderSign(sign).stream()
            .filter(budgetAuthorization::isAuthorized)
            .filter(plan -> TRUE.equals(plan.getActive()) || Objects.equals(plan.getId(), keepPlanId))
            .filter(plan -> OrderBudgetBinding.periodsOverlap(plan, validFrom, until))
            .toList();
        if (candidates.isEmpty()) {
            return List.of();
        }
        var suborderSigns = suborderSignsOf(sign);
        return candidates.stream()
            .filter(plan -> OrderBudgetBinding.scopeMeetsPattern(plan, sign, trimToNull(suborderPattern),
                suborderSigns))
            .sorted(comparing(OrderBudget::getValidFrom).thenComparing(OrderBudget::getName))
            .toList();
    }

    @Authorized(requiresManager = true)
    public void save(OrderPricingData data) {
        var validUntil = data.validUntil() != null ? data.validUntil() : OPEN_END;
        checkCustomerorderExists(data.customerorderSign());
        checkEmployeeExists(data.employeeSign());
        checkSuborderPatternMatches(data.customerorderSign(), data.suborderSign());
        var plan = resolvePlan(data, validUntil);
        checkNoOverlap(data.customerorderSign(), data.suborderSign(), data.employeeSign(),
            data.orderBudgetId(), data.validFrom(), validUntil, null);
        var pricing = new OrderPricing();
        apply(pricing, data, plan);
        orderPricingRepository.save(pricing);
    }

    /**
     * The customer order is deliberately not checked here: a rate references its order by sign and
     * outlives it (#957, → {@code CustomerorderFilterOption}). Demanding the order on every edit
     * would leave a rate whose order is gone only deletable, and editing it is how it gets corrected.
     */
    @Authorized(requiresManager = true)
    public void update(long id, OrderPricingData data) {
        var validUntil = data.validUntil() != null ? data.validUntil() : OPEN_END;
        checkEmployeeExists(data.employeeSign());
        checkSuborderPatternMatches(data.customerorderSign(), data.suborderSign());
        var plan = resolvePlan(data, validUntil);
        checkNoOverlap(data.customerorderSign(), data.suborderSign(), data.employeeSign(),
            data.orderBudgetId(), data.validFrom(), validUntil, id);
        var pricing = getById(id);
        apply(pricing, data, plan);
        orderPricingRepository.save(pricing);
    }

    @Authorized(requiresManager = true)
    public void delete(long id) {
        orderPricingRepository.deleteById(id);
    }

    /**
     * Carries every rate of {@code oldSign} over to {@code newSign} (#966). Driven by the event of
     * the employee module, where changing a sign takes a manager.
     */
    public void movePricingsToSign(String oldSign, String newSign) {
        orderPricingRepository.updateEmployeeSign(oldSign, newSign);
    }

    /**
     * A rate names its employee by sign, so a typo or a post that bypasses the select puts a sign
     * into the record that no person carries (#958). Such a rate never matches during controlling
     * and the work silently falls back to the order-wide rate, so the sign is refused when written.
     *
     * <p>No sign at all is the normal case: the rate then applies to everyone on the order.
     */
    private void checkEmployeeExists(String employeeSign) {
        if (employeeSign != null && employeeService.getEmployeeBySign(employeeSign) == null) {
            throw new InvalidDataException(ErrorCode.BU_EMPLOYEE_SIGN_UNKNOWN, employeeSign);
        }
    }

    /** Only on create — see {@link #update} for why an edit must not insist on the order. */
    private void checkCustomerorderExists(String customerorderSign) {
        if (customerorderService.getCustomerorderBySign(customerorderSign) == null) {
            throw new InvalidDataException(ErrorCode.BU_CUSTOMERORDER_SIGN_UNKNOWN, customerorderSign);
        }
    }

    /**
     * The suborder pattern is entered by hand, so it can be a typo or refer to another customer
     * order. It would then never match during controlling and the rate would silently fall back to
     * the order-wide one, so require that it covers at least one suborder of the chosen order.
     */
    private void checkSuborderPatternMatches(String customerorderSign, String suborderSign) {
        if (suborderSign != null && !suborderService.existsSuborderMatching(customerorderSign, suborderSign)) {
            throw new BusinessRuleException(ErrorCode.BU_SUBORDER_NOT_IN_ORDER);
        }
    }

    /**
     * Two rates conflict only when they carry the same plan as well (#1065). A plan-bound rate next
     * to the plan-less one it narrows is the point of the new level, exactly as a specific pattern
     * over a general one is the point of the old one.
     */
    private void checkNoOverlap(String co, String so, String emp, Long budgetId,
                                LocalDate from, LocalDate until, Long excludeId) {
        var overlapping = orderPricingRepository.findOverlapping(co, so, emp, budgetId, from, until, excludeId);
        if (!overlapping.isEmpty()) {
            throw new BusinessRuleException(ErrorCode.BU_PRICING_OVERLAP);
        }
    }

    /**
     * The plan the rate names, refused where it could never apply (#1065). The check repeats what
     * {@link #getSelectablePlans} filters by rather than trusting the select: a post can carry any
     * id, and an id the list never offered is exactly the case that would earn nothing in silence.
     */
    private OrderBudget resolvePlan(OrderPricingData data, LocalDate validUntil) {
        if (data.orderBudgetId() == null) {
            return null;
        }
        var plan = orderBudgetRepository.findById(data.orderBudgetId())
            .orElseThrow(() -> new InvalidDataException(ErrorCode.BU_BUDGET_NOT_FOUND, data.orderBudgetId()));
        budgetAuthorization.checkAuthorized(plan);
        if (!OrderBudgetBinding.scopeMeetsPattern(plan, data.customerorderSign(), data.suborderSign(),
            suborderSignsOf(data.customerorderSign()))) {
            throw new BusinessRuleException(ErrorCode.BU_BUDGET_SCOPE_DISJOINT, plan.getName());
        }
        if (!OrderBudgetBinding.periodsOverlap(plan, data.validFrom(), validUntil)) {
            throw new BusinessRuleException(ErrorCode.BU_BUDGET_PERIOD_DISJOINT, plan.getName());
        }
        return plan;
    }

    /**
     * The complete order signs of the order's suborders — the ground the scope check stands on. An
     * order that is gone, or not stored yet, has none; the two cases that need no suborder at all
     * answer without this list anyway (→ {@link OrderBudgetBinding}).
     */
    private List<String> suborderSignsOf(String customerorderSign) {
        var customerorder = customerorderService.getCustomerorderBySign(customerorderSign);
        if (customerorder == null || customerorder.getId() == null) {
            return List.of();
        }
        return suborderService.getSubordersByCustomerorderId(customerorder.getId()).stream()
            .map(Suborder::getCompleteOrderSign)
            .toList();
    }

    private void apply(OrderPricing pricing, OrderPricingData data, OrderBudget plan) {
        pricing.setCustomerorderSign(data.customerorderSign());
        pricing.setSuborderSign(data.suborderSign());
        pricing.setEmployeeSign(data.employeeSign());
        pricing.setOrderBudget(plan);
        pricing.setDescription(data.description());
        pricing.setPriceCentsPerHour(data.priceCentsPerHour());
        pricing.setValidFrom(data.validFrom());
        pricing.setValidUntil(data.validUntil() != null ? data.validUntil() : OPEN_END);
    }

}
