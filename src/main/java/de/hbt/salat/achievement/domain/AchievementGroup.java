package de.hbt.salat.achievement.domain;

import de.hbt.salat.common.achievement.AchievementDefinition;
import java.time.LocalDate;
import java.util.List;

/**
 * One achievement on the profile page (#1501): the levels the person has reached and the next one,
 * {@code null} once every level is reached.
 */
public record AchievementGroup(AchievementDefinition definition, List<Reached> reached, Next next) {

  public record Reached(int level, LocalDate achievedOn) {
  }

  /** {@code expectedOn} is the day it is reached if nothing changes; {@code null} where it cannot be told. */
  public record Next(int level, LocalDate expectedOn) {
  }
}
