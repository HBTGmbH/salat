package org.tb.dailyreport;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TrainingRepository extends CrudRepository<Timereport, Long> {

  @Query("""
      select t.employeecontract.id, sum(t.durationhours), sum(t.durationminutes) from Timereport t
      where t.employeecontract.freelancer is false and t.employeecontract.dailyWorkingTimeMinutes > 0
      and t.referenceday.refdate >= :begin and t.referenceday.refdate <= :end  and t.training = true
      group by t.employeecontract.id
  """)
  List<Object[]> getProjectTrainingTimesByDates(LocalDate begin, LocalDate end);
  
  @Query("""
      select t.employeecontract.id, sum(t.durationhours), sum(t.durationminutes) from Timereport t 
      where t.employeecontract.freelancer=false and t.employeecontract.dailyWorkingTimeMinutes > 0
      and t.referenceday.refdate >= :begin and t.referenceday.refdate <= :end
      and t.suborder.customerorder.id = :customerorderId and  t.suborder.sign not like 'x_%'  
      group by t.employeecontract.id
  """)
  List<Object[]> getCommonTrainingTimesByDates(LocalDate begin, LocalDate end, long customerorderId);

  @Query("""
      select sum(t.durationhours), sum(t.durationminutes) from Timereport t
      where t.referenceday.refdate >= :begin and t.referenceday.refdate <= :end
      and t.employeecontract.id = :employeecontractId and t.training = true
  """)
  Optional<Object[]> getProjectTrainingTimesByDatesAndEmployeeContractId(long employeecontractId, LocalDate begin, LocalDate end);

  @Query("""
      select sum(t.durationhours), sum(t.durationminutes) from Timereport t
      where t.referenceday.refdate >= :begin and t.referenceday.refdate <= :end
      and t.employeecontract.id = :employeecontractId
      and t.suborder.customerorder.id = :customerorderId and t.suborder.sign not like 'x_%'
  """)
  Optional<Object[]> getCommonTrainingTimesByDatesAndEmployeeContractId(long employeecontractId, LocalDate begin, LocalDate end, long customerorderId);

}
