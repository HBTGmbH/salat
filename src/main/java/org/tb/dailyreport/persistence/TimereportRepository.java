package org.tb.dailyreport.persistence;

import static org.tb.common.GlobalConstants.INVOICE_YES;
import static org.tb.common.GlobalConstants.MINUTES_PER_HOUR;

import jakarta.persistence.QueryHint;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.hibernate.jpa.HibernateHints;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.NativeQuery;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import org.tb.dailyreport.domain.MonthlyReportedMinutes;
import org.tb.dailyreport.domain.Timereport;
import org.tb.jira.command.TicketDaySum;

@Repository
public interface TimereportRepository extends CrudRepository<Timereport, Long>, JpaSpecificationExecutor<Timereport> {

  /**
   * Restricts a sum to the bookings that are working time (#463): standby is booked like any other
   * time but counts towards no working time sum. The type of the suborder wins over the one of its
   * customer order, and no type at all is a standard order — the same fallback
   * {@code Suborder#getEffectiveOrderType} applies, expressed in JPQL.
   */
  String IS_WORKING_TIME = """
      and (case when tr.suborder.orderType is not null then tr.suborder.orderType
                else tr.suborder.customerorder.orderType end
           is distinct from org.tb.order.domain.OrderType.BEREITSCHAFT)
      """;

  @QueryHints(value = {
          @QueryHint(name = HibernateHints.HINT_CACHEABLE, value = "true"),
          @QueryHint(name = HibernateHints.HINT_CACHE_REGION, value = "TimereportRepository.findAllByEmployeecontractIdAndReferencedayRefdate")
    }
  )
  @Query("select t from Timereport t where t.deleted = false and t.employeecontract.id = :employeecontractId and t.referenceday.refdate = :refDate")
  List<Timereport> findAllByEmployeecontractIdAndReferencedayRefdate(long employeecontractId, LocalDate refDate);

  /**
   * The given bookings in one statement. Callers that hold a set of ids — the budget module holds
   * the ids of the bookings assigned to a plan (#974) — would otherwise ask for them one by one.
   * Deleted bookings are left out, as everywhere: their assignments are cleaned up on delete.
   */
  @Query("select t from Timereport t where t.deleted = false and t.id in :ids")
  List<Timereport> findAllByIdIn(Collection<Long> ids);

  @Query("select t from Timereport t where t.deleted = false and t.employeecontract.id = :employeecontractId and t.referenceday.refdate >= :refDate")
  List<Timereport> findAllByEmployeecontractIdAndReferencedayRefdateIsGreaterThanEqual(long employeecontractId, LocalDate refDate);

  @Query("select t from Timereport t where t.deleted = false and t.status = :status and t.employeecontract.id = :employeecontractId and t.referenceday.refdate <= :refDate")
  List<Timereport> findAllByEmployeecontractIdAndStatusAndReferencedayRefdateIsLessThanEqual(long employeecontractId, String status, LocalDate refDate);

  @QueryHints(value = {
          @QueryHint(name = HibernateHints.HINT_CACHEABLE, value = "true"),
          @QueryHint(name = HibernateHints.HINT_CACHE_REGION, value = "TimereportRepository.findAllByEmployeecontractIdAndReferencedayBetween")
    }
  )
  @Query("""
      select t from Timereport t
      where t.deleted = false and t.employeecontract.id = :employeecontractId
        and t.referenceday.refdate >= coalesce(:begin, t.referenceday.refdate) and t.referenceday.refdate <= coalesce(:end, t.referenceday.refdate)
      order by t.employeecontract.employee.sign asc,
      t.referenceday.refdate asc,
      t.employeeorder.suborder.customerorder.sign asc,
      t.employeeorder.suborder.sign asc
      """)
  List<Timereport> findAllByEmployeecontractIdAndReferencedayBetween(long employeecontractId, LocalDate begin, LocalDate end);

  @Query("""
      select t from Timereport t
      where t.deleted = false and t.employeeorder.id = :employeeorderId
        and t.referenceday.refdate >= coalesce(:begin, t.referenceday.refdate) and t.referenceday.refdate <= coalesce(:end, t.referenceday.refdate)
      order by t.referenceday.refdate asc
      """)
  List<Timereport> findAllByEmployeeorderIdAndReferencedayBetween(long employeeorderId, LocalDate begin, LocalDate end);

  @Query("""
      select t from Timereport t
      where t.deleted = false and t.employeecontract.id = :employeecontractId
      and (t.referenceday.refdate < t.employeecontract.validFrom
      or t.employeecontract.validUntil is not null and t.referenceday.refdate > t.employeecontract.validUntil)
      order by t.referenceday.refdate asc, t.suborder.customerorder.sign asc, t.suborder.sign asc
      """)
  List<Timereport> findAllByEmployeecontractIdAndInvalidRegardingEmployeecontractValidity(long employeecontractId);

