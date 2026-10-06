package de.hbt.salat.budget.persistence;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
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
    /** With order, suborder and plan: the flat rate list shows them (#1367). */
    @Query("""
        SELECT f FROM OrderFlatRate f JOIN FETCH f.customerorder LEFT JOIN FETCH f.suborder
          LEFT JOIN FETCH f.orderBudget
        WHERE f.customerorder.id = :customerorderId
        ORDER BY f.validFrom ASC
        """)
    List<OrderFlatRate> findByCustomerorderIdOrderByValidFromAsc(Long customerorderId);

    /** All flat rates with order, suborder and plan, like {@link #findByCustomerorderIdOrderByValidFromAsc}. */
    @Query("""
        SELECT f FROM OrderFlatRate f JOIN FETCH f.customerorder LEFT JOIN FETCH f.suborder
          LEFT JOIN FETCH f.orderBudget
        """)
    List<OrderFlatRate> findAllWithReferences();

    @Query("SELECT f FROM OrderFlatRate f WHERE f.customerorder.id IN :customerorderIds ORDER BY f.id ASC")
    List<OrderFlatRate> findByCustomerorderIdInOrderByIdAsc(Collection<Long> customerorderIds);

    /** One flat rate with order and suborder: the detail page shows their signs (#1367). */
    @Query("""
        SELECT f FROM OrderFlatRate f JOIN FETCH f.customerorder LEFT JOIN FETCH f.suborder
        WHERE f.id = :id
        """)
    Optional<OrderFlatRate> findWithScopeById(long id);

    @Query("SELECT COUNT(f) FROM OrderFlatRate f WHERE f.customerorder.id = :customerorderId")
    long countByCustomerorderId(Long customerorderId);

    @Query("SELECT COUNT(f) FROM OrderFlatRate f WHERE f.suborder.id = :suborderId")
    long countBySuborderId(Long suborderId);

    /**
     * The customer orders the list view offers for filtering. Taken from the flat rates themselves
     * rather than from the selectable orders, for the reason
     * {@code OrderPricingRepository#findDistinctCustomerorderIds} gives: a flat rate outlives its
     * order's visibility and has to stay reachable when the order is hidden or expired.
     */
    @Query("SELECT DISTINCT f.customerorder.id FROM OrderFlatRate f")
    List<Long> findDistinctCustomerorderIds();

    /** The flat rates bound to one budget plan — what its detail page lists (#1065). */
    @Query("""
        SELECT f FROM OrderFlatRate f LEFT JOIN FETCH f.suborder
        WHERE f.orderBudget.id = :budgetId
        ORDER BY f.validFrom ASC, f.id ASC
        """)
    List<OrderFlatRate> findByOrderBudgetId(@Param("budgetId") long orderBudgetId);

}
