package org.tb.budget.service;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.tb.auth.domain.Authorized;
import org.tb.budget.domain.OrderBudget;
import org.tb.budget.domain.OrderBudgetAdjustment;
import org.tb.budget.domain.OrderBudgetAdjustmentData;
import org.tb.budget.domain.OrderBudgetData;
import org.tb.budget.persistence.OrderBudgetDAO;
import org.tb.budget.persistence.OrderBudgetRepository;
import org.tb.common.exception.ErrorCode;
import org.tb.common.exception.InvalidDataException;

@Service
@Transactional
@RequiredArgsConstructor
@Authorized
public class OrderBudgetService {

    private final OrderBudgetDAO orderBudgetDAO;
    private final OrderBudgetRepository orderBudgetRepository;

    @Transactional(readOnly = true)
    public OrderBudget getById(long id) {
        return orderBudgetDAO.findById(id)
            .orElseThrow(() -> new InvalidDataException(ErrorCode.BU_BUDGET_NOT_FOUND, id));
    }

    @Transactional(readOnly = true)
    public List<OrderBudget> getAll() {
        return orderBudgetDAO.findAll();
    }

    @Transactional(readOnly = true)
    public List<OrderBudget> getByCustomerorderSign(String customerorderSign) {
        return orderBudgetDAO.findByCustomerorderSign(customerorderSign);
    }

    @Transactional(readOnly = true)
    public List<OrderBudget> getActiveByCustomerorderSign(String customerorderSign) {
        return orderBudgetDAO.findActiveByCustomerorderSign(customerorderSign);
    }

    @Authorized(requiresManager = true)
    public OrderBudget create(OrderBudgetData data) {
        var budget = new OrderBudget();
        apply(budget, data);
        return orderBudgetRepository.save(budget);
    }

    @Authorized(requiresManager = true)
    public void update(long id, OrderBudgetData data) {
        var budget = getById(id);
        apply(budget, data);
        orderBudgetRepository.save(budget);
    }

    @Authorized(requiresManager = true)
    public void setActive(long id, boolean active) {
        var budget = getById(id);
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

    private void apply(OrderBudget budget, OrderBudgetData data) {
        budget.setName(data.name());
        budget.setCustomerorderSign(data.customerorderSign());
        budget.setSuborderSign(data.suborderSign());
        budget.setValidFrom(data.validFrom());
        budget.setValidUntil(data.validUntil());
        budget.setActive(Boolean.TRUE.equals(data.active()));
    }

}
