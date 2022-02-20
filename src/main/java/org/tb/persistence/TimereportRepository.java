package org.tb.persistence;

import static org.tb.GlobalConstants.INVOICE_YES;
import static org.tb.GlobalConstants.MINUTES_PER_HOUR;

import java.util.Date;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import org.tb.bdom.Timereport;

@Repository
public interface TimereportRepository extends CrudRepository<Timereport, Long> {

  @Query("select t from Timereport t "
      + "where t.employeecontract.id = :employeecontractId and "
      + "t.referenceday.refdate >= :begin and t.referenceday.refdate <= :end "
      + "order by t.employeecontract.employee.sign asc, "
      + "t.referenceday.refdate asc, "
      + "t.employeeorder.suborder.customerorder.sign asc, "
      + "t.employeeorder.suborder.sign asc")
  List<Timereport> findAllByEmployeecontractIdAndReferencedayBetween(long employeecontractId, Date begin, Date end);

  @Query("select t from Timereport t "
      + "where t.employeecontract.id = :employeecontractId "
      + "and (t.referenceday.refdate < t.employeecontract.validFrom "
      + "or t.employeecontract.validUntil is not null and t.referenceday.refdate > t.employeecontract.validUntil) "
      + "order by t.referenceday.refdate asc, t.suborder.customerorder.sign asc, t.suborder.sign asc")
  List<Timereport> findAllByEmployeecontractIdAndInvalidRegardingEmployeecontractValidity(long employeecontractId);

  @Query("select t from Timereport t "
      + "where t.employeecontract.id = :employeecontractId "
      + "and (t.referenceday.refdate < t.employeeorder.fromDate "
      + "or t.employeeorder.untilDate is not null and t.referenceday.refdate > t.employeeorder.untilDate) "
      + "order by t.referenceday.refdate asc, t.suborder.customerorder.sign asc, t.suborder.sign asc")
  List<Timereport> findAllByEmployeecontractIdAndInvalidRegardingEmployeeorderValidity(long employeecontractId);

  @Query("select t from Timereport t where t.employeecontract.id = :employeecontractId "
      + "and t.referenceday.refdate >= :releaseDate "
      + "and t.durationminutes = 0 and t.durationhours = 0 "
      + "order by t.referenceday.refdate asc, t.suborder.customerorder.sign asc, t.suborder.sign asc")
  List<Timereport> findAllByEmployeecontractIdAndInvalidRegardingZeroDuration(long employeecontractId, Date releaseDate);

  @Query("select sum(tr.durationminutes) + " + MINUTES_PER_HOUR + " * sum(tr.durationhours) from Timereport tr "
      + "where tr.suborder.id = :suborderId and tr.employeecontract.id = :employeecontractId")
  Optional<Long> getReportedMinutesForSuborderAndEmployeeContract(long suborderId, long employeecontractId);

  @Query("select sum(tr.durationminutes) + " + MINUTES_PER_HOUR + " * sum(tr.durationhours) from Timereport tr "
      + "where tr.suborder.invoice = '" + INVOICE_YES + "' and tr.employeeorder.suborder.customerorder.id = :customerorderId")
  Optional<Long> getReportedMinutesForCustomerorder(long customerorderId);

}
