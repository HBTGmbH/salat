package de.hbt.salat.employee.service;

import de.hbt.salat.common.achievement.AchievementContributor;
import de.hbt.salat.common.achievement.AchievementDefinition;
import de.hbt.salat.common.achievement.ReachedAchievement;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Years with the company as an achievement (#1501), counted by {@link TenureService}. */
@Component
@RequiredArgsConstructor
public class TenureAchievementContributor implements AchievementContributor {

  public static final AchievementDefinition TENURE =
      new AchievementDefinition("tenure", List.of(1, 5, 10, 15, 20, 25, 30, 35, 40, 45, 50), "trophy");

  private final TenureService tenureService;

  @Override
  public List<AchievementDefinition> getAchievements() {
    return List.of(TENURE);
  }

  @Override
  public List<ReachedAchievement> findReached(LocalDate today) {
    return tenureService.getTenuresOfEmployeesEmployedOn(today).entrySet().stream()
        .flatMap(entry -> TENURE.getLevels().stream()
            .flatMap(years -> entry.getValue().reachedOn(years).stream()
                .filter(day -> !day.isAfter(today))
                .map(day -> new ReachedAchievement(entry.getKey(), TENURE.getKey(), years, day))))
        .toList();
  }

  @Override
  public Optional<LocalDate> forecast(long employeeId, AchievementDefinition achievement, int level, LocalDate today) {
    if (!TENURE.equals(achievement)) {
      return Optional.empty();
    }
    return tenureService.getTenure(employeeId).reachedOn(level);
  }
}
