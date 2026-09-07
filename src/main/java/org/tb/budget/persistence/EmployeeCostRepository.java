package org.tb.budget.persistence;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.PagingAndSortingRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.tb.budget.domain.EmployeeCost;

@Repository
public interface EmployeeCostRepository
    extends CrudRepository<EmployeeCost, Long>, PagingAndSortingRepository<EmployeeCost, Long> {

    List<EmployeeCost> findAllByOrderByNameAscValidFromAsc();

    /**
     * All cost records carrying the given name. Several records share one name to model a rate that
     * changed over time — the name is the category, not the key of a single record.
     */
    List<EmployeeCost> findByNameOrderByValidFromAsc(String name);

    @Query("SELECT DISTINCT c.name FROM EmployeeCost c ORDER BY c.name")
    List<String> findDistinctNames();

    @Query("""
        SELECT c FROM EmployeeCost c
        WHERE c.name = :name
          AND c.validFrom <= :until AND c.validUntil >= :from
          AND (:excludeId IS NULL OR c.id != :excludeId)
        """)
    List<EmployeeCost> findOverlapping(
        @Param("name") String name,
        @Param("from") LocalDate validFrom,
        @Param("until") LocalDate validUntil,
        @Param("excludeId") Long excludeId);

    @Query("SELECT c FROM EmployeeCost c WHERE c.name = :name AND c.validFrom <= :date AND c.validUntil >= :date")
    Optional<EmployeeCost> findEffectiveByName(@Param("name") String name, @Param("date") LocalDate date);

}
