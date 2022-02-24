package org.tb.persistence;

import static org.tb.GlobalConstants.INVOICE_YES;
import static org.tb.GlobalConstants.MINUTES_PER_HOUR;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import org.tb.bdom.Timereport;

@Repository
public interface TimereportRepository extends CrudRepository<Timereport, Long>, JpaSpecificationExecutor<Timereport> {

  List<Timereport> findAllByEmployeecontractIdAndReferencedayRefdate(long employeecontractId, LocalDate refDate);

  List<Timereport> findAllByEmployeecontractIdAndReferencedayRefdateIsGreaterThanEqual(long employeecontractId, LocalDate refDate);

  List<Timereport> findAllByEmployeecontractIdAndStatusAndReferencedayRefdateIsLessThanEqual(long employeecontractId, String status, LocalDate date);

  @Query("select t from Timereport t "
      + "where t.employeecontract.id = :employeecontractId and "
      + "t.referenceday.refdate >= :begin and t.referenceday.refdate <= :end "
      + "order by t.employeecontract.employee.sign asc, "
      + "t.referenceday.refdate asc, "
      + "t.employeeorder.suborder.customerorder.sign asc, "
      + "t.employeeorder.suborder.sign asc")
  List<Timereport> findAllByEmployeecontractIdAndReferencedayBetween(long employeecontractId, LocalDate begin, LocalDate end);

  @Query("select t from Timereport t "
      + "where t.employeecontract.id = :employeecontractId "
      + "and (t.referenceday.refdate < t.employeecontract.validFrom "
      + "or t.employeecontract.validUntil is not null and t.referenceday.refdate > t.employeecontract.validUntil) "
      + "order by t.referenceday.refdate asc, t.suborder.customerorder.sign asc, t.suborder.sign asc")
  List<Timereport> findAllByEmployeecontractIdAndInvalidRegardingEmployeecontractValidity(long employeecontractId);

  @Query("select t from Timereport t "
      + "where t.employeecontract.id = :employeecontractId "
      + "and (t.referenceday.refdate < t.employeeorder.fromLocalDate "
      + "or t.employeeorder.untilLocalDate is not null and t.referenceday.refdate > t.employeeorder.untilDate) "
      + "order by t.referenceday.refdate asc, t.suborder.customerorder.sign asc, t.suborder.sign asc")
  List<Timereport> findAllByEmployeecontractIdAndInvalidRegardingEmployeeorderValidity(long employeecontractId);

  @Query("select t from Timereport t where t.employeecontract.id = :employeecontractId "
      + "and t.referenceday.refdate >= :releaseLocalDate "
      + "and t.durationminutes = 0 and t.durationhours = 0 "
      + "order by t.referenceday.refdate asc, t.suborder.customerorder.sign asc, t.suborder.sign asc")
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
      where tr.referenceday.refdate >= :begin and tr.referenceday.refdate <= :end
      and tr.employeeorder.suborder.id = :suborderId
  """)
  Optional<Long> getReportedMinutesForSuborderAndBetween(long suborderId, LocalDate begin, LocalDate end);

  @Query("""
      select sum(tr.durationminutes) + 60 * sum(tr.durationhours) from Timereport tr
      where tr.referenceday.refdate >= :begin and tr.referenceday.refdate <= :end
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
      where tr.referenceday.refdate >= :begin and tr.referenceday.refdate <= :end
      and tr.employeeorder.employeecontract.id = :employeecontractId
  """)
  Optional<Long> getReportedMinutesForEmployeecontractAndBetween(long employeecontractId, LocalDate begin, LocalDate end);

}
