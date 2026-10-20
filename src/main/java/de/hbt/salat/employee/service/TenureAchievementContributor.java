package de.hbt.salat.employee.service;

import static de.hbt.salat.common.achievement.AchievementLevel.level;
import static de.hbt.salat.common.achievement.AchievementTier.BRONZE;
import static de.hbt.salat.common.achievement.AchievementTier.DIAMOND;
import static de.hbt.salat.common.achievement.AchievementTier.GOLD;
import static de.hbt.salat.common.achievement.AchievementTier.GRANDMASTER;
import static de.hbt.salat.common.achievement.AchievementTier.MASTER;
import static de.hbt.salat.common.achievement.AchievementTier.PLATINUM;
import static de.hbt.salat.common.achievement.AchievementTier.SILVER;

import de.hbt.salat.common.achievement.AchievementContributor;
import de.hbt.salat.common.achievement.AchievementDefinition;
import de.hbt.salat.common.achievement.AchievementPerson;
import de.hbt.salat.common.achievement.AchievementProgress;
import de.hbt.salat.common.achievement.ReachedAchievement;
import de.hbt.salat.employee.domain.Tenure;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.function.BiFunction;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Time with the company as achievements (#1501), counted by {@link TenureService}: the first 100
 * days, and the years as „Loyalist“.
 */
@Component
@RequiredArgsConstructor
public class TenureAchievementContributor implements AchievementContributor {

  public static final AchievementDefinition FIRST_100_DAYS =
      new AchievementDefinition("first-100-days", "flag", 10, List.of(level(100, GOLD)));

  public static final AchievementDefinition TENURE = new AchievementDefinition("tenure", "calendar", 20, List.of(
      level(1, BRONZE), level(5, SILVER), level(10, GOLD), level(15, PLATINUM), level(20, DIAMOND), level(25, MASTER),
      level(30, GRANDMASTER), level(35, GRANDMASTER), level(40, GRANDMASTER), level(45, GRANDMASTER),
      level(50, GRANDMASTER)));

  private final TenureService tenureService;

  @Override
  public List<AchievementDefinition> getAchievements() {
    return List.of(FIRST_100_DAYS, TENURE);
  }

  @Override
  public List<ReachedAchievement> findReached(LocalDate today) {
    return tenureService.getTenuresOfEmployeesEmployedOn(today).entrySet().stream()
        .flatMap(entry -> getAchievements().stream()
            .flatMap(achievement -> achievement.getLevels().stream()
                .flatMap(level -> reachedOn(achievement).apply(entry.getValue(), level).stream()
                    .filter(day -> !day.isAfter(today))
                    .map(day -> ReachedAchievement.ofEmployee(entry.getKey(), achievement.getKey(), level, day)))))
        .toList();
  }

  @Override
  public Optional<AchievementProgress> progress(AchievementPerson person, AchievementDefinition achievement, int level,
      LocalDate today) {
    if (!getAchievements().contains(achievement)) {
      return Optional.empty();
    }
    return reachedOn(achievement).apply(tenureService.getTenure(person.employeeId()), level).map(AchievementProgress::on);
  }

  private static BiFunction<Tenure, Integer, Optional<LocalDate>> reachedOn(AchievementDefinition achievement) {
    return FIRST_100_DAYS.equals(achievement) ? Tenure::reachedAfterDays : Tenure::reachedOn;
  }
}
