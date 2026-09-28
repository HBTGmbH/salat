package de.hbt.salat.etl.persistence;

import java.util.Optional;
import de.hbt.salat.etl.domain.ETLDefinition;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ETLDefinitionRepository extends JpaRepository<ETLDefinition, String> {

  Optional<ETLDefinition> findByName(String name);

}
