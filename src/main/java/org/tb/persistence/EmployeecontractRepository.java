package org.tb.persistence;

import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import org.tb.bdom.Employeecontract;

@Repository
public interface EmployeecontractRepository extends CrudRepository<Employeecontract, Long> {

}
