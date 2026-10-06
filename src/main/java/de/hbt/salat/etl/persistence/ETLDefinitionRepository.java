package de.hbt.salat.etl.persistence;

import java.util.List;
import java.util.Optional;
import de.hbt.salat.etl.domain.ETLDefinition;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ETLDefinitionRepository extends JpaRepository<ETLDefinition, Long> {

  Optional<ETLDefinition> findByName(String name);

  /**
   * All definitions with their dependencies in one query (#1350). Every dependency is itself one of
   * the definitions loaded here, so the graph is complete without a query per definition.
   */
  @Query("select distinct d from ETLDefinition d left join fetch d.dependencies")
  List<ETLDefinition> findAllWithDependencies();

}
