package de.hbt.salat.budget.persistence;

import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.PagingAndSortingRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import de.hbt.salat.budget.domain.EmployeeCostAssignment;

@Repository
public interface EmployeeCostAssignmentRepository
    extends CrudRepository<EmployeeCostAssignment, Long>, PagingAndSortingRepository<EmployeeCostAssignment, Long> {

    List<EmployeeCostAssignment> findAllByOrderByEmployeeCostNameAscIdAsc();

    List<EmployeeCostAssignment> findByEmployeeCostName(String employeeCostName);

    long countByEmployeeCostName(String employeeCostName);

    @Query("""
        SELECT a FROM EmployeeCostAssignment a
        WHERE a.employeeId = :emp
          AND ((:so IS NULL AND a.suborderSign IS NULL) OR a.suborderSign = :so)
          AND a.validFrom <= :until AND a.validUntil >= :from
          AND (:excludeId IS NULL OR a.id != :excludeId)
        """)
    List<EmployeeCostAssignment> findOverlapping(
        @Param("emp") long employeeId,
        @Param("so") String suborderSign,
        @Param("from") LocalDate validFrom,
        @Param("until") LocalDate validUntil,
        @Param("excludeId") Long excludeId);

    @Query("SELECT a FROM EmployeeCostAssignment a WHERE a.employeeId = :emp"
        + " AND a.suborderSign = :so"
        + " AND a.validFrom <= :date AND a.validUntil >= :date")
    List<EmployeeCostAssignment> findEffectiveSuborderSpecific(
        @Param("emp") long employeeId,
        @Param("so") String suborderSign,
        @Param("date") LocalDate date);

    @Query("SELECT a FROM EmployeeCostAssignment a WHERE a.employeeId = :emp"
        + " AND a.suborderSign IS NULL"
        + " AND a.validFrom <= :date AND a.validUntil >= :date")
    List<EmployeeCostAssignment> findEffectiveGeneral(
        @Param("emp") long employeeId,
        @Param("date") LocalDate date);

    /**
     * Keeps the sign column in step with the person (#966, #968). The application resolves by
     * {@code employeeId} and no longer needs it; views, ETL definitions and reports still join on
     * it until they have moved to {@code employee_id}.
     */
    @Modifying
    @Query("UPDATE EmployeeCostAssignment a SET a.employeeSign = :sign WHERE a.employeeId = :employeeId")
    int updateEmployeeSign(@Param("employeeId") long employeeId, @Param("sign") String sign);

}
