package org.tb.employee.persistence;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.PagingAndSortingRepository;
import org.springframework.stereotype.Repository;
import org.tb.employee.domain.Employee;

@Repository
public interface EmployeeRepository extends PagingAndSortingRepository<Employee, Long>, JpaSpecificationExecutor<Employee>,
    CrudRepository<Employee, Long> {

  Optional<Employee> findBySign(String sign);

  Optional<Employee> findByLoginname(String loginname);

}
