package org.tb.dailyreport.persistence;

import static org.tb.common.GlobalConstants.INVOICE_YES;
import static org.tb.common.GlobalConstants.MINUTES_PER_HOUR;

import jakarta.persistence.QueryHint;
import java.time.LocalDate;
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
import org.tb.dailyreport.domain.Timereport;

@Repository
public interface TimereportRepository extends CrudRepository<Timereport, Long>, JpaSpecificationExecutor<Timereport> {

  @QueryHints(value = {
          @QueryHint(name = HibernateHints.HINT_CACHEABLE, value = "true"),
          @QueryHint(name = HibernateHints.HINT_CACHE_REGION, value = "TimereportRepository.findAllByEmployeecontractIdAndReferencedayRefdate")
    }
  )
  List<Timereport> findAllByEmployeecontractIdAndReferencedayRefdate(long employeecontractId, LocalDate refDate);

  List<Timereport> findAllByEmployeecontractIdAndReferencedayRefdateIsGreaterThanEqual(long employeecontractId, LocalDate refDate);

  List<Timereport> findAllByEmployeecontractIdAndStatusAndReferencedayRefdateIsLessThanEqual(long employeecontractId, String status, LocalDate date);

  @QueryHints(value = {
          @QueryHint(name = HibernateHints.HINT_CACHEABLE, value = "true"),
          @QueryHint(name = HibernateHints.HINT_CACHE_REGION, value = "TimereportRepository.findAllByEmployeecontractIdAndReferencedayBetween")
    }
  )
  @Query("""
      select t from Timereport t
      where t.employeecontract.id = :employeecontractId
        and t.referenceday.refdate >= coalesce(:begin, t.referenceday.refdate) and t.referenceday.refdate <= coalesce(:end, t.referenceday.refdate)
      order by t.employeecontract.employee.sign asc,
      t.referenceday.refdate asc,
      t.employeeorder.suborder.customerorder.sign asc,
      t.employeeorder.suborder.sign asc
      """)
  List<Timereport> findAllByEmployeecontractIdAndReferencedayBetween(long employeecontractId, LocalDate begin, LocalDate end);

  @Query("""
      select t from Timereport t
      where t.employeeorder.id = :employeeorderId
        and t.referenceday.refdate >= coalesce(:begin, t.referenceday.refdate) and t.referenceday.refdate <= coalesce(:end, t.referenceday.refdate)
      order by t.referenceday.refdate asc
      """)
  List<Timereport> findAllByEmployeeorderIdAndReferencedayBetween(long employeeorderId, LocalDate begin, LocalDate end);

  @Query("""
      select t from Timereport t
      where t.employeecontract.id = :employeecontractId
      and (t.referenceday.refdate < t.employeecontract.validFrom
      or t.employeecontract.validUntil is not null and t.referenceday.refdate > t.employeecontract.validUntil)
      order by t.referenceday.refdate asc, t.suborder.customerorder.sign asc, t.suborder.sign asc
      """)
  List<Timereport> findAllByEmployeecontractIdAndInvalidRegardingEmployeecontractValidity(long employeecontractId);

  @Query("""
      select t from Timereport t
      where t.employeecontract.id = :employeecontractId
      and (t.referenceday.refdate < t.employeeorder.fromDate
      or t.employeeorder.untilDate is not null and t.referenceday.refdate > t.employeeorder.untilDate)
      order by t.referenceday.refdate asc, t.suborder.customerorder.sign asc, t.suborder.sign asc
      """)
  List<Timereport> findAllByEmployeecontractIdAndInvalidRegardingEmployeeorderValidity(long employeecontractId);

  @Query("""
      select t from Timereport t where t.employeecontract.id = :employeecontractId
      and t.referenceday.refdate >= :releaseDate
      and t.durationminutes = 0 and t.durationhours = 0
      and t.deleted = false
      order by t.referenceday.refdate asc, t.suborder.customerorder.sign asc, t.suborder.sign asc
      """)
  List<Timereport> findAllByEmployeecontractIdAndInvalidRegardingZeroDuration(long employeecontractId, LocalDate releaseDate);

