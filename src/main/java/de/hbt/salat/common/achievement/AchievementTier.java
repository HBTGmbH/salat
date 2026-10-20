package de.hbt.salat.common.achievement;

import java.util.Locale;

/**
 * The material of a medal (#1501): it says how far someone has come, so it rises with the level.
 * The medal SVGs are built per tier by the skill {@code achievement-medals}; {@link #fileName()} is
 * the part of their file name.
 */
public enum AchievementTier {
  BRONZE, SILVER, GOLD, PLATINUM, DIAMOND, MASTER, GRANDMASTER;

  public String fileName() {
    return name().toLowerCase(Locale.ROOT);
  }
}
