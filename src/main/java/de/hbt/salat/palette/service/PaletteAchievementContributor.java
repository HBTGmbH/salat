package de.hbt.salat.palette.service;

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
import de.hbt.salat.palette.domain.PaletteUseDay;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Runs of the command palette as an achievement, „Palette Junkie“ (#1501), counted by
 * {@link PaletteUseService} from the day the counting came in. The palette knows people by their
 * login only, so it reports by login.
 */
@Component
@RequiredArgsConstructor
public class PaletteAchievementContributor implements AchievementContributor {

  public static final AchievementDefinition PALETTE = new AchievementDefinition("palette", "keyboard", 40, List.of(
      level(100, BRONZE), level(500, SILVER), level(1000, GOLD), level(2500, PLATINUM), level(5000, DIAMOND),
      level(10000, MASTER)));

  private final PaletteUseService paletteUseService;

  @Override
  public List<AchievementDefinition> getAchievements() {
    return List.of(PALETTE);
  }

  @Override
  public List<ReachedAchievement> findReached(LocalDate today) {
    var reached = new ArrayList<ReachedAchievement>();
    paletteUseService.getUseDaysByLogin().forEach((salatUserId, days) -> reached.addAll(reachedLevels(salatUserId, days, today)));
    return reached;
  }

  @Override
  public Optional<AchievementProgress> progress(AchievementPerson person, AchievementDefinition achievement, int level,
      LocalDate today) {
    if (!PALETTE.equals(achievement)) {
      return Optional.empty();
    }
    return Optional.of(AchievementProgress.missing((int) Math.max(1, level - paletteUseService.getUses(person.salatUserId()))));
  }

  /** The days on which the running sum of runs crosses each level; the days come ordered. */
  static List<ReachedAchievement> reachedLevels(long salatUserId, List<PaletteUseDay> days, LocalDate today) {
    var reached = new ArrayList<ReachedAchievement>();
    var levels = PALETTE.getLevels();
    int next = 0;
    long uses = 0;
    for (var day : days) {
      if (day.day().isAfter(today)) {
        break;
      }
      uses += day.uses();
      while (next < levels.size() && uses >= levels.get(next)) {
        reached.add(ReachedAchievement.ofLogin(salatUserId, PALETTE.getKey(), levels.get(next), day.day()));
        next++;
      }
    }
    return reached;
  }
}