  @Query("""
      select t from Timereport t
      where t.deleted = false and t.employeecontract.id = :employeecontractId
      and (t.referenceday.refdate < t.employeeorder.fromDate
      or t.employeeorder.untilDate is not null and t.referenceday.refdate > t.employeeorder.untilDate)
      order by t.referenceday.refdate asc, t.suborder.customerorder.sign asc, t.suborder.sign asc
      """)
  List<Timereport> findAllByEmployeecontractIdAndInvalidRegardingEmployeeorderValidity(long employeecontractId);

  @Query("""
      select t from Timereport t where t.deleted = false and t.employeecontract.id = :employeecontractId
      and t.referenceday.refdate >= :releaseDate
      and t.durationminutes = 0 and t.durationhours = 0
      order by t.referenceday.refdate asc, t.suborder.customerorder.sign asc, t.suborder.sign asc
      """)
  List<Timereport> findAllByEmployeecontractIdAndInvalidRegardingZeroDuration(long employeecontractId, LocalDate releaseDate);

  /**
   * The customer orders anything was booked on in the period (#779). Signs rather than orders,
   * because the caller uses them to decide what to evaluate, not to display them — and one query
   * instead of loading every booking of the period only to throw the bookings away.
   */
  @Query("""
      select distinct t.suborder.customerorder.sign from Timereport t
      where t.deleted = false and t.referenceday.refdate between :from and :until
      """)
  List<String> findDistinctCustomerorderSignsBetween(LocalDate from, LocalDate until);

  /**
   * The days in {@code [from, until]} on which the contract has at least one booking (#1124), each
   * once. Every status and every order type counts, an absence as much as project work: the
   * question is whether anything was booked, not what. Dates rather than bookings, because the
   * caller only asks which days are empty — and without the per-row READ filter of
   * {@code TimereportDAO}, under which a booking the viewer may not read would look like a gap.
   * Whoever calls this answers for the authorization.
   */
  @Query("""
      select distinct t.referenceday.refdate from Timereport t
      where t.deleted = false and t.employeecontract.id = :employeecontractId
        and t.referenceday.refdate between :from and :until
      """)
  List<LocalDate> findBookedDaysBetween(long employeecontractId, LocalDate from, LocalDate until);

  @Query("select sum(tr.durationminutes) + " + MINUTES_PER_HOUR + " * sum(tr.durationhours) from Timereport tr "
      + "where tr.deleted = false and tr.suborder.id = :suborderId and tr.employeecontract.id = :employeecontractId")
  Optional<Long> getReportedMinutesForSuborderAndEmployeeContract(long suborderId, long employeecontractId);

  @Query("select sum(tr.durationminutes) + " + MINUTES_PER_HOUR + " * sum(tr.durationhours) from Timereport tr "
      + "where tr.deleted = false and tr.suborder.invoice = '" + INVOICE_YES + "' and tr.employeeorder.suborder.customerorder.id = :customerorderId")
  Optional<Long> getReportedMinutesForCustomerorder(long customerorderId);

  @Query("""
      select sum(tr.durationminutes) + 60 * sum(tr.durationhours) from Timereport tr
      where tr.deleted = false and tr.employeeorder.suborder.id in (:ids)
  """)
  Optional<Long> getReportedMinutesForSuborders(List<Long> ids);

  @Query("""
      select sum(tr.durationminutes) + 60 * sum(tr.durationhours) from Timereport tr
      where tr.deleted = false and tr.referenceday.refdate >= coalesce(:begin, tr.referenceday.refdate) and tr.referenceday.refdate <= coalesce(:end, tr.referenceday.refdate)
      and tr.employeeorder.suborder.id = :suborderId
  """)
  Optional<Long> getReportedMinutesForSuborderAndBetween(long suborderId, LocalDate begin, LocalDate end);

  @Query("""
      select sum(tr.durationminutes) + 60 * sum(tr.durationhours) from Timereport tr
      where tr.deleted = false and tr.referenceday.refdate >= coalesce(:begin, tr.referenceday.refdate) and tr.referenceday.refdate <= coalesce(:end, tr.referenceday.refdate)
      and tr.employeeorder.id = :employeeorderId
  """)
  Optional<Long> getReportedMinutesForEmployeeorderAndBetween(long employeeorderId, LocalDate begin, LocalDate end);

  @Query("""
      select sum(tr.durationminutes) + 60 * sum(tr.durationhours) from Timereport tr
      where tr.deleted = false and tr.employeeorder.id = :employeeorderId
  """)
  Optional<Long> getReportedMinutesForEmployeeorder(long employeeorderId);

