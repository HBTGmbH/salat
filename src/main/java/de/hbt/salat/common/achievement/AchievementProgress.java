package de.hbt.salat.common.achievement;

import java.time.LocalDate;

/**
 * How far the next level is (#1501), for the profile page: either the day it is reached if nothing
 * changes - years with the company - or how much is still missing - hours, calls, betas.
 */
public record AchievementProgress(LocalDate expectedOn, Integer remaining) {

  public static AchievementProgress on(LocalDate expectedOn) {
    return new AchievementProgress(expectedOn, null);
  }

  public static AchievementProgress missing(int remaining) {
    return new AchievementProgress(null, remaining);
  }
}
