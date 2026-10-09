package de.hbt.salat.beta.service;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;
import de.hbt.salat.common.beta.BetaFeature;
import de.hbt.salat.common.beta.BetaFeatureContributor;

/**
 * The betas there are (#1447): whatever the modules contribute through
 * {@link BetaFeatureContributor} — the pattern of {@code UiStateKeyRegistry}. The beta module knows
 * none of them itself. Two modules contributing the same key stop the application at start: the key
 * is what a person's switch is stored under.
 */
@Component
public class BetaFeatureRegistry {

  private final List<BetaFeature> features;

  public BetaFeatureRegistry(List<BetaFeatureContributor> contributors) {
    var byKey = contributors.stream()
        .flatMap(contributor -> contributor.getBetaFeatures().stream())
        .collect(Collectors.toMap(BetaFeature::getKey, Function.identity(), (first, second) -> {
          throw new IllegalStateException("beta key contributed twice: " + first.getKey());
        }));
    this.features = byKey.values().stream().sorted(Comparator.comparing(BetaFeature::getKey)).toList();
  }

  public List<BetaFeature> all() {
    return features;
  }

  public Optional<BetaFeature> find(String key) {
    return features.stream().filter(feature -> feature.getKey().equals(key)).findFirst();
  }

  public boolean isKnown(String key) {
    return find(key).isPresent();
  }
}
