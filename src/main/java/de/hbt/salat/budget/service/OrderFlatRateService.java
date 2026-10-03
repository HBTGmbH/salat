package de.hbt.salat.budget.service;

import static java.lang.Boolean.TRUE;
import static java.util.Comparator.comparing;
import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toMap;
import static org.apache.commons.lang3.StringUtils.trimToNull;

import java.time.LocalDate;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.StreamSupport;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import de.hbt.salat.auth.domain.Authorized;
import de.hbt.salat.budget.auth.BudgetAuthorization;
import de.hbt.salat.budget.domain.FlatRateRhythm;
import de.hbt.salat.budget.domain.OrderBudget;
import de.hbt.salat.budget.domain.OrderBudgetBinding;
import de.hbt.salat.budget.domain.OrderFlatRate;
import de.hbt.salat.budget.domain.OrderFlatRateData;
import de.hbt.salat.budget.domain.OrderFlatRateInstalment;
import de.hbt.salat.budget.domain.OrderFlatRateInstalmentData;
import de.hbt.salat.budget.domain.OrderFlatRateLookup;
import de.hbt.salat.budget.domain.OrderFlatRateRow;
import de.hbt.salat.budget.domain.OrderPosition;
import de.hbt.salat.budget.domain.SelectablePlans;
import de.hbt.salat.budget.persistence.OrderBudgetRepository;
import de.hbt.salat.budget.persistence.OrderFlatRateRepository;
import de.hbt.salat.common.exception.BusinessRuleException;
import de.hbt.salat.common.exception.ErrorCode;
import de.hbt.salat.common.exception.InvalidDataException;
import de.hbt.salat.order.domain.Customerorder;
import de.hbt.salat.order.domain.CustomerorderOption;
import de.hbt.salat.order.domain.Suborder;
import de.hbt.salat.order.service.CustomerorderService;
import de.hbt.salat.order.service.SuborderService;

/**
 * The flat rates of an order (#972) — amounts that fall due on a date instead of being earned by the
 * hour.
 *
 * <p>There is no overlap rule, unlike {@link OrderPricingService}: several definitions on one order
 * add up on purpose, so a monthly retainer and the instalments of the same order are meant to sit on
 * top of each other. What is checked is that the record can resolve at all — order and suborder
 * are referenced by id (#1205), and a suborder outside the order would silently earn nothing.
 */
@Service
@Transactional
@RequiredArgsConstructor
@Authorized
public class OrderFlatRateService {

    /**
     * By the sign the order has today, then by start of validity. The sign comes from the order, not
     * from the flat rate's sign column, which only mirrors it for reports (#1212); a flat rate without
     * an order has nothing but that column.
     */
    private static final Comparator<OrderFlatRateRow> BY_ORDER_SIGN_THEN_VALID_FROM = Comparator
        .comparing((OrderFlatRateRow row) -> row.customerorder() != null
            ? row.customerorder().getSign() : row.flatRate().getCustomerorderSign(),
            Comparator.nullsLast(Comparator.naturalOrder()))
        .thenComparing(row -> row.flatRate().getValidFrom(), Comparator.nullsLast(Comparator.naturalOrder()));

    private final OrderFlatRateRepository orderFlatRateRepository;
    private final OrderBudgetRepository orderBudgetRepository;
    private final SuborderService suborderService;
    private final CustomerorderService customerorderService;
    private final BudgetAuthorization budgetAuthorization;
    private final OrderPositions orderPositions;

    @Transactional(readOnly = true)
    public List<OrderFlatRate> getAll() {
        return StreamSupport.stream(orderFlatRateRepository.findAll().spliterator(), false).toList();
    }

    /**
     * The rows of the list view, optionally narrowed to one customer order. The two filters follow
     * the rate list (→ AGENTS.md, "List View Filter Toggles"): unless asked otherwise, definitions
     * that have expired themselves are left out, and so are those of orders whose own validity has
     * expired.
     *
     * <p>Every row carries the schedule its definition amounts to over the definition's whole
     * validity, so the list can show what a monthly rate or a set of instalments adds up to instead
     * of only the amount of a single due date.
     */
    @Transactional(readOnly = true)
    public List<OrderFlatRateRow> getRows(String customerorderSign, boolean showInactive,
                                          boolean showInactiveOrders) {
        var sign = trimToNull(customerorderSign);
        var flatRates = sign == null ? getAll() : byCustomerorderSign(sign);
        var ordersById = ordersOf(flatRates);
        var suborderSigns = suborderSignsOf(flatRates);
        var planNames = planNamesOf(flatRates);
        return flatRates.stream()
            .filter(flatRate -> showInactive || flatRate.getCurrentlyValid())
            // Most flat rates name no plan at all, and an immutable map refuses a null key outright.
            .map(flatRate -> new OrderFlatRateRow(flatRate,
                flatRate.getCustomerorderId() == null ? null : ordersById.get(flatRate.getCustomerorderId()),
                flatRate.getSuborderId() == null ? null : suborderSigns.get(flatRate.getSuborderId()),
                flatRate.dueAmountsWithin(flatRate.getValidFrom(), flatRate.getValidUntil()),
                flatRate.getOrderBudgetId() == null ? null : planNames.get(flatRate.getOrderBudgetId())))
            .filter(row -> showInactiveOrders || orderStillValid(row))
            .sorted(BY_ORDER_SIGN_THEN_VALID_FROM)
            .toList();
    }

