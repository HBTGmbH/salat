package org.tb.dailyreport;

import java.time.LocalDate;
import java.util.Optional;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface WorkingdayRepository extends CrudRepository<Workingday, Long> {

  Optional<Workingday> findByRefdayAndEmployeecontractId(LocalDate refday, long employeecontractId);

}
