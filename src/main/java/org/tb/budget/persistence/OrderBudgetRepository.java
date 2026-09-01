package org.tb.budget.persistence;

import java.util.List;
import org.springframework.data.jpa.repository.Query;
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

    /**
     * Active budgets with their adjustments already fetched — every caller sums the adjustments,
     * so leaving them lazy costs one statement per budget.
     */
    @Query("""
        SELECT DISTINCT b FROM OrderBudget b LEFT JOIN FETCH b.adjustments
        WHERE b.active = true
        ORDER BY b.customerorderSign ASC, b.validFrom ASC
        """)
    List<OrderBudget> findAllActiveWithAdjustments();

    List<OrderBudget> findByActiveAndAlertThresholdPercentIsNotNull(Boolean active);

}
