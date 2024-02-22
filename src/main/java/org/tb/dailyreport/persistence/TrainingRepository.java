package org.tb.dailyreport.persistence;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import org.tb.dailyreport.domain.Timereport;
import org.tb.dailyreport.domain.TrainingInformation;

@Repository
public interface TrainingRepository extends CrudRepository<Timereport, Long> {

  @Query("""
      select new org.tb.dailyreport.domain.TrainingInformation(t.employeecontract.id, sum(t.durationhours), sum(t.durationminutes)) from Timereport t
      where t.employeecontract.freelancer = false and t.employeecontract.dailyWorkingTimeMinutes > 0
      and t.referenceday.refdate >= :begin and t.referenceday.refdate <= :end  and t.training = true
      group by t.employeecontract.id
  """)
  List<TrainingInformation> getProjectTrainingTimesByDates(LocalDate begin, LocalDate end);
  
  @Query("""
      select new org.tb.dailyreport.domain.TrainingInformation(t.employeecontract.id, sum(t.durationhours), sum(t.durationminutes)) from Timereport t
      where t.employeecontract.freelancer=false and t.employeecontract.dailyWorkingTimeMinutes > 0
      and t.referenceday.refdate >= :begin and t.referenceday.refdate <= :end
      and t.suborder.customerorder.id = :customerorderId and  t.suborder.sign not like 'x_%'
      group by t.employeecontract.id
  """)
  List<TrainingInformation> getCommonTrainingTimesByDates(LocalDate begin, LocalDate end, long customerorderId);

  @Query("""
      select new org.tb.dailyreport.domain.TrainingInformation(t.employeecontract.id, sum(t.durationhours), sum(t.durationminutes)) from Timereport t
      where t.referenceday.refdate >= :begin and t.referenceday.refdate <= :end
      and t.employeecontract.id = :employeecontractId and t.training = true
      group by t.employeecontract.id
  """)
  Optional<TrainingInformation> getProjectTrainingTimesByDatesAndEmployeeContractId(long employeecontractId, LocalDate begin, LocalDate end);

  @Query("""
      select new org.tb.dailyreport.domain.TrainingInformation(t.employeecontract.id, sum(t.durationhours), sum(t.durationminutes)) from Timereport t
      where t.referenceday.refdate >= :begin and t.referenceday.refdate <= :end
      and t.employeecontract.id = :employeecontractId
      and t.suborder.customerorder.id = :customerorderId and t.suborder.sign not like 'x_%'
      group by t.employeecontract.id
  """)
  Optional<TrainingInformation> getCommonTrainingTimesByDatesAndEmployeeContractId(long employeecontractId, LocalDate begin, LocalDate end, long customerorderId);

}
