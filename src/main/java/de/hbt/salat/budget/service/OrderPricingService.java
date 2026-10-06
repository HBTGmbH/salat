package de.hbt.salat.budget.service;

import static java.util.Comparator.comparing;
import static org.apache.commons.lang3.StringUtils.trimToNull;

import static java.lang.Boolean.TRUE;

import java.time.LocalDate;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
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
import de.hbt.salat.budget.persistence.MasterDataReferences;
import de.hbt.salat.budget.persistence.OrderBudgetRepository;
import de.hbt.salat.budget.persistence.OrderPricingRepository;
import de.hbt.salat.common.exception.BusinessRuleException;
import de.hbt.salat.common.exception.ErrorCode;
import de.hbt.salat.common.exception.InvalidDataException;
import de.hbt.salat.employee.domain.Employee;
import de.hbt.salat.employee.service.EmployeeService;
import de.hbt.salat.order.domain.Customerorder;
import de.hbt.salat.order.domain.CustomerorderOption;
import de.hbt.salat.order.service.CustomerorderService;
import de.hbt.salat.order.service.SuborderService;

@Service
@Transactional
@RequiredArgsConstructor
@Authorized
public class OrderPricingService {

    /** An open rate end, stored as a sentinel rather than as {@code null}. */
    private static final LocalDate OPEN_END = LocalDate.of(2999, 12, 31);

    /**
     * By the sign the order has today, then by start of validity. The sign comes from the order (#1212).
     */
    private static final Comparator<OrderPricingRow> BY_ORDER_SIGN_THEN_VALID_FROM = Comparator
        .comparing((OrderPricingRow row) -> row.customerorder().getSign())
        .thenComparing(row -> row.pricing().getValidFrom(), Comparator.nullsLast(Comparator.naturalOrder()));

    private final OrderPricingRepository orderPricingRepository;
    private final OrderBudgetRepository orderBudgetRepository;
    private final SuborderService suborderService;
    private final CustomerorderService customerorderService;
    private final EmployeeService employeeService;
    private final BudgetAuthorization budgetAuthorization;
    private final MasterDataReferences masterDataReferences;

