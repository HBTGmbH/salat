package org.tb.employee.persistence;

import java.util.List;
import org.hibernate.sql.ast.tree.expression.Over;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.PagingAndSortingRepository;
import org.springframework.stereotype.Repository;
import org.tb.employee.domain.Overtime;

@Repository
public interface OvertimeRepository extends PagingAndSortingRepository<Overtime, Long>, CrudRepository<Overtime, Long> {

  List<Overtime> findAllByEmployeecontractId(long employeeContractId);

}
