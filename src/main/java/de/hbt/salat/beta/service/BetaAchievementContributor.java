package de.hbt.salat.beta.service;

import static de.hbt.salat.common.achievement.AchievementLevel.level;
import static de.hbt.salat.common.achievement.AchievementTier.BRONZE;
import static de.hbt.salat.common.achievement.AchievementTier.GOLD;
import static de.hbt.salat.common.achievement.AchievementTier.SILVER;

import de.hbt.salat.beta.domain.AnsweredBeta;
import de.hbt.salat.common.achievement.AchievementContributor;
import de.hbt.salat.common.achievement.AchievementDefinition;
import de.hbt.salat.common.achievement.AchievementPerson;
import de.hbt.salat.common.achievement.AchievementProgress;
import de.hbt.salat.common.achievement.ReachedAchievement;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Betas tried and answered the question about, „Beta-Pionier“ (#1501), counted by
 * {@link BetaAchievementService}. The n-th answered beta reaches level n on the day of its answer.
 */
@Component
@RequiredArgsConstructor
public class BetaAchievementContributor implements AchievementContributor {

  public static final AchievementDefinition BETA = new AchievementDefinition("beta", "flask", 50,
      List.of(level(1, BRONZE), level(3, SILVER), level(5, GOLD)));

  private final BetaAchievementService betaAchievementService;

  @Override
  public List<AchievementDefinition> getAchievements() {
    return List.of(BETA);
  }

  @Override
  public List<ReachedAchievement> findReached(LocalDate today) {
    var reached = new ArrayList<ReachedAchievement>();
    betaAchievementService.getAnsweredByEmployee().forEach((employeeId, answered) -> reached.addAll(reachedLevels(employeeId, answered, today)));
    return reached;
  }

  @Override
  public Optional<AchievementProgress> progress(AchievementPerson person, AchievementDefinition achievement, int level,
      LocalDate today) {
    if (!BETA.equals(achievement)) {
      return Optional.empty();
    }
    return Optional.of(AchievementProgress.missing((int) Math.max(1, level - betaAchievementService.countAnswered(person.employeeId()))));
  }

  static List<ReachedAchievement> reachedLevels(long employeeId, List<AnsweredBeta> answered, LocalDate today) {
    var reached = new ArrayList<ReachedAchievement>();
    var counted = answered.stream().filter(beta -> !beta.answeredOn().isAfter(today)).toList();
    for (int level : BETA.getLevels()) {
      if (counted.size() >= level) {
        reached.add(ReachedAchievement.ofEmployee(employeeId, BETA.getKey(), level, counted.get(level - 1).answeredOn()));
      }
    }
    return reached;
  }
}
