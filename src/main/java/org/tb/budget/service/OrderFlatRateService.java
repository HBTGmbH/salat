package org.tb.budget.service;

import static java.lang.Boolean.TRUE;
import static java.util.Comparator.comparing;
import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toMap;
import static org.apache.commons.lang3.StringUtils.trimToNull;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.StreamSupport;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.tb.auth.domain.Authorized;
import org.tb.budget.auth.BudgetAuthorization;
import org.tb.budget.domain.FlatRateRhythm;
import org.tb.budget.domain.OrderBudget;
import org.tb.budget.domain.OrderBudgetBinding;
import org.tb.budget.domain.OrderFlatRate;
import org.tb.budget.domain.OrderFlatRateData;
import org.tb.budget.domain.OrderFlatRateInstalment;
import org.tb.budget.domain.OrderFlatRateInstalmentData;
import org.tb.budget.domain.OrderFlatRateLookup;
import org.tb.budget.domain.OrderFlatRateRow;
import org.tb.budget.domain.SelectablePlans;
import org.tb.budget.persistence.OrderBudgetRepository;
import org.tb.budget.persistence.OrderFlatRateRepository;
import org.tb.common.exception.BusinessRuleException;
import org.tb.common.exception.ErrorCode;
import org.tb.common.exception.InvalidDataException;
import org.tb.order.domain.Customerorder;
import org.tb.order.service.CustomerorderService;
import org.tb.order.service.SuborderService;

/**
 * The flat rates of an order (#972) — amounts that fall due on a date instead of being earned by the
 * hour.
 *
 * <p>There is no overlap rule, unlike {@link OrderPricingService}: several definitions on one order
 * add up on purpose, so a monthly retainer and the instalments of the same order are meant to sit on
 * top of each other. What is checked is that the record can resolve at all — an unknown order or
 * suborder sign would silently earn nothing (#958).
 */
@Service
@Transactional
@RequiredArgsConstructor
@Authorized
public class OrderFlatRateService {

    private final OrderFlatRateRepository orderFlatRateRepository;
    private final OrderBudgetRepository orderBudgetRepository;
    private final SuborderService suborderService;
    private final CustomerorderService customerorderService;
    private final BudgetAuthorization budgetAuthorization;

