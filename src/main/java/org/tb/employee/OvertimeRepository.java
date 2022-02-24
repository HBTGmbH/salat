package org.tb.employee;

import java.util.List;
import org.springframework.data.repository.PagingAndSortingRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface OvertimeRepository extends PagingAndSortingRepository<Overtime, Long> {

  List<Overtime> findAllByEmployeecontractId(long employeeContractId);

}
