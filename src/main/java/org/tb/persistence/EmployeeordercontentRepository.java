package org.tb.persistence;

import org.springframework.data.repository.PagingAndSortingRepository;
import org.springframework.stereotype.Repository;
import org.tb.bdom.Employeeordercontent;

@Repository
public interface EmployeeordercontentRepository extends PagingAndSortingRepository<Employeeordercontent, Long> {

}
