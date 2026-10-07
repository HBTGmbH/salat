package de.hbt.salat.budget.persistence;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import de.hbt.salat.budget.domain.CostCategory;

@Repository
public interface CostCategoryRepository extends CrudRepository<CostCategory, Long> {

    Optional<CostCategory> findByName(String name);

    boolean existsByName(String name);

    List<CostCategory> findAllByOrderByNameAsc();

    /**
     * Whether a line of a fixed-price calculation names the category (#1404) — it then has to stay,
     * even once no rate period and no assignment refers to it any more.
     */
    @Query("SELECT COUNT(c) > 0 FROM OrderBudgetCalculation c WHERE c.category.id = :categoryId")
    boolean isUsedByCalculation(@Param("categoryId") long categoryId);

}
