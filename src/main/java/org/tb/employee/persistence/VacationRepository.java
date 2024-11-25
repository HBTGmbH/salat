package org.tb.employee.persistence;

import java.util.List;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import org.tb.employee.domain.Vacation;

@Repository
public interface VacationRepository extends CrudRepository<Vacation, Long> {

  List<Vacation> findByEmployeecontractId(Long employeecontractId);

}
