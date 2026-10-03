package de.hbt.salat.budget.persistence;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.PagingAndSortingRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import de.hbt.salat.budget.domain.BudgetPlanPresence;
import de.hbt.salat.budget.domain.OrderBudget;

@Repository
public interface OrderBudgetRepository
    extends CrudRepository<OrderBudget, Long>, PagingAndSortingRepository<OrderBudget, Long> {

    /** The plans of a customer order, by its id (#1205) — the sign column is a mirror for reports only. */
    List<OrderBudget> findByCustomerorderId(Long customerorderId);

    List<OrderBudget> findByCustomerorderIdAndActive(Long customerorderId, Boolean active);

    long countByCustomerorderId(Long customerorderId);

    long countBySuborderId(Long suborderId);

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

    /**
     * Like {@link #findAllActiveWithAdjustments()}, restricted to the given customer orders.
     * The dashboard filters this way instead of dropping rows afterwards, so the utilizations are
     * only computed for budgets that end up on the page. Callers must not pass an empty collection —
     * {@code IN ()} is not valid SQL; an empty restriction means "nothing matches" and is answered
     * without a query.
     */
    @Query("""
        SELECT DISTINCT b FROM OrderBudget b LEFT JOIN FETCH b.adjustments
        WHERE b.active = true AND b.customerorderId IN :ids
        ORDER BY b.customerorderSign ASC, b.validFrom ASC
        """)
    List<OrderBudget> findAllActiveWithAdjustmentsByCustomerorderIds(@Param("ids") Collection<Long> customerorderIds);

    /**
     * The plans of several customer orders at once, for the dashboard (#1222). Callers must not pass
     * an empty collection.
     */
    List<OrderBudget> findByCustomerorderIdInAndActive(Collection<Long> customerorderIds, Boolean active);

    /**
     * The given plans with their scope entries fetched — in one statement for all of them instead of
     * one per plan when the progress reads them (#1222). Separate from the adjustments because
     * Hibernate cannot fetch two lists in one query. The plans the caller holds are managed by the
     * same session, so this initializes their collections in place. Callers must not pass an empty
     * collection.
     */
    @Query("""
        SELECT DISTINCT b FROM OrderBudget b LEFT JOIN FETCH b.scopeEntries
        WHERE b.id IN :ids
        """)
    List<OrderBudget> findWithScopeEntriesByIdIn(@Param("ids") Collection<Long> ids);

    List<OrderBudget> findByActiveAndAlertThresholdPercentIsNotNull(Boolean active);

    /**
     * The given customer orders that have plans at all, each with whether one of them is active —
     * one grouped query for the targets of the command palette (#1157). Callers must not pass an
     * empty collection.
     */
    @Query("""
        SELECT new de.hbt.salat.budget.domain.BudgetPlanPresence(b.customerorderId,
            max(case when b.active = true then 1 else 0 end))
        FROM OrderBudget b
        WHERE b.customerorderId IN :ids
        GROUP BY b.customerorderId
        """)
    List<BudgetPlanPresence> findPlanPresenceByCustomerorderIds(@Param("ids") Collection<Long> customerorderIds);

    /**
     * The customer orders that have at least one active plan — the only orders a backfill run
     * (#910) can assign anything on. Selecting the signs instead of the plans keeps the run from
     * loading every plan of the installation just to learn which orders to visit. A plan whose order
     * the migration could not resolve (#1205) has no order to visit.
     */
    @Query("""
        SELECT DISTINCT b.customerorderId FROM OrderBudget b
        WHERE b.active = true AND b.customerorderId IS NOT NULL
        """)
    List<Long> findActiveCustomerorderIds();

    /**
     * Active budgets of the customer order whose validity overlaps the given period, excluding the
     * one being edited. Whether such a budget is an actual conflict depends on its scope, which the
     * service decides — two plans on <em>different</em> suborders may share a period.
     */
    @Query("""
        SELECT b FROM OrderBudget b
        WHERE b.customerorderId = :co
          AND b.active = true
          AND b.validFrom <= :until AND b.validUntil >= :from
          AND (:excludeId IS NULL OR b.id != :excludeId)
        """)
    List<OrderBudget> findActiveOverlapping(
        @Param("co") Long customerorderId,
        @Param("from") LocalDate validFrom,
        @Param("until") LocalDate validUntil,
        @Param("excludeId") Long excludeId);

}
