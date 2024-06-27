package org.tb.dailyreport.persistence;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import org.tb.dailyreport.domain.Workingday;

@Repository
public interface WorkingdayRepository extends CrudRepository<Workingday, Long> {

  Optional<Workingday> findByRefdayAndEmployeecontractId(LocalDate refday, long employeecontractId);

  List<Workingday> findAllByEmployeecontractId(long employeeContractId);

  @Query("""
      select wd from Workingday wd
      where wd.employeecontract.id = :employeecontractId and
      wd.refday >= :begin and wd.refday <= :end
      """)
  List<Workingday> findAllByEmployeecontractIdAndReferencedayBetween(long employeecontractId, LocalDate begin, LocalDate end);


}
