package org.tb.employee.persistence;

import java.util.List;
import java.util.Optional;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import org.tb.employee.domain.Vacation;

@Repository
public interface VacationRepository extends CrudRepository<Vacation, Long> {

  List<Vacation> findAllByEmployeecontractId(Long employeecontractId);
  Optional<Vacation> findByEmployeecontractIdAndYear(long employeecontractId, int year);

}
