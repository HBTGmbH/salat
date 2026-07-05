package org.tb.budget.persistence;

import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.PagingAndSortingRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.tb.budget.domain.EmployeeCostAssignment;

@Repository
public interface EmployeeCostAssignmentRepository
    extends CrudRepository<EmployeeCostAssignment, Long>, PagingAndSortingRepository<EmployeeCostAssignment, Long> {

    List<EmployeeCostAssignment> findAllByOrderByEmployeeCostNameAscEmployeeSignAsc();

    List<EmployeeCostAssignment> findByEmployeeCostName(String employeeCostName);

    @Query("""
        SELECT a FROM EmployeeCostAssignment a
        WHERE a.employeeSign = :emp
          AND ((:so IS NULL AND a.suborderSign IS NULL) OR a.suborderSign = :so)
          AND a.validFrom <= :until AND a.validUntil >= :from
          AND (:excludeId IS NULL OR a.id != :excludeId)
        """)
    List<EmployeeCostAssignment> findOverlapping(
        @Param("emp") String employeeSign,
        @Param("so") String suborderSign,
        @Param("from") LocalDate validFrom,
        @Param("until") LocalDate validUntil,
        @Param("excludeId") Long excludeId);

    @Query("SELECT a FROM EmployeeCostAssignment a WHERE a.employeeSign = :emp"
        + " AND a.suborderSign = :so"
        + " AND a.validFrom <= :date AND a.validUntil >= :date")
    List<EmployeeCostAssignment> findEffectiveSuborderSpecific(
        @Param("emp") String employeeSign,
        @Param("so") String suborderSign,
        @Param("date") LocalDate date);

    @Query("SELECT a FROM EmployeeCostAssignment a WHERE a.employeeSign = :emp"
        + " AND a.suborderSign IS NULL"
        + " AND a.validFrom <= :date AND a.validUntil >= :date")
    List<EmployeeCostAssignment> findEffectiveGeneral(
        @Param("emp") String employeeSign,
        @Param("date") LocalDate date);

}