    @Transactional(readOnly = true)
    public List<OrderFlatRate> getAll() {
        return orderFlatRateRepository.findAllByOrderByCustomerorderSignAscValidFromAsc();
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
                                          boolean showExpiredOrders) {
        var sign = trimToNull(customerorderSign);
        var flatRates = sign == null ? getAll() : getByCustomerorderSign(sign);
        var ordersBySign = ordersOf(flatRates);
        var planNames = planNamesOf(flatRates);
        return flatRates.stream()
            .filter(flatRate -> showInactive || flatRate.getCurrentlyValid())
            // Most flat rates name no plan at all, and an immutable map refuses a null key outright.
            .map(flatRate -> new OrderFlatRateRow(flatRate, ordersBySign.get(flatRate.getCustomerorderSign()),
                flatRate.dueAmountsWithin(flatRate.getValidFrom(), flatRate.getValidUntil()),
                flatRate.getOrderBudgetId() == null ? null : planNames.get(flatRate.getOrderBudgetId())))
            .filter(row -> showExpiredOrders || orderStillValid(row))
            .toList();
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
    public SelectablePlans getSelectablePlans(String customerorderSign, String suborderSign,
                                              LocalDate validFrom, LocalDate validUntil,
                                              Long keepPlanId) {
        var sign = trimToNull(customerorderSign);
        if (sign == null) {
            return SelectablePlans.none();
        }
        var authorized = orderBudgetRepository.findByCustomerorderSign(sign).stream()
            .filter(budgetAuthorization::isAuthorized)
            .toList();
        var periodKnown = validFrom != null && validUntil != null;
        var fitting = authorized.stream()
            .filter(plan -> TRUE.equals(plan.getActive()))
            .filter(plan -> OrderBudgetBinding.scopeMeetsSuborder(plan, sign, trimToNull(suborderSign)))
            .filter(plan -> !periodKnown || OrderBudgetBinding.periodsOverlap(plan, validFrom, validUntil))
            .sorted(comparing(OrderBudget::getValidFrom).thenComparing(OrderBudget::getName))
            .toList();
        return SelectablePlans.of(fitting, authorized, keepPlanId);
    }

    private Map<String, Customerorder> ordersOf(List<OrderFlatRate> flatRates) {
        var signs = flatRates.stream().map(OrderFlatRate::getCustomerorderSign).distinct().toList();
        return customerorderService.getCustomerordersBySigns(signs).stream()
            .collect(toMap(Customerorder::getSign, identity(), (first, second) -> first));
    }

    /**
     * A definition whose order no longer exists stays visible, for the reason
     * {@code OrderPricingService} gives: the order is the only way into the record, so hiding it
     * would put it out of reach of the user interface for good.
     */
    private static boolean orderStillValid(OrderFlatRateRow row) {
        return row.customerorder() == null || row.customerorder().getCurrentlyValid();
    }

    /** The customer orders that have at least one flat rate — the filter options of the list view. */
    @Transactional(readOnly = true)
    public List<String> getCustomerorderSignsWithFlatRate() {
        return orderFlatRateRepository.findDistinctCustomerorderSigns();
    }

    @Transactional(readOnly = true)
    public OrderFlatRate getById(long id) {
        return orderFlatRateRepository.findById(id)
            .orElseThrow(() -> new InvalidDataException(ErrorCode.BU_FLAT_RATE_NOT_FOUND, id));
    }

    @Transactional(readOnly = true)
    public List<OrderFlatRate> getByCustomerorderSign(String customerorderSign) {
        return orderFlatRateRepository.findByCustomerorderSignOrderByValidFromAsc(customerorderSign);
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
    public OrderFlatRateLookup lookupFor(Collection<String> customerorderSigns) {
        if (customerorderSigns.isEmpty()) {
            return OrderFlatRateLookup.of(List.of());
        }
        return OrderFlatRateLookup.of(
            orderFlatRateRepository.findByCustomerorderSignInOrderByIdAsc(customerorderSigns));
    }

    @Authorized(requiresManager = true)
    public long save(OrderFlatRateData data) {
        checkCustomerorderExists(data.customerorderSign());
        checkSuborderExists(data.customerorderSign(), data.suborderSign());
        checkAmountPresent(data);
        var plan = resolvePlan(data);
        var flatRate = new OrderFlatRate();
        apply(flatRate, data, plan);
        return orderFlatRateRepository.save(flatRate).getId();
    }

    /**
     * The customer order is deliberately not checked here, for the reason
     * {@code OrderPricingService#update} gives: a flat rate references its order by sign and outlives
     * it, and editing is how such a record gets corrected.
     */
    @Authorized(requiresManager = true)
    public void update(long id, OrderFlatRateData data) {
        checkSuborderExists(data.customerorderSign(), data.suborderSign());
        checkAmountPresent(data);
        var plan = resolvePlan(data);
        var flatRate = getById(id);
        apply(flatRate, data, plan);
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

    /** Only on create — see {@link #update} for why an edit must not insist on the order. */
    private void checkCustomerorderExists(String customerorderSign) {
        if (customerorderService.getCustomerorderBySign(customerorderSign) == null) {
            throw new InvalidDataException(ErrorCode.BU_CUSTOMERORDER_SIGN_UNKNOWN, customerorderSign);
        }
    }

    /**
     * The suborder is named by complete order sign, not by a pattern as an hourly rate names it: a
     * flat rate is a single agreed amount and has nothing to spread over several matches. A sign
     * that names no suborder of the order would never be allocated to a plan, so it is refused.
     */
    private void checkSuborderExists(String customerorderSign, String suborderSign) {
        if (suborderSign != null
            && !suborderService.existsByCompleteOrderSign(customerorderSign, suborderSign)) {
            throw new BusinessRuleException(ErrorCode.BU_SUBORDER_NOT_IN_ORDER);
        }
    }

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
    private OrderBudget resolvePlan(OrderFlatRateData data) {
        if (data.orderBudgetId() == null) {
            return null;
        }
        var plan = orderBudgetRepository.findById(data.orderBudgetId())
            .orElseThrow(() -> new InvalidDataException(ErrorCode.BU_BUDGET_NOT_FOUND, data.orderBudgetId()));
        budgetAuthorization.checkAuthorized(plan);
        if (!OrderBudgetBinding.scopeMeetsSuborder(plan, data.customerorderSign(), data.suborderSign())) {
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

    private void apply(OrderFlatRate flatRate, OrderFlatRateData data, OrderBudget plan) {
        flatRate.setCustomerorderSign(data.customerorderSign());
        flatRate.setSuborderSign(data.suborderSign());
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
