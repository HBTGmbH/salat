package de.hbt.salat.achievement.service;

import de.hbt.salat.common.achievement.AchievementContributor;
import de.hbt.salat.common.achievement.AchievementDefinition;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * The achievements there are (#1501): whatever the modules contribute through
 * {@link AchievementContributor} — the pattern of {@code BetaFeatureRegistry}. Two contributions of
 * the same key stop the application at start: the key is what an achievement is recorded under.
 */
@Component
public class AchievementRegistry {

  private final List<AchievementContributor> contributors;
  private final Map<String, AchievementDefinition> definitions = new LinkedHashMap<>();
  private final Map<String, AchievementContributor> contributorsByKey = new LinkedHashMap<>();

  public AchievementRegistry(List<AchievementContributor> contributors) {
    this.contributors = List.copyOf(contributors);
    contributors.stream()
        .flatMap(contributor -> contributor.getAchievements().stream()
            .map(definition -> Map.entry(definition, contributor)))
        .sorted(Map.Entry.comparingByKey(Comparator.comparing(AchievementDefinition::getPosition)
            .thenComparing(AchievementDefinition::getKey)))
        .forEach(entry -> {
          if (definitions.putIfAbsent(entry.getKey().getKey(), entry.getKey()) != null) {
            throw new IllegalStateException("achievement key contributed twice: " + entry.getKey().getKey());
          }
          contributorsByKey.put(entry.getKey().getKey(), entry.getValue());
        });
  }

  public List<AchievementContributor> contributors() {
    return contributors;
  }

  /** Every achievement, in the order of the profile page. */
  public List<AchievementDefinition> all() {
    return List.copyOf(definitions.values());
  }

  public Optional<AchievementDefinition> find(String key) {
    return Optional.ofNullable(definitions.get(key));
  }

  /** Whether the level is one of a contributed achievement — what is not is dropped silently. */
  public boolean isKnown(String key, int level) {
    return find(key).filter(definition -> definition.hasLevel(level)).isPresent();
  }

  public AchievementContributor contributorOf(AchievementDefinition definition) {
    return contributorsByKey.get(definition.getKey());
  }
}