    /**
     * The complete signs of the suborders of the given flat rates, by id — the list names them as
     * they are called today, not as the mirror column has them (#1212).
     */
    private Map<Long, String> suborderSignsOf(List<OrderFlatRate> flatRates) {
        var ids = flatRates.stream().map(OrderFlatRate::getSuborderId).filter(Objects::nonNull)
            .distinct().toList();
        return suborderService.getCompleteOrderSignsByIds(ids);
    }

    /** The names of the plans the given flat rates are booked against, by id — one query for all. */
    private Map<Long, String> planNamesOf(List<OrderFlatRate> flatRates) {
        var ids = flatRates.stream().map(OrderFlatRate::getOrderBudgetId).filter(Objects::nonNull)
            .distinct().toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        return StreamSupport.stream(orderBudgetRepository.findAllById(ids).spliterator(), false)
            .collect(toMap(OrderBudget::getId, OrderBudget::getName, (first, second) -> first));
    }

    /**
     * The budget plans this flat rate may be booked against (#1065) — the options of the select and
     * the set the saving judges by. The suborder is a concrete sign here, not a pattern, so the
     * scope check is {@code BudgetScope.covers} on its own.
     *
     * <p>The period narrows the list only once it is complete, for the reason
     * {@code OrderPricingService#getSelectablePlans} gives: on a new record it is entered below the
     * plan. A flat rate knows no open end, so a missing end here means "not entered yet" rather than
     * "runs on" — and half a period cannot rule a plan out without ruling out plans a complete one
     * would keep.
     *
     * <p>The plan the form already holds survives the narrowing here as well (→
     * {@link SelectablePlans}); only authorization keeps applying.
     *
     * @param keepPlanId what the form currently holds, or {@code null} on a fresh create form
     */
    @Transactional(readOnly = true)
    public SelectablePlans getSelectablePlans(Long customerorderId, Long suborderId,
                                              LocalDate validFrom, LocalDate validUntil,
                                              Long keepPlanId) {
        if (customerorderId == null) {
            return SelectablePlans.none();
        }
        var authorized = orderBudgetRepository.findByCustomerorderId(customerorderId).stream()
            .filter(budgetAuthorization::isAuthorized)
            .toList();
        var position = orderPositions.of(customerorderId, suborderId).orElse(null);
        var periodKnown = validFrom != null && validUntil != null;
        var fitting = authorized.stream()
            .filter(plan -> TRUE.equals(plan.getActive()))
            .filter(plan -> OrderBudgetBinding.scopeMeetsSuborder(plan, position))
            .filter(plan -> !periodKnown || OrderBudgetBinding.periodsOverlap(plan, validFrom, validUntil))
            .sorted(comparing(OrderBudget::getValidFrom).thenComparing(OrderBudget::getName))
            .toList();
        var selectable = SelectablePlans.of(fitting, authorized, keepPlanId);
        return selectable.withScopeSigns(
            customerorderService.getCustomerorderSignsByIds(List.of(customerorderId)).get(customerorderId),
            suborderService.getCompleteOrderSignsByIds(selectable.suborderIds()));
    }

    private Map<Long, Customerorder> ordersOf(List<OrderFlatRate> flatRates) {
        var ids = flatRates.stream().map(OrderFlatRate::getCustomerorderId).filter(Objects::nonNull)
            .distinct().toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        return customerorderService.getCustomerordersByIds(ids).stream()
            .collect(toMap(Customerorder::getId, identity(), (first, second) -> first));
    }

    /**
     * A definition whose order no longer exists stays visible, for the reason
     * {@code OrderPricingService} gives: the order is the only way into the record, so hiding it
     * would put it out of reach of the user interface for good.
     */
    private static boolean orderStillValid(OrderFlatRateRow row) {
        return row.customerorder() == null || row.customerorder().getCurrentlyValid();
    }

    /** The customer orders that have at least one flat rate, by sign — the filter options of the list view. */
    @Transactional(readOnly = true)
    public List<CustomerorderOption> getCustomerordersWithFlatRate() {
        return customerorderService.getCustomerorderOptionsByIds(orderFlatRateRepository.findDistinctCustomerorderIds());
    }

