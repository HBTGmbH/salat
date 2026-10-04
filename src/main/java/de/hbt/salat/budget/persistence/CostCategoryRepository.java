package de.hbt.salat.budget.persistence;

import java.util.List;
import java.util.Optional;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import de.hbt.salat.budget.domain.CostCategory;

@Repository
public interface CostCategoryRepository extends CrudRepository<CostCategory, Long> {

    Optional<CostCategory> findByName(String name);

    boolean existsByName(String name);

    List<CostCategory> findAllByOrderByNameAsc();

}