  @Query("select sum(tr.durationminutes) + " + MINUTES_PER_HOUR + " * sum(tr.durationhours) from Timereport tr "
      + "where tr.suborder.id = :suborderId and tr.employeecontract.id = :employeecontractId")
  Optional<Long> getReportedMinutesForSuborderAndEmployeeContract(long suborderId, long employeecontractId);

  @Query("select sum(tr.durationminutes) + " + MINUTES_PER_HOUR + " * sum(tr.durationhours) from Timereport tr "
      + "where tr.suborder.invoice = '" + INVOICE_YES + "' and tr.employeeorder.suborder.customerorder.id = :customerorderId")
  Optional<Long> getReportedMinutesForCustomerorder(long customerorderId);

  @Query("""
      select sum(tr.durationminutes) + 60 * sum(tr.durationhours) from Timereport tr
      where tr.employeeorder.suborder.id in (:ids)
  """)
  Optional<Long> getReportedMinutesForSuborders(List<Long> ids);

  @Query("""
      select sum(tr.durationminutes) + 60 * sum(tr.durationhours) from Timereport tr
      where tr.referenceday.refdate >= coalesce(:begin, tr.referenceday.refdate) and tr.referenceday.refdate <= coalesce(:end, tr.referenceday.refdate)
      and tr.employeeorder.suborder.id = :suborderId
  """)
  Optional<Long> getReportedMinutesForSuborderAndBetween(long suborderId, LocalDate begin, LocalDate end);

  @Query("""
      select sum(tr.durationminutes) + 60 * sum(tr.durationhours) from Timereport tr
      where tr.referenceday.refdate >= coalesce(:begin, tr.referenceday.refdate) and tr.referenceday.refdate <= coalesce(:end, tr.referenceday.refdate)
      and tr.employeeorder.id = :employeeorderId
  """)
  Optional<Long> getReportedMinutesForEmployeeorderAndBetween(long employeeorderId, LocalDate begin, LocalDate end);

  @Query("""
      select sum(tr.durationminutes) + 60 * sum(tr.durationhours) from Timereport tr
      where tr.employeeorder.id = :employeeorderId
  """)
  Optional<Long> getReportedMinutesForEmployeeorder(long employeeorderId);

  @Query("""
      select sum(tr.durationminutes) + 60 * sum(tr.durationhours) from Timereport tr
      where tr.referenceday.refdate >= coalesce(:begin, tr.referenceday.refdate) and tr.referenceday.refdate <= coalesce(:end, tr.referenceday.refdate)
      and tr.employeeorder.employeecontract.id = :employeecontractId
  """)
  Optional<Long> getReportedMinutesForEmployeecontractAndBetween(long employeecontractId, LocalDate begin, LocalDate end);

  List<Timereport> findAllByEmployeecontractId(long employeecontractId);

  List<Timereport> findAllByEmployeeorderIdAndReferencedayRefdate(long employeeorderId, LocalDate refDate);

  List<Timereport> findAllBySuborderId(long suborderId);

  @Query("""
      select tr.employeeorder.suborder.customerorder.id, sum(tr.durationminutes) + 60 * sum(tr.durationhours) from Timereport tr
      where tr.employeeorder.suborder.customerorder.id in (:ids) group by tr.employeeorder.suborder.customerorder.id
  """)
  List<Long[]> getReportedMinutesForCustomerordersAsMap(List<Long> ids);

  @Query("""
      select tr.employeeorder.suborder.id, sum(tr.durationminutes) + 60 * sum(tr.durationhours) from Timereport tr
      where tr.employeeorder.suborder.id in (:ids) group by tr.employeeorder.suborder.id
  """)
  List<Long[]> getReportedMinutesForSubordersAsMap(List<Long> ids);

  @Query("""
      select tr.employeeorder.id, sum(tr.durationminutes) + 60 * sum(tr.durationhours) from Timereport tr
      where tr.employeeorder.id in (:ids) group by tr.employeeorder.id
  """)
  List<Long[]> getReportedMinutesForEmployeeordersAsMap(List<Long> ids);

  @Modifying
  @NativeQuery("DELETE FROM timereport WHERE employeeorder_id = :employeeorderId and deleted = true")
  int hardDeleteSoftDeletedByEmployeeorderId(long employeeorderId);

}
