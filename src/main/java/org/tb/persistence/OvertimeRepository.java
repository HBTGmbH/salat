package org.tb.persistence;

import java.util.List;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import org.tb.bdom.Overtime;

@Repository
public interface OvertimeRepository extends CrudRepository<Overtime, Long> {

  List<Overtime> findAllByEmployeecontractId(long employeeContractId);

}
