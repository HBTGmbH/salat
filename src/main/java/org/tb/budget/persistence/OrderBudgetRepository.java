package org.tb.budget.persistence;

import java.util.List;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.PagingAndSortingRepository;
import org.springframework.stereotype.Repository;
import org.tb.budget.domain.OrderBudget;

@Repository
public interface OrderBudgetRepository
    extends CrudRepository<OrderBudget, Long>, PagingAndSortingRepository<OrderBudget, Long> {

    List<OrderBudget> findByCustomerorderSign(String customerorderSign);

    List<OrderBudget> findByCustomerorderSignAndActive(String customerorderSign, Boolean active);

    List<OrderBudget> findAllByOrderByCustomerorderSignAscValidFromAsc();

}
