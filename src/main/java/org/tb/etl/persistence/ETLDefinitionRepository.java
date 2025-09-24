package org.tb.etl.persistence;

import java.util.Optional;
import org.tb.etl.domain.ETLDefinition;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ETLDefinitionRepository extends JpaRepository<ETLDefinition, String> {

  Optional<ETLDefinition> findByName(String name);

}
