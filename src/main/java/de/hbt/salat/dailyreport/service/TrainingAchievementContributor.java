package de.hbt.salat.dailyreport.service;

import static de.hbt.salat.common.achievement.AchievementLevel.level;
import static de.hbt.salat.common.achievement.AchievementTier.BRONZE;
import static de.hbt.salat.common.achievement.AchievementTier.GOLD;
import static de.hbt.salat.common.achievement.AchievementTier.PLATINUM;
import static de.hbt.salat.common.achievement.AchievementTier.SILVER;

import de.hbt.salat.common.achievement.AchievementContributor;
import de.hbt.salat.common.achievement.AchievementDefinition;
import de.hbt.salat.common.achievement.AchievementPerson;
import de.hbt.salat.common.achievement.AchievementProgress;
import de.hbt.salat.common.achievement.ReachedAchievement;
import de.hbt.salat.dailyreport.domain.TrainingDay;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Hours of training as an achievement (#1501), counted by {@link TrainingAchievementService}. A level
 * is reached on the day the booked training adds up to it; 1000 hours is the top.
 */
@Component
@RequiredArgsConstructor
public class TrainingAchievementContributor implements AchievementContributor {

  public static final AchievementDefinition TRAINING = new AchievementDefinition("training", "mortarboard", 30,
      List.of(level(100, BRONZE), level(200, SILVER), level(500, GOLD), level(1000, PLATINUM)));

  private static final int MINUTES_PER_HOUR = 60;

  private final TrainingAchievementService trainingAchievementService;

  @Override
  public List<AchievementDefinition> getAchievements() {
    return List.of(TRAINING);
  }

  @Override
  public List<ReachedAchievement> findReached(LocalDate today) {
    var reached = new ArrayList<ReachedAchievement>();
    trainingAchievementService.getTrainingDaysOfEmployeesEmployedOn(today)
        .forEach((employeeId, days) -> reached.addAll(reachedLevels(employeeId, days, today)));
    return reached;
  }

  @Override
  public Optional<AchievementProgress> progress(AchievementPerson person, AchievementDefinition achievement, int level,
      LocalDate today) {
    if (!TRAINING.equals(achievement)) {
      return Optional.empty();
    }
    long missing = (long) level * MINUTES_PER_HOUR - trainingAchievementService.getTrainingMinutes(person.employeeId());
    // whole hours, rounded up: 30 minutes still missing is one hour to go, not none
    return Optional.of(AchievementProgress.missing((int) Math.max(1, (missing + MINUTES_PER_HOUR - 1) / MINUTES_PER_HOUR)));
  }

  /** The days on which the running sum of training crosses each level; the days come ordered. */
  static List<ReachedAchievement> reachedLevels(long employeeId, List<TrainingDay> days, LocalDate today) {
    var reached = new ArrayList<ReachedAchievement>();
    var levels = TRAINING.getLevels();
    int next = 0;
    long minutes = 0;
    for (var day : days) {
      if (day.day().isAfter(today)) {
        break;
      }
      minutes += day.minutes();
      while (next < levels.size() && minutes >= (long) levels.get(next) * MINUTES_PER_HOUR) {
        reached.add(ReachedAchievement.ofEmployee(employeeId, TRAINING.getKey(), levels.get(next), day.day()));
        next++;
      }
    }
    return reached;
  }
}
