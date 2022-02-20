package org.tb.persistence;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.repository.PagingAndSortingRepository;
import org.springframework.stereotype.Repository;
import org.tb.bdom.Employee;

@Repository
public interface EmployeeRepository extends PagingAndSortingRepository<Employee, Long>, JpaSpecificationExecutor<Employee> {

  Optional<Employee> findBySign(String sign);

  Optional<Employee> findByLoginname(String loginname);

}