    @Transactional(readOnly = true)
    public OrderFlatRate getById(long id) {
        return orderFlatRateRepository.findById(id)
            .orElseThrow(() -> new InvalidDataException(ErrorCode.BU_FLAT_RATE_NOT_FOUND, id));
    }

    /** The flat rates of the order the filter names, read by the id behind the sign (#1205). */
    private List<OrderFlatRate> byCustomerorderSign(String customerorderSign) {
        var customerorderId = customerorderService.getCustomerorderIdBySign(customerorderSign);
        return customerorderId == null
            ? List.of()
            : orderFlatRateRepository.findByCustomerorderIdOrderByValidFromAsc(customerorderId);
    }

    /** Where the flat rate sits in the order tree — empty for one the migration could not resolve. */
    @Transactional(readOnly = true)
    public Optional<OrderPosition> positionOf(OrderFlatRate flatRate) {
        return orderPositions.of(flatRate);
    }

    /** The flat rates booked against one budget plan (#1065) — what the plan's detail page lists. */
    @Transactional(readOnly = true)
    public List<OrderFlatRate> getByOrderBudgetId(long orderBudgetId) {
        return orderFlatRateRepository.findByOrderBudgetId(orderBudgetId);
    }

    /**
     * Loads the flat rates of the given customer orders into an in-memory lookup. The controlling
     * expands the schedules once per evaluation, so they must not be resolved by query.
     */
    @Transactional(readOnly = true)
    public OrderFlatRateLookup lookupFor(Collection<Long> customerorderIds) {
        if (customerorderIds.isEmpty()) {
            return OrderFlatRateLookup.of(List.of());
        }
        return OrderFlatRateLookup.of(
            orderFlatRateRepository.findByCustomerorderIdInOrderByIdAsc(customerorderIds));
    }

    @Authorized(requiresManager = true)
    public long save(OrderFlatRateData data) {
        var scope = scopeOf(data, null);
        checkAmountPresent(data);
        var plan = resolvePlan(data, scope);
        var flatRate = new OrderFlatRate();
        apply(flatRate, data, scope, plan);
        return orderFlatRateRepository.save(flatRate).getId();
    }

    /**
     * Editing is how a flat rate the migration could not resolve gets corrected (#1205): the form
     * names the order, and a suborder that stays unresolved is kept rather than read as "the whole
     * order" — see {@link #scopeOf}.
     */
    @Authorized(requiresManager = true)
    public void update(long id, OrderFlatRateData data) {
        var flatRate = getById(id);
        var scope = scopeOf(data, flatRate);
        checkAmountPresent(data);
        var plan = resolvePlan(data, scope);
        apply(flatRate, data, scope, plan);
        orderFlatRateRepository.save(flatRate);
    }

    @Authorized(requiresManager = true)
    public void delete(long id) {
        orderFlatRateRepository.deleteById(id);
    }

    /**
     * Adds one agreed payment. Its date has to lie inside the validity of the definition: the
     * validity is what the list, the filters and the controlling window judge the record by, so an
     * instalment outside it would be invisible in all three while still being due.
     */
    @Authorized(requiresManager = true)
    public void addInstalment(long flatRateId, OrderFlatRateInstalmentData data) {
        var flatRate = getById(flatRateId);
        if (flatRate.getRhythm() != FlatRateRhythm.INSTALMENTS) {
            throw new BusinessRuleException(ErrorCode.BU_FLAT_RATE_NOT_BILLED_IN_INSTALMENTS);
        }
        if (data.due().isBefore(flatRate.getValidFrom()) || data.due().isAfter(flatRate.getValidUntil())) {
            throw new BusinessRuleException(ErrorCode.BU_FLAT_RATE_INSTALMENT_OUTSIDE_PERIOD,
                data.due(), flatRate.getValidFrom(), flatRate.getValidUntil());
        }
        var instalment = new OrderFlatRateInstalment();
        instalment.setOrderFlatRate(flatRate);
        instalment.setAmount(data.amount());
        instalment.setDue(data.due());
        instalment.setComment(trimToNull(data.comment()));
        flatRate.getInstalments().add(instalment);
        orderFlatRateRepository.save(flatRate);
    }

    @Authorized(requiresManager = true)
    public void removeInstalment(long flatRateId, long instalmentId) {
        var flatRate = getById(flatRateId);
        var removed = flatRate.getInstalments().removeIf(i -> i.getId() != null && i.getId() == instalmentId);
        if (!removed) {
            throw new InvalidDataException(ErrorCode.BU_FLAT_RATE_INSTALMENT_NOT_FOUND, instalmentId);
        }
        orderFlatRateRepository.save(flatRate);
    }

