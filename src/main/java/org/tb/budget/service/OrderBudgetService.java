package org.tb.budget.service;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.tb.auth.domain.Authorized;
import org.tb.budget.auth.BudgetAuthorization;
import org.tb.budget.domain.BudgetMode;
import org.tb.budget.domain.OrderBudget;
import org.tb.budget.domain.OrderBudgetAdjustment;
import org.tb.budget.domain.OrderBudgetAdjustmentData;
import org.tb.budget.domain.OrderBudgetData;
import org.tb.budget.domain.OrderBudgetScopeEntry;
import org.tb.budget.domain.OrderBudgetScopeEntryData;
import org.tb.budget.persistence.OrderBudgetRepository;
import org.tb.common.exception.BusinessRuleException;
import org.tb.common.exception.ErrorCode;
import org.tb.common.exception.InvalidDataException;
import org.tb.common.util.DateUtils;
import org.tb.order.service.SuborderService;

@Service
@Transactional
@RequiredArgsConstructor
@Authorized
public class OrderBudgetService {

    private final OrderBudgetRepository orderBudgetRepository;
    private final SuborderService suborderService;
    private final BudgetAuthorization budgetAuthorization;

    /**
     * Every caller goes through here, so this is where the customer order of the plan is checked —
     * the detail view as well as every write path. Managers pass unconditionally.
     */
    @Transactional(readOnly = true)
    public OrderBudget getById(long id) {
        var budget = orderBudgetRepository.findById(id)
            .orElseThrow(() -> new InvalidDataException(ErrorCode.BU_BUDGET_NOT_FOUND, id));
        budgetAuthorization.checkAuthorized(budget);
        return budget;
    }

    @Transactional(readOnly = true)
    public List<OrderBudget> getAll() {
        return orderBudgetRepository.findAllByOrderByCustomerorderSignAscValidFromAsc();
    }

    @Transactional(readOnly = true)
    public List<OrderBudget> getAllActive() {
        return orderBudgetRepository.findAllActiveWithAdjustments();
    }

    /** All plans the current user may see, ordered like {@link #getAll()}. */
    @Transactional(readOnly = true)
    public List<OrderBudget> getAllVisible() {
        return filterAuthorized(getAll());
    }

    /** The visible plans of one customer order, optionally including the inactive ones. */
    @Transactional(readOnly = true)
    public List<OrderBudget> getVisibleByCustomerorderSign(String customerorderSign, boolean includeInactive) {
        budgetAuthorization.checkAuthorizedForCustomerorder(customerorderSign);
        return includeInactive
            ? getByCustomerorderSign(customerorderSign)
            : getActiveByCustomerorderSign(customerorderSign);
    }

    /** The active plans the current user may see — the basis of the dashboard. */
    @Transactional(readOnly = true)
    public List<OrderBudget> getAllActiveVisible() {
        return filterAuthorized(getAllActive());
    }

    /**
     * The active plans the current user may see, restricted to the given customer order signs.
     * {@code null} means no restriction at all; an empty collection means nothing matches and is
     * answered without a query. The authorization filter still runs on top — a restriction narrows
     * the result, it never widens it.
     */
    @Transactional(readOnly = true)
    public List<OrderBudget> getAllActiveVisible(Collection<String> restrictToCustomerorderSigns) {
        if (restrictToCustomerorderSigns == null) {
            return getAllActiveVisible();
        }
        if (restrictToCustomerorderSigns.isEmpty()) {
            return List.of();
        }
        return filterAuthorized(
            orderBudgetRepository.findAllActiveWithAdjustmentsBySigns(restrictToCustomerorderSigns));
    }

    private List<OrderBudget> filterAuthorized(List<OrderBudget> budgets) {
        if (budgetAuthorization.seesAllCustomerorders()) {
            return budgets;
        }
        return budgets.stream().filter(budgetAuthorization::isAuthorized).toList();
    }

    @Transactional(readOnly = true)
    public List<OrderBudget> getByCustomerorderSign(String customerorderSign) {
        return orderBudgetRepository.findByCustomerorderSign(customerorderSign);
    }

    @Transactional(readOnly = true)
    public List<OrderBudget> getActiveByCustomerorderSign(String customerorderSign) {
        return orderBudgetRepository.findByCustomerorderSignAndActive(customerorderSign, Boolean.TRUE);
    }

    @Authorized(requiresManager = true)
    public OrderBudget create(OrderBudgetData data) {
        // Checked before apply, which does not know the id that has to be excluded from the search.
        checkModeNotMixed(data.customerorderSign(), data.suborderSign(),
            data.validFrom(), data.validUntil(), data.active(), null);
        var budget = new OrderBudget();
        apply(budget, data);
        return orderBudgetRepository.save(budget);
    }

    @Authorized(requiresManager = true)
    public void update(long id, OrderBudgetData data) {
        checkModeNotMixed(data.customerorderSign(), data.suborderSign(),
            data.validFrom(), data.validUntil(), data.active(), id);
        var budget = getById(id);
        apply(budget, data);
        orderBudgetRepository.save(budget);
    }

    @Authorized(requiresManager = true)
    public void setActive(long id, boolean active) {
        var budget = getById(id);
        // Only active plans conflict, so activating one can create a conflict that saving it did not.
        if (active) {
            checkModeNotMixed(budget.getCustomerorderSign(), budget.getSuborderSign(),
                budget.getValidFrom(), budget.getValidUntil(), true, id);
        }
        budget.setActive(active);
        orderBudgetRepository.save(budget);
    }

