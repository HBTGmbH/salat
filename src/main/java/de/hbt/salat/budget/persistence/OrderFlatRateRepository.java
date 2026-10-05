package de.hbt.salat.budget.persistence;

import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.PagingAndSortingRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import de.hbt.salat.budget.domain.OrderFlatRate;

@Repository
public interface OrderFlatRateRepository
    extends CrudRepository<OrderFlatRate, Long>, PagingAndSortingRepository<OrderFlatRate, Long> {

    /** The flat rates of a customer order, by its id (#1205). */
    List<OrderFlatRate> findByCustomerorderIdOrderByValidFromAsc(Long customerorderId);

    List<OrderFlatRate> findByCustomerorderIdInOrderByIdAsc(Collection<Long> customerorderIds);

    long countByCustomerorderId(Long customerorderId);

    long countBySuborderId(Long suborderId);

    /**
     * The customer orders the list view offers for filtering. Taken from the flat rates themselves
     * rather than from the selectable orders, for the reason
     * {@code OrderPricingRepository#findDistinctCustomerorderIds} gives: a flat rate outlives its
     * order's visibility and has to stay reachable when the order is hidden or expired.
     */
    @Query("SELECT DISTINCT f.customerorderId FROM OrderFlatRate f")
    List<Long> findDistinctCustomerorderIds();

    /** The flat rates bound to one budget plan — what its detail page lists (#1065). */
    @Query("""
        SELECT f FROM OrderFlatRate f
        WHERE f.orderBudget.id = :budgetId
        ORDER BY f.validFrom ASC, f.id ASC
        """)
    List<OrderFlatRate> findByOrderBudgetId(@Param("budgetId") long orderBudgetId);

}
