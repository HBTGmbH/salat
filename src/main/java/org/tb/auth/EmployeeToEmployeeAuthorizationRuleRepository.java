package org.tb.auth;

import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface EmployeeToEmployeeAuthorizationRuleRepository extends CrudRepository<EmployeeToEmployeeAuthorizationRule, Long> {
}
