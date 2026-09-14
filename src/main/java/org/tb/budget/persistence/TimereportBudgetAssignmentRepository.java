package org.tb.budget.persistence;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.PagingAndSortingRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.tb.budget.domain.AssignedBooking;
import org.tb.budget.domain.AssignedBookingTotals;
import org.tb.budget.domain.TimereportBudgetAssignment;
import org.tb.budget.domain.TimereportBudgetLink;

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
     * The bookings of a plan in a period, youngest first, at most {@code limit} of them (#997).
     *
     * <p>Joins {@code Timereport} over the plain id {@code TimereportBudgetAssignment} carries — the
     * assignment references the booking by id and not by association (#908), so the join has to be
     * written out. Nothing of the entity leaves this query: the result is a record (→ ADR-0021).
     *
     * <p>Sorting and capping belong here and not in the caller. The caller would have to load every
     * booking of the plan to find the youngest 200 of them, which on a plan with thousands of
     * bookings is the whole point being missed.
     *
     * <p>Within a day the keys only have to make the order total and stable, which the id at the end
     * does. Deliberately <em>not</em> sorted by the suborder column the list displays: that column
     * shows {@code Suborder#getCompleteOrderSign()}, the whole chain of parents, which is assembled
     * in Java and stored nowhere. Sorting by the bare {@code sign} would look like sorting by the
     * displayed value while doing something else — two suborders under different parents share it.
     * The suborder id at least keeps the rows of one suborder together.
     *
     * <p>Deleted bookings need no clause of their own — {@code Timereport} carries
     * {@code @SQLRestriction("deleted = false")}.
     */
    @Query("""
        SELECT new org.tb.budget.domain.AssignedBooking(
               t.id, t.referenceday.refdate, t.suborder.id,
               t.employeecontract.employee.sign,
               concat(t.employeecontract.employee.firstname, ' ', t.employeecontract.employee.lastname),
               t.durationhours, t.durationminutes, t.taskdescription)
        FROM TimereportBudgetAssignment a, Timereport t
        WHERE t.id = a.timereportId
          AND a.orderBudget.id = :budgetId
          AND t.referenceday.refdate >= :from AND t.referenceday.refdate <= :until
        ORDER BY t.referenceday.refdate DESC,
                 t.employeecontract.employee.sign ASC,
                 t.suborder.id ASC,
                 t.id ASC
        """)
    List<AssignedBooking> findAssignedBookings(@Param("budgetId") long orderBudgetId,
                                               @Param("from") LocalDate from,
                                               @Param("until") LocalDate until,
                                               Limit limit);

    /**
     * How many bookings the plan holds in the period and how long they are — over the whole period,
     * not over the capped list of {@link #findAssignedBookings} (#997). Derived from the list, the
     * figures would understate every plan that has more bookings than the cap shows.
     */
    @Query("""
        SELECT new org.tb.budget.domain.AssignedBookingTotals(
               count(t), sum(t.durationhours * 60 + t.durationminutes))
        FROM TimereportBudgetAssignment a, Timereport t
        WHERE t.id = a.timereportId
          AND a.orderBudget.id = :budgetId
          AND t.referenceday.refdate >= :from AND t.referenceday.refdate <= :until
        """)
    AssignedBookingTotals findAssignedBookingTotals(@Param("budgetId") long orderBudgetId,
                                                    @Param("from") LocalDate from,
                                                    @Param("until") LocalDate until);

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

    /**
     * The complete booking-to-plan mapping of a customer order (#913). The controlling asks for it
     * once per evaluation and decides from it which plan a booking counts against — that mapping
     * replaced the derived coverage, so it has to be cheap: a flat pair per row instead of entities
     * that pull their plan association along.
     */
    @Query("""
        SELECT new org.tb.budget.domain.TimereportBudgetLink(a.timereportId, a.orderBudget.id)
        FROM TimereportBudgetAssignment a
        WHERE a.orderBudget.customerorderSign = :customerorderSign
        """)
    List<TimereportBudgetLink> findLinksByCustomerorderSign(@Param("customerorderSign") String customerorderSign);

    /**
     * The assignments of the given bookings, so the bulk assignment (#911) learns in one statement
     * which of its selection already belongs to a plan and to which one. Callers must not pass an
     * empty collection — {@code IN ()} is not valid SQL.
     */
    @Query("""
        SELECT a FROM TimereportBudgetAssignment a
        WHERE a.timereportId IN :timereportIds
        """)
    List<TimereportBudgetAssignment> findByTimereportIdIn(@Param("timereportIds") Collection<Long> timereportIds);

    @Modifying
    @Query("DELETE FROM TimereportBudgetAssignment a WHERE a.timereportId IN :timereportIds")
    void deleteByTimereportIdIn(@Param("timereportIds") Collection<Long> timereportIds);

}
