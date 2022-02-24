package org.tb.order;

import org.springframework.data.repository.PagingAndSortingRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface EmployeeordercontentRepository extends PagingAndSortingRepository<Employeeordercontent, Long> {

}
