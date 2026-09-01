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

    List<OrderPricing> findByCustomerorderSign(String customerorderSign);

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
