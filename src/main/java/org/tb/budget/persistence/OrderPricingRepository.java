package org.tb.budget.persistence;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.PagingAndSortingRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.tb.budget.domain.OrderPricing;

@Repository
public interface OrderPricingRepository
    extends CrudRepository<OrderPricing, Long>, PagingAndSortingRepository<OrderPricing, Long> {

    List<OrderPricing> findAllByOrderByCustomerorderSignAscValidFromAsc();

    List<OrderPricing> findByCustomerorderSignOrderByValidFromAsc(String customerorderSign);

    /**
     * The customer orders the list view offers for filtering (#949). Taken from the pricings
     * themselves rather than from the selectable orders: a pricing refers to its order by sign and
     * outlives it, so an order that has been hidden or has expired still needs to be reachable —
     * those are the rows one is looking for when tidying up.
     */
    @Query("SELECT DISTINCT p.customerorderSign FROM OrderPricing p ORDER BY p.customerorderSign ASC")
    List<String> findDistinctCustomerorderSigns();

    List<OrderPricing> findByCustomerorderSignInOrderByIdAsc(Collection<String> customerorderSigns);

    /**
     * Pricings competing with the given one. Two rows only conflict when they carry the <em>same</em>
     * suborder pattern — layering a specific pattern over a general one is the point of the
     * hierarchy and must stay allowed. {@code NULL} and the empty string mean the same thing to the
     * matching, so they are folded together here as well; legacy rows hold both.
     */
    @Query("""
        SELECT p FROM OrderPricing p
        WHERE p.customerorderSign = :co
          AND COALESCE(p.suborderSign, '') = COALESCE(:so, '')
          AND COALESCE(p.employeeSign, '') = COALESCE(:emp, '')
          AND p.validFrom <= :until AND p.validUntil >= :from
          AND (:excludeId IS NULL OR p.id != :excludeId)
        """)
    List<OrderPricing> findOverlapping(
        @Param("co") String customerorderSign,
        @Param("so") String suborderSign,
        @Param("emp") String employeeSign,
        @Param("from") LocalDate validFrom,
        @Param("until") LocalDate validUntil,
        @Param("excludeId") Long excludeId);

}
