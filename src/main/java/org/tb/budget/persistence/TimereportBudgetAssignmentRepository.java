package org.tb.budget.persistence;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.PagingAndSortingRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.tb.budget.domain.TimereportBudgetAssignment;

@Repository
public interface TimereportBudgetAssignmentRepository
    extends CrudRepository<TimereportBudgetAssignment, Long>,
            PagingAndSortingRepository<TimereportBudgetAssignment, Long> {

    Optional<TimereportBudgetAssignment> findByTimereportId(long timereportId);

    @Query("""
        SELECT a.timereportId FROM TimereportBudgetAssignment a
        WHERE a.orderBudget.id = :budgetId
        ORDER BY a.timereportId
        """)
    List<Long> findTimereportIdsByOrderBudgetId(@Param("budgetId") long orderBudgetId);

    long countByOrderBudgetId(long orderBudgetId);

    /**
     * The bookings already assigned to any plan of the customer order. A booking of this order can
     * only ever be assigned to one of its own plans, so this is the complete set the backfill run
     * (#910) has to leave alone — asked once per order rather than with an {@code IN} list of every
     * booking found.
     */
    @Query("""
        SELECT a.timereportId FROM TimereportBudgetAssignment a
        WHERE a.orderBudget.customerorderSign = :customerorderSign
        """)
    List<Long> findTimereportIdsByCustomerorderSign(@Param("customerorderSign") String customerorderSign);

    @Modifying
    @Query("DELETE FROM TimereportBudgetAssignment a WHERE a.timereportId IN :timereportIds")
    void deleteByTimereportIdIn(@Param("timereportIds") Collection<Long> timereportIds);

}