    @Authorized(requiresManager = true)
    public void addAdjustment(long budgetId, OrderBudgetAdjustmentData data) {
        var budget = getById(budgetId);
        var adjustment = new OrderBudgetAdjustment();
        adjustment.setOrderBudget(budget);
        adjustment.setAmount(data.amount());
        adjustment.setEffective(data.effective());
        adjustment.setComment(data.comment());
        budget.getAdjustments().add(adjustment);
        orderBudgetRepository.save(budget);
    }

    @Authorized(requiresManager = true)
    public void removeAdjustment(long budgetId, long adjustmentId) {
        var budget = getById(budgetId);
        budget.getAdjustments().removeIf(a -> a.getId() != null && a.getId().equals(adjustmentId));
        orderBudgetRepository.save(budget);
    }

    @Authorized(requiresManager = true)
    public void updateAlertSentAt(long id, LocalDate alertSentAt) {
        var budget = getById(id);
        budget.setAlertSentAt(alertSentAt);
        orderBudgetRepository.save(budget);
    }

    @Authorized(requiresManager = true)
    public void addScopeEntry(long budgetId, OrderBudgetScopeEntryData data) {
        var budget = getById(budgetId);
        var entry = new OrderBudgetScopeEntry();
        entry.setOrderBudget(budget);
        entry.setRefdate(data.refdate());
        entry.setPercent(data.percent());
        entry.setComment(data.comment());
        budget.getScopeEntries().add(entry);
        orderBudgetRepository.save(budget);
    }

    @Authorized(requiresManager = true)
    public void removeScopeEntry(long budgetId, long entryId) {
        var budget = getById(budgetId);
        budget.getScopeEntries().removeIf(e -> e.getId() != null && e.getId().equals(entryId));
        orderBudgetRepository.save(budget);
    }

    private void apply(OrderBudget budget, OrderBudgetData data) {
        checkSuborderBelongsToOrder(data.customerorderSign(), data.suborderSign());
        budget.setName(data.name());
        budget.setCustomerorderSign(data.customerorderSign());
        budget.setSuborderSign(data.suborderSign());
        budget.setValidFrom(data.validFrom());
        budget.setValidUntil(data.validUntil());
        budget.setActive(Boolean.TRUE.equals(data.active()));
        budget.setAlertThresholdPercent(data.alertThresholdPercent());
        budget.setProgressMode(data.progressMode());
    }

    /**
     * The suborder dropdown lists the suborders of all customer orders, so a sign can be submitted
     * that does not exist below the chosen order. Such a budget would never match a suborder during
     * controlling and would silently behave as if it did not exist, so reject it here. Budgets are
     * furthermore only kept on first level suborders (#905).
     */
    private void checkSuborderBelongsToOrder(String customerorderSign, String suborderSign) {
        if (suborderSign == null) {
            return;
        }
        if (!suborderService.existsByCompleteOrderSign(customerorderSign, suborderSign)) {
            throw new BusinessRuleException(ErrorCode.BU_SUBORDER_NOT_IN_ORDER);
        }
        if (!suborderService.isFirstLevelSuborder(customerorderSign, suborderSign)) {
            throw new BusinessRuleException(ErrorCode.BU_SUBORDER_NOT_FIRST_LEVEL);
        }
    }

    /**
     * At any point in time a customer order is budgeted either as a whole or per first level
     * suborder, never both (#905). That is the one rule left, and it is a real one: the two modes
     * answer different questions, and a period in which both applied would have no defined answer.
     *
     * <p>Overlapping plans of the <em>same</em> mode are allowed since #914 — several plans on the
     * same suborder included. The ban existed only because a booking's plan was derived from
     * (suborder, date) and an overlap made that ambiguous; the explicit assignment (#908) and the
     * switched evaluation (#913) decide it instead. Businesswise the overlap is the normal case: a
     * follow-up order starts before the running budget ends.
     *
     * <p>Switching mode stays possible as soon as the periods do not overlap — order-wide until the
     * end of the year, per suborder from January.
     *
     * <p>Only active plans take part; an inactive one is in no calculation and may stay on as an
     * archive. Activating one therefore has to check again.
     */
    private void checkModeNotMixed(String customerorderSign, String suborderSign,
                                   LocalDate validFrom, LocalDate validUntil,
                                   boolean active, Long excludeId) {
        if (!active || validFrom == null || validUntil == null) {
            return;
        }
        var orderWide = isOrderWide(suborderSign);
        for (var other : orderBudgetRepository.findActiveOverlapping(
                customerorderSign, validFrom, validUntil, excludeId)) {
            if (orderWide != isOrderWide(other.getSuborderSign())) {
                // Naming the plan that stands in the way is the whole point of the message: the
                // period to move is the one of that plan, not of the one being saved.
                throw new BusinessRuleException(ErrorCode.BU_BUDGET_LEVEL_MIXED,
                    other.getName(), other.getValidFrom(), other.getValidUntil());
            }
        }
    }

    /**
     * Which mode the customer order is budgeted in today: as a whole, per first level suborder, or
     * not at all. Well defined because mixing the two is what {@link #checkModeNotMixed} prevents.
     */
    @Transactional(readOnly = true)
    public BudgetMode currentMode(String customerorderSign) {
        var today = DateUtils.today();
        return getActiveByCustomerorderSign(customerorderSign).stream()
            .filter(b -> !today.isBefore(b.getValidFrom()) && !today.isAfter(b.getValidUntil()))
            .map(b -> isOrderWide(b.getSuborderSign()) ? BudgetMode.ORDER_WIDE : BudgetMode.PER_SUBORDER)
            .findFirst()
            .orElse(BudgetMode.NONE);
    }

    /** {@code null} and blank both mean "the whole customer order", as everywhere else. */
    private static boolean isOrderWide(String suborderSign) {
        return suborderSign == null || suborderSign.isBlank();
    }

}
