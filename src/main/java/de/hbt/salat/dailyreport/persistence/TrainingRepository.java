package de.hbt.salat.dailyreport.persistence;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import de.hbt.salat.dailyreport.domain.Timereport;
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

}
