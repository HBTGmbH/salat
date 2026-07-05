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

    @Query("SELECT c FROM EmployeeCost c WHERE c.name = :name AND c.validFrom <= :date AND c.validUntil >= :date")
    Optional<EmployeeCost> findEffectiveByName(@Param("name") String name, @Param("date") LocalDate date);

}
