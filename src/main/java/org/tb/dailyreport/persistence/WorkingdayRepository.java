package org.tb.dailyreport.persistence;

import java.time.LocalDate;
import java.util.Optional;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import org.tb.dailyreport.domain.Workingday;

@Repository
public interface WorkingdayRepository extends CrudRepository<Workingday, Long> {

  Optional<Workingday> findByRefdayAndEmployeecontractId(LocalDate refday, long employeecontractId);

}