    /**
     * What the flat rate is about: the customer order and the suborder, by id (#1205). The suborder is
     * a concrete one, not a pattern as an hourly rate names it: a flat rate is a single agreed amount
     * and has nothing to spread over several matches. A suborder outside the order would never be
     * allocated to a plan, so it is refused.
     *
     * @param edited the flat rate being edited, or {@code null} on create. A suborder the migration
     *               could not resolve stays unresolved while the form names none — an empty choice
     *               must not turn it into a rate on the whole order.
     */
    private FlatRateScope scopeOf(OrderFlatRateData data, OrderFlatRate edited) {
        var customerorder = data.customerorderId() == null
            ? null
            : customerorderService.getCustomerorderById(data.customerorderId());
        if (customerorder == null) {
            throw new InvalidDataException(ErrorCode.CO_NOT_FOUND, data.customerorderId());
        }
        if (data.suborderId() == null) {
            var keepUnresolved = edited != null && !edited.isOrderWide() && edited.getSuborderId() == null;
            return new FlatRateScope(customerorder, null, keepUnresolved ? null : OrderPosition.orderWide(customerorder.getId()),
                keepUnresolved);
        }
        var suborder = suborderService.getSuborderById(data.suborderId());
        if (suborder == null) {
            throw new InvalidDataException(ErrorCode.SO_NOT_FOUND, data.suborderId());
        }
        if (!customerorder.getId().equals(suborder.getCustomerorder().getId())) {
            throw new BusinessRuleException(ErrorCode.BU_SUBORDER_NOT_IN_ORDER);
        }
        return new FlatRateScope(customerorder, suborder, OrderPosition.of(suborder), false);
    }

    /**
     * @param position        where the flat rate sits — {@code null} while the suborder stays unresolved
     * @param keepUnresolved  whether the stored suborder sign is kept without an id
     */
    private record FlatRateScope(Customerorder customerorder, Suborder suborder, OrderPosition position,
                                 boolean keepUnresolved) {}

    /** Instalments carry their own amounts; the other two rhythms repeat the one of the definition. */
    private void checkAmountPresent(OrderFlatRateData data) {
        if (data.rhythm().hasOwnAmount() && (data.amount() == null || data.amount().signum() == 0)) {
            throw new BusinessRuleException(ErrorCode.BU_FLAT_RATE_AMOUNT_REQUIRED);
        }
    }

    /**
     * The plan the flat rate names, refused where it could never hold the amounts (#1065). Checked
     * here and not only in the select, because this is what lets {@code FlatRateAllocation} take a
     * named plan without weighing period and scope again.
     */
    private OrderBudget resolvePlan(OrderFlatRateData data, FlatRateScope scope) {
        if (data.orderBudgetId() == null) {
            return null;
        }
        var plan = orderBudgetRepository.findById(data.orderBudgetId())
            .orElseThrow(() -> new InvalidDataException(ErrorCode.BU_BUDGET_NOT_FOUND, data.orderBudgetId()));
        budgetAuthorization.checkAuthorized(plan);
        if (!OrderBudgetBinding.scopeMeetsSuborder(plan, scope.position())) {
            throw new BusinessRuleException(ErrorCode.BU_BUDGET_SCOPE_DISJOINT, plan.getName());
        }
        if (!OrderBudgetBinding.periodsOverlap(plan, data.validFrom(), effectiveValidUntil(data))) {
            throw new BusinessRuleException(ErrorCode.BU_BUDGET_PERIOD_DISJOINT, plan.getName());
        }
        return plan;
    }

    /** What {@link #apply} will store as the end — a single amount is due on one day. */
    private static LocalDate effectiveValidUntil(OrderFlatRateData data) {
        return data.rhythm() == FlatRateRhythm.ONCE ? data.validFrom() : data.validUntil();
    }

    private void apply(OrderFlatRate flatRate, OrderFlatRateData data, FlatRateScope scope, OrderBudget plan) {
        flatRate.setCustomerorderId(scope.customerorder().getId());
        // mirrors for readers outside the application, written from the records (#1205)
        flatRate.setCustomerorderSign(scope.customerorder().getSign());
        if (!scope.keepUnresolved()) {
            flatRate.setSuborderId(scope.suborder() == null ? null : scope.suborder().getId());
            flatRate.setSuborderSign(scope.suborder() == null ? null : scope.suborder().getCompleteOrderSign());
        }
        flatRate.setOrderBudget(plan);
        flatRate.setDescription(data.description());
        flatRate.setRhythm(data.rhythm());
        // An amount left on an instalment definition would look like it earns something on its own.
        flatRate.setAmount(data.rhythm().hasOwnAmount() ? data.amount() : null);
        flatRate.setValidFrom(data.validFrom());
        // A single amount is due on one day, so its end cannot say anything else.
        flatRate.setValidUntil(effectiveValidUntil(data));
    }

}
