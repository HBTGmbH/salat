package de.hbt.salat.dailyreport.service;

import static de.hbt.salat.common.achievement.AchievementLevel.level;
import static de.hbt.salat.common.achievement.AchievementTier.BRONZE;
import static de.hbt.salat.common.achievement.AchievementTier.DIAMOND;
import static de.hbt.salat.common.achievement.AchievementTier.GOLD;
import static de.hbt.salat.common.achievement.AchievementTier.MASTER;
import static de.hbt.salat.common.achievement.AchievementTier.PLATINUM;
import static de.hbt.salat.common.achievement.AchievementTier.SILVER;

import de.hbt.salat.common.achievement.AchievementContributor;
import de.hbt.salat.common.achievement.AchievementDefinition;
import de.hbt.salat.common.achievement.AchievementPerson;
import de.hbt.salat.common.achievement.AchievementProgress;
import de.hbt.salat.common.achievement.ReachedAchievement;
import de.hbt.salat.dailyreport.domain.RegularTrainingDay;
import de.hbt.salat.dailyreport.domain.WorkingDays;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Working days of regular training in a calendar year as a yearly achievement (#1512), counted by
 * {@link TrainingAchievementService}. Every year starts anew; a level is reached on the day the
 * year's training adds up to it.
 *
 * <p>A working day is the daily working time of the contract the training was booked on, as with the
 * vacation; a person with several contracts in a year collects on all of them. Only the regular
 * training counts, none booked on a project, and none on a freelancer contract. The levels end at
 * ten days, what the training rules of the company provide for: booking more brings no medal.
 */
@Component
@RequiredArgsConstructor
public class TrainingYearAchievementContributor implements AchievementContributor {

  public static final AchievementDefinition TRAINING_YEAR = AchievementDefinition.yearly("training-year", "book", 31,
      List.of(level(1, BRONZE), level(2, SILVER), level(4, GOLD), level(6, PLATINUM), level(8, DIAMOND),
          level(10, MASTER)));

  /** What is shown as missing when the level is reached but not yet recorded: the job records it at night. */
  private static final BigDecimal LEAST_MISSING = new BigDecimal("0.1");

  private final TrainingAchievementService trainingAchievementService;

  @Override
  public List<AchievementDefinition> getAchievements() {
    return List.of(TRAINING_YEAR);
  }

  @Override
  public List<ReachedAchievement> findReached(LocalDate today) {
    var reached = new ArrayList<ReachedAchievement>();
    trainingAchievementService.getRegularTrainingDaysOfEmployeesEmployedOn(today)
        .forEach((employeeId, days) -> reached.addAll(reachedLevels(employeeId, days, today)));
    return reached;
  }

  @Override
  public Optional<AchievementProgress> progress(AchievementPerson person, AchievementDefinition achievement, int level,
      LocalDate today) {
    if (!TRAINING_YEAR.equals(achievement)) {
      return Optional.empty();
    }
    var missing = trainingAchievementService.getRegularTrainingWorkingDaysOfYear(person.employeeId(), today)
        .missingTo(level);
    return Optional.of(AchievementProgress.missing(missing.max(LEAST_MISSING)));
  }

  /**
   * The days on which the working days of each year cross each level; the days come ordered, and
   * each year starts again from nothing.
   */
  static List<ReachedAchievement> reachedLevels(long employeeId, List<RegularTrainingDay> days, LocalDate today) {
    var reached = new ArrayList<ReachedAchievement>();
    var levels = TRAINING_YEAR.getLevels();
    int year = 0;
    int next = 0;
    var sum = WorkingDays.ZERO;
    for (var day : days) {
      if (day.day().isAfter(today)) {
        break;
      }
      if (day.day().getYear() != year) {
        year = day.day().getYear();
        next = 0;
        sum = WorkingDays.ZERO;
      }
      sum = sum.plus(day.minutes(), day.dailyWorkingTime().toMinutes());
      while (next < levels.size() && sum.reaches(levels.get(next))) {
        reached.add(ReachedAchievement.ofEmployee(employeeId, TRAINING_YEAR.getKey(), levels.get(next), day.day()));
        next++;
      }
    }
    return reached;
  }
}
