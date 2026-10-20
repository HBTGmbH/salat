package de.hbt.salat.common.achievement;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * An achievement a person can reach, in levels - years with the company in 1, 5, 10 … (#837, #1501).
 * It belongs to the module that knows what is achieved: that module declares it as a constant and
 * contributes it through an {@link AchievementContributor}, the way modules contribute their betas.
 * The achievement module records, announces and shows achievements, but knows none of them.
 *
 * <p>Every achievement is a medal: the pictogram says what was achieved, the {@link AchievementTier}
 * of the level how far someone has come, and the level's value stands on the medal's plate. The
 * medal SVGs are built by the skill {@code achievement-medals} as
 * {@code /images/achievement/medal-<pictogram>-<tier>.svg}.
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
  private final String pictogram;
  private final int position;
  private final List<AchievementLevel> levels;

  /**
   * @param key       the key the achievement is recorded under; never change it once recorded
   * @param pictogram the pictogram of its medal, the name of a source in the skill {@code achievement-medals}
   * @param position  where it stands among the others on the profile page, lowest first
   * @param levels    the levels, values positive and strictly ascending, tiers not descending
   */
  public AchievementDefinition(String key, String pictogram, int position, List<AchievementLevel> levels) {
    if (key == null || !KEY.matcher(key).matches()) {
      throw new IllegalArgumentException("achievement key must be lower case letters, digits and dashes: " + key);
    }
    if (pictogram == null || !KEY.matcher(pictogram).matches()) {
      throw new IllegalArgumentException("pictogram must be lower case letters, digits and dashes: " + key);
    }
    if (levels == null || levels.isEmpty()) {
      throw new IllegalArgumentException("an achievement has at least one level: " + key);
    }
    for (int i = 0; i < levels.size(); i++) {
      var level = levels.get(i);
      if (level.value() < 1 || i > 0 && level.value() <= levels.get(i - 1).value()) {
        throw new IllegalArgumentException("level values must be positive and strictly ascending: " + key);
      }
      if (i > 0 && level.tier().compareTo(levels.get(i - 1).tier()) < 0) {
        throw new IllegalArgumentException("a higher level never gets a lower tier: " + key);
      }
    }
    this.key = key;
    this.pictogram = pictogram;
    this.position = position;
    this.levels = List.copyOf(levels);
  }

  public String getKey() {
    return key;
  }

  public String getPictogram() {
    return pictogram;
  }

  public int getPosition() {
    return position;
  }

  /** The values of the levels, ascending. */
  public List<Integer> getLevels() {
    return levels.stream().map(AchievementLevel::value).toList();
  }

  public boolean hasLevel(int level) {
    return levels.stream().anyMatch(candidate -> candidate.value() == level);
  }

  /** The material of the level's medal. */
  public AchievementTier tierOf(int level) {
    return levels.stream().filter(candidate -> candidate.value() == level).findFirst()
        .map(AchievementLevel::tier)
        .orElseThrow(() -> new IllegalArgumentException("no level " + level + " in " + key));
  }

  /** The lowest level above {@code level}; the first level for {@code null}. */
  public Optional<Integer> nextLevelAfter(Integer level) {
    return getLevels().stream().filter(candidate -> level == null || candidate > level).findFirst();
  }

  /** Message key of the achievement's name, the heading of its medals on the profile page. */
  public String groupKey() {
    return "main.achievement." + key + ".group";
  }

  /**
   * Message key of what a level stands for - „10 Jahre bei HBT“ -, for the popup and the
   * notification; its only argument is the level.
   */
  public String titleKey() {
    return "main.achievement." + key + ".title";
  }

  /** Message key of the name of one level - „Urgestein“; every level has one. */
  public String rankKey(int level) {
    return "main.achievement." + key + ".rank." + level;
  }

  /** Message key of what the achievement is given for, for the popover on the profile page. */
  public String descriptionKey() {
    return "main.achievement." + key + ".description";
  }

  /** Message key of how to get it - the hint the popover of a level still to come gives. */
  public String hintKey() {
    return "main.achievement." + key + ".hint";
  }

  /** Message key of what is still missing to the next level - „noch 37 Aufrufe“; its argument is the amount. */
  public String remainingKey() {
    return "main.achievement." + key + ".remaining";
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
