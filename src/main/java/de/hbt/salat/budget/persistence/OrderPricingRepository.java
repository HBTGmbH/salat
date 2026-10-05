package de.hbt.salat.budget.persistence;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.PagingAndSortingRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import de.hbt.salat.budget.domain.OrderPricing;

@Repository
public interface OrderPricingRepository
    extends CrudRepository<OrderPricing, Long>, PagingAndSortingRepository<OrderPricing, Long> {

    List<OrderPricing> findByCustomerorderIdOrderByValidFromAsc(long customerorderId);

    /**
     * The customer orders the list view offers for filtering (#949). Taken from the pricings
     * themselves rather than from the selectable orders: a pricing outlives its order's visibility,
     * so an order that has been hidden or has expired still needs to be reachable — those are the
     * rows one is looking for when tidying up. By id (#1212).
     */
    @Query("SELECT DISTINCT p.customerorderId FROM OrderPricing p")
    List<Long> findDistinctCustomerorderIds();

    List<OrderPricing> findByCustomerorderIdInOrderByIdAsc(Collection<Long> customerorderIds);

    long countByCustomerorderId(long customerorderId);

    /**
     * Pricings competing with the given one. Two rows only conflict when they carry the <em>same</em>
     * suborder pattern <em>and</em> the same budget plan — layering a specific pattern over a general
     * one is the point of the hierarchy and must stay allowed, and so is a plan-bound rate next to
     * the plan-less one it narrows (#1065). {@code NULL} and the empty string mean the same thing to
     * the matching, so they are folded together here as well; legacy rows hold both.
     *
     * <p>The order is compared by id (#1212).
     *
     * <p>Person and plan need no such folding: both are foreign keys and either set or {@code NULL}.
     * {@code p.orderBudget.id} reads that key without joining the plan.
     */
    @Query("""
        SELECT p FROM OrderPricing p
        WHERE p.customerorderId = :co
          AND COALESCE(p.suborderSign, '') = COALESCE(:so, '')
          AND ((:emp IS NULL AND p.employeeId IS NULL) OR p.employeeId = :emp)
          AND ((:budgetId IS NULL AND p.orderBudget.id IS NULL) OR p.orderBudget.id = :budgetId)
          AND p.validFrom <= :until AND p.validUntil >= :from
          AND (:excludeId IS NULL OR p.id != :excludeId)
        """)
    List<OrderPricing> findOverlapping(
        @Param("co") long customerorderId,
        @Param("so") String suborderSign,
        @Param("emp") Long employeeId,
        @Param("budgetId") Long orderBudgetId,
        @Param("from") LocalDate validFrom,
        @Param("until") LocalDate validUntil,
        @Param("excludeId") Long excludeId);

    /** The rates bound to one budget plan — what its detail page lists (#1065). */
    @Query("""
        SELECT p FROM OrderPricing p
        WHERE p.orderBudget.id = :budgetId
        ORDER BY p.validFrom ASC, p.id ASC
        """)
    List<OrderPricing> findByOrderBudgetId(@Param("budgetId") long orderBudgetId);

}
