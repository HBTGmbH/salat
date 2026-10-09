package de.hbt.salat.beta.service;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;
import de.hbt.salat.beta.domain.BetaDefinition;
import de.hbt.salat.beta.domain.BetaFeature;

/**
 * The betas there are (#1447), as {@link BetaDefinition}s built from {@link BetaFeature}. The
 * services ask here rather than the enum, so that a test can stand in a beta while there is none.
 */
@Component
public class BetaCatalog {

  public List<BetaDefinition> all() {
    return Arrays.stream(BetaFeature.values()).map(BetaFeature::definition).toList();
  }

  public Optional<BetaDefinition> find(String key) {
    return all().stream().filter(definition -> definition.key().equals(key)).findFirst();
  }

  public boolean isKnown(String key) {
    return find(key).isPresent();
  }
}
