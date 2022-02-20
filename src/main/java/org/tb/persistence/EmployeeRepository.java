package org.tb.persistence;

import java.util.Optional;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import org.tb.bdom.Employee;

@Repository
public interface EmployeeRepository extends CrudRepository<Employee, Long> {

  Optional<Employee> findBySign(String sign);

  Optional<Employee> findByLoginname(String loginname);

}
