package org.tb.budget.persistence;

import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.PagingAndSortingRepository;
import org.springframework.data.repository.query.Param;
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

    /**
     * Active budgets of the customer order whose validity overlaps the given period, excluding the
     * one being edited. Whether such a budget is an actual conflict depends on its scope, which the
     * service decides — two plans on <em>different</em> suborders may share a period.
     */
    @Query("""
        SELECT b FROM OrderBudget b
        WHERE b.customerorderSign = :co
          AND b.active = true
          AND b.validFrom <= :until AND b.validUntil >= :from
          AND (:excludeId IS NULL OR b.id != :excludeId)
        """)
    List<OrderBudget> findActiveOverlapping(
        @Param("co") String customerorderSign,
        @Param("from") LocalDate validFrom,
        @Param("until") LocalDate validUntil,
        @Param("excludeId") Long excludeId);

}
