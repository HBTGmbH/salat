package de.hbt.salat.common.achievement;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Contributes the achievements of a module (#1501) - the pattern of {@code BetaFeatureContributor}
 * and {@code PaletteProvider}. Implement it in the module that knows what is achieved and annotate
 * the implementation with {@code @Component}. Declare each achievement as a
 * {@code public static final} constant of the implementation: the check that every achievement has
 * its texts reads the constants without Spring.
 *
 * <p>The achievement module calls it from a nightly job, which runs as a job user (manager, people
 * lead and backoffice), and for the profile page of the logged-in person.
 */
public interface AchievementContributor {

  List<AchievementDefinition> getAchievements();

  /**
   * Every level of its achievements that the persons employed on {@code today} have reached by
   * then, each with the day it was reached - by employee, or by login where the module knows people
   * only by their login. Levels already recorded may come again; the job records only what is new.
   */
  List<ReachedAchievement> findReached(LocalDate today);

  /**
   * How far the person is from the level, for the profile page: the day it is reached if nothing
   * changes, or what is still missing. Empty where that cannot be told.
   */
  default Optional<AchievementProgress> progress(AchievementPerson person, AchievementDefinition achievement,
      int level, LocalDate today) {
    return Optional.empty();
  }
}
