package org.tb.budget.persistence;

import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.tb.budget.domain.OrderBudget;

@Component
@RequiredArgsConstructor
public class OrderBudgetDAO {

    private final OrderBudgetRepository orderBudgetRepository;

    public Optional<OrderBudget> findById(long id) {
        return orderBudgetRepository.findById(id);
    }

    public List<OrderBudget> findAll() {
        return orderBudgetRepository.findAllByOrderByCustomerorderSignAscValidFromAsc();
    }

    public List<OrderBudget> findByCustomerorderSign(String customerorderSign) {
        return orderBudgetRepository.findByCustomerorderSign(customerorderSign);
    }

    public List<OrderBudget> findActiveByCustomerorderSign(String customerorderSign) {
        return orderBudgetRepository.findByCustomerorderSignAndActive(customerorderSign, Boolean.TRUE);
    }

    public List<OrderBudget> findByCustomerorderSignAndSuborderSign(String customerorderSign, String suborderSign) {
        return orderBudgetRepository.findByCustomerorderSignAndSuborderSign(customerorderSign, suborderSign);
    }

}
