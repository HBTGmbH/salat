package org.tb.employee.persistence;

import java.util.List;
import org.springframework.data.repository.PagingAndSortingRepository;
import org.springframework.stereotype.Repository;
import org.tb.employee.domain.EmployeeFavoriteReport;


@Repository
public interface FavoriteReportRepository extends PagingAndSortingRepository<EmployeeFavoriteReport, Long> {
    List<EmployeeFavoriteReport> findAllByEmployeeId(long employeeId);
    void deleteEmployeeFavoriteReportById( Long id);
}
