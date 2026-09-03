package org.tb.budget.service;

import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.tb.auth.domain.Authorized;
import org.tb.budget.auth.BudgetAuthorization;
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
        checkNoConflict(data.customerorderSign(), data.suborderSign(),
            data.validFrom(), data.validUntil(), data.active(), null);
        var budget = new OrderBudget();
        apply(budget, data);
        return orderBudgetRepository.save(budget);
    }

    @Authorized(requiresManager = true)
    public void update(long id, OrderBudgetData data) {
        checkNoConflict(data.customerorderSign(), data.suborderSign(),
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
            checkNoConflict(budget.getCustomerorderSign(), budget.getSuborderSign(),
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
     * At any point in time a customer order is budgeted either as a whole — by exactly one plan — or
     * per first level suborder, by at most one plan each, never both (#905). That reduces to a single
     * pairwise rule: two active plans of the same order whose periods overlap have to be on suborder
     * level and on <em>different</em> suborders.
     *
     * <p>Only active plans conflict; an inactive one takes part in no calculation and may stay on as
     * an archive.
     */
    private void checkNoConflict(String customerorderSign, String suborderSign,
                                 LocalDate validFrom, LocalDate validUntil,
                                 boolean active, Long excludeId) {
        if (!active || validFrom == null || validUntil == null) {
            return;
        }
        for (var other : orderBudgetRepository.findActiveOverlapping(
                customerorderSign, validFrom, validUntil, excludeId)) {
            var orderWide = isOrderWide(suborderSign);
            var otherOrderWide = isOrderWide(other.getSuborderSign());
            if (orderWide != otherOrderWide) {
                throw new BusinessRuleException(ErrorCode.BU_BUDGET_LEVEL_MIXED);
            }
            if (orderWide || suborderSign.equals(other.getSuborderSign())) {
                throw new BusinessRuleException(ErrorCode.BU_BUDGET_OVERLAP);
            }
        }
    }

    /** {@code null} and blank both mean "the whole customer order", as everywhere else. */
    private static boolean isOrderWide(String suborderSign) {
        return suborderSign == null || suborderSign.isBlank();
    }

}
