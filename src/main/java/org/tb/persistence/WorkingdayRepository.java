package org.tb.persistence;

import java.util.Date;
import java.util.Optional;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import org.tb.bdom.Workingday;

@Repository
public interface WorkingdayRepository extends CrudRepository<Workingday, Long> {

  Optional<Workingday> findByRefdayAndEmployeecontractId(Date refday, long employeecontractId);

}