    @Transactional(readOnly = true)
    public List<OrderPricing> getAll() {
        return orderPricingRepository.findAllWithReferences();
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
    public List<OrderPricingRow> getRows(Long customerorderId, boolean showInactive,
                                         boolean showInactiveOrders) {
        var pricings = customerorderId == null
            ? getAll()
            : orderPricingRepository.findByCustomerorderIdOrderByValidFromAsc(customerorderId);
        var coverage = OrderPricingLookup.of(pricings);
        return pricings.stream()
            .filter(pricing -> showInactive || pricing.getCurrentlyValid())
            .map(pricing -> row(pricing, coverage))
            .filter(row -> showInactiveOrders || orderStillValid(row))
            .sorted(BY_ORDER_SIGN_THEN_VALID_FROM)
            .toList();
    }

    /**
     * Order, person and plan come with the rate (#1367): the list queries fetch them, so a row reads
     * them off the references instead of looking them up.
     */
    private static OrderPricingRow row(OrderPricing pricing, OrderPricingLookup coverage) {
        var order = pricing.getCustomerorder();
        return new OrderPricingRow(pricing, order, OrderPricingDeviation.of(pricing, order, coverage),
            employeeSignOf(pricing), pricing.getOrderBudget() == null ? null : pricing.getOrderBudget().getName());
    }

    /** The sign a rate is shown with: the person's, or {@code null} for a rate for everyone (#968). */
    private static String employeeSignOf(OrderPricing pricing) {
        return pricing.getEmployee() == null ? null : pricing.getEmployee().getSign();
    }

    private static boolean orderStillValid(OrderPricingRow row) {
        return row.customerorder().getCurrentlyValid();
    }

    /** The customer orders that have at least one pricing, by sign — the filter options of the list view. */
    @Transactional(readOnly = true)
    public List<CustomerorderOption> getCustomerordersWithPricing() {
        return customerorderService.getCustomerorderOptionsByIds(orderPricingRepository.findDistinctCustomerorderIds());
    }

    @Transactional(readOnly = true)
    public OrderPricing getById(long id) {
        return orderPricingRepository.findById(id)
            .orElseThrow(() -> new InvalidDataException(ErrorCode.BU_PRICING_NOT_FOUND, id));
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
    public OrderPricingLookup lookupFor(Collection<Long> customerorderIds) {
        if (customerorderIds.isEmpty()) {
            return OrderPricingLookup.of(List.of());
        }
        return OrderPricingLookup.of(orderPricingRepository.findByCustomerorderIdInOrderByIdAsc(customerorderIds));
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
    public SelectablePlans getSelectablePlans(Long customerorderId, String suborderPattern,
                                              LocalDate validFrom, LocalDate validUntil,
                                              Long keepPlanId) {
        if (customerorderId == null) {
            return SelectablePlans.none();
        }
        var order = orderOf(customerorderId);
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
        var selectable = SelectablePlans.of(fitting, authorized, keepPlanId);
        return selectable.withScopeSigns();
    }


    @Authorized(requiresManager = true)
    public void save(OrderPricingData data) {
        var validUntil = data.validUntil() != null ? data.validUntil() : OPEN_END;
        var customerorder = customerorderOf(data);
        var employee = employeeOf(data);
        checkSuborderPatternMatches(customerorder, data.suborderSign());
        var plan = resolvePlan(data, validUntil);
        checkNoOverlap(customerorder.getId(), data.suborderSign(), data.employeeId(),
            data.orderBudgetId(), data.validFrom(), validUntil, null);
        var pricing = new OrderPricing();
        apply(pricing, data, customerorder, employee, plan);
        orderPricingRepository.save(pricing);
    }

    /**
     * The order is required on an edit as well: it is referenced by id now and cannot go away while
     * a rate names it (#1212).
     */
    @Authorized(requiresManager = true)
    public void update(long id, OrderPricingData data) {
        var validUntil = data.validUntil() != null ? data.validUntil() : OPEN_END;
        var pricing = getById(id);
        var customerorder = customerorderOf(data);
        var employee = employeeOf(data);
        checkSuborderPatternMatches(customerorder, data.suborderSign());
        var plan = resolvePlan(data, validUntil);
        checkNoOverlap(customerorder.getId(), data.suborderSign(), data.employeeId(),
            data.orderBudgetId(), data.validFrom(), validUntil, id);
        apply(pricing, data, customerorder, employee, plan);
        orderPricingRepository.save(pricing);
    }

    @Authorized(requiresManager = true)
    public void delete(long id) {
        orderPricingRepository.deleteById(id);
    }

    /**
     * The person the rate is for, or {@code null} for a rate for everyone on the order — the normal
     * case. An id nobody carries is refused here rather than by the foreign key, which would only
     * fail the statement.
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

    /** The order the rate names — required, and refused here rather than by the foreign key. */
    private Customerorder customerorderOf(OrderPricingData data) {
        var customerorder = data.customerorderId() == null
            ? null
            : customerorderService.getCustomerorderById(data.customerorderId());
        if (customerorder == null) {
            throw new InvalidDataException(ErrorCode.CO_NOT_FOUND, data.customerorderId());
        }
        return customerorder;
    }

    /**
     * The suborder pattern is entered by hand, so it can be a typo or refer to another customer
     * order. It would then never match during controlling and the rate would silently fall back to
     * the order-wide one, so require that it covers at least one suborder of the chosen order.
     */
    private void checkSuborderPatternMatches(Customerorder customerorder, String suborderSign) {
        if (suborderSign != null && !suborderService.existsSuborderMatching(customerorder.getId(), suborderSign)) {
            throw new BusinessRuleException(ErrorCode.BU_SUBORDER_NOT_IN_ORDER);
        }
    }

    /**
     * Two rates conflict only when they carry the same plan as well (#1065). A plan-bound rate next
     * to the plan-less one it narrows is the point of the new level, exactly as a specific pattern
     * over a general one is the point of the old one.
     */
    private void checkNoOverlap(long co, String so, Long employeeId, Long budgetId,
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
        var order = orderOf(data.customerorderId());
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
     * The order a rate names, with its suborders — the ground the scope check stands on. The rate
     * names its order by id (#1212) and its suborders by pattern (#957); the plan is compared by id
     * (#1205), so every suborder brings both its complete order sign and its position. An order that
     * is not there yields {@code null}; the two cases that need no suborder at all answer without the
     * list anyway (→ {@link OrderBudgetBinding}).
     */
    private PricedOrder orderOf(Long customerorderId) {
        var customerorder = customerorderId == null ? null : customerorderService.getCustomerorderById(customerorderId);
        if (customerorder == null || customerorder.getId() == null) {
            return null;
        }
        var suborders = suborderService.getSubordersByCustomerorderId(customerorder.getId()).stream()
            .map(suborder -> new PositionedSuborder(suborder.getCompleteOrderSign(), OrderPosition.of(suborder)))
            .toList();
        return new PricedOrder(customerorder.getId(), suborders);
    }

    private record PricedOrder(long customerorderId, List<PositionedSuborder> suborders) {}

    private void apply(OrderPricing pricing, OrderPricingData data, Customerorder customerorder, Employee employee,
                       OrderBudget plan) {
        pricing.setCustomerorder(masterDataReferences.customerorder(customerorder.getId()));
        pricing.setSuborderSign(data.suborderSign());
        pricing.setEmployee(masterDataReferences.employee(employee == null ? null : employee.getId()));
        pricing.setOrderBudget(plan);
        pricing.setDescription(data.description());
        pricing.setPriceCentsPerHour(data.priceCentsPerHour());
        pricing.setValidFrom(data.validFrom());
        pricing.setValidUntil(data.validUntil() != null ? data.validUntil() : OPEN_END);
    }

}
