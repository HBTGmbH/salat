package de.hbt.salat.achievement.domain;

import de.hbt.salat.common.achievement.AchievementDefinition;
import de.hbt.salat.common.achievement.AchievementTier;
import java.time.LocalDate;
import java.util.List;

/**
 * The profile page (#1501): every level the person has reached, in the order it was reached, and
 * for each achievement the next level still to come.
 */
public record AchievementProfile(List<Reached> reached, List<Next> next) {

  public record Reached(AchievementDefinition definition, int level, AchievementTier tier, LocalDate achievedOn) {
  }

  /**
   * The next level of an achievement: {@code expectedOn} is the day it is reached if nothing
   * changes, {@code remaining} how much is still missing; either or both {@code null} where it
   * cannot be told.
   */
  public record Next(AchievementDefinition definition, int level, LocalDate expectedOn, Integer remaining) {
  }
}
