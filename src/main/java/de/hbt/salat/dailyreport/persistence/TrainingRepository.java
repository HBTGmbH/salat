package de.hbt.salat.dailyreport.persistence;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import de.hbt.salat.dailyreport.domain.Timereport;
import de.hbt.salat.dailyreport.domain.TrainingDay;
import de.hbt.salat.dailyreport.domain.TrainingInformation;

@Repository
public interface TrainingRepository extends CrudRepository<Timereport, Long> {

  @Query("""
      select new de.hbt.salat.dailyreport.domain.TrainingInformation(t.employeeorder.employeecontract.id, sum(t.duration)) from Timereport t
      where t.employeeorder.employeecontract.freelancer = false and t.employeeorder.employeecontract.dailyWorkingTimeMinutes > 0
      and t.referenceday.refdate >= coalesce(:begin, t.referenceday.refdate) and t.referenceday.refdate <= coalesce(:end, t.referenceday.refdate) and t.training = true
      group by t.employeeorder.employeecontract.id
  """)
  List<TrainingInformation> getProjectTrainingTimesByDates(LocalDate begin, LocalDate end);
  
  @Query("""
      select new de.hbt.salat.dailyreport.domain.TrainingInformation(t.employeeorder.employeecontract.id, sum(t.duration)) from Timereport t
      where t.employeeorder.employeecontract.freelancer=false and t.employeeorder.employeecontract.dailyWorkingTimeMinutes > 0
      and t.referenceday.refdate >= coalesce(:begin, t.referenceday.refdate) and t.referenceday.refdate <= coalesce(:end, t.referenceday.refdate)
      and t.employeeorder.suborder.customerorder.id = :customerorderId and  t.employeeorder.suborder.sign not like 'x_%'
      group by t.employeeorder.employeecontract.id
  """)
  List<TrainingInformation> getCommonTrainingTimesByDates(LocalDate begin, LocalDate end, long customerorderId);

  @Query("""
      select new de.hbt.salat.dailyreport.domain.TrainingInformation(t.employeeorder.employeecontract.id, sum(t.duration)) from Timereport t
      where t.referenceday.refdate >= coalesce(:begin, t.referenceday.refdate) and t.referenceday.refdate <= coalesce(:end, t.referenceday.refdate)
      and t.employeeorder.employeecontract.id = :employeecontractId and t.training = true and t.employeeorder.suborder.sign != 'FORTBILDUNG'
      group by t.employeeorder.employeecontract.id
  """)
  Optional<TrainingInformation> getProjectTrainingTimesByDatesAndEmployeeContractId(long employeecontractId, LocalDate begin, LocalDate end);

  @Query("""
      select new de.hbt.salat.dailyreport.domain.TrainingInformation(t.employeeorder.employeecontract.id, sum(t.duration)) from Timereport t
      where t.referenceday.refdate >= coalesce(:begin, t.referenceday.refdate) and t.referenceday.refdate <= coalesce(:end, t.referenceday.refdate)
      and t.employeeorder.employeecontract.id = :employeecontractId
      and t.employeeorder.suborder.customerorder.id = :customerorderId and t.employeeorder.suborder.sign not like 'x_%'
      group by t.employeeorder.employeecontract.id
  """)
  Optional<TrainingInformation> getCommonTrainingTimesByDatesAndEmployeeContractId(long employeecontractId, LocalDate begin, LocalDate end, long customerorderId);

  /**
   * The training of every person employed on the day, per person and day, ordered by day (#1501):
   * bookings flagged as training and bookings on a suborder of the regular training - the rule of
   * the training account in „Meine Konten“. The ids of those suborders are never empty: the caller
   * passes a placeholder when there are none.
   */
  @Query("""
      select new de.hbt.salat.dailyreport.domain.TrainingDay(ec.employee.id, t.referenceday.refdate, sum(t.duration))
      from Timereport t join t.employeeorder eo join eo.employeecontract ec
      where (t.training = true or eo.suborder.id in :regularTrainingSuborderIds)
      and ec.employee.id in (
          select v.employee.id from Employeecontract v
          where v.validFrom <= :day and (v.validUntil is null or v.validUntil >= :day))
      group by ec.employee.id, t.referenceday.refdate
      order by ec.employee.id, t.referenceday.refdate
  """)
  List<TrainingDay> findTrainingDaysOfEmployeesEmployedOn(Collection<Long> regularTrainingSuborderIds, LocalDate day);

  /** All training of one person in minutes, by the same rule (#1501). */
  @Query("""
      select coalesce(sum(t.duration), 0)
      from Timereport t join t.employeeorder eo join eo.employeecontract ec
      where ec.employee.id = :employeeId and (t.training = true or eo.suborder.id in :regularTrainingSuborderIds)
  """)
  long sumTrainingMinutesOfEmployee(long employeeId, Collection<Long> regularTrainingSuborderIds);

}