  @Query("""
      select sum(tr.durationminutes) + 60 * sum(tr.durationhours) from Timereport tr
      where tr.deleted = false and tr.employeecontract.id = :employeecontractId
      and tr.referenceday.refdate >= coalesce(:begin, tr.referenceday.refdate) and tr.referenceday.refdate <= coalesce(:end, tr.referenceday.refdate)
  """ + IS_WORKING_TIME)
  Optional<Long> getReportedMinutesForEmployeecontractAndBetween(long employeecontractId, LocalDate begin, LocalDate end);

  @Query("""
      select new org.tb.dailyreport.domain.MonthlyReportedMinutes(
             extract(year from tr.referenceday.refdate),
             extract(month from tr.referenceday.refdate),
             sum(tr.durationminutes) + 60 * sum(tr.durationhours))
      from Timereport tr
      where tr.deleted = false and tr.employeecontract.id = :employeecontractId
      and tr.referenceday.refdate >= :begin and tr.referenceday.refdate <= :end
  """ + IS_WORKING_TIME + """
      group by extract(year from tr.referenceday.refdate), extract(month from tr.referenceday.refdate)
  """)
  List<MonthlyReportedMinutes> getReportedMinutesByMonthForEmployeecontract(long employeecontractId, LocalDate begin, LocalDate end);

  @Query("select t from Timereport t where t.deleted = false and t.employeecontract.id = :employeecontractId")
  List<Timereport> findAllByEmployeecontractId(long employeecontractId);

  @Query("""
      select t from Timereport t where t.deleted = false and t.employeeorder.id = :employeeorderId
      and t.referenceday.refdate = :refDate
  """)
  List<Timereport> findAllByEmployeeorderIdAndReferencedayRefdate(long employeeorderId, LocalDate refDate);

  @Query("""
      select tr.employeeorder.suborder.customerorder.id, sum(tr.durationminutes) + 60 * sum(tr.durationhours) from Timereport tr
      where tr.deleted = false and tr.employeeorder.suborder.customerorder.id in (:ids) group by tr.employeeorder.suborder.customerorder.id
  """)
  List<Long[]> getReportedMinutesForCustomerordersAsMap(List<Long> ids);

  @Query("""
      select tr.employeeorder.suborder.id, sum(tr.durationminutes) + 60 * sum(tr.durationhours) from Timereport tr
      where tr.deleted = false and tr.employeeorder.suborder.id in (:ids) group by tr.employeeorder.suborder.id
  """)
  List<Long[]> getReportedMinutesForSubordersAsMap(List<Long> ids);

  @Query("""
      select tr.employeeorder.id, sum(tr.durationminutes) + 60 * sum(tr.durationhours) from Timereport tr
      where tr.deleted = false and tr.employeeorder.id in (:ids) group by tr.employeeorder.id
  """)
  List<Long[]> getReportedMinutesForEmployeeordersAsMap(List<Long> ids);

  /**
   * What was booked per day and ticket reference on the given suborders (#1007), summed over all
   * people — the shape a JIRA worklog has: one number per day and ticket, no person, no task.
   *
   * <p>The whole period is summed on every run rather than only the days that changed since the
   * last one. A booking is soft-deleted through {@code @SQLDelete}, which writes nothing but
   * {@code deleted = true} — {@code lastupdate} is not moved, because the auditing listener does
   * not run on a delete. A deleted booking is therefore recognisable by no timestamp at all, and
   * exactly its disappearance has to lower the sum. Comparing full sums against what was last
   * written sidesteps that: what is gone is simply not in the answer.
   */
  @Query("""
      select new org.tb.jira.command.TicketDaySum(
             tr.referenceday.refdate,
             tr.ticketReference,
             sum(tr.durationminutes) + 60 * sum(tr.durationhours))
      from Timereport tr
      where tr.deleted = false
        and tr.ticketReference is not null
        and tr.suborder.id in (:suborderIds)
        and tr.referenceday.refdate >= :from and tr.referenceday.refdate <= :until
      group by tr.referenceday.refdate, tr.ticketReference
  """)
  List<TicketDaySum> getTicketDaySums(Collection<Long> suborderIds, LocalDate from, LocalDate until);

  @Modifying
  @NativeQuery("DELETE FROM timereport WHERE employeeorder_id = :employeeorderId and deleted = true")
  int hardDeleteSoftDeletedByEmployeeorderId(long employeeorderId);

  @Query("""
      select sum(tr.durationminutes) + 60 * sum(tr.durationhours) from Timereport tr
      where tr.deleted = false and tr.employeeorder.id = :employeeorderId
      and tr.referenceday.refdate >= coalesce(:begin, tr.referenceday.refdate) and tr.referenceday.refdate <= coalesce(:end, tr.referenceday.refdate)
  """)
  Optional<Long> getReportedMinutesForEmployeeorder(long employeeorderId, LocalDate begin, LocalDate end);
}
