package de.hbt.salat.common.achievement;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * An achievement a person can reach, in levels — years with the company in 1, 5, 10 … (#837, #1501).
 * It belongs to the module that knows what is achieved: that module declares it as a constant and
 * contributes it through an {@link AchievementContributor}, the way modules contribute their betas.
 * The achievement module records, announces and shows achievements, but knows none of them.
 *
 * <p>Only positive incentives (#837): an achievement rewards the person's own conduct. There is no
 * ranking, no negative counterpart, and nobody sees the achievements of somebody else.
 *
 * <p>Recorded achievements are matched by {@link #getKey()}; the key of an achievement no module
 * contributes any more is dropped silently when they are read, so no cleanup migration is needed.
 */
public final class AchievementDefinition {

  /** Lower case letters, digits and dashes; the key goes into message keys and element ids. */
  private static final Pattern KEY = Pattern.compile("[a-z0-9][a-z0-9-]{0,63}");

  private final String key;
  private final List<Integer> levels;
  private final String icon;

  /**
   * @param key    the key the achievement is recorded under; never change it once recorded
   * @param levels the levels, positive and strictly ascending
   * @param icon   the name of the Tabler icon the achievement is shown with, without {@code ti-}
   */
  public AchievementDefinition(String key, List<Integer> levels, String icon) {
    if (key == null || !KEY.matcher(key).matches()) {
      throw new IllegalArgumentException("achievement key must be lower case letters, digits and dashes: " + key);
    }
    if (levels == null || levels.isEmpty()) {
      throw new IllegalArgumentException("an achievement has at least one level: " + key);
    }
    for (int i = 0; i < levels.size(); i++) {
      if (levels.get(i) < 1 || i > 0 && levels.get(i) <= levels.get(i - 1)) {
        throw new IllegalArgumentException("levels must be positive and strictly ascending: " + key);
      }
    }
    this.key = key;
    this.levels = List.copyOf(levels);
    this.icon = Objects.requireNonNull(icon);
  }

  public String getKey() {
    return key;
  }

  public List<Integer> getLevels() {
    return levels;
  }

  public String getIcon() {
    return icon;
  }

  public boolean hasLevel(int level) {
    return levels.contains(level);
  }

  /** The lowest level above {@code level}; the first level for {@code null}. */
  public Optional<Integer> nextLevelAfter(Integer level) {
    return levels.stream().filter(candidate -> level == null || candidate > level).findFirst();
  }

  /** Message key of the heading the levels are grouped under on the profile page. */
  public String groupKey() {
    return "main.achievement." + key + ".group";
  }

  /**
   * Message key of the full name of one level — „5 Jahre bei HBT“ —, for the popup and the
   * notification; its only argument is the level.
   */
  public String titleKey() {
    return "main.achievement." + key + ".title";
  }

  /** Message key of the short name of one level under its group — „5 Jahre“; its only argument is the level. */
  public String levelKey() {
    return "main.achievement." + key + ".level";
  }

  @Override
  public boolean equals(Object other) {
    return other instanceof AchievementDefinition definition && definition.key.equals(key);
  }

  @Override
  public int hashCode() {
    return Objects.hash(key);
  }

  @Override
  public String toString() {
    return key;
  }
}
