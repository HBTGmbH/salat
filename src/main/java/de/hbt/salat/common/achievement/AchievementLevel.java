package de.hbt.salat.common.achievement;

/**
 * One level of an achievement (#1501): the value that has to be reached - years, hours, calls - and
 * the material of its medal.
 */
public record AchievementLevel(int value, AchievementTier tier) {

  public static AchievementLevel level(int value, AchievementTier tier) {
    return new AchievementLevel(value, tier);
  }
}
