package de.hbt.salat.budget.persistence;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.PagingAndSortingRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import de.hbt.salat.budget.domain.EmployeeCost;

/**
 * The rate periods. Every query goes through the category (#1209), never through the name column,
 * which only mirrors the category for readers outside the application.
 */
@Repository
public interface EmployeeCostRepository
    extends CrudRepository<EmployeeCost, Long>, PagingAndSortingRepository<EmployeeCost, Long> {

    List<EmployeeCost> findAllByOrderByCategoryNameAscValidFromAsc();

    /** The rate periods of one category, oldest first. */
    List<EmployeeCost> findByCategoryIdOrderByValidFromAsc(long categoryId);

    long countByCategoryId(long categoryId);

    /** The names of the categories that carry at least one rate period. */
    @Query("SELECT DISTINCT c.category.name FROM EmployeeCost c ORDER BY c.category.name")
    List<String> findDistinctNames();

    @Query("""
        SELECT c FROM EmployeeCost c
        WHERE c.category.id = :categoryId
          AND c.validFrom <= :until AND c.validUntil >= :from
          AND (:excludeId IS NULL OR c.id != :excludeId)
        """)
    List<EmployeeCost> findOverlapping(
        @Param("categoryId") long categoryId,
        @Param("from") LocalDate validFrom,
        @Param("until") LocalDate validUntil,
        @Param("excludeId") Long excludeId);

    @Query("""
        SELECT c FROM EmployeeCost c
        WHERE c.category.id = :categoryId AND c.validFrom <= :date AND c.validUntil >= :date
        """)
    Optional<EmployeeCost> findEffectiveByCategoryId(@Param("categoryId") long categoryId,
                                                     @Param("date") LocalDate date);

}
