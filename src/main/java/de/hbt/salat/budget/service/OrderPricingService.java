package de.hbt.salat.budget.service;

import static java.util.Comparator.comparing;
import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toMap;
import static java.util.stream.Collectors.toSet;
import static org.apache.commons.lang3.StringUtils.trimToNull;

import static java.lang.Boolean.TRUE;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.StreamSupport;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import de.hbt.salat.auth.domain.Authorized;
import de.hbt.salat.budget.auth.BudgetAuthorization;
import de.hbt.salat.budget.domain.OrderBudget;
import de.hbt.salat.budget.domain.OrderBudgetBinding;
import de.hbt.salat.budget.domain.OrderBudgetBinding.PositionedSuborder;
import de.hbt.salat.budget.domain.OrderPosition;
import de.hbt.salat.budget.domain.OrderPricing;
import de.hbt.salat.budget.domain.OrderPricingData;
import de.hbt.salat.budget.domain.OrderPricingDeviation;
import de.hbt.salat.budget.domain.OrderPricingLookup;
import de.hbt.salat.budget.domain.OrderPricingRow;
import de.hbt.salat.budget.domain.SelectablePlans;
import de.hbt.salat.budget.persistence.OrderBudgetRepository;
import de.hbt.salat.budget.persistence.OrderPricingRepository;
import de.hbt.salat.common.exception.BusinessRuleException;
import de.hbt.salat.common.exception.ErrorCode;
import de.hbt.salat.common.exception.InvalidDataException;
import de.hbt.salat.employee.domain.Employee;
import de.hbt.salat.employee.service.EmployeeService;
import de.hbt.salat.order.domain.Customerorder;
import de.hbt.salat.order.service.CustomerorderService;
import de.hbt.salat.order.service.SuborderService;

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
                                         boolean showInactiveOrders) {
        var sign = trimToNull(customerorderSign);
        var pricings = sign == null ? getAll() : getByCustomerorderSign(sign);
        var ordersBySign = ordersOf(pricings);
        var coverage = OrderPricingLookup.of(pricings);
        var employeeSigns = employeeSignsOf(pricings);
        var planNames = planNamesOf(pricings);
        return pricings.stream()
            .filter(pricing -> showInactive || pricing.getCurrentlyValid())
            .map(pricing -> row(pricing, ordersBySign.get(pricing.getCustomerorderSign()), coverage,
                employeeSigns, planNames))
            .filter(row -> showInactiveOrders || orderStillValid(row))
            .toList();
    }

    private static OrderPricingRow row(OrderPricing pricing, Customerorder order,
                                       OrderPricingLookup coverage, Map<Long, String> employeeSigns,
                                       Map<Long, String> planNames) {
        // Most rates carry no plan at all, and an immutable map refuses a null key outright.
        var planId = pricing.getOrderBudgetId();
        return new OrderPricingRow(pricing, order, OrderPricingDeviation.of(pricing, order, coverage),
            employeeSignOf(pricing, employeeSigns), planId == null ? null : planNames.get(planId));
    }

    /**
     * The current signs of the people the given rates are for, by id (#968) — one query for the
     * whole list.
     */
    @Transactional(readOnly = true)
    public Map<Long, String> employeeSignsOf(Collection<OrderPricing> pricings) {
        var ids = pricings.stream().map(OrderPricing::getEmployeeId).filter(Objects::nonNull)
            .collect(toSet());
        return ids.isEmpty() ? Map.of() : employeeService.getSignsByIds(ids);
    }

    /**
     * The sign a rate is shown with: the person's, or — for a rate whose person the migration could
     * not resolve (#968) — the one it was stored with, which is all there is to recognize it by.
     */
    private static String employeeSignOf(OrderPricing pricing, Map<Long, String> employeeSigns) {
        if (pricing.isEmployeeUnresolved()) {
            return pricing.getEmployeeSign();
        }
        return pricing.getEmployeeId() == null ? null : employeeSigns.get(pricing.getEmployeeId());
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
     * to one would apply to nobody.
     *
     * <p><strong>Each condition is only applied once the field it reads has been filled in.</strong>
     * On a new rate the validity is entered below the plan, so demanding it first left the select
     * dead at the moment it is operated — and the empty-state hint then claimed that no plan
     * qualifies when in truth none had been judged yet. What is already known narrows the list, the
     * rest narrows it as soon as it is entered. Nothing is lost by that: the validity is a required
     * field, and the saving applies all three conditions regardless of what the select offered.
     *
     * <p><strong>The plan the form already holds survives every one of them</strong> (→
     * {@link SelectablePlans}). Only authorization is no condition but a boundary, and it keeps
     * applying.
     *
     * @param keepPlanId what the form currently holds — the stored plan of the rate being edited,
     *                   or whatever has been picked since; {@code null} on a fresh create form
     */
    @Transactional(readOnly = true)
    public SelectablePlans getSelectablePlans(String customerorderSign, String suborderPattern,
                                              LocalDate validFrom, LocalDate validUntil,
                                              Long keepPlanId) {
        var sign = trimToNull(customerorderSign);
        if (sign == null) {
            return SelectablePlans.none();
        }
        var order = orderOf(sign);
        if (order == null) {
            return SelectablePlans.none();
        }
        var authorized = orderBudgetRepository.findByCustomerorderId(order.customerorderId()).stream()
            .filter(budgetAuthorization::isAuthorized)
            .toList();
        if (authorized.isEmpty()) {
            return SelectablePlans.none();
        }
        // An empty end is an open one here and needs no field of its own — unlike the start, which
        // is simply not entered yet while the form is being filled in.
        var until = validUntil != null ? validUntil : OPEN_END;
        var fitting = authorized.stream()
            .filter(plan -> TRUE.equals(plan.getActive()))
            .filter(plan -> validFrom == null || OrderBudgetBinding.periodsOverlap(plan, validFrom, until))
            .filter(plan -> OrderBudgetBinding.scopeMeetsPattern(plan, order.customerorderId(),
                trimToNull(suborderPattern), order.suborders()))
            .sorted(comparing(OrderBudget::getValidFrom).thenComparing(OrderBudget::getName))
            .toList();
        return SelectablePlans.of(fitting, authorized, keepPlanId);
    }


    @Authorized(requiresManager = true)
    public void save(OrderPricingData data) {
        var validUntil = data.validUntil() != null ? data.validUntil() : OPEN_END;
        checkCustomerorderExists(data.customerorderSign());
        var employee = employeeOf(data);
        checkSuborderPatternMatches(data.customerorderSign(), data.suborderSign());
        var plan = resolvePlan(data, validUntil);
        checkNoOverlap(data.customerorderSign(), data.suborderSign(), data.employeeId(),
            data.orderBudgetId(), data.validFrom(), validUntil, null);
        var pricing = new OrderPricing();
        apply(pricing, data, employee, plan);
        orderPricingRepository.save(pricing);
    }

    /**
     * The customer order is deliberately not checked here: a rate references its order by sign and
     * outlives it (#957, → {@code CustomerorderFilterOption}). Demanding the order on every edit
     * would leave a rate whose order is gone only deletable, and editing it is how it gets corrected.
     *
     * <p>A rate whose person the migration could not resolve (#968) stays unresolved when saved
     * without a person. The form cannot offer that person, so its empty choice would otherwise turn
     * one person's rate into the rate of everyone on the order — without anybody having picked that.
     * Such a rate applies to nobody and so competes with no other; it is not checked for overlaps.
     */
    @Authorized(requiresManager = true)
    public void update(long id, OrderPricingData data) {
        var validUntil = data.validUntil() != null ? data.validUntil() : OPEN_END;
        var pricing = getById(id);
        var employee = employeeOf(data);
        var staysUnresolved = employee == null && pricing.isEmployeeUnresolved();
        checkSuborderPatternMatches(data.customerorderSign(), data.suborderSign());
        var plan = resolvePlan(data, validUntil);
        if (!staysUnresolved) {
            checkNoOverlap(data.customerorderSign(), data.suborderSign(), data.employeeId(),
                data.orderBudgetId(), data.validFrom(), validUntil, id);
        }
        var unresolvedSign = pricing.getEmployeeSign();
        apply(pricing, data, employee, plan);
        if (staysUnresolved) {
            pricing.setEmployeeSign(unresolvedSign);
        }
        orderPricingRepository.save(pricing);
    }

    @Authorized(requiresManager = true)
    public void delete(long id) {
        orderPricingRepository.deleteById(id);
    }

    /**
     * Writes the new sign of a person into the sign column of their rates (#966, #968) — see
     * {@code EmployeeCostService#followSignChange}. Driven by the event of the employee module,
     * where changing a sign takes a manager.
     */
    public void followSignChange(long employeeId, String newSign) {
        orderPricingRepository.updateEmployeeSign(employeeId, newSign);
    }

    /**
     * The person the rate is for, or {@code null} for a rate for everyone on the order — the normal
     * case. An id nobody carries is refused here rather than by the foreign key, which would only
     * fail the statement; and the person is needed anyway, for the sign column that is still
     * written alongside the id (#968).
     */
    private Employee employeeOf(OrderPricingData data) {
        if (data.employeeId() == null) {
            return null;
        }
        var employee = employeeService.getEmployeeById(data.employeeId());
        if (employee == null) {
            throw new InvalidDataException(ErrorCode.EM_NOT_FOUND, data.employeeId());
        }
        return employee;
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
    private void checkNoOverlap(String co, String so, Long employeeId, Long budgetId,
                                LocalDate from, LocalDate until, Long excludeId) {
        var overlapping = orderPricingRepository.findOverlapping(co, so, employeeId, budgetId, from, until, excludeId);
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
        var order = orderOf(data.customerorderSign());
        if (order == null || !OrderBudgetBinding.scopeMeetsPattern(plan, order.customerorderId(),
            data.suborderSign(), order.suborders())) {
            throw new BusinessRuleException(ErrorCode.BU_BUDGET_SCOPE_DISJOINT, plan.getName());
        }
        if (!OrderBudgetBinding.periodsOverlap(plan, data.validFrom(), validUntil)) {
            throw new BusinessRuleException(ErrorCode.BU_BUDGET_PERIOD_DISJOINT, plan.getName());
        }
        return plan;
    }

    /**
     * The order a rate names by sign, with its suborders — the ground the scope check stands on. The
     * rate keeps naming its order by sign and its suborders by pattern (#957); the plan is compared by
     * id (#1205), so every suborder brings both its complete order sign and its position. An order that
     * is gone, or not stored yet, yields {@code null}; the two cases that need no suborder at all
     * answer without the list anyway (→ {@link OrderBudgetBinding}).
     */
    private PricedOrder orderOf(String customerorderSign) {
        var customerorder = customerorderService.getCustomerorderBySign(customerorderSign);
        if (customerorder == null || customerorder.getId() == null) {
            return null;
        }
        var suborders = suborderService.getSubordersByCustomerorderId(customerorder.getId()).stream()
            .map(suborder -> new PositionedSuborder(suborder.getCompleteOrderSign(), OrderPosition.of(suborder)))
            .toList();
        return new PricedOrder(customerorder.getId(), suborders);
    }

    private record PricedOrder(long customerorderId, List<PositionedSuborder> suborders) {}

    private void apply(OrderPricing pricing, OrderPricingData data, Employee employee, OrderBudget plan) {
        pricing.setCustomerorderSign(data.customerorderSign());
        pricing.setSuborderSign(data.suborderSign());
        pricing.setEmployeeId(employee == null ? null : employee.getId());
        pricing.setEmployeeSign(employee == null ? null : employee.getSign());
        pricing.setOrderBudget(plan);
        pricing.setDescription(data.description());
        pricing.setPriceCentsPerHour(data.priceCentsPerHour());
        pricing.setValidFrom(data.validFrom());
        pricing.setValidUntil(data.validUntil() != null ? data.validUntil() : OPEN_END);
    }

}
